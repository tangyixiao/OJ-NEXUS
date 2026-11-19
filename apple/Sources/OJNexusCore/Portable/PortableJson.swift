import Foundation

/// A decoded portable JSON value. Object entries keep document order.
///
/// Key equality and ordering are **code-point (UTF-8 byte) based**, never
/// Swift's canonical-equivalence `String ==`: the frozen contract (and the
/// Python/C# oracles) treat canonically-equivalent spellings as distinct
/// object keys.
public indirect enum PortableValue: Sendable {
    /// An object with ordered entries; duplicates are rejected at decode time.
    case obj([(key: String, value: PortableValue)])
    case arr([PortableValue])
    case str(String)
    /// A number preserved verbatim — the exact source number text is the canonical form.
    case num(String)
    case bool(Bool)
    case null

    public func get(_ key: String) -> PortableValue? {
        guard case .obj(let entries) = self else { return nil }
        let wanted = Array(key.utf8)
        for entry in entries where entry.key.utf8.elementsEqual(wanted) {
            return entry.value
        }
        return nil
    }
}

/// Strict portable JSON decode and canonical re-encode, hand-written because
/// JSONSerialization/JSONDecoder normalize duplicate keys and fold numbers
/// through double — both break the frozen contract.
///
/// The decode is stricter than any platform default (duplicate keys, trailing
/// data, comments, NaN/Infinity, lone surrogates, invalid UTF-8 are all
/// `INVALID_INPUT`) and the writer reproduces the Python canonical form
/// byte-for-byte:
/// `json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"`.
/// Numbers are preserved verbatim (mirroring Android's `Num(literal)`).
public enum PortableJson {
    static let maxDepth = 200

    // MARK: decode

    public static func parse(_ data: Data) throws -> PortableValue {
        let bytes = [UInt8](data)
        if bytes.count >= 3, bytes[0] == 0xEF, bytes[1] == 0xBB, bytes[2] == 0xBF {
            throw invalidInput("byte order mark")
        }
        var scanner = Scanner(bytes: bytes)
        scanner.skipWhitespace()
        let value = try scanner.parseValue(depth: 1)
        scanner.skipWhitespace()
        if !scanner.atEnd {
            throw invalidInput("trailing data after the JSON document")
        }
        return value
    }

    struct Scanner {
        let bytes: [UInt8]
        var index = 0

        var atEnd: Bool { index >= bytes.count }

        mutating func skipWhitespace() {
            while index < bytes.count {
                switch bytes[index] {
                case 0x20, 0x09, 0x0A, 0x0D:
                    index += 1
                default:
                    return
                }
            }
        }

        mutating func parseValue(depth: Int) throws -> PortableValue {
            if depth > PortableJson.maxDepth {
                throw invalidInput("nesting depth over \(PortableJson.maxDepth)")
            }
            guard index < bytes.count else {
                throw invalidInput("empty input")
            }
            switch bytes[index] {
            case Byte.openBrace:
                return try parseObject(depth: depth)
            case Byte.openBracket:
                return try parseArray(depth: depth)
            case Byte.quote:
                return .str(try parseString())
            case Byte.t:
                try parseLiteral("true")
                return .bool(true)
            case Byte.f:
                try parseLiteral("false")
                return .bool(false)
            case Byte.n:
                try parseLiteral("null")
                return .null
            default:
                return try parseNumber()
            }
        }

        mutating func parseLiteral(_ literal: String) throws {
            for expected in literal.utf8 {
                guard index < bytes.count, bytes[index] == expected else {
                    throw invalidInput("invalid literal")
                }
                index += 1
            }
        }

        mutating func parseNumber() throws -> PortableValue {
            let start = index
            if index < bytes.count, bytes[index] == Byte.dash {
                index += 1
            }
            // integer part: "0" or [1-9][0-9]*
            guard index < bytes.count else { throw invalidInput("invalid number") }
            if bytes[index] == Byte.zero {
                index += 1
            } else if (Byte.one...Byte.nine).contains(bytes[index]) {
                index += 1
                while index < bytes.count, isDigit(bytes[index]) {
                    index += 1
                }
            } else {
                throw invalidInput("invalid number")
            }
            if index < bytes.count, bytes[index] == Byte.dot {
                index += 1
                guard index < bytes.count, isDigit(bytes[index]) else {
                    throw invalidInput("invalid number")
                }
                while index < bytes.count, isDigit(bytes[index]) {
                    index += 1
                }
            }
            if index < bytes.count, bytes[index] == Byte.e || bytes[index] == Byte.eUpper {
                index += 1
                if index < bytes.count, bytes[index] == Byte.plus || bytes[index] == Byte.dash {
                    index += 1
                }
                guard index < bytes.count, isDigit(bytes[index]) else {
                    throw invalidInput("invalid number")
                }
                while index < bytes.count, isDigit(bytes[index]) {
                    index += 1
                }
            }
            var literal = String(decoding: bytes[start..<index], as: UTF8.self)
            // Android's normalizeNumber: "-0" canonicalizes to "0" (Python int
            // semantics do the same at dumps time).
            if literal == "-0" { literal = "0" }
            return .num(literal)
        }

