using System.Buffers;
using System.Text;
using System.Text.Json;

namespace OjNexus.Windows.Core.Portable;

/// <summary>A decoded portable JSON value. Order of object entries is document order.</summary>
public abstract record PortableValue
{
    /// <summary>An object with ordered entries; duplicates are rejected at decode time.</summary>
    public sealed record Obj(IReadOnlyList<ObjectEntry> Entries) : PortableValue
    {
        public PortableValue? Get(string key)
        {
            foreach (var entry in Entries)
            {
                if (string.Equals(entry.Key, key, StringComparison.Ordinal))
                {
                    return entry.Value;
                }
            }

            return null;
        }
    }

    public sealed record Arr(IReadOnlyList<PortableValue> Items) : PortableValue;

    public sealed record Str(string Value) : PortableValue;

    /// <summary>A number preserved verbatim â€?the exact source token text is the canonical form.</summary>
    public sealed record Num(string Literal) : PortableValue;

    public sealed record Bool(bool Value) : PortableValue;

    public sealed record Null : PortableValue;
}

public sealed record ObjectEntry(string Key, PortableValue Value);

/// <summary>
/// Strict portable JSON decode and canonical re-encode. The decode is stricter
/// than <see cref="JsonDocument"/> (duplicate keys, trailing data, comments,
/// NaN/Infinity, lone surrogates are all <c>INVALID_INPUT</c>) and the writer
/// reproduces the Python canonical form byte-for-byte:
/// <c>json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"</c>.
/// Numbers are preserved verbatim (mirroring Android's <c>Num(literal)</c>).
/// </summary>
public static class PortableJson
{
    private const int MaxDepth = 200;
    private static readonly byte[] Utf8ByteOrderMark = [0xEF, 0xBB, 0xBF];

    public static PortableValue Parse(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        if (bytes.Length >= 3 && bytes.AsSpan(0, 3).SequenceEqual(Utf8ByteOrderMark))
        {
            throw InvalidInput("byte order mark");
        }

        var readerOptions = new JsonReaderOptions
        {
            MaxDepth = MaxDepth,
            CommentHandling = JsonCommentHandling.Disallow,
            AllowTrailingCommas = false,
        };
        var reader = new Utf8JsonReader(bytes, readerOptions);
        PortableValue value;
        try
        {
            value = ReadValue(ref reader);
            if (reader.Read())
            {
                throw InvalidInput("trailing data after the JSON document");
            }
        }
        catch (JsonException exception)
        {
            throw InvalidInput(exception.Message);
        }
        catch (InvalidOperationException exception)
        {
            throw InvalidInput(exception.Message);
        }

        return value;
    }

    /// <summary>Canonical bytes: sorted keys by Unicode code point, compact separators, Python escaping, trailing newline.</summary>
    public static byte[] CanonicalBytes(PortableValue value)
    {
        var output = new MemoryStream();
        WriteCanonical(output, value);
        output.WriteByte((byte)'\n');
        return output.ToArray();
    }

    private static void WriteCanonical(MemoryStream output, PortableValue value)
    {
        switch (value)
        {
            case PortableValue.Null:
                output.Write("null"u8);
                break;
            case PortableValue.Bool boolean:
                output.Write(boolean.Value ? "true"u8 : "false"u8);
                break;
            case PortableValue.Num number:
                output.Write(Encoding.UTF8.GetBytes(number.Literal));
                break;
            case PortableValue.Str text:
                WriteQuotedString(output, text.Value);
                break;
            case PortableValue.Arr array:
                output.WriteByte((byte)'[');
                for (var index = 0; index < array.Items.Count; index++)
                {
                    if (index > 0)
                    {
                        output.WriteByte((byte)',');
                    }

                    WriteCanonical(output, array.Items[index]);
                }

                output.WriteByte((byte)']');
                break;
            case PortableValue.Obj obj:
                output.WriteByte((byte)'{');
                var sorted = SortEntries(obj.Entries);
                for (var index = 0; index < sorted.Count; index++)
                {
                    if (index > 0)
                    {
                        output.WriteByte((byte)',');
                    }

                    WriteQuotedString(output, sorted[index].Key);
                    output.WriteByte((byte)':');
                    WriteCanonical(output, sorted[index].Value);
                }

                output.WriteByte((byte)'}');
                break;
        }
    }

