import Foundation

#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct CodeforcesAdapter: JudgeAdapter, Sendable {
    public let judge: JudgeID = .codeforces

    public init() {}

    public func profileURL(for handle: String) throws -> URL {
        guard let normalized = JudgeAccount(judge: .codeforces, handle: handle)?.handle else {
            throw AdapterError.invalidPayload
        }
        var components = URLComponents(string: "https://codeforces.com/api/user.info")
        components?.queryItems = [URLQueryItem(name: "handles", value: normalized)]
        guard let url = components?.url else { throw AdapterError.invalidPayload }
        return url
    }

    public func fetchProfile(handle: String, using client: any HTTPClient) async throws -> PublicProfile {
        let url = try profileURL(for: handle)
        let data = try await client.get(url)
        return try decodeProfile(data, requestedHandle: handle)
    }

    public func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile {
        let envelope: Envelope
        do {
            envelope = try JSONDecoder().decode(Envelope.self, from: data)
        } catch {
            throw AdapterError.invalidPayload
        }

        guard envelope.status == "OK", let dto = envelope.result.first,
              let account = JudgeAccount(judge: .codeforces, handle: dto.handle ?? requestedHandle) else {
            throw AdapterError.apiFailure
        }
        let displayName = [dto.firstName, dto.lastName]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return PublicProfile(judge: account.judge, handle: account.handle, rating: dto.rating,
                             rank: dto.rank, displayName: displayName.isEmpty ? nil : displayName)
    }

    private struct Envelope: Decodable, Sendable {
        let status: String
        let result: [ProfileDTO]

        private enum CodingKeys: String, CodingKey {
            case status
            case result
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            status = try container.decode(String.self, forKey: .status)
            result = try container.decodeIfPresent([ProfileDTO].self, forKey: .result) ?? []
        }
    }

    private struct ProfileDTO: Decodable, Sendable {
        let handle: String?
        let firstName: String?
        let lastName: String?
        let rating: Int?
        let rank: String?
    }
}
