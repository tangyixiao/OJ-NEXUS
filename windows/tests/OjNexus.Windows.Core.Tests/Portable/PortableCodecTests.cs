using System.Security.Cryptography;
using System.Text;
using OjNexus.Windows.Core.Portable;
using Xunit;

namespace OjNexus.Windows.Core.Tests.Portable;

public sealed class PortableCodecTests
{
    private const string GoldenIdentity = "81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44";
    private readonly PortableCodec codec = new();

    private static string GoldenDir => Path.Combine(AppContext.BaseDirectory, "portable", "golden");

    private static byte[] GoldenBytes(string file) =>
        File.ReadAllBytes(Path.Combine(GoldenDir, file));

    private static IReadOnlyList<(string CaseId, string File, string Expect, string? ErrorCategory)> ReadCases()
    {
        var cases = PortableJson.Parse(GoldenBytes("cases.json")) as PortableValue.Obj
            ?? throw new InvalidOperationException("cases.json root must be an object");
        var array = (PortableValue.Arr)cases.Get("cases")!;
        var result = new List<(string, string, string, string?)>();
        foreach (var item in array.Items)
        {
            var obj = (PortableValue.Obj)item;
            var caseId = ((PortableValue.Str)obj.Get("caseId")!).Value;
            var file = ((PortableValue.Str)obj.Get("file")!).Value;
            var expect = ((PortableValue.Str)obj.Get("expect")!).Value;
            var category = obj.Get("errorCategory") is PortableValue.Str wire ? wire.Value : null;
            result.Add((caseId, file, expect, category));
        }

        return result;
    }

    [Fact]
    public void GoldenCorpus_MatchesCheckerExpectations()
    {
        var failures = new List<string>();
        foreach (var (caseId, file, expect, errorCategory) in ReadCases())
        {
            try
            {
                codec.Validate(GoldenBytes(file));
                if (expect != "valid")
                {
                    failures.Add($"{caseId}: expected reject ({errorCategory}) but validation passed");
                }
            }
            catch (PortableContractException reject)
            {
                if (expect != "invalid")
                {
                    failures.Add($"{caseId}: expected valid but rejected as {reject.Reject.Wire}");
                    continue;
                }

                if (!string.Equals(reject.Reject.Wire, errorCategory, StringComparison.Ordinal))
                {
                    failures.Add($"{caseId}: expected {errorCategory} but got {reject.Reject.Wire}");
                }
            }
        }

        Assert.True(failures.Count == 0, string.Join(Environment.NewLine, failures));
    }

    [Fact]
    public void GoldenCorpus_CoversAllFifteenCases()
    {
        Assert.Equal(15, ReadCases().Count);
    }

    [Fact]
    public void CanonicalIdentity_OfMixedGoldenPackage_IsPinned()
    {
        var canonical = codec.CanonicalBytes(PortableJson.Parse(GoldenBytes("pw-p01.json")));
        var identity = Convert.ToHexString(SHA256.HashData(canonical)).ToLowerInvariant();

        Assert.Equal(GoldenIdentity, identity);
        Assert.Equal(GoldenIdentity, codec.Identity(canonical));
    }

    [Fact]
    public void CanonicalBytes_AreStableAcrossRuns()
    {
        var golden = GoldenBytes("pw-p01.json");
        var first = codec.CanonicalBytes(PortableJson.Parse(golden));
        var second = codec.CanonicalBytes(PortableJson.Parse(golden));

        Assert.Equal(first, second);
    }

    [Fact]
    public void CanonicalWriter_ReproducesPythonForm()
    {
        // A document whose canonical form exercises sorted keys, compact
        // separators, the five named escapes, lowercase \u00xx control escapes,
        // raw non-ASCII, verbatim integers and the trailing newline.
        var doc = new PortableValue.Obj(
        [
            new ObjectEntry("zeta", new PortableValue.Num("10")),
            new ObjectEntry("alpha", new PortableValue.Str("a\nb\"c\\d")),
            new ObjectEntry("Beta", new PortableValue.Str("emoji 🙂 and control\u0001")),
            new ObjectEntry("nested", new PortableValue.Obj([new ObjectEntry("z", new PortableValue.Bool(true))])),
            new ObjectEntry("list", new PortableValue.Arr([new PortableValue.Num("-3"), new PortableValue.Null()])),
        ]);

        var bytes = PortableJson.CanonicalBytes(doc);
        var text = Encoding.UTF8.GetString(bytes);

        Assert.Equal(
            "{\"Beta\":\"emoji 🙂 and control\\u0001\",\"alpha\":\"a\\nb\\\"c\\\\d\",\"list\":[-3,null],\"nested\":{\"z\":true},\"zeta\":10}\n",
            text);
    }