    private static void WriteQuotedString(MemoryStream output, string value)
    {
        output.WriteByte((byte)'"');
        var index = 0;
        while (index < value.Length)
        {
            var character = value[index];
            if (char.IsSurrogatePair(value, index))
            {
                // Astral planes: emit the whole pair as raw UTF-8, never halves.
                output.Write(Encoding.UTF8.GetBytes(value.Substring(index, 2)));
                index += 2;
                continue;
            }

            switch (character)
            {
                case '"': output.Write("\\\""u8); break;
                case '\\': output.Write("\\\\"u8); break;
                case '\b': output.Write("\\b"u8); break;
                case '\f': output.Write("\\f"u8); break;
                case '\n': output.Write("\\n"u8); break;
                case '\r': output.Write("\\r"u8); break;
                case '\t': output.Write("\\t"u8); break;
                default:
                    if (character < ' ')
                    {
                        output.Write(Encoding.ASCII.GetBytes($"\\u{((int)character):x4}"));
                    }
                    else
                    {
                        output.Write(Encoding.UTF8.GetBytes(character.ToString()));
                    }

                    break;
            }

            index++;
        }

        output.WriteByte((byte)'"');
    }

    private static IReadOnlyList<ObjectEntry> SortEntries(IReadOnlyList<ObjectEntry> entries)
    {
        var sorted = new List<ObjectEntry>(entries);
        sorted.Sort((left, right) => CompareCodePoints(left.Key, right.Key));
        return sorted;
    }

    /// <summary>Python <c>sorted()</c> orders by Unicode code point; UTF-16 ordinal misorders astral planes.</summary>
    public static int CompareCodePoints(string left, string right)
    {
        var leftIndex = 0;
        var rightIndex = 0;
        while (leftIndex < left.Length && rightIndex < right.Length)
        {
            int leftScalar = char.ConvertToUtf32(left, leftIndex);
            int rightScalar = char.ConvertToUtf32(right, rightIndex);
            if (leftScalar != rightScalar)
            {
                return leftScalar.CompareTo(rightScalar);
            }

            leftIndex += char.IsSurrogatePair(left, leftIndex) ? 2 : 1;
            rightIndex += char.IsSurrogatePair(right, rightIndex) ? 2 : 1;
        }

        return (left.Length - leftIndex).CompareTo(right.Length - rightIndex);
    }

    private static PortableValue ReadValue(ref Utf8JsonReader reader)
    {
        if (!reader.Read())
        {
            throw InvalidInput("empty input");
        }

        return ReadValueAfterTokenStart(ref reader);
    }

    private static PortableValue ReadValueAfterTokenStart(ref Utf8JsonReader reader)
    {
        switch (reader.TokenType)
        {
            case JsonTokenType.Null:
                return new PortableValue.Null();
            case JsonTokenType.True:
                return new PortableValue.Bool(true);
            case JsonTokenType.False:
                return new PortableValue.Bool(false);
            case JsonTokenType.Number:
                return new PortableValue.Num(Encoding.UTF8.GetString(reader.ValueSpan));
            case JsonTokenType.String:
                return new PortableValue.Str(ReadValidString(ref reader));
        }

        if (reader.TokenType == JsonTokenType.StartObject)
        {
            var entries = new List<ObjectEntry>();
            var keys = new HashSet<string>(StringComparer.Ordinal);
            int depth = reader.CurrentDepth;
            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndObject && reader.CurrentDepth == depth)
                {
                    return new PortableValue.Obj(entries);
                }

                if (reader.TokenType != JsonTokenType.PropertyName)
                {
                    throw InvalidInput("object member name expected");
                }

                var key = ReadValidString(ref reader);
                if (!keys.Add(key))
                {
                    throw InvalidInput($"duplicate object key '{key}'");
                }

                reader.Read();
                var entryValue = ReadValueAfterTokenStart(ref reader);
                entries.Add(new ObjectEntry(key, entryValue));
            }

            throw InvalidInput("unterminated object");
        }

        if (reader.TokenType == JsonTokenType.StartArray)
        {
            var items = new List<PortableValue>();
            int depth = reader.CurrentDepth;
            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndArray && reader.CurrentDepth == depth)
                {
                    return new PortableValue.Arr(items);
                }

                items.Add(ReadValueAfterTokenStart(ref reader));
            }

            throw InvalidInput("unterminated array");
        }

        throw InvalidInput($"unexpected token {reader.TokenType}");
    }

    private static string ReadValidString(ref Utf8JsonReader reader)
    {
        var value = reader.GetString() ?? throw InvalidInput("null string token");
        if (IsIllFormedUtf16(value))
        {
            throw InvalidInput("lone surrogate escape");
        }

        return value;
    }

    private static bool IsIllFormedUtf16(string value)
    {
        for (var index = 0; index < value.Length; index++)
        {
            if (char.IsHighSurrogate(value[index]))
            {
                if (index + 1 >= value.Length || !char.IsLowSurrogate(value[index + 1]))
                {
                    return true;
                }

                index++;
            }
            else if (char.IsLowSurrogate(value[index]))
            {
                return true;
            }
        }

        return false;
    }

    private static PortableContractException InvalidInput(string detail) =>
        new(new PortableReject(PortableRejectCategory.InvalidInput, detail, "$"));
}
