import Foundation

public struct LuoguAdapter: JudgeAdapter, Sendable {
    public let judge: JudgeID = .luogu

    public init() {}

    public func profileURL(for handle: String) throws -> URL {
        guard let account = JudgeAccount(judge: .luogu, handle: handle),
              let uid = account.handle.split(separator: ":", maxSplits: 1).last,
              !uid.isEmpty,
              uid.allSatisfy({ $0.wholeNumberValue != nil }),
              let url = URL(string: "https://www.luogu.com.cn/api/user/info/\(uid)") else {
            throw AdapterError.invalidPayload
        }
        return url
    }

    public func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile {
        let envelope: Envelope
        do {
            envelope = try JSONDecoder().decode(Envelope.self, from: data)
        } catch {
            throw AdapterError.invalidPayload
        }
        guard let account = JudgeAccount(judge: .luogu, handle: requestedHandle),
              let requestedUID = account.handle.split(separator: ":", maxSplits: 1).last.flatMap({ Int($0) }),
              let user = envelope.user,
              let userUID = user.uid,
              userUID == requestedUID else {
            throw AdapterError.apiFailure
        }
        return PublicProfile(judge: account.judge, handle: account.handle, rating: user.elo,
                             displayName: user.name)
    }

    private struct Envelope: Decodable, Sendable {
        let user: User?
    }

    private struct User: Decodable, Sendable {
        let uid: Int?
        let name: String?
        let elo: Int?
    }
}
