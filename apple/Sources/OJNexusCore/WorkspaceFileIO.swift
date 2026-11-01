import Foundation
import Darwin

enum WorkspaceFileIO {
    static func failure(_ error: Error) -> WorkspaceStorageFailure {
        if let failure = error as? WorkspaceStorageFailure { return failure }
        if let posix = error as? POSIXError,
           posix.code == .EWOULDBLOCK || posix.code == .EAGAIN { return .busy }
        return .unavailable
    }

    static func read(_ url: URL) throws -> Data? {
        let descriptor = open(url.path, O_RDONLY)
        guard descriptor >= 0 else {
            let code = errno
            if code == ENOENT {
                var parent = url.deletingLastPathComponent().path
                while true {
                    var info = stat()
                    if lstat(parent, &info) == 0 {
                        guard (info.st_mode & S_IFMT) == S_IFDIR else { throw WorkspaceStorageFailure.unavailable }
                        return nil
                    }
                    if errno != ENOENT { throw WorkspaceStorageFailure.unavailable }
                    let next = URL(fileURLWithPath: parent).deletingLastPathComponent().path
                    if next == parent || next.isEmpty { throw WorkspaceStorageFailure.unavailable }
                    parent = next
                }
            }
            throw WorkspaceStorageFailure.unavailable
        }
        defer { _ = close(descriptor) }
        var info = stat()
        guard fstat(descriptor, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG else {
            throw WorkspaceStorageFailure.unavailable
        }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 16_384)
        while true {
            let count = buffer.withUnsafeMutableBytes { raw in
                Darwin.read(descriptor, raw.baseAddress, raw.count)
            }
            if count == 0 { return data }
            if count < 0 {
                if errno == EINTR { continue }
                throw WorkspaceStorageFailure.unavailable
            }
            data.append(contentsOf: buffer.prefix(count))
        }
    }

    static func withLock<T>(url: URL, _ body: () throws -> T) throws -> T {
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let lockURL = directory.appendingPathComponent("state.json.lock")
        let descriptor = open(lockURL.path, O_CREAT | O_RDWR, mode_t(0o600))
        guard descriptor >= 0 else { throw WorkspaceStorageFailure.unavailable }
        defer { _ = close(descriptor) }
        guard flock(descriptor, LOCK_EX | LOCK_NB) == 0 else {
            if errno == EWOULDBLOCK || errno == EAGAIN { throw WorkspaceStorageFailure.busy }
            throw WorkspaceStorageFailure.unavailable
        }
        defer { _ = flock(descriptor, LOCK_UN) }
        return try body()
    }

    static func install(_ data: Data, at destination: URL, replace: Bool) throws {
        let stage = destination.deletingLastPathComponent()
            .appendingPathComponent(".state-stage-\(UUID().uuidString)")
        let descriptor = open(stage.path, O_CREAT | O_EXCL | O_WRONLY, mode_t(0o600))
        guard descriptor >= 0 else { throw WorkspaceStorageFailure.unavailable }
        var ownsStage = true
        var descriptorOpen = true
        defer {
            if descriptorOpen { _ = close(descriptor) }
            if ownsStage { _ = unlink(stage.path) }
        }
        try data.withUnsafeBytes { raw in
            guard let base = raw.baseAddress else { return }
            var offset = 0
            while offset < raw.count {
                let count = Darwin.write(descriptor, base.advanced(by: offset), raw.count - offset)
                if count < 0 && errno == EINTR { continue }
                guard count > 0 else { throw WorkspaceStorageFailure.unavailable }
                offset += count
            }
        }
        guard fsync(descriptor) == 0 else { throw WorkspaceStorageFailure.unavailable }
        let closeResult = close(descriptor)
        descriptorOpen = false
        guard closeResult == 0 else { throw WorkspaceStorageFailure.unavailable }
        if replace {
            guard rename(stage.path, destination.path) == 0 else { throw WorkspaceStorageFailure.unavailable }
        } else {
            guard link(stage.path, destination.path) == 0 else {
                if errno == EEXIST { throw WorkspaceStorageFailure.conflict }
                throw WorkspaceStorageFailure.unavailable
            }
            _ = unlink(stage.path)
        }
        ownsStage = false
    }
}