        mutating func parseString() throws -> String {
            index += 1 // opening quote (caller verified)
            var out = [UInt8]()
            while true {
                guard index < bytes.count else { throw invalidInput("unterminated string") }
                let byte = bytes[index]
                if byte == Byte.quote {
                    index += 1
                    return try stringFromUTF8(out)
                }
                if byte == Byte.backslash {
                    index += 1
                    guard index < bytes.count else { throw invalidInput("unterminated escape") }
                    let escape = bytes[index]
                    switch escape {
                    case Byte.quote:
                        out.append(Byte.quote); index += 1
                    case Byte.backslash:
                        out.append(Byte.backslash); index += 1
                    case Byte.slash:
                        out.append(Byte.slash); index += 1
                    case Byte.b:
                        out.append(0x08); index += 1
                    case Byte.f:
                        out.append(0x0C); index += 1
                    case Byte.n:
                        out.append(0x0A); index += 1
                    case Byte.r:
                        out.append(0x0D); index += 1
                    case Byte.t:
                        out.append(0x09); index += 1
                    case Byte.u:
                        index += 1
                        let first = try parseHex4()
                        if (0xD800...0xDBFF).contains(first) {
                            // An adjacent low-surrogate escape must follow.
                            guard index + 1 < bytes.count,
                                  bytes[index] == Byte.backslash,
                                  bytes[index + 1] == Byte.u else {
                                throw invalidInput("lone surrogate escape")
                            }
                            index += 2
                            let second = try parseHex4()
                            guard (0xDC00...0xDFFF).contains(second) else {
                                throw invalidInput("lone surrogate escape")
                            }
                            let scalar = 0x10000 + ((first - 0xD800) << 10) + (second - 0xDC00)
                            appendScalar(scalar, to: &out)
                        } else if (0xDC00...0xDFFF).contains(first) {
                            throw invalidInput("lone surrogate escape")
                        } else {
                            appendScalar(first, to: &out)
                        }
                    default:
                        throw invalidInput("invalid escape")
                    }
                    continue
                }
                if byte < 0x20 {
                    throw invalidInput("control character in string")
                }
                out.append(byte)
                index += 1
            }
        }

        mutating func parseHex4() throws -> UInt32 {
            guard index + 4 <= bytes.count else { throw invalidInput("invalid unicode escape") }
            var value: UInt32 = 0
            for _ in 0..<4 {
                let byte = bytes[index]
                let digit: UInt32
                switch byte {
                case Byte.zero...Byte.nine:
                    digit = UInt32(byte - Byte.zero)
                case Byte.a...Byte.f:
                    digit = UInt32(byte - Byte.a) + 10
                case Byte.aUpper...Byte.fUpper:
                    digit = UInt32(byte - Byte.aUpper) + 10
                default:
                    throw invalidInput("invalid unicode escape")
                }
                value = value << 4 | digit
                index += 1
            }
            return value
        }

        mutating func parseObject(depth: Int) throws -> PortableValue {
            index += 1
            var entries: [(key: String, value: PortableValue)] = []
            var seen = Set<[UInt8]>()
            skipWhitespace()
            if index < bytes.count, bytes[index] == Byte.closeBrace {
                index += 1
                return .obj(entries)
            }
            while true {
                skipWhitespace()
                guard index < bytes.count, bytes[index] == Byte.quote else {
                    throw invalidInput("object member name expected")
                }
                let key = try parseString()
                let keyBytes = Array(key.utf8)
                if !seen.insert(keyBytes).inserted {
                    throw invalidInput("duplicate object key '\(key)'")
                }
                skipWhitespace()
                guard index < bytes.count, bytes[index] == Byte.colon else {
                    throw invalidInput("expected ':' in object")
                }
                index += 1
                skipWhitespace()
                let value = try parseValue(depth: depth + 1)
                entries.append((key, value))
                skipWhitespace()
                guard index < bytes.count else { throw invalidInput("unterminated object") }
                if bytes[index] == Byte.comma {
                    index += 1
                    continue
                }
                if bytes[index] == Byte.closeBrace {
                    index += 1
                    return .obj(entries)
                }
                throw invalidInput("expected ',' or '}' in object")
            }
        }

