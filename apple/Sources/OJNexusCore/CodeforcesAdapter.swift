import Foundation

#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct CodeforcesAdapter: JudgeAdapter, Sendable {
    public let judge: JudgeID = .codeforces
    public let reliability = "OFFICIAL"
    public let capabilities = ["PROFILE", "RATING", "SUBMISSIONS"]

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

    public func ratingURL(for handle: String) throws -> URL {
        guard let normalized = JudgeAccount(judge: .codeforces, handle: handle)?.handle else {
            throw AdapterError.invalidPayload
        }
        var components = URLComponents(string: "https://codeforces.com/api/user.rating")
        components?.queryItems = [URLQueryItem(name: "handle", value: normalized)]
        guard let url = components?.url else { throw AdapterError.invalidPayload }
        return url
    }

    public func submissionsURL(for handle: String) throws -> URL {
        guard let normalized = JudgeAccount(judge: .codeforces, handle: handle)?.handle else {
            throw AdapterError.invalidPayload
        }
        var components = URLComponents(string: "https://codeforces.com/api/user.status")
        components?.queryItems = [
            URLQueryItem(name: "handle", value: normalized),
            URLQueryItem(name: "from", value: "1"),
            URLQueryItem(name: "count", value: "1000"),
        ]
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

        guard let requestedAccount = JudgeAccount(judge: .codeforces, handle: requestedHandle),
              envelope.status == "OK", let dto = envelope.result.first,
              let returnedHandle = dto.handle,
              let account = JudgeAccount(judge: .codeforces, handle: returnedHandle),
              judgeHandlesMatch(.codeforces, account.handle, requestedAccount.handle) else {
            throw AdapterError.apiFailure
        }
        let displayName = [dto.firstName, dto.lastName]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return PublicProfile(judge: account.judge, handle: account.handle, rating: dto.rating,
                             rank: dto.rank, displayName: displayName.isEmpty ? nil : displayName)
    }

    public func decodeRating(_ data: Data, requestedHandle: String) throws -> [RatingChange] {
        let envelope: RatingEnvelope
        do {
            envelope = try JSONDecoder().decode(RatingEnvelope.self, from: data)
        } catch {
            throw AdapterError.invalidPayload
        }
        guard let account = JudgeAccount(judge: .codeforces, handle: requestedHandle),
              envelope.status == "OK" else {
            throw AdapterError.apiFailure
        }
        return try envelope.result.map { dto in
            guard let newRating = dto.newRating,
                  let change = RatingChange(
                      judge: account.judge,
                      handle: account.handle,
                      contestID: String(dto.contestID),
                      contestName: dto.contestName,
                      oldRating: dto.oldRating,
                      newRating: newRating,
                      rank: dto.rank,
                      occurredAt: dto.ratingUpdateTimeSeconds.map {
                          Date(timeIntervalSince1970: TimeInterval($0))
                      }) else {
                throw AdapterError.invalidPayload
            }
            return change
        }
    }

    public func decodeSubmissions(_ data: Data, requestedHandle: String) throws -> [SubmissionRecord] {
        let envelope: SubmissionEnvelope
        do {
            envelope = try JSONDecoder().decode(SubmissionEnvelope.self, from: data)
        } catch {
            throw AdapterError.invalidPayload
        }
        guard let account = JudgeAccount(judge: .codeforces, handle: requestedHandle),
              envelope.status == "OK" else {
            throw AdapterError.apiFailure
        }
        return try envelope.result.map { dto in
            let problemID: String?
            if let index = dto.problem?.index {
                problemID = dto.contestID.map { "\($0)\(index)" } ?? index
            } else {
                problemID = nil
            }
            guard dto.id > 0,
                  let verdict = dto.verdict,
                  let submission = SubmissionRecord(
                      judge: account.judge,
                      handle: account.handle,
                      externalID: String(dto.id),
                      verdict: verdict,
                      problemID: problemID,
                      problemName: dto.problem?.name,
                      language: dto.programmingLanguage,
                      submittedAt: dto.creationTimeSeconds.map {
                          Date(timeIntervalSince1970: TimeInterval($0))
                      }) else {
                throw AdapterError.invalidPayload
            }
            return submission
        }
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

    private struct RatingEnvelope: Decodable, Sendable {
        let status: String
        let result: [RatingDTO]

        private enum CodingKeys: String, CodingKey {
            case status
            case result
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            status = try container.decode(String.self, forKey: .status)
            result = try container.decodeIfPresent([RatingDTO].self, forKey: .result) ?? []
        }
    }

    private struct RatingDTO: Decodable, Sendable {
        let contestID: Int
        let contestName: String?
        let oldRating: Int?
        let newRating: Int?
        let rank: Int?
        let ratingUpdateTimeSeconds: Int?

        private enum CodingKeys: String, CodingKey {
            case contestID = "contestId"
            case contestName
            case oldRating
            case newRating
            case rank
            case ratingUpdateTimeSeconds
        }
    }

    private struct SubmissionEnvelope: Decodable, Sendable {
        let status: String
        let result: [SubmissionDTO]

        private enum CodingKeys: String, CodingKey {
            case status
            case result
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            status = try container.decode(String.self, forKey: .status)
            result = try container.decodeIfPresent([SubmissionDTO].self, forKey: .result) ?? []
        }
    }

    private struct SubmissionDTO: Decodable, Sendable {
        let id: Int
        let contestID: Int?
        let creationTimeSeconds: Int?
        let verdict: String?
        let programmingLanguage: String?
        let problem: ProblemDTO?

        private enum CodingKeys: String, CodingKey {
            case id
            case contestID = "contestId"
            case creationTimeSeconds
            case verdict
            case programmingLanguage
            case problem
        }
    }

    private struct ProblemDTO: Decodable, Sendable {
        let index: String?
        let name: String?
    }

    private struct ProfileDTO: Decodable, Sendable {
        let handle: String?
        let firstName: String?
        let lastName: String?
        let rating: Int?
        let rank: String?
    }
}
