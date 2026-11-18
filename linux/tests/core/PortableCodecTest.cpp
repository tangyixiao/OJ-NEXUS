#include <QtTest/QtTest>

#include <QDir>
#include <QFile>
#include <QIODevice>

#include "ojnexus/core/portable_codec.h"
#include "ojnexus/core/portable_json.h"

#include <optional>

namespace {

QString goldenPath(const QString& name)
{
    return QStringLiteral(OJNEXUS_PORTABLE_GOLDEN_DIR) + QLatin1Char('/') + name;
}

QByteArray readGolden(const QString& name)
{
    QFile file(goldenPath(name));
    QVERIFY2(file.open(QIODevice::ReadOnly), qPrintable(file.errorString()));
    return file.readAll();
}

std::optional<OjNexus::Portable::PortableReject> tryValidate(const QByteArray& bytes)
{
    try {
        const OjNexus::Portable::PortableCodec codec;
        static_cast<void>(codec.validate(bytes));
        return std::nullopt;
    } catch (const OjNexus::Portable::PortableContractException& exception) {
        return exception.reject();
    }
}

std::optional<OjNexus::Portable::PortableReject> tryParse(const QByteArray& bytes)
{
    try {
        static_cast<void>(OjNexus::Portable::PortableJson::parse(bytes));
        return std::nullopt;
    } catch (const OjNexus::Portable::PortableContractException& exception) {
        return exception.reject();
    }
}

/// A minimal VALID package (one problem, empty other collections).
QByteArray minimalValidPackage()
{
    return QByteArrayLiteral(
        "{"
        "\"formatVersion\":1,"
        "\"scopeId\":\"scope.min\","
        "\"sourcePlatform\":\"android\","
        "\"generator\":{\"name\":\"gen\",\"version\":\"1\"},"
        "\"createdAtEpochMs\":1760000000000,"
        "\"counts\":{\"problems\":1,\"attempts\":0,\"notes\":0,\"reviews\":0,\"sessions\":0},"
        "\"problems\":[{"
        "\"stableId\":\"cf.1A\","
        "\"sourceScopeId\":\"scope.min\","
        "\"origin\":\"scope\","
        "\"judge\":\"codeforces\","
        "\"externalId\":\"1A\","
        "\"title\":\"Watermelon\","
        "\"solved\":true,"
        "\"dayKey\":\"2026-01-01\""
        "}],"
        "\"attempts\":[],"
        "\"notes\":[],"
        "\"reviews\":[],"
        "\"sessions\":[]"
        "}");
}

} // namespace

class PortableCodecTest final : public QObject {
    Q_OBJECT

private slots:
    // --- JSON layer ---
    void jsonCanonicalRoundTripSortsKeysByCodePoint();
    void jsonNegativeZeroCanonicalizesToZero();
    void jsonNumberLiteralsArePreservedVerbatim();
    void jsonRejectsDuplicateKeysTrailingDataAndBom();
    void jsonRejectsLoneSurrogateEscapes();
    void jsonRejectsRawControlCharactersAndInvalidUtf8();
    void jsonAstralStringsRoundTripWhole();
    void compareCodePointsOrdersByScalarValue();

    // --- codec layer ---
    void goldenCorpus_MatchesCheckerExpectations();
    void canonicalIdentity_OfMixedGoldenPackage_IsPinned();
    void pathUnsafe_MatchesPythonDollarSemantics();
    void formatVersionFractional_IsRejectedAtVersionGate();
    void leadingZeroInteger_IsRejectedAtStrictDecode();
    void credentialShapedKeys_AreNeverPortable();
    void extraNestedObjects_AreRejectedBeyondTwoLevels();
    void countsMismatch_IsRejected();
    void duplicateNoteProblemId_IsRejected();
    void sessionMissingReference_IsRejected();
    void missingFormatVersion_IsRejectedBeforeSchema();
};