        mutating func parseArray(depth: Int) throws -> PortableValue {
            index += 1
            var items = [PortableValue]()
            skipWhitespace()
            if index < bytes.count, bytes[index] == Byte.closeBracket {
                index += 1
                return .arr(items)
            }
            while true {
                skipWhitespace()
                let item = try parseValue(depth: depth + 1)
                items.append(item)
                skipWhitespace()
                guard index < bytes.count else { throw invalidInput("unterminated array") }
                if bytes[index] == Byte.comma {
                    index += 1
                    continue
                }
                if bytes[index] == Byte.closeBracket {
                    index += 1
                    return .arr(items)
                }
                throw invalidInput("expected ',' or ']' in array")
            }
        }

        func isDigit(_ byte: UInt8) -> Bool {
            (Byte.zero...Byte.nine).contains(byte)
        }
    }

    // MARK: strict UTF-8

    /// Strict UTF-8 validation and decoding: rejects overlong forms, UTF-8
    /// encoded surrogates, and anything above U+10FFFF — the same set a JVM or
    /// Qt decoder rejects, so one decode passes on every platform.
    static func stringFromUTF8(_ bytes: [UInt8]) throws -> String {
        var scalars = [Unicode.Scalar]()
        scalars.reserveCapacity(bytes.count)
        var index = 0
        while index < bytes.count {
            let byte = bytes[index]
            if byte < 0x80 {
                scalars.append(Unicode.Scalar(byte))
                index += 1
                continue
            }
            var codePoint: UInt32
            let length: Int
            switch byte {
            case 0xC2...0xDF:
                codePoint = UInt32(byte & 0x1F)
                length = 2
            case 0xE0:
                codePoint = UInt32(byte & 0x0F)
                length = 3
            case 0xE1...0xEC, 0xEE...0xEF:
                codePoint = UInt32(byte & 0x0F)
                length = 3
            case 0xED:
                // Second byte must stay below U+D800: encoded surrogates rejected.
                codePoint = UInt32(byte & 0x0F)
                length = 3
            case 0xF0:
                codePoint = UInt32(byte & 0x07)
                length = 4
            case 0xF1...0xF3:
                codePoint = UInt32(byte & 0x07)
                length = 4
            case 0xF4:
                // Fourth byte must stay within U+10FFFF.
                codePoint = UInt32(byte & 0x07)
                length = 4
            default:
                throw invalidInput("invalid UTF-8 in string")
            }
            guard index + length <= bytes.count else {
                throw invalidInput("invalid UTF-8 in string")
            }
            for offset in 1..<length {
                let continuation = bytes[index + offset]
                guard (0x80...0xBF).contains(continuation) else {
                    throw invalidInput("invalid UTF-8 in string")
                }
                codePoint = (codePoint << 6) | UInt32(continuation & 0x3F)
            }
            switch byte {
            case 0xE0:
                guard (0xA0...0xBF).contains(bytes[index + 1]) else {
                    throw invalidInput("invalid UTF-8 in string")
                }
            case 0xED:
                guard (0x80...0x9F).contains(bytes[index + 1]) else {
                    throw invalidInput("invalid UTF-8 in string")
                }
            case 0xF0:
                guard (0x90...0xBF).contains(bytes[index + 1]) else {
                    throw invalidInput("invalid UTF-8 in string")
                }
            case 0xF4:
                guard (0x80...0x8F).contains(bytes[index + 1]) else {
                    throw invalidInput("invalid UTF-8 in string")
                }
            default:
                break
            }
            guard let scalar = Unicode.Scalar(codePoint) else {
                throw invalidInput("invalid UTF-8 in string")
            }
            scalars.append(scalar)
            index += length
        }
        return String(String.UnicodeScalarView(scalars))
    }

    static func appendScalar(_ codePoint: UInt32, to out: inout [UInt8]) {
        switch codePoint {
        case 0..<0x80:
            out.append(UInt8(codePoint))
        case 0x80..<0x800:
            out.append(UInt8(0xC0 | (codePoint >> 6)))
            out.append(UInt8(0x80 | (codePoint & 0x3F)))
        case 0x800..<0x10000:
            out.append(UInt8(0xE0 | (codePoint >> 12)))
            out.append(UInt8(0x80 | ((codePoint >> 6) & 0x3F)))
            out.append(UInt8(0x80 | (codePoint & 0x3F)))
        default:
            out.append(UInt8(0xF0 | (codePoint >> 18)))
            out.append(UInt8(0x80 | ((codePoint >> 12) & 0x3F)))
            out.append(UInt8(0x80 | ((codePoint >> 6) & 0x3F)))
            out.append(UInt8(0x80 | (codePoint & 0x3F)))
        }
    }

    // MARK: canonical encode

    /// Canonical bytes: keys sorted by Unicode code point, compact separators,
    /// Python escaping, trailing newline.
    public static func canonicalBytes(_ value: PortableValue) -> Data {
        var out = [UInt8]()
        writeCanonical(value, into: &out)
        out.append(Byte.newline)
        return Data(out)
    }

