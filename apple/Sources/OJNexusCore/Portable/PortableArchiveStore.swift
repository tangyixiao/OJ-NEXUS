import Foundation

/// What the bounded archive currently holds: the stored package's identity and
/// its canonical bytes, ready for lossless re-export.
public struct PortableArchiveSnapshot: Sendable {
    public let identity: String
    public let canonicalPackageBytes: Data
    public let recordCounts: [String: Int]

    public init(identity: String, canonicalPackageBytes: Data, recordCounts: [String: Int]) {
        self.identity = identity
        self.canonicalPackageBytes = canonicalPackageBytes
        self.recordCounts = recordCounts
    }
}

/// File-based bounded archive for imported portable packages (phase 135).
///
/// One versioned document per store (`portable-archive.json`, formatVersion 1)
/// holding the canonical package; a full-replace import is a single atomic
/// swap built on the same machinery as `LocalWorkspaceStore` (flock, raw-byte
/// CAS, staged install with rename-or-link, snapshot-first backup). Guarded
/// startup: `load()` performs zero writes and never creates files.
public actor PortableArchiveStore {
    private let url: URL?
    private var status: WorkspaceStorageStatus = .unloaded
    /// Outer optional = "has loaded"; inner = bytes-or-nil (nil = fresh store).
    private var baseline: Data??
    private var observedMain: Data?
    private var observedBackup: Data?

    public init() {
        self.url = PortableArchiveStore.defaultURL()
    }

    public init(url: URL?) {
        self.url = url
    }

    public static func defaultURL(fileManager: FileManager = .default) -> URL? {
        guard let support = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        return support
            .appendingPathComponent("OJ-NEXUS", isDirectory: true)
            .appendingPathComponent("portable-archive.json")
    }

    public func storageStatus() -> WorkspaceStorageStatus {
        status
    }

    /// Guarded read-only load. Returns nil for a fresh store; throws typed
    /// failures for corrupt/unsupported content (recoverable when the sibling
    /// backup still decodes, mirroring the local snapshot semantics).
    public func load() throws -> PortableArchiveSnapshot? {
        guard let url = url else {
            status = .blocked(.unavailable)
            throw WorkspaceStorageFailure.unavailable
        }
        do {
            guard let bytes = try WorkspaceFileIO.read(url) else {
                if observedMain != nil || observedBackup != nil {
                    baseline = .some(nil)
                    if let backupBytes = try WorkspaceFileIO.read(backupURL(for: url)),
                       let snapshot = try decodeArchive(bytes: backupBytes) {
                        status = .recoverable(.missingEstablished)
                        baseline = nil
                        return snapshot
                    }
                    status = .blocked(.missingEstablished)
                    throw WorkspaceStorageFailure.missingEstablished
                }
                baseline = .some(nil)
                status = .ready
                return nil
            }
            observedMain = bytes
            observedBackup = try WorkspaceFileIO.read(backupURL(for: url))
            baseline = .some(bytes)
            let snapshot = try decodeArchive(bytes: bytes)
            status = .ready
            return snapshot
        } catch let failure as WorkspaceStorageFailure {
            if case .recoverable = status {} else if case .blocked = status {} else {
                status = .blocked(failure)
            }
            throw failure
        } catch {
            status = .blocked(.corrupt)
            throw WorkspaceStorageFailure.corrupt
        }
    }

    /// Transactional full replace: the canonical package becomes the archive.
    /// Single atomic swap; a prior snapshot is installed first (house order).
    public func replace(_ package: PortableValidatedPackage) throws -> PortableArchiveSnapshot {
        guard status == .ready, baseline != nil, let url = url else {
            throw WorkspaceStorageFailure.notLoaded
        }
        let document = PortableValue.obj([
            ("formatVersion", PortableValue.num("1")),
            ("identity", PortableValue.str(package.identity)),
            ("package", package.root),
        ])
        let intended = PortableJson.canonicalBytes(document)
        let snapshot = PortableArchiveSnapshot(
            identity: package.identity,
            canonicalPackageBytes: package.canonicalBytes,
            recordCounts: archiveCounts(package)
        )
        return try tryInstall(intended: intended, snapshot: snapshot, url: url)
    }

    /// Removes the archive (main + backup) under the store lock.
    public func clear() throws {
        guard status == .ready, baseline != nil, let url = url else {
            throw WorkspaceStorageFailure.notLoaded
        }
        try WorkspaceFileIO.withLock(url) {
            let backup = backupURL(for: url)
            if (try? WorkspaceFileIO.read(backup)) != nil {
                try? FileManager.default.removeItem(at: backup)
            }
            if (try? WorkspaceFileIO.read(url)) != nil {
                try? FileManager.default.removeItem(at: url)
            }
        }
        baseline = .some(nil)
        observedMain = nil
        observedBackup = nil
        status = .ready
    }

    // MARK: internals

    private func tryInstall(
        intended: Data,
        snapshot: PortableArchiveSnapshot,
        url: URL
    ) throws -> PortableArchiveSnapshot {
        do {
            try WorkspaceFileIO.withLock(url) {
                let current = try WorkspaceFileIO.read(url)
                guard current == (baseline ?? nil) else {
                    throw WorkspaceStorageFailure.conflict
                }
                let backup = backupURL(for: url)
                if let current = current {
                    _ = try WorkspaceFileIO.install(current, at: backup, replace: true)
                } else if (try WorkspaceFileIO.read(backup)) != nil {
                    throw WorkspaceStorageFailure.backupUnavailable
                } else {
                    _ = try WorkspaceFileIO.install(intended, at: backup, replace: false)
                }
                _ = try WorkspaceFileIO.install(intended, at: url, replace: current != nil)
            }
        } catch let failure as WorkspaceStorageFailure where failure == .uncertainOutcome {
            // The main install may or may not have landed; re-read and decide.
            let reread = try WorkspaceFileIO.read(url)
            if reread == intended {
                baseline = .some(intended)
                observedMain = intended
                status = .ready
                return snapshot
            }
            throw WorkspaceStorageFailure.uncertainOutcome
        }
        baseline = .some(intended)
        observedMain = intended
        status = .ready
        return snapshot
    }

    private func backupURL(for url: URL) -> URL {
        URL(fileURLWithPath: url.path + ".bak")
    }

    /// Strict archive decode: format version gate, shape checks, and an
    /// integrity re-derivation — the canonical package bytes are recomputed
    /// and their SHA-256 must equal the stored identity.
    private func decodeArchive(bytes: Data) throws -> PortableArchiveSnapshot {
        let parsed: PortableValue
        do {
            parsed = try PortableJson.parse(bytes)
        } catch let failure as PortableContractError {
            throw archiveFailure(failure.reject)
        }
        guard case .obj = parsed else {
            throw WorkspaceStorageFailure.corrupt
        }
        guard case .num(let version) = parsed.get("formatVersion") ?? .null,
              version == "1" else {
            throw WorkspaceStorageFailure.unsupportedVersion
        }
        guard case .str(let identity) = parsed.get("identity") ?? .null,
              identity.count == 64 else {
            throw WorkspaceStorageFailure.corrupt
        }
        guard let package = parsed.get("package"), case .obj = package else {
            throw WorkspaceStorageFailure.corrupt
        }
        let canonical = PortableJson.canonicalBytes(package)
        let derived = PortableCodec.sha256Hex(canonical)
        guard derived == identity else {
            throw WorkspaceStorageFailure.corrupt
        }
        var recordCounts = [String: Int]()
        for collection in PortableSpec.collections {
            if case .arr(let items) = package.get(collection) ?? .null {
                recordCounts[collection] = items.count
            } else {
                recordCounts[collection] = 0
            }
        }
        return PortableArchiveSnapshot(
            identity: identity,
            canonicalPackageBytes: canonical,
            recordCounts: recordCounts
        )
    }

    /// Map a codec rejection onto the store's typed failure vocabulary:
    /// unsupported versions stay distinct, every other violation is corrupt.
    private func archiveFailure(_ reject: PortableReject) -> WorkspaceStorageFailure {
        reject.category == .unsupportedVersion ? .unsupportedVersion : .corrupt
    }

    private func archiveCounts(_ package: PortableValidatedPackage) -> [String: Int] {
        var counts = [String: Int]()
        for collection in PortableSpec.collections {
            counts[collection] = package.records[collection]?.count ?? 0
        }
        return counts
    }
}
