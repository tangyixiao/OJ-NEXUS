import Foundation

public enum WorkspaceStorageFailure: Error, Equatable, Sendable {
    case notLoaded, corrupt, unsupportedVersion, unavailable, busy, conflict
    case backupUnavailable, recoveryUnavailable, uncertainOutcome, missingEstablished

    public var rawValue: String {
        switch self {
        case .notLoaded: return "NOT LOADED"
        case .corrupt: return "CORRUPT"
        case .unsupportedVersion: return "UNSUPPORTED VERSION"
        case .unavailable: return "UNAVAILABLE"
        case .busy: return "BUSY"
        case .conflict: return "CONFLICT"
        case .backupUnavailable: return "BACKUP UNAVAILABLE"
        case .recoveryUnavailable: return "RECOVERY UNAVAILABLE"
        case .uncertainOutcome: return "UNCERTAIN OUTCOME"
        case .missingEstablished: return "ESTABLISHED STATE MISSING"
        }
    }
}

public enum WorkspaceStorageStatus: Equatable, Sendable {
    case unloaded
    case ready
    case blocked(WorkspaceStorageFailure)
    case recoverable(WorkspaceStorageFailure)
}