void PortableCodecTest::jsonCanonicalRoundTripSortsKeysByCodePoint()
{
    using namespace OjNexus::Portable;
    // Sorted by code point: "a" (0x61) < "b" (0x62) < U+00E9 (0xE9) < U+1F600.
    const auto doc = PortableValue::object(QList<ObjectEntry>{
        {QStringLiteral("b"), PortableValue::number(QStringLiteral("1"))},
        {QStringLiteral("a"), PortableValue::string(QStringLiteral("x"))},
        {QStringLiteral("\u00E9"), PortableValue::boolean(true)},
        {QStringLiteral("\U0001F600"), PortableValue::null()},
    });
    const auto canonical = PortableJson::canonicalBytes(doc);
    // Python: json.dumps(doc, sort_keys=True, separators=(",", ":"),
    // ensure_ascii=False) + "\n".
    const QByteArray expected =
        "{\"a\":\"x\",\"b\":1,"
        "\"\xC3\xA9\":true,"
        "\"\xF0\x9F\x98\x80\":null}\n";
    QCOMPARE(canonical, expected);
}

void PortableCodecTest::jsonNegativeZeroCanonicalizesToZero()
{
    using namespace OjNexus::Portable;
    const auto doc = PortableJson::parse(QByteArray("{\"extra\":{\"a\":-0}}"));
    const auto text = QString::fromUtf8(PortableJson::canonicalBytes(doc));
    QCOMPARE(text, QStringLiteral("{\"extra\":{\"a\":0}}\n"));
}

void PortableCodecTest::jsonNumberLiteralsArePreservedVerbatim()
{
    using namespace OjNexus::Portable;
    const auto doc = PortableJson::parse(QByteArray("[1.0, 1e5, 9007199254740993, -0.0, 0]"));
    QCOMPARE(doc.items().at(0).text(), QStringLiteral("1.0"));
    QCOMPARE(doc.items().at(1).text(), QStringLiteral("1e5"));
    QCOMPARE(doc.items().at(2).text(), QStringLiteral("9007199254740993"));
    QCOMPARE(doc.items().at(3).text(), QStringLiteral("-0.0"));
    QCOMPARE(doc.items().at(4).text(), QStringLiteral("0"));
}

void PortableCodecTest::jsonRejectsDuplicateKeysTrailingDataAndBom()
{
    using namespace OjNexus::Portable;
    const auto duplicate = tryParse(QByteArray("{\"a\":1,\"a\":2}"));
    QVERIFY(duplicate.has_value());
    QCOMPARE(duplicate->category, PortableRejectCategory::InvalidInput);

    const auto trailing = tryParse(QByteArray("{} {}"));
    QVERIFY(trailing.has_value());

    QByteArray withBom;
    withBom.append('\xEF');
    withBom.append('\xBB');
    withBom.append('\xBF');
    withBom.append("{}");
    const auto bom = tryParse(withBom);
    QVERIFY(bom.has_value());
}

void PortableCodecTest::jsonRejectsLoneSurrogateEscapes()
{
    using namespace OjNexus::Portable;
    QVERIFY(tryParse(QByteArray("\"\\uD800\"")).has_value());
    QVERIFY(tryParse(QByteArray("\"\\uDC00x\"")).has_value());
    QVERIFY(!tryParse(QByteArray("\"\\uD83D\\uDE00\"")).has_value());
}

void PortableCodecTest::jsonRejectsRawControlCharactersAndInvalidUtf8()
{
    using namespace OjNexus::Portable;
    QByteArray control("\"a\x01\"");
    QVERIFY(tryParse(control).has_value());
    // Truncated UTF-8 sequence (0xC3 with no continuation byte) followed by a
    // closing quote is invalid UTF-8 inside a string.
    QByteArray truncated("\"a\xC3\"");
    QVERIFY(tryParse(truncated).has_value());
}

void PortableCodecTest::jsonAstralStringsRoundTripWhole()
{
    using namespace OjNexus::Portable;
    const auto doc = PortableJson::parse(QByteArray("\"\xF0\x9F\x98\x80\""));
    QCOMPARE(doc.text(), QStringLiteral("\U0001F600"));
    const auto canonical = QString::fromUtf8(PortableJson::canonicalBytes(doc));
    QCOMPARE(canonical, QStringLiteral("\"\U0001F600\"\n"));
}

