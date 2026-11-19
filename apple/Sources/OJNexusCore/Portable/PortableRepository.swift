import Foundation

/// Thrown when a confirmed apply is aborted at a pre-write checkpoint.
public struct PortableApplyCancelled: Error, Sendable, CustomStringConvertible {
    public init() {}

    public var description: String { "PORTABLE APPLY CANCELLED — NO RECORDS WRITTEN" }
}

/// Orchestration between the portable codec and the bounded archive store:
/// `preview` is a pure read; `apply` revalidates from the bytes (never
/// trusting a prior preview), no-ops on an identical identity and otherwise
/// swaps the whole archive in the store's single transaction; `export`
/// re-emits the stored canonical package (its identity was re-derived and
/// checked against the stored one at load, proving no accepted record was
/// lost); `cancel` aborts an in-flight apply at cooperative pre-write
/// checkpoints and never writes.
public actor PortableRepository {
    private let store: PortableArchiveStore
    private var applyGeneration = 0

    public init(store: PortableArchiveStore) {
        self.store = store
    }

    /// Read-only validation + preview. Performs zero store calls.
    public func preview(_ bytes: Data) throws -> PortablePreview {
        try PortableCodec.preview(bytes)
    }

    /// User-confirmed transactional apply of one package.
    public func apply(_ bytes: Data) async throws -> PortableApplyResult {
        let generation = applyGeneration
        // PortableContractError propagates: whole-package reject, zero writes.
        let package = try PortableCodec.validate(bytes)

        let stored = try await store.load()
        try ensureNotCancelled(generation)

        if let stored = stored, stored.identity == package.identity {
            let decisions = PortableCodec.classify(package)
            return PortableApplyResult(
                identity: package.identity,
                editableCount: decisions.filter { $0.tier == .editable }.count,
                storedOnlyCount: decisions.filter { $0.tier == .storedOnly }.count,
                rejectedCount: 0,
                rowsUpserted: 0,
                rowsDeleted: 0,
                identical: true
            )
        }

        try ensureNotCancelled(generation) // last pre-write checkpoint.
        let snapshot = try await store.replace(package)
        let decisions = PortableCodec.classify(package)
        let previousRows = stored?.recordCounts.values.reduce(0, +) ?? 0
        return PortableApplyResult(
            identity: package.identity,
            editableCount: decisions.filter { $0.tier == .editable }.count,
            storedOnlyCount: decisions.filter { $0.tier == .storedOnly }.count,
            rejectedCount: 0,
            rowsUpserted: snapshot.recordCounts.values.reduce(0, +),
            rowsDeleted: previousRows,
            identical: false
        )
    }

    /// Re-emit the archived package's canonical bytes. The archive's integrity
    /// was proven at load: the canonical bytes are recomputed there and their
    /// SHA-256 must equal the stored identity.
    public func export() async throws -> Data {
        guard let stored = try await store.load() else {
            throw PortableExportEmpty()
        }
        return stored.canonicalPackageBytes
    }

    /// Aborts an in-flight apply at its next pre-write checkpoint. Never writes.
    public func cancel() {
        applyGeneration += 1
    }

    private func ensureNotCancelled(_ generation: Int) throws {
        if applyGeneration != generation {
            throw PortableApplyCancelled()
        }
    }
}

/// Export with nothing archived yet — a typed, visible state (never a crash).
public struct PortableExportEmpty: Error, Sendable, CustomStringConvertible {
    public init() {}

    public var description: String { "PORTABLE ARCHIVE IS EMPTY — NOTHING TO EXPORT" }
}
