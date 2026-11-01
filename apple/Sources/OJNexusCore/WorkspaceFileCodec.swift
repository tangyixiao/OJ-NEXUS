import Foundation
import CoreFoundation

enum WorkspaceFileCodec {
    static let knownKeys: Set<String> = ["formatVersion", "accounts", "ledger", "profiles", "ratingChanges", "submissions"]

    static func decode(_ data: Data) throws -> LocalWorkspaceState {
        do {
            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw WorkspaceStorageFailure.corrupt
            }
            guard Set(root.keys).isSubset(of: knownKeys) else { throw WorkspaceStorageFailure.unsupportedVersion }
            if let rawVersion = root["formatVersion"] {
                guard let number = rawVersion as? NSNumber,
                      CFGetTypeID(number) != CFBooleanGetTypeID(),
                      number.doubleValue.isFinite,
                      number.doubleValue.rounded(.towardZero) == number.doubleValue else {
                    throw WorkspaceStorageFailure.unsupportedVersion
                }
                guard number.intValue == 1 else { throw WorkspaceStorageFailure.unsupportedVersion }
            }
            return try JSONDecoder().decode(LocalWorkspaceState.self, from: data)
        } catch let failure as WorkspaceStorageFailure {
            throw failure
        } catch {
            throw WorkspaceStorageFailure.corrupt
        }
    }

    static func encode(_ state: LocalWorkspaceState) throws -> Data {
        do {
            let encoded = try JSONEncoder().encode(state)
            guard var root = try JSONSerialization.jsonObject(with: encoded) as? [String: Any] else {
                throw WorkspaceStorageFailure.unavailable
            }
            root["formatVersion"] = 1
            return try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
        } catch let failure as WorkspaceStorageFailure {
            throw failure
        } catch {
            throw WorkspaceStorageFailure.unavailable
        }
    }
}