void PortableCodecTest::compareCodePointsOrdersByScalarValue()
{
    using namespace OjNexus::Portable;
    // U+10000 encodes as the surrogate pair 0xD800 0xDC00, which sorts BELOW
    // U+FFFD (0xFFFD) in UTF-16 ordinal order - exactly the astral
    // misordering the code-point comparator must prevent.
    QVERIFY(PortableJson::compareCodePoints(QStringLiteral("\U00010000"), QStringLiteral("\uFFFD")) > 0);
    QVERIFY(PortableJson::compareCodePoints(QStringLiteral("\uFFFD"), QStringLiteral("\U00010000")) < 0);
    QCOMPARE(PortableJson::compareCodePoints(QStringLiteral("abc"), QStringLiteral("abc")), 0);
    QVERIFY(PortableJson::compareCodePoints(QStringLiteral("ab"), QStringLiteral("abc")) < 0);
}

void PortableCodecTest::goldenCorpus_MatchesCheckerExpectations()
{
    using namespace OjNexus::Portable;
    const auto casesBytes = readGolden(QStringLiteral("cases.json"));
    const auto cases = PortableJson::parse(casesBytes);
    QVERIFY(cases.kind() == PortableValue::Kind::Obj);
    const auto* list = cases.get(QStringLiteral("cases"));
    QVERIFY(list != nullptr && list->kind() == PortableValue::Kind::Arr);

    for (const auto& entry : list->items()) {
        const auto caseId = entry.get(QStringLiteral("caseId"))->text();
        const auto file = entry.get(QStringLiteral("file"))->text();
        const auto expect = entry.get(QStringLiteral("expect"))->text();
        const auto bytes = readGolden(file);
        const auto reject = tryValidate(bytes);
        if (expect == QStringLiteral("valid")) {
            if (reject.has_value()) {
                const auto fail = QStringLiteral("%1 expected valid, got %2").arg(caseId, reject->toString());
                QFAIL(qPrintable(fail));
            }
        } else {
            const auto category = entry.get(QStringLiteral("errorCategory"))->text();
            if (!reject.has_value()) {
                const auto fail = QStringLiteral("%1 expected reject %2, got valid").arg(caseId, category);
                QFAIL(qPrintable(fail));
            }
            if (reject->wire() != category) {
                const auto fail = QStringLiteral("%1 expected %2, got %3 (%4)")
                                      .arg(caseId, category, reject->wire(), reject->toString());
                QFAIL(qPrintable(fail));
            }
        }
    }
}

void PortableCodecTest::canonicalIdentity_OfMixedGoldenPackage_IsPinned()
{
    using namespace OjNexus::Portable;
    const PortableCodec codec;
    const auto bytes = readGolden(QStringLiteral("pw-p01.json"));
    const auto package = codec.validate(bytes);
    QCOMPARE(package.identity,
        QStringLiteral("81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44"));
    // Re-canonicalizing the validated root must be byte-stable.
    QCOMPARE(codec.canonicalBytes(package.root), package.canonicalBytes);
}

void PortableCodecTest::pathUnsafe_MatchesPythonDollarSemantics()
{
    using namespace OjNexus::Portable;
    // Regression (phase 133 round-2 parity): the RAW pattern must be used for
    // PATH_UNSAFE - a `^..$` path with a trailing newline matches Python's,
    // Java's and .NET's `$` (before the final terminator) and must be rejected.
    QByteArray poisoned = minimalValidPackage();
    poisoned.replace(QByteArrayLiteral("\"title\":\"Watermelon\""),
        QByteArrayLiteral("\"title\":\"..\\n\""));
    const auto reject = tryValidate(poisoned);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::PathUnsafe);

    // The same path WITHOUT the trailing newline still matches `^\.\.$` plainly.
    QByteArray plain = minimalValidPackage();
    plain.replace(QByteArrayLiteral("\"title\":\"Watermelon\""), QByteArrayLiteral("\"title\":\"..\""));
    const auto plainReject = tryValidate(plain);
    QVERIFY(plainReject.has_value());
    QCOMPARE(plainReject->category, PortableRejectCategory::PathUnsafe);
}

