import Foundation

public struct AtCoderAdapter: JudgeAdapter, Sendable {
    public let judge: JudgeID = .atcoder
    public let reliability = "COMMUNITY_SOURCE_WITH_EXPERIMENTAL_PROFILE"
    public let capabilities = ["PROFILE", "RATING", "SUBMISSIONS"]

    public init() {}

    public func profileURL(for handle: String) throws -> URL {
        guard let account = JudgeAccount(judge: .atcoder, handle: handle),
              account.handle.unicodeScalars.allSatisfy({
                  ($0.value >= 48 && $0.value <= 57) ||
                  ($0.value >= 65 && $0.value <= 90) ||
                  ($0.value >= 97 && $0.value <= 122) ||
                  $0.value == 45 || $0.value == 95
              }),
              let url = URL(string: "https://atcoder.jp/users/\(account.handle)") else {
            throw AdapterError.invalidPayload
        }
        return url
    }

    public func ratingURL(for handle: String) throws -> URL {
        try profileURL(for: handle)
    }

    public func submissionsURL(for handle: String) throws -> URL {
        guard let account = JudgeAccount(judge: .atcoder, handle: handle),
              var components = URLComponents(
                  string: "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions") else {
            throw AdapterError.invalidPayload
        }
        components.queryItems = [
            URLQueryItem(name: "user", value: account.handle),
            URLQueryItem(name: "from_second", value: "0"),
        ]
        guard let url = components.url else { throw AdapterError.invalidPayload }
        return url
    }

    public func decodeProfile(_ data: Data, requestedHandle: String) throws -> PublicProfile {
        guard let html = String(data: data, encoding: .utf8),
              let requestedAccount = JudgeAccount(judge: .atcoder, handle: requestedHandle) else {
            throw AdapterError.invalidPayload
        }
        let pattern = #"<title>\s*([^<]+?)\s*-\s*AtCoder"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let range = Range(match.range(at: 1), in: html) else {
            throw AdapterError.apiFailure
        }
        let titleHandle = String(html[range]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard let titleAccount = JudgeAccount(judge: .atcoder, handle: titleHandle) else {
            throw AdapterError.invalidPayload
        }
        guard judgeHandlesMatch(.atcoder, titleAccount.handle, requestedAccount.handle) else {
            throw AdapterError.apiFailure
        }
        let historyPattern = #"var\s+rank_history\s*=\s*(\[.*?\]);\s*</script>"#
        var rating: Int?
        if let historyRegex = try? NSRegularExpression(pattern: historyPattern, options: [.dotMatchesLineSeparators]),
           let historyMatch = historyRegex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
           let historyRange = Range(historyMatch.range(at: 1), in: html) {
            do {
                let rows = try JSONDecoder().decode([RatingRow].self, from: Data(html[historyRange].utf8))
                rating = rows.last?.rating
            } catch {
                throw AdapterError.invalidPayload
            }
        }
        return PublicProfile(judge: titleAccount.judge, handle: titleAccount.handle, rating: rating)
    }

    public func decodeRating(_ data: Data, requestedHandle: String) throws -> [RatingChange] {
        guard let html = String(data: data, encoding: .utf8),
              let requestedAccount = JudgeAccount(judge: .atcoder, handle: requestedHandle) else {
            throw AdapterError.invalidPayload
        }
        let titlePattern = #"<title>\s*([^<]+?)\s*-\s*AtCoder"#
        guard let titleRegex = try? NSRegularExpression(pattern: titlePattern, options: [.caseInsensitive]),
              let titleMatch = titleRegex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let titleRange = Range(titleMatch.range(at: 1), in: html) else {
            throw AdapterError.apiFailure
        }
        let titleHandle = String(html[titleRange]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard let titleAccount = JudgeAccount(judge: .atcoder, handle: titleHandle),
              judgeHandlesMatch(.atcoder, titleAccount.handle, requestedAccount.handle) else {
            throw AdapterError.apiFailure
        }
        let historyPattern = #"var\s+rank_history\s*=\s*(\[.*?\]);\s*</script>"#
        guard let historyRegex = try? NSRegularExpression(
                  pattern: historyPattern, options: [.dotMatchesLineSeparators]),
              let historyMatch = historyRegex.firstMatch(
                  in: html, range: NSRange(html.startIndex..., in: html)),
              let historyRange = Range(historyMatch.range(at: 1), in: html) else {
            return []
        }
        let rows: [RatingRow]
        do {
            rows = try JSONDecoder().decode([RatingRow].self, from: Data(html[historyRange].utf8))
        } catch {
            throw AdapterError.invalidPayload
        }
        var changes: [RatingChange] = []
        var previousRating: Int?
        for row in rows {
            guard let newRating = row.rating else { continue }
            guard let contestURL = row.contestURL,
                  let contestID = contestURL.split(separator: "/").last,
                  !contestID.isEmpty,
                  let change = RatingChange(
                      judge: titleAccount.judge,
                      handle: titleAccount.handle,
                      contestID: String(contestID),
                      contestName: row.contestName,
                      oldRating: row.oldRating ?? previousRating,
                      newRating: newRating,
                      rank: row.rank,
                      occurredAt: row.endTime.flatMap(Self.parseDate)) else {
                throw AdapterError.invalidPayload
            }
            changes.append(change)
            previousRating = newRating
        }
        return changes
    }

    public func decodeSubmissions(_ data: Data, requestedHandle: String) throws -> [SubmissionRecord] {
        guard let account = JudgeAccount(judge: .atcoder, handle: requestedHandle) else {
            throw AdapterError.invalidPayload
        }
        let rows: [SubmissionDTO]
        do {
            rows = try JSONDecoder().decode([SubmissionDTO].self, from: data)
        } catch {
            throw AdapterError.invalidPayload
        }
        return try rows.map { row in
            guard row.id > 0,
                  let verdict = row.result,
                  let submission = SubmissionRecord(
                      judge: account.judge,
                      handle: account.handle,
                      externalID: String(row.id),
                      verdict: verdict,
                      problemID: row.problemID,
                      language: row.language,
                      submittedAt: row.epochSecond.map {
                          Date(timeIntervalSince1970: TimeInterval($0))
                      }) else {
                throw AdapterError.invalidPayload
            }
            return submission
        }
    }

    private struct RatingRow: Decodable, Sendable {
        let rating: Int?
        let oldRating: Int?
        let rank: Int?
        let contestName: String?
        let contestURL: String?
        let endTime: String?

        private enum CodingKeys: String, CodingKey {
            case rating = "Rating"
            case oldRating = "OldRating"
            case rank = "Rank"
            case contestName = "ContestName"
            case contestURL = "ContestUrl"
            case endTime = "EndTime"
        }
    }

    private struct SubmissionDTO: Decodable, Sendable {
        let id: Int
        let problemID: String?
        let epochSecond: Int?
        let result: String?
        let language: String?

        private enum CodingKeys: String, CodingKey {
            case id
            case problemID = "problem_id"
            case epochSecond = "epoch_second"
            case result
            case language
        }
    }

    private static func parseDate(_ value: String) -> Date? {
        ISO8601DateFormatter().date(from: value)
    }
}
