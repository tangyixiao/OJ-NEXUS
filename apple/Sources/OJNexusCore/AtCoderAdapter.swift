import Foundation

public struct AtCoderAdapter: JudgeAdapter, Sendable {
    public let judge: JudgeID = .atcoder

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
        guard titleAccount.handle.caseInsensitiveCompare(requestedAccount.handle) == .orderedSame else {
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

    private struct RatingRow: Decodable, Sendable {
        let rating: Int?

        private enum CodingKeys: String, CodingKey {
            case rating = "Rating"
        }
    }
}