void PortableCodecTest::formatVersionFractional_IsRejectedAtVersionGate()
{
    using namespace OjNexus::Portable;
    QByteArray poisoned = minimalValidPackage();
    poisoned.replace(QByteArrayLiteral("\"formatVersion\":1,"), QByteArrayLiteral("\"formatVersion\":1.5,"));
    const auto reject = tryValidate(poisoned);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::InvalidInput);
    QVERIFY(reject->location == QStringLiteral("$.formatVersion"));
}

void PortableCodecTest::leadingZeroInteger_IsRejectedAtStrictDecode()
{
    using namespace OjNexus::Portable;
    QByteArray poisoned = minimalValidPackage();
    poisoned.replace(QByteArrayLiteral("\"formatVersion\":1,"), QByteArrayLiteral("\"formatVersion\":01,"));
    const auto reject = tryValidate(poisoned);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::InvalidInput);
}

void PortableCodecTest::credentialShapedKeys_AreNeverPortable()
{
    using namespace OjNexus::Portable;
    // `extra` is the open object: the schema walk lets unknown keys through
    // there, and the credential-shape check rejects them.
    QByteArray poisoned = minimalValidPackage();
    poisoned.replace(QByteArrayLiteral("\"origin\":\"scope\","),
        QByteArrayLiteral("\"origin\":\"scope\",\"extra\":{\"apikey\":\"x\"},"));
    const auto reject = tryValidate(poisoned);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::UnknownField);
    QVERIFY(reject->detail.contains(QStringLiteral("credential-shaped")));
}

void PortableCodecTest::extraNestedObjects_AreRejectedBeyondTwoLevels()
{
    using namespace OjNexus::Portable;
    QByteArray poisoned = minimalValidPackage();
    poisoned.replace(QByteArrayLiteral("\"origin\":\"scope\","),
        QByteArrayLiteral("\"origin\":\"scope\",\"extra\":{\"deep\":{\"deeper\":1}},"));
    const auto reject = tryValidate(poisoned);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::UnknownField);
    QVERIFY(reject->detail.contains(QStringLiteral("2-level depth")));
}

void PortableCodecTest::countsMismatch_IsRejected()
{
    using namespace OjNexus::Portable;
    // Deterministic probe 1: counts.problems declares 2 but one record exists.
    QByteArray mismatch = minimalValidPackage();
    mismatch.replace(QByteArrayLiteral("\"counts\":{\"problems\":1"),
        QByteArrayLiteral("\"counts\":{\"problems\":2"));
    const auto reject = tryValidate(mismatch);
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::CountsMismatch);

    // Deterministic probe 2: the corpus counts-mismatch case.
    const auto corpusReject = tryValidate(readGolden(QStringLiteral("pw-counts-mismatch.json")));
    QVERIFY(corpusReject.has_value());
    QCOMPARE(corpusReject->category, PortableRejectCategory::CountsMismatch);
}

void PortableCodecTest::duplicateNoteProblemId_IsRejected()
{
    using namespace OjNexus::Portable;
    const auto reject = tryValidate(readGolden(QStringLiteral("pw-n06.json")));
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::DuplicateId);
}

void PortableCodecTest::sessionMissingReference_IsRejected()
{
    using namespace OjNexus::Portable;
    const auto reject = tryValidate(readGolden(QStringLiteral("pw-n05.json")));
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::MissingReference);
}

void PortableCodecTest::missingFormatVersion_IsRejectedBeforeSchema()
{
    using namespace OjNexus::Portable;
    const auto reject = tryValidate(QByteArray("{\"scopeId\":\"s\"}"));
    QVERIFY(reject.has_value());
    QCOMPARE(reject->category, PortableRejectCategory::InvalidInput);
    QVERIFY(reject->detail.contains(QStringLiteral("formatVersion")));
}

QTEST_MAIN(PortableCodecTest)
#include "PortableCodecTest.moc"
