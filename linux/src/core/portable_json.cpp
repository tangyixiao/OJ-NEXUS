#include "ojnexus/core/portable_json.h"

#include <QChar>

#include <algorithm>

namespace OjNexus::Portable {
namespace {

PortableContractException invalidInput(const QString& detail)
{
    return PortableContractException{PortableReject{
        PortableRejectCategory::InvalidInput, detail, QStringLiteral("$")}};
}

struct Payload {
    QList<PortableValue> items;
    QList<ObjectEntry> entries;
};

bool isWhitespace(char c)
{
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

bool isDigit(char c)
{
    return c >= '0' && c <= '9';
}

bool isHex(char c)
{
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

int hexValue(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return c - 'A' + 10;
}

/// Appends one scalar value ( BMP or astral) to a UTF-16 QString.
void appendScalar(QString& out, char32_t scalar)
{
    if (scalar > 0xFFFF) {
        const auto adjusted = scalar - 0x10000;
        out.append(QChar(static_cast<ushort>(0xD800 + (adjusted >> 10))));
        out.append(QChar(static_cast<ushort>(0xDC00 + (adjusted & 0x3FF))));
    } else {
        out.append(QChar(static_cast<ushort>(scalar)));
    }
}

/// Strict UTF-8 decoder for raw string content; returns false on invalid bytes.
/// Raw surrogate encodings (ED A0-BF) and overlong forms are rejected.
bool decodeUtf8Into(const char* data, int begin, int end, QString& out)
{
    int i = begin;
    while (i < end) {
        const auto byte0 = static_cast<unsigned char>(data[i]);
        if (byte0 < 0x80) {
            out.append(QChar(static_cast<ushort>(byte0)));
            ++i;
            continue;
        }
        int length = 0;
        char32_t scalar = 0;
        if (byte0 >= 0xC2 && byte0 <= 0xDF) {
            length = 2;
            scalar = byte0 & 0x1F;
        } else if (byte0 >= 0xE0 && byte0 <= 0xEF) {
            length = 3;
            scalar = byte0 & 0x0F;
        } else if (byte0 >= 0xF0 && byte0 <= 0xF4) {
            length = 4;
            scalar = byte0 & 0x07;
        } else {
            return false;
        }
        if (i + length > end) return false;
        for (int extra = 1; extra < length; ++extra) {
            const auto continuation = static_cast<unsigned char>(data[i + extra]);
            if ((continuation & 0xC0) != 0x80) return false;
            scalar = (scalar << 6) | (continuation & 0x3F);
        }
        if (length == 3 && scalar < 0x800) return false;               // overlong
        if (length == 3 && scalar >= 0xD800 && scalar <= 0xDFFF) return false; // surrogate encoding
        if (length == 4 && scalar < 0x10000) return false;             // overlong
        if (scalar > 0x10FFFF) return false;
        appendScalar(out, scalar);
        i += length;
    }
    return true;
}

/// Recursive-descent strict JSON parser over a UTF-8 byte buffer.
class Parser {
public:
    Parser(const char* data, int size) : data_(data), size_(size) {}

    PortableValue parseDocument()
    {
        skipWhitespace();
        if (atEnd()) throw invalidInput(QStringLiteral("empty input"));
        auto value = parseValue();
        skipWhitespace();
        if (!atEnd()) throw invalidInput(QStringLiteral("trailing data after the JSON document"));
        return value;
    }

private:
    void skipWhitespace()
    {
        while (!atEnd() && isWhitespace(data_[pos_])) ++pos_;
    }

    bool atEnd() const { return pos_ >= size_; }
    char peek() const { return data_[pos_]; }

    PortableValue parseValue()
    {
        if (depth_ >= 200) throw invalidInput(QStringLiteral("maximum depth exceeded"));
        if (atEnd()) throw invalidInput(QStringLiteral("unexpected end of input"));
        const char c = peek();
        switch (c) {
        case 'n': return parseKeyword(QStringLiteral("null"), PortableValue::null());
        case 't': return parseKeyword(QStringLiteral("true"), PortableValue::boolean(true));
        case 'f': return parseKeyword(QStringLiteral("false"), PortableValue::boolean(false));
        case '"': return PortableValue::string(parseString());
        case '{': return parseObject();
        case '[': return parseArray();
        default: return parseNumber();
        }
    }

    PortableValue parseKeyword(const QString& keyword, PortableValue value)
    {
        if (pos_ + keyword.size() > size_) throw invalidInput(QStringLiteral("invalid token"));
        for (qsizetype i = 0; i < keyword.size(); ++i) {
            if (data_[pos_ + i] != keyword.at(i).toLatin1())
                throw invalidInput(QStringLiteral("invalid token"));
        }
        pos_ += static_cast<int>(keyword.size());
        return value;
    }

    PortableValue parseNumber()
    {
        const int start = pos_;
        if (!atEnd() && peek() == '-') ++pos_;
        if (atEnd()) throw invalidInput(QStringLiteral("invalid number"));
        if (peek() == '0') {
            ++pos_;
        } else if (isDigit(peek())) {
            while (!atEnd() && isDigit(peek())) ++pos_;
        } else {
            throw invalidInput(QStringLiteral("invalid number"));
        }
        if (!atEnd() && peek() == '.') {
            ++pos_;
            if (atEnd() || !isDigit(peek())) throw invalidInput(QStringLiteral("invalid number"));
            while (!atEnd() && isDigit(peek())) ++pos_;
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            ++pos_;
            if (!atEnd() && (peek() == '+' || peek() == '-')) ++pos_;
            if (atEnd() || !isDigit(peek())) throw invalidInput(QStringLiteral("invalid number"));
            while (!atEnd() && isDigit(peek())) ++pos_;
        }
        QString literal = QString::fromUtf8(data_ + start, pos_ - start);
        // Android's normalizeNumber: "-0" canonicalizes to "0" (Python int
        // semantics do the same at dumps time).
        if (literal == QStringLiteral("-0")) literal = QStringLiteral("0");
        return PortableValue::number(literal);
    }

    QString parseString()
    {
        ++pos_; // opening quote
        QString out;
        while (true) {
            if (atEnd()) throw invalidInput(QStringLiteral("unterminated string"));
            const auto byte = static_cast<unsigned char>(peek());
            if (byte == '"') {
                ++pos_;
                return out;
            }
            if (byte < 0x20) throw invalidInput(QStringLiteral("unescaped control character in string"));
            if (byte == '\\') {
                ++pos_;
                if (atEnd()) throw invalidInput(QStringLiteral("unterminated escape"));
                const char escape = peek();
                ++pos_;
                switch (escape) {
                case '"': out.append(QChar('"')); break;
                case '\\': out.append(QChar('\\')); break;
                case '/': out.append(QChar('/')); break;
                case 'b': out.append(QChar('\b')); break;
                case 'f': out.append(QChar('\f')); break;
                case 'n': out.append(QChar('\n')); break;
                case 'r': out.append(QChar('\r')); break;
                case 't': out.append(QChar('\t')); break;
                case 'u': {
                    const auto scalar = parseUnicodeEscape();
                    appendScalar(out, scalar);
                    break;
                }
                default: throw invalidInput(QStringLiteral("invalid escape character"));
                }
                continue;
            }
            // Raw UTF-8 sequence (multi-byte); single bytes >= 0x80 fall through
            // to the decoder which validates them.
            int begin = pos_;
            while (!atEnd()) {
                const auto raw = static_cast<unsigned char>(data_[pos_]);
                if (raw == '"' || raw == '\\' || raw < 0x20) break;
                ++pos_;
            }
            if (!decodeUtf8Into(data_, begin, pos_, out))
                throw invalidInput(QStringLiteral("invalid UTF-8 in string"));
        }
    }

    /// Parses one \uXXXX escape; combines surrogate pairs. Rejects lone surrogates.
    char32_t parseUnicodeEscape()
    {
        const auto first = parseHex4();
        if (first >= 0xDC00 && first <= 0xDFFF)
            throw invalidInput(QStringLiteral("lone surrogate escape"));
        if (first < 0xD800 || first > 0xDBFF) return first;
        if (pos_ + 1 >= size_ || data_[pos_] != '\\' || data_[pos_ + 1] != 'u')
            throw invalidInput(QStringLiteral("lone surrogate escape"));
        pos_ += 2;
        const auto second = parseHex4();
        if (second < 0xDC00 || second > 0xDFFF)
            throw invalidInput(QStringLiteral("lone surrogate escape"));
        return 0x10000 + ((first - 0xD800) << 10) + (second - 0xDC00);
    }

    char32_t parseHex4()
    {
        if (pos_ + 4 > size_) throw invalidInput(QStringLiteral("invalid \\u escape"));
        char32_t value = 0;
        for (int i = 0; i < 4; ++i) {
            if (!isHex(data_[pos_ + i])) throw invalidInput(QStringLiteral("invalid \\u escape"));
            value = (value << 4) | static_cast<char32_t>(hexValue(data_[pos_ + i]));
        }
        pos_ += 4;
        return value;
    }

    PortableValue parseObject()
    {
        ++pos_; // '{'
        ++depth_;
        QList<ObjectEntry> entries;
        QSet<QString> keys;
        skipWhitespace();
        if (!atEnd() && peek() == '}') {
            ++pos_;
            --depth_;
            return PortableValue::object(entries);
        }
        while (true) {
            skipWhitespace();
            if (atEnd() || peek() != '"')
                throw invalidInput(QStringLiteral("object member name expected"));
            const auto key = parseString();
            if (keys.contains(key))
                throw invalidInput(QStringLiteral("duplicate object key '%1'").arg(key));
            keys.insert(key);
            skipWhitespace();
            if (atEnd() || peek() != ':')
                throw invalidInput(QStringLiteral("expected ':' after object member name"));
            ++pos_;
            skipWhitespace();
            entries.push_back(ObjectEntry{key, parseValue()});
            skipWhitespace();
            if (atEnd()) throw invalidInput(QStringLiteral("unterminated object"));
            if (peek() == ',') {
                ++pos_;
                continue;
            }
            if (peek() == '}') {
                ++pos_;
                --depth_;
                return PortableValue::object(entries);
            }
            throw invalidInput(QStringLiteral("expected ',' or '}' in object"));
        }
    }

    PortableValue parseArray()
    {
        ++pos_; // '['
        ++depth_;
        QList<PortableValue> items;
        skipWhitespace();
        if (!atEnd() && peek() == ']') {
            ++pos_;
            --depth_;
            return PortableValue::array(items);
        }
        while (true) {
            skipWhitespace();
            items.push_back(parseValue());
            skipWhitespace();
            if (atEnd()) throw invalidInput(QStringLiteral("unterminated array"));
            if (peek() == ',') {
                ++pos_;
                continue;
            }
            if (peek() == ']') {
                ++pos_;
                --depth_;
                return PortableValue::array(items);
            }
            throw invalidInput(QStringLiteral("expected ',' or ']' in array"));
        }
    }

    const char* data_;
    int size_;
    int pos_ = 0;
    int depth_ = 0;
};

/// Writes one UTF-16 string as Python-style canonical JSON: escapes per
/// json.dumps(ensure_ascii=False); astral pairs are emitted whole as UTF-8.
void writeQuotedString(QByteArray& out, const QString& value)
{
    out.append('"');
    int index = 0;
    const auto length = value.size();
    while (index < length) {
        const auto character = value.at(index);
        if (character.isSurrogate()) {
            if (character.isHighSurrogate() && index + 1 < length && value.at(index + 1).isLowSurrogate()) {
                // Astral planes: emit the whole pair as raw UTF-8, never halves.
                char32_t scalar = 0x10000 + ((character.unicode() - 0xD800) << 10)
                    + (value.at(index + 1).unicode() - 0xDC00);
                out.append(static_cast<char>(0xF0 | (scalar >> 18)));
                out.append(static_cast<char>(0x80 | ((scalar >> 12) & 0x3F)));
                out.append(static_cast<char>(0x80 | ((scalar >> 6) & 0x3F)));
                out.append(static_cast<char>(0x80 | (scalar & 0x3F)));
                index += 2;
                continue;
            }
            // Lone surrogates cannot appear in canonical output; the writer is
            // only fed validated values, so treat this as an internal error.
            throw invalidInput(QStringLiteral("lone surrogate in canonical output"));
        }
        switch (character.unicode()) {
        case u'"': out.append("\\\""); break;
        case u'\\': out.append("\\\\"); break;
        case u'\b': out.append("\\b"); break;
        case u'\f': out.append("\\f"); break;
        case u'\n': out.append("\\n"); break;
        case u'\r': out.append("\\r"); break;
        case u'\t': out.append("\\t"); break;
        default:
            if (character.unicode() < 0x20) {
                out.append(QString::fromLatin1("\\u%1").arg(character.unicode(), 4, 16, QLatin1Char('0')).toLatin1());
            } else {
                // BMP char: UTF-8 encode (1-3 bytes).
                const auto scalar = character.unicode();
                if (scalar < 0x80) {
                    out.append(static_cast<char>(scalar));
                } else if (scalar < 0x800) {
                    out.append(static_cast<char>(0xC0 | (scalar >> 6)));
                    out.append(static_cast<char>(0x80 | (scalar & 0x3F)));
                } else {
                    out.append(static_cast<char>(0xE0 | (scalar >> 12)));
                    out.append(static_cast<char>(0x80 | ((scalar >> 6) & 0x3F)));
                    out.append(static_cast<char>(0x80 | (scalar & 0x3F)));
                }
            }
            break;
        }
        ++index;
    }
    out.append('"');
}

void writeCanonical(QByteArray& out, const PortableValue& value);

void writeObjectEntries(QByteArray& out, const QList<ObjectEntry>& entries)
{
    auto sorted = entries;
    std::sort(sorted.begin(), sorted.end(),
        [](const ObjectEntry& left, const ObjectEntry& right) {
            return PortableJson::compareCodePoints(left.key, right.key) < 0;
        });
    bool first = true;
    for (const auto& entry : sorted) {
        if (!first) out.append(',');
        first = false;
        writeQuotedString(out, entry.key);
        out.append(':');
        writeCanonical(out, entry.value);
    }
}

void writeCanonical(QByteArray& out, const PortableValue& value)
{
    switch (value.kind()) {
    case PortableValue::Kind::Null: out.append("null"); break;
    case PortableValue::Kind::Bool: out.append(value.boolValue() ? "true" : "false"); break;
    case PortableValue::Kind::Num: out.append(value.text().toUtf8()); break;
    case PortableValue::Kind::Str: writeQuotedString(out, value.text()); break;
    case PortableValue::Kind::Arr: {
        out.append('[');
        bool first = true;
        for (const auto& item : value.items()) {
            if (!first) out.append(',');
            first = false;
            writeCanonical(out, item);
        }
        out.append(']');
        break;
    }
    case PortableValue::Kind::Obj: {
        out.append('{');
        writeObjectEntries(out, value.entries());
        out.append('}');
        break;
    }
    }
}

} // namespace

PortableValue PortableValue::boolean(bool value)
{
    PortableValue result;
    result.kind_ = Kind::Bool;
    result.bool_ = value;
    return result;
}

PortableValue PortableValue::number(QString literal)
{
    PortableValue result;
    result.kind_ = Kind::Num;
    result.str_ = std::move(literal);
    return result;
}

PortableValue PortableValue::string(QString value)
{
    PortableValue result;
    result.kind_ = Kind::Str;
    result.str_ = std::move(value);
    return result;
}

PortableValue PortableValue::array(QList<PortableValue> items)
{
    PortableValue result;
    result.kind_ = Kind::Arr;
    result.payload_ = std::make_shared<Payload>();
    result.payload_->items = std::move(items);
    return result;
}

PortableValue PortableValue::object(QList<ObjectEntry> entries)
{
    PortableValue result;
    result.kind_ = Kind::Obj;
    result.payload_ = std::make_shared<Payload>();
    result.payload_->entries = std::move(entries);
    return result;
}

const QList<PortableValue>& PortableValue::items() const
{
    static const QList<PortableValue> empty;
    return payload_ ? payload_->items : empty;
}

const QList<ObjectEntry>& PortableValue::entries() const
{
    static const QList<ObjectEntry> empty;
    return payload_ ? payload_->entries : empty;
}

const PortableValue* PortableValue::get(const QString& key) const
{
    if (kind_ != Kind::Obj || !payload_) return nullptr;
    for (const auto& entry : payload_->entries) {
        if (entry.key == key) return &entry.value;
    }
    return nullptr;
}

PortableValue PortableJson::parse(const QByteArray& bytes)
{
    if (bytes.size() >= 3 && static_cast<unsigned char>(bytes[0]) == 0xEF &&
        static_cast<unsigned char>(bytes[1]) == 0xBB && static_cast<unsigned char>(bytes[2]) == 0xBF) {
        throw invalidInput(QStringLiteral("byte order mark"));
    }
    Parser parser(bytes.constData(), static_cast<int>(bytes.size()));
    return parser.parseDocument();
}

QByteArray PortableJson::canonicalBytes(const PortableValue& value)
{
    QByteArray out;
    writeCanonical(out, value);
    out.append('\n');
    return out;
}

int PortableJson::compareCodePoints(const QString& left, const QString& right)
{
    int leftIndex = 0;
    int rightIndex = 0;
    const auto leftLength = left.size();
    const auto rightLength = right.size();
    while (leftIndex < leftLength && rightIndex < rightLength) {
        char32_t leftScalar = left.at(leftIndex).unicode();
        char32_t rightScalar = right.at(rightIndex).unicode();
        if (left.at(leftIndex).isHighSurrogate() && leftIndex + 1 < leftLength && left.at(leftIndex + 1).isLowSurrogate()) {
            leftScalar = 0x10000 + ((leftScalar - 0xD800) << 10) + (left.at(leftIndex + 1).unicode() - 0xDC00);
        } else if (left.at(leftIndex).isSurrogate()) {
            // Unpaired surrogate (cannot come from validated JSON); compare as its raw unit.
            leftScalar = 0x110000 + left.at(leftIndex).unicode();
        }
        if (right.at(rightIndex).isHighSurrogate() && rightIndex + 1 < rightLength && right.at(rightIndex + 1).isLowSurrogate()) {
            rightScalar = 0x10000 + ((rightScalar - 0xD800) << 10) + (right.at(rightIndex + 1).unicode() - 0xDC00);
        } else if (right.at(rightIndex).isSurrogate()) {
            rightScalar = 0x110000 + right.at(rightIndex).unicode();
        }
        if (leftScalar != rightScalar) return leftScalar < rightScalar ? -1 : 1;
        leftIndex += left.at(leftIndex).isHighSurrogate() && leftIndex + 1 < leftLength && left.at(leftIndex + 1).isLowSurrogate() ? 2 : 1;
        rightIndex += right.at(rightIndex).isHighSurrogate() && rightIndex + 1 < rightLength && right.at(rightIndex + 1).isLowSurrogate() ? 2 : 1;
    }
    const auto leftRemaining = leftLength - leftIndex;
    const auto rightRemaining = rightLength - rightIndex;
    if (leftRemaining == rightRemaining) return 0;
    return leftRemaining < rightRemaining ? -1 : 1;
}

} // namespace OjNexus::Portable