    [Fact]
    public void CanonicalKeySorting_UsesCodePointOrder_NotUtf16Ordinal()
    {
        // U+FF61 (halfwidth ideographic full stop) is BMP; U+10000 is astral.
        // UTF-16 ordinal sorts U+FF61 after the surrogate pair; code-point
        // order sorts it before.
        var doc = new PortableValue.Obj(
        [
            new ObjectEntry(char.ConvertFromUtf32(0x10000), new PortableValue.Null()),
            new ObjectEntry(char.ConvertFromUtf32(0xFF61), new PortableValue.Null()),
        ]);

        var text = Encoding.UTF8.GetString(PortableJson.CanonicalBytes(doc));

        var stopIndex = text.IndexOf(char.ConvertFromUtf32(0xFF61), StringComparison.Ordinal);
        var astralIndex = text.IndexOf(char.ConvertFromUtf32(0x10000), StringComparison.Ordinal);
        Assert.True(stopIndex < astralIndex, $"code-point order violated: {text}");
    }

    [Fact]
    public void StrictDecode_RejectsDuplicateObjectKeysAnywhere()
    {
        var bytes = """{"a":1,"a":2}"""u8.ToArray();
        RejectsAs(bytes, PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void StrictDecode_RejectsTrailingData()
    {
        var bytes = """{"a":1} trailing"""u8.ToArray();
        RejectsAs(bytes, PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void StrictDecode_RejectsNanAndComments()
    {
        RejectsAs("""{"a":NaN}"""u8.ToArray(), PortableRejectCategory.InvalidInput);
        RejectsAs("// c\n{}"u8.ToArray(), PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void StrictDecode_RejectsLoneSurrogateEscapes()
    {
        var bytes = Encoding.UTF8.GetBytes("""{"a":"\ud800"}""");
        RejectsAs(bytes, PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void StrictDecode_RejectsByteOrderMark()
    {
        var bytes = new byte[] { 0xEF, 0xBB, 0xBF };
        bytes = bytes.Concat("{}"u8.ToArray()).ToArray();
        RejectsAs(bytes, PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void VersionGate_RejectsMissingAndMalformedAndFuture()
    {
        RejectsAs("""{"scopeId":"s"}"""u8.ToArray(), PortableRejectCategory.InvalidInput);
        RejectsAs("""{"formatVersion":"1"}"""u8.ToArray(), PortableRejectCategory.InvalidInput);
        RejectsAs("""{"formatVersion":2}"""u8.ToArray(), PortableRejectCategory.UnsupportedVersion);
    }

    [Fact]
    public void CredentialShapedKeys_AreRejectedAnywhere()
    {
        // extra is schema-open, so the key reaches the credential walk.
        RejectsAs(
            Encoding.UTF8.GetBytes(PlainPackage("""{"sessionid":"x"}""")),
            PortableRejectCategory.UnknownField);
    }

    [Fact]
    public void ExtraDepth_IsBoundedAtTwoLevels()
    {
        RejectsAs(
            Encoding.UTF8.GetBytes(PlainPackage("""{"a":{"b":"c"}}""")),
            PortableRejectCategory.UnknownField);
    }

    [Fact]
    public void PathShapes_AreRejected()
    {
        RejectsAs(
            Encoding.UTF8.GetBytes(PlainPackage("""{"a":"C:\\tmp\\x"}""")),
            PortableRejectCategory.PathUnsafe);
        RejectsAs(
            Encoding.UTF8.GetBytes(PlainPackage("""{"a":"../up"}""")),
            PortableRejectCategory.PathUnsafe);
    }

    private const string PlainPackageTemplate = """
        {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01","extra":$EXTRA}],"reviews":[],"sessions":[]}
        """;

    /// <summary>A fully valid one-problem/one-note package with the given extra block injected into the note.</summary>
    private static string PlainPackage(string extraJson) =>
        PlainPackageTemplate.Replace("$EXTRA", extraJson, StringComparison.Ordinal);

    [Fact]
    public void SyntheticDetection_MatchesAndroidSemantics()
    {
        // The mixed golden's generator is synthetic-fixture-gen: the whole
        // package is synthetic and carries the fixture source tag.
        var golden = codec.Validate(GoldenBytes("pw-p01.json"));
        Assert.True(golden.Synthetic);
        Assert.Equal("SYNTHETIC-TEST-FIXTURE", golden.SourceTag);

        // A plain generator keeps PORTABLE-V1...
        const string plain = """
            {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01"}],"reviews":[],"sessions":[]}
            """;
        var plainPackage = codec.Validate(Encoding.UTF8.GetBytes(plain));
        Assert.False(plainPackage.Synthetic);
        Assert.Equal("PORTABLE-V1", plainPackage.SourceTag);

        // ...and a record-level extra.synthetic = true flips the package.
        const string marked = """
            {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01","extra":{"synthetic":true}}],"reviews":[],"sessions":[]}
            """;
        var markedPackage = codec.Validate(Encoding.UTF8.GetBytes(marked));
        Assert.True(markedPackage.Synthetic);
        Assert.Equal("SYNTHETIC-TEST-FIXTURE", markedPackage.SourceTag);
    }

    [Fact]
    public void IntegerLiterals_AreFullStringMatched()
    {
        // Regression (independent review): unanchored Regex.IsMatch classified
        // "1.5"/"1e5"/"01" as integers and let a raw FormatException escape
        // later in the pipeline. Android uses full-string matches; the schema
        // walk must reject all three as UNKNOWN_FIELD, and a fractional
        // formatVersion stays INVALID_INPUT at the version gate.
        RejectsAs(
            Encoding.UTF8.GetBytes("""{"formatVersion":1,"createdAtEpochMs":1.5}"""),
            PortableRejectCategory.UnknownField);
        RejectsAs(
            Encoding.UTF8.GetBytes("""{"formatVersion":1,"createdAtEpochMs":1e5}"""),
            PortableRejectCategory.UnknownField);
        RejectsAs(
            Encoding.UTF8.GetBytes("""{"formatVersion":1,"createdAtEpochMs":01}"""),
            PortableRejectCategory.InvalidInput);
        RejectsAs(
            Encoding.UTF8.GetBytes("""{"formatVersion":1.5}"""),
            PortableRejectCategory.InvalidInput);
    }

    [Fact]
    public void PathUnsafe_MatchesPythonDollarSemantics()
    {
        // Regression (independent review): translating the terminal `$` to `\z`
        // for PATH_UNSAFE accepted "…\n" strings (a `^\.\.$` path with a
        // trailing newline) that Python and Java both reject. .NET's default
        // `$` already matches before a final line terminator, so the raw
        // pattern must be used here.
        RejectsAs(
            Encoding.UTF8.GetBytes(PlainPackage("""{"a":"..\n"}""")),
            PortableRejectCategory.PathUnsafe);
    }

    [Fact]
    public void NegativeZero_CanonicalizesToZero()
    {
        // Regression (independent review): Android's normalizeNumber maps "-0"
        // to "0" — Python's int semantics do the same at dumps time — so the
        // canonical form must never carry "-0".
        var doc = PortableJson.Parse(Encoding.UTF8.GetBytes("""{"extra":{"a":-0}}"""));
        var text = Encoding.UTF8.GetString(PortableJson.CanonicalBytes(doc));

        Assert.Equal("""{"extra":{"a":0}}""" + "\n", text);
    }

    private static void RejectsAs(byte[] bytes, PortableRejectCategory expected)
    {
        var exception = Assert.Throws<PortableContractException>(() => new PortableCodec().Validate(bytes));
        Assert.Equal(expected, exception.Reject.Category);
    }
}