    static func writeCanonical(_ value: PortableValue, into out: inout [UInt8]) {
        switch value {
        case .null:
            out.append(contentsOf: Array("null".utf8))
        case .bool(let boolean):
            out.append(contentsOf: Array((boolean ? "true" : "false").utf8))
        case .num(let literal):
            out.append(contentsOf: Array(literal.utf8))
        case .str(let text):
            writeQuotedString(text, into: &out)
        case .arr(let items):
            out.append(Byte.openBracket)
            for (offset, item) in items.enumerated() {
                if offset > 0 { out.append(Byte.comma) }
                writeCanonical(item, into: &out)
            }
            out.append(Byte.closeBracket)
        case .obj(let entries):
            out.append(Byte.openBrace)
            let sorted = entries.sorted {
                compareCodePoints($0.key, $1.key) < 0
            }
            for (offset, entry) in sorted.enumerated() {
                if offset > 0 { out.append(Byte.comma) }
                writeQuotedString(entry.key, into: &out)
                out.append(Byte.colon)
                writeCanonical(entry.value, into: &out)
            }
            out.append(Byte.closeBrace)
        }
    }

    static func writeQuotedString(_ value: String, into out: inout [UInt8]) {
        out.append(Byte.quote)
        for scalar in value.unicodeScalars {
            switch scalar.value {
            case 0x22: out.append(contentsOf: Array("\\\"".utf8))
            case 0x5C: out.append(contentsOf: Array("\\\\".utf8))
            case 0x08: out.append(contentsOf: Array("\\b".utf8))
            case 0x0C: out.append(contentsOf: Array("\\f".utf8))
            case 0x0A: out.append(contentsOf: Array("\\n".utf8))
            case 0x0D: out.append(contentsOf: Array("\\r".utf8))
            case 0x09: out.append(contentsOf: Array("\\t".utf8))
            default:
                if scalar.value < 0x20 {
                    out.append(contentsOf: Array(String(format: "\\u%04x", scalar.value).utf8))
                } else {
                    // Astral planes: emit the whole scalar as raw UTF-8, never halves.
                    appendScalar(scalar.value, to: &out)
                }
            }
        }
        out.append(Byte.quote)
    }

    /// Python `sorted()` orders strings by Unicode code point; comparing UTF-8
    /// bytes is exactly that order (a UTF-8 property), and avoids the UTF-16
    /// astral-plane misordering a naive ordinal compare would produce.
    public static func compareCodePoints(_ left: String, _ right: String) -> Int {
        let leftBytes = Array(left.utf8)
        let rightBytes = Array(right.utf8)
        let shared = min(leftBytes.count, rightBytes.count)
        for offset in 0..<shared where leftBytes[offset] != rightBytes[offset] {
            return Int(leftBytes[offset]) - Int(rightBytes[offset])
        }
        return leftBytes.count - rightBytes.count
    }
}

/// Every decode failure is the frozen `INVALID_INPUT` category located at the
/// document root (the scanner rejects structure before deeper attribution).
private func invalidInput(_ detail: String) -> PortableContractError {
    PortableContractError(PortableReject(
        category: .invalidInput,
        detail: detail,
        location: "$"
    ))
}

/// ASCII byte constants used by the scanner and writer.
enum Byte {
    static let tab = UInt8(ascii: "\t")
    static let newline = UInt8(ascii: "\n")
    static let carriageReturn = UInt8(ascii: "\r")
    static let space = UInt8(ascii: " ")
    static let quote = UInt8(ascii: "\"")
    static let backslash = UInt8(ascii: "\\")
    static let slash = UInt8(ascii: "/")
    static let colon = UInt8(ascii: ":")
    static let comma = UInt8(ascii: ",")
    static let openBrace = UInt8(ascii: "{")
    static let closeBrace = UInt8(ascii: "}")
    static let openBracket = UInt8(ascii: "[")
    static let closeBracket = UInt8(ascii: "]")
    static let dash = UInt8(ascii: "-")
    static let dot = UInt8(ascii: ".")
    static let plus = UInt8(ascii: "+")
    static let zero = UInt8(ascii: "0")
    static let one = UInt8(ascii: "1")
    static let nine = UInt8(ascii: "9")
    static let a = UInt8(ascii: "a")
    static let b = UInt8(ascii: "b")
    static let e = UInt8(ascii: "e")
    static let f = UInt8(ascii: "f")
    static let n = UInt8(ascii: "n")
    static let r = UInt8(ascii: "r")
    static let t = UInt8(ascii: "t")
    static let u = UInt8(ascii: "u")
    static let aUpper = UInt8(ascii: "A")
    static let eUpper = UInt8(ascii: "E")
    static let fUpper = UInt8(ascii: "F")
}
