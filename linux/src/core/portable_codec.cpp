#include "ojnexus/core/portable_codec.h"

#include <QCryptographicHash>
#include <QRegularExpression>
#include <QVariant>

#include <charconv>
#include <limits>
#include <memory>
#include <optional>
#include <utility>

namespace OjNexus::Portable {
namespace {

constexpr int kMaxLen = 16'384; // schema maxLength counts characters, not bytes

// Android compiles PATH_UNSAFE with the RAW pattern (no `$` translation): PCRE2's
// default `$` matches before a final line terminator exactly like Python's, Java's
// and .NET's, so translating it to `\z` here would ACCEPT "…\n" strings the
// references reject. Only schema patterns get the translation.
constexpr auto kPathUnsafePattern = "(^[A-Za-z]:\\\\)|^/|\\.\\./|^\\.\\.$";

PortableContractException buildReject(PortableRejectCategory category, const QString& detail, const QString& location)
{
    return PortableContractException{PortableReject{category, detail, location}};
}

QString childPath(const QString& path, const QString& key)
{
    return QStringLiteral("%1.%2").arg(path, key);
}

/// Mirrors the Android codec's pattern handling: a terminal unescaped `$`
/// becomes the absolute-end anchor (Android compiles to `\z` because Java's
/// `$` is not absolute-end), and `\d` becomes `[0-9]` because Java's `\d`
/// is ASCII-only while .NET's matches Unicode digits.
QRegularExpression compilePattern(const QString& pattern)
{
    QString effective = pattern;
    if (effective.endsWith(QLatin1Char('$')) && !effective.endsWith(QStringLiteral("\\$"))) {
        effective.chop(1);
        effective += QStringLiteral("\\z");
    }
    effective.replace(QStringLiteral("\\d"), QStringLiteral("[0-9]"));
    return QRegularExpression{effective, QRegularExpression::UseUnicodePropertiesOption};
}

/// Anchored full-string integer-literal classification: Android uses
/// full-string matches, so "1.5", "1e5" and "01" must all classify as
/// non-integers. Implemented as a direct scan (never an unanchored regex).
bool isIntegerLiteral(const QString& literal)
{
    int index = 0;
    if (index < literal.size() && literal.at(index) == QLatin1Char('-')) ++index;
    if (index >= literal.size()) return false;
    const auto first = literal.at(index);
    if (first == QLatin1Char('0')) return index + 1 == literal.size();
    if (first < QLatin1Char('1') || first > QLatin1Char('9')) return false;
    for (++index; index < literal.size(); ++index) {
        if (!literal.at(index).isDigit()) return false;
    }
    return true;
}

/// Strict int64 parse mirroring C# long.TryParse (no whitespace tolerance,
/// overflow fails). Returns nullopt on any failure including out-of-range.
std::optional<qint64> tryParseInt64(const QString& literal)
{
    const auto utf8 = literal.toUtf8();
    qint64 value = 0;
    const auto result = std::from_chars(utf8.constBegin(), utf8.constEnd(), value, 10);
    if (result.ec != std::errc{} || result.ptr != utf8.constEnd()) return std::nullopt;
    return value;
}

/// Required-int parse for manifest fields. A literal the schema accepted but
/// that cannot fit int64 is rejected deterministically here: the Android and
/// Windows references crash on the same input (NumberFormatException /
/// OverflowException from long parse), and no consumer can hold the value, so
/// this is the honest non-crash behavior (recorded deviation from the C#
/// reference, see the phase 134 review).
qint64 parseRequiredInt64(const QString& literal, const QString& path)
{
    const auto parsed = tryParseInt64(literal);
    if (!parsed.has_value()) {
        throw buildReject(PortableRejectCategory::InvalidInput,
            QStringLiteral("value out of range for a 64-bit integer: %1").arg(literal), path);
    }
    return parsed.value();
}

QString typeName(const PortableValue& value)
{
    switch (value.kind()) {
    case PortableValue::Kind::Obj: return QStringLiteral("object");
    case PortableValue::Kind::Arr: return QStringLiteral("array");
    case PortableValue::Kind::Str: return QStringLiteral("string");
    case PortableValue::Kind::Num: return isIntegerLiteral(value.text()) ? QStringLiteral("integer") : QStringLiteral("number");
    case PortableValue::Kind::Bool: return QStringLiteral("boolean");
    case PortableValue::Kind::Null: return QStringLiteral("null");
    }
    return QStringLiteral("unknown");
}

QString jsonRepr(const PortableValue& value)
{
    switch (value.kind()) {
    case PortableValue::Kind::Str: return QStringLiteral("\"%1\"").arg(value.text());
    case PortableValue::Kind::Num: return value.text();
    case PortableValue::Kind::Bool: return value.boolValue() ? QStringLiteral("true") : QStringLiteral("false");
    case PortableValue::Kind::Null: return QStringLiteral("null");
    case PortableValue::Kind::Obj: return QStringLiteral("object");
    case PortableValue::Kind::Arr: return QStringLiteral("array");
    }
    return QStringLiteral("unknown");
}

// --- schema subset rule machinery (draft-07 subset) ---

struct Rule;
using RulePtr = std::shared_ptr<const Rule>;

struct Rule {
    enum class Kind { Obj, Arr, Str, Int, Bool };

    Kind kind = Kind::Bool;

    // Obj
    QStringList required;
    bool closed = true;
    // Ordered property list (C# iterates its dictionary in insertion order).
    QList<QPair<QString, RulePtr>> propList;
    QMap<QString, int> propIndex;

    // Arr
    RulePtr arrItems;
    bool hasMaxItems = false;
    int maxItems = 0;

    // Str
    std::optional<QRegularExpression> strPattern;
    bool hasMinLen = false;
    int minLen = 0;
    bool hasMaxLen = false;
    int maxLenValue = 0;
    bool hasEnumSet = false;
    QSet<QString> enumSet;

    // Int
    bool hasMin = false;
    qint64 minValue = 0;
    bool hasIntEnum = false;
    QList<qint64> intEnum;
};

RulePtr makeObj(const QStringList& required, QList<QPair<QString, RulePtr>> props, bool closed = true)
{
    auto rule = std::make_shared<Rule>();
    rule->kind = Rule::Kind::Obj;
    rule->required = required;
    rule->closed = closed;
    for (int i = 0; i < props.size(); ++i) {
        rule->propIndex.insert(props.at(i).first, i);
    }
    rule->propList = std::move(props);
    return rule;
}

RulePtr makeStr(bool hasPattern = false, const QString& pattern = {},
    bool hasMinLen = false, int minLen = 0, bool hasMaxLen = false, int maxLen = 0,
    bool hasEnumSet = false, QSet<QString> enumSet = {})
{
    auto rule = std::make_shared<Rule>();
    rule->kind = Rule::Kind::Str;
    if (hasPattern) rule->strPattern = compilePattern(pattern);
    rule->hasMinLen = hasMinLen;
    rule->minLen = minLen;
    rule->hasMaxLen = hasMaxLen;
    rule->maxLenValue = maxLen;
    rule->hasEnumSet = hasEnumSet;
    rule->enumSet = std::move(enumSet);
    return rule;
}

RulePtr makeInt(bool hasMin = false, qint64 min = 0, bool hasIntEnum = false, QList<qint64> intEnum = {})
{
    auto rule = std::make_shared<Rule>();
    rule->kind = Rule::Kind::Int;
    rule->hasMin = hasMin;
    rule->minValue = min;
    rule->hasIntEnum = hasIntEnum;
    rule->intEnum = std::move(intEnum);
    return rule;
}

RulePtr makeBool()
{
    auto rule = std::make_shared<Rule>();
    rule->kind = Rule::Kind::Bool;
    return rule;
}

RulePtr makeArr(RulePtr items, bool hasMaxItems = false, int maxItems = 0)
{
    auto rule = std::make_shared<Rule>();
    rule->kind = Rule::Kind::Arr;
    rule->arrItems = std::move(items);
    rule->hasMaxItems = hasMaxItems;
    rule->maxItems = maxItems;
    return rule;
}

QSet<QString> stringSet(std::initializer_list<const char*> values)
{
    QSet<QString> set;
    for (const auto* value : values) set.insert(QString::fromLatin1(value));
    return set;
}

struct SchemaTables {
    RulePtr rootSchema;
    QMap<QString, RulePtr> entitySchemas;
};

/// Builds the frozen schema subset (mirrors the Windows static constructor).
SchemaTables buildSchemaTables()
{
    const auto stableId = makeStr(true, PortableSpec::stableIdPattern(), false, 0, true, kMaxLen);
    const auto dayKey = makeStr(true, PortableSpec::dayKeyPattern(), false, 0, true, kMaxLen);
    const auto origin = makeStr(false, {}, false, 0, false, 0, true, PortableSpec::origins());
    // `extra` is declared as {"type":"object"} only: value shapes and depth
    // are enforced by the checker beyond the schema subset, not here; the
    // object itself is OPEN (Closed: false in the C# reference).
    const auto extra = makeObj({}, {}, false);
    const auto plainStr = makeStr(false, {}, false, 0, true, kMaxLen);
    const auto epoch = makeInt(true, 0);

    const auto withBase = [](QList<QPair<QString, RulePtr>> extraProps) {
        auto all = QList<QPair<QString, RulePtr>>{
            {QStringLiteral("stableId"), nullptr},
            {QStringLiteral("sourceScopeId"), nullptr},
            {QStringLiteral("origin"), nullptr},
            {QStringLiteral("extra"), nullptr},
        };
        all += extraProps;
        return all;
    };
    const auto applyBase = [](QList<QPair<QString, RulePtr>>& props) {
        props[0].second = makeStr(true, PortableSpec::stableIdPattern(), false, 0, true, kMaxLen);
        props[1].second = props[0].second;
        props[2].second = makeStr(false, {}, false, 0, false, 0, true, PortableSpec::origins());
        props[3].second = makeObj({}, {}, false);
    };

    auto problemsProps = withBase({
        {QStringLiteral("judge"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::judges())},
        {QStringLiteral("externalId"), makeStr(true, PortableSpec::externalIdPattern(), false, 0, true, kMaxLen)},
        {QStringLiteral("title"), plainStr},
        {QStringLiteral("sourceUrl"), makeStr(true, QStringLiteral("^https?://"), false, 0, true, kMaxLen)},
        {QStringLiteral("solved"), makeBool()},
        {QStringLiteral("firstSolvedAtEpochMs"), epoch},
        {QStringLiteral("difficulty"), makeInt()}, // absent stays absent; never coerced to 0
        {QStringLiteral("tags"), makeArr(plainStr)},
        {QStringLiteral("dayKey"), dayKey},
    });
    applyBase(problemsProps);
    const auto problemsEntity = makeObj(
        QStringList{QStringLiteral("stableId"), QStringLiteral("sourceScopeId"), QStringLiteral("origin"),
            QStringLiteral("judge"), QStringLiteral("externalId"), QStringLiteral("title"),
            QStringLiteral("solved"), QStringLiteral("dayKey")},
        problemsProps);

    auto attemptsProps = withBase({
        {QStringLiteral("problemId"), stableId},
        {QStringLiteral("timestampEpochMs"), epoch},
        {QStringLiteral("dayKey"), dayKey},
        {QStringLiteral("verdict"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::verdicts())},
        {QStringLiteral("rawVerdict"), plainStr},
        {QStringLiteral("language"), plainStr},
        {QStringLiteral("durationMin"), epoch},
        {QStringLiteral("externalSubmissionId"), plainStr},
        {QStringLiteral("note"), plainStr},
    });
    applyBase(attemptsProps);
    const auto attemptsEntity = makeObj(
        QStringList{QStringLiteral("stableId"), QStringLiteral("sourceScopeId"), QStringLiteral("origin"),
            QStringLiteral("problemId"), QStringLiteral("timestampEpochMs"), QStringLiteral("verdict"),
            QStringLiteral("dayKey")},
        attemptsProps);

    auto notesProps = withBase({
        {QStringLiteral("problemId"), stableId},
        {QStringLiteral("keyInsight"), plainStr},
        {QStringLiteral("implementationNotes"), plainStr},
        {QStringLiteral("complexity"), plainStr},
        {QStringLiteral("general"), plainStr},
        {QStringLiteral("updatedEpochMs"), epoch},
        {QStringLiteral("dayKey"), dayKey},
    });
    applyBase(notesProps);
    const auto notesEntity = makeObj(
        QStringList{QStringLiteral("stableId"), QStringLiteral("sourceScopeId"), QStringLiteral("origin"),
            QStringLiteral("problemId"), QStringLiteral("keyInsight"), QStringLiteral("implementationNotes"),
            QStringLiteral("complexity"), QStringLiteral("general"), QStringLiteral("updatedEpochMs"),
            QStringLiteral("dayKey")},
        notesProps);

    auto reviewsProps = withBase({
        {QStringLiteral("problemId"), stableId},
        {QStringLiteral("stage"), makeInt(true, 1)},
        {QStringLiteral("dueEpochMs"), epoch},
        {QStringLiteral("dueDayKey"), dayKey},
        {QStringLiteral("lastResult"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::lastResults())},
        {QStringLiteral("lastReviewedEpochMs"), epoch},
        {QStringLiteral("createdEpochMs"), epoch},
        {QStringLiteral("dayKey"), dayKey},
    });
    applyBase(reviewsProps);
    const auto reviewsEntity = makeObj(
        QStringList{QStringLiteral("stableId"), QStringLiteral("sourceScopeId"), QStringLiteral("origin"),
            QStringLiteral("problemId"), QStringLiteral("stage"), QStringLiteral("dueEpochMs"),
            QStringLiteral("dueDayKey"), QStringLiteral("createdEpochMs"), QStringLiteral("dayKey")},
        reviewsProps);

    auto sessionsProps = withBase({
        {QStringLiteral("type"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::sessionTypes())},
        {QStringLiteral("state"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::sessionStates())},
        {QStringLiteral("startedEpochMs"), epoch},
        {QStringLiteral("finishedEpochMs"), epoch},
        {QStringLiteral("totalPausedMs"), epoch},
        {QStringLiteral("targetDurationMin"), epoch},
        {QStringLiteral("problemIds"), makeArr(makeStr(true, PortableSpec::stableIdPattern(), false, 0, true, kMaxLen))},
        {QStringLiteral("dayKey"), dayKey},
    });
    applyBase(sessionsProps);
    const auto sessionsEntity = makeObj(
        QStringList{QStringLiteral("stableId"), QStringLiteral("sourceScopeId"), QStringLiteral("origin"),
            QStringLiteral("type"), QStringLiteral("state"), QStringLiteral("startedEpochMs"),
            QStringLiteral("totalPausedMs"), QStringLiteral("dayKey")},
        sessionsProps);

    const auto generatorSchema = makeObj(
        QStringList{QStringLiteral("name"), QStringLiteral("version")},
        QList<QPair<QString, RulePtr>>{
            {QStringLiteral("name"), makeStr(false, {}, true, 1, true, kMaxLen)},
            {QStringLiteral("version"), makeStr(false, {}, true, 1, true, kMaxLen)},
        });

    const auto countsSchema = makeObj(
        QStringList{QStringLiteral("problems"), QStringLiteral("attempts"), QStringLiteral("notes"),
            QStringLiteral("reviews"), QStringLiteral("sessions")},
        QList<QPair<QString, RulePtr>>{
            {QStringLiteral("problems"), epoch},
            {QStringLiteral("attempts"), epoch},
            {QStringLiteral("notes"), epoch},
            {QStringLiteral("reviews"), epoch},
            {QStringLiteral("sessions"), epoch},
        });

    const auto collection = [](const RulePtr& entity) { return makeArr(entity, true, PortableSpec::kMaxRecords); };

    SchemaTables tables;
    tables.entitySchemas = QMap<QString, RulePtr>{
        {QStringLiteral("problems"), problemsEntity},
        {QStringLiteral("attempts"), attemptsEntity},
        {QStringLiteral("notes"), notesEntity},
        {QStringLiteral("reviews"), reviewsEntity},
        {QStringLiteral("sessions"), sessionsEntity},
    };
    QList<QPair<QString, RulePtr>> rootProps{
        {QStringLiteral("formatVersion"), makeInt(false, 0, true, QList<qint64>{1})},
        {QStringLiteral("scopeId"), makeStr(true, PortableSpec::stableIdPattern(), false, 0, true, kMaxLen)},
        {QStringLiteral("sourcePlatform"), makeStr(false, {}, false, 0, false, 0, true, PortableSpec::platforms())},
        {QStringLiteral("generator"), generatorSchema},
        {QStringLiteral("createdAtEpochMs"), epoch},
        {QStringLiteral("counts"), countsSchema},
        {QStringLiteral("problems"), collection(tables.entitySchemas.value(QStringLiteral("problems")))},
        {QStringLiteral("attempts"), collection(tables.entitySchemas.value(QStringLiteral("attempts")))},
        {QStringLiteral("notes"), collection(tables.entitySchemas.value(QStringLiteral("notes")))},
        {QStringLiteral("reviews"), collection(tables.entitySchemas.value(QStringLiteral("reviews")))},
        {QStringLiteral("sessions"), collection(tables.entitySchemas.value(QStringLiteral("sessions")))},
    };
    tables.rootSchema = makeObj(
        QStringList{QStringLiteral("formatVersion"), QStringLiteral("scopeId"), QStringLiteral("sourcePlatform"),
            QStringLiteral("generator"), QStringLiteral("createdAtEpochMs"), QStringLiteral("counts"),
            QStringLiteral("problems"), QStringLiteral("attempts"), QStringLiteral("notes"),
            QStringLiteral("reviews"), QStringLiteral("sessions")},
        rootProps);
    return tables;
}

const SchemaTables& schemaTables()
{
    static const SchemaTables tables = buildSchemaTables();
    return tables;
}

const QRegularExpression& pathUnsafeRegex()
{
    static const QRegularExpression regex =
        QRegularExpression{QString::fromLatin1(kPathUnsafePattern), QRegularExpression::UseUnicodePropertiesOption};
    return regex;
}

PortableContractException typeReject(const QString& expected, const PortableValue& value, const QString& path)
{
    return buildReject(PortableRejectCategory::UnknownField,
        QStringLiteral("type: expected type %1, got %2").arg(expected, typeName(value)), path);
}

void validateSchemaValue(const PortableValue& value, const Rule& rule, const QString& path);

void validateSchema(const PortableValue& value, const Rule& rule, const QString& path)
{
    if (value.kind() != PortableValue::Kind::Obj) throw typeReject(QStringLiteral("object"), value, path);

    for (const auto& requiredKey : rule.required) {
        if (value.get(requiredKey) == nullptr) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("required: missing required key \"%1\"").arg(requiredKey), path);
        }
    }

    if (rule.closed) {
        for (const auto& entry : value.entries()) {
            if (!rule.propIndex.contains(entry.key)) {
                throw buildReject(PortableRejectCategory::UnknownField,
                    QStringLiteral("additionalProperties: unknown key \"%1\" (additionalProperties: false; "
                        "nothing is silently ignored)").arg(entry.key),
                    childPath(path, entry.key));
            }
        }
    }

    for (const auto& prop : rule.propList) {
        const auto* childValue = value.get(prop.first);
        if (childValue == nullptr) continue;
        validateSchemaValue(*childValue, *prop.second, childPath(path, prop.first));
    }
}

void validateSchemaValue(const PortableValue& value, const Rule& rule, const QString& path)
{
    switch (rule.kind) {
    case Rule::Kind::Obj:
        validateSchema(value, rule, path);
        break;
    case Rule::Kind::Arr: {
        if (value.kind() != PortableValue::Kind::Arr) throw typeReject(QStringLiteral("array"), value, path);
        const auto& items = value.items();
        if (rule.arrItems != nullptr) {
            for (int index = 0; index < items.size(); ++index) {
                validateSchemaValue(items.at(index), *rule.arrItems,
                    QStringLiteral("%1[%2]").arg(path).arg(index));
            }
        }
        if (rule.hasMaxItems && items.size() > rule.maxItems) {
            throw buildReject(PortableRejectCategory::SizeLimit,
                QStringLiteral("maxItems: array must have at most %1 items, got %2").arg(rule.maxItems).arg(items.size()),
                path);
        }
        break;
    }
    case Rule::Kind::Str: {
        if (value.kind() != PortableValue::Kind::Str) throw typeReject(QStringLiteral("string"), value, path);
        const auto& text = value.text();
        if (rule.strPattern.has_value() && !rule.strPattern->match(text).hasMatch()) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("pattern: value does not match pattern %1").arg(rule.strPattern->pattern()), path);
        }
        if (rule.hasMinLen && text.size() < rule.minLen) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("minLength: string must have at least %1 characters, got %2").arg(rule.minLen).arg(text.size()),
                path);
        }
        if (rule.hasMaxLen && text.size() > rule.maxLenValue) {
            throw buildReject(PortableRejectCategory::SizeLimit,
                QStringLiteral("maxLength: string must have at most %1 characters, got %2").arg(rule.maxLenValue).arg(text.size()),
                path);
        }
        if (rule.hasEnumSet && !rule.enumSet.contains(text)) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("enum: value is not one of the admitted values"), path);
        }
        break;
    }
    case Rule::Kind::Int: {
        if (value.kind() != PortableValue::Kind::Num || !isIntegerLiteral(value.text()))
            throw typeReject(QStringLiteral("integer"), value, path);
        const auto parsed = tryParseInt64(value.text());
        if (rule.hasMin && parsed.has_value() && parsed.value() < rule.minValue) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("minimum: value must be >= %1").arg(rule.minValue), path);
        }
        if (rule.hasIntEnum && (!parsed.has_value() || !rule.intEnum.contains(parsed.value()))) {
            throw buildReject(PortableRejectCategory::UnknownField,
                QStringLiteral("enum: value is not one of the admitted values"), path);
        }
        break;
    }
    case Rule::Kind::Bool:
        if (value.kind() != PortableValue::Kind::Bool) throw typeReject(QStringLiteral("boolean"), value, path);
        break;
    }
}

// --- contract rules beyond the schema subset ---

bool isCredentialShaped(const QString& key)
{
    // C# Regex.Replace(key, "[\s._\-]+", ""): strip runs of whitespace, dots,
    // underscores and hyphens, then lowercase. The Unicode property option
    // keeps \s semantics identical to .NET's Unicode \s.
    static const QRegularExpression stripPattern =
        QRegularExpression{QStringLiteral("[\\s._\\-]+"), QRegularExpression::UseUnicodePropertiesOption};
    const auto normalized = QString{key}.replace(stripPattern, QString()).toLower();
    for (const auto& token : PortableSpec::credentialTokens()) {
        if (normalized.contains(token)) return true;
    }
    return false;
}

void checkCredentials(const PortableValue& value, const QString& path)
{
    switch (value.kind()) {
    case PortableValue::Kind::Obj:
        for (const auto& entry : value.entries()) {
            const auto child = childPath(path, entry.key);
            if (isCredentialShaped(entry.key)) {
                throw buildReject(PortableRejectCategory::UnknownField,
                    QStringLiteral("credential-shaped key \"%1\" — credentials are never portable").arg(entry.key), child);
            }
            checkCredentials(entry.value, child);
        }
        break;
    case PortableValue::Kind::Arr:
        for (int index = 0; index < value.items().size(); ++index) {
            checkCredentials(value.items().at(index), QStringLiteral("%1[%2]").arg(path).arg(index));
        }
        break;
    default:
        break;
    }
}

void checkExtraDepth(const PortableValue& value, const QString& path)
{
    switch (value.kind()) {
    case PortableValue::Kind::Obj:
        for (const auto& entry : value.entries()) {
            const auto child = childPath(path, entry.key);
            if (entry.key == QStringLiteral("extra") && entry.value.kind() == PortableValue::Kind::Obj) {
                for (const auto& extraEntry : entry.value.entries()) {
                    const auto extraPath = childPath(child, extraEntry.key);
                    if (extraEntry.value.kind() == PortableValue::Kind::Obj) {
                        throw buildReject(PortableRejectCategory::UnknownField,
                            QStringLiteral("extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded"),
                            extraPath);
                    }
                    if (extraEntry.value.kind() == PortableValue::Kind::Arr) {
                        const auto& extraItems = extraEntry.value.items();
                        for (int index = 0; index < extraItems.size(); ++index) {
                            if (extraItems.at(index).kind() == PortableValue::Kind::Obj ||
                                extraItems.at(index).kind() == PortableValue::Kind::Arr) {
                                throw buildReject(PortableRejectCategory::UnknownField,
                                    QStringLiteral("extra values may be scalars or arrays of scalars; the 2-level depth limit is exceeded"),
                                    QStringLiteral("%1[%2]").arg(extraPath).arg(index));
                            }
                        }
                    }
                }
            } else {
                checkExtraDepth(entry.value, child);
            }
        }
        break;
    case PortableValue::Kind::Arr:
        for (int index = 0; index < value.items().size(); ++index) {
            checkExtraDepth(value.items().at(index), QStringLiteral("%1[%2]").arg(path).arg(index));
        }
        break;
    default:
        break;
    }
}

void checkCollectionSizes(const PortableValue& root)
{
    for (const auto& collection : PortableSpec::collections()) {
        const auto* array = root.get(collection);
        if (array == nullptr || array->kind() != PortableValue::Kind::Arr) continue;
        if (array->items().size() > PortableSpec::kMaxRecords) {
            throw buildReject(PortableRejectCategory::SizeLimit,
                QStringLiteral("collection %1 holds %2 records, limit is %3")
                    .arg(collection).arg(array->items().size()).arg(PortableSpec::kMaxRecords),
                QStringLiteral("$.%1").arg(collection));
        }
    }
}

void checkStringSizes(const PortableValue& value, const QString& path)
{
    switch (value.kind()) {
    case PortableValue::Kind::Obj:
        for (const auto& entry : value.entries()) {
            const auto child = childPath(path, entry.key);
            const auto keySize = entry.key.toUtf8().size();
            if (keySize > PortableSpec::kMaxStringBytes) {
                throw buildReject(PortableRejectCategory::SizeLimit,
                    QStringLiteral("object key is %1 UTF-8 bytes, limit is %2").arg(keySize).arg(PortableSpec::kMaxStringBytes),
                    child);
            }
            checkStringSizes(entry.value, child);
        }
        break;
    case PortableValue::Kind::Arr:
        for (int index = 0; index < value.items().size(); ++index) {
            checkStringSizes(value.items().at(index), QStringLiteral("%1[%2]").arg(path).arg(index));
        }
        break;
    case PortableValue::Kind::Str: {
        const auto size = value.text().toUtf8().size();
        if (size > PortableSpec::kMaxStringBytes) {
            throw buildReject(PortableRejectCategory::SizeLimit,
                QStringLiteral("string is %1 UTF-8 bytes, limit is %2").arg(size).arg(PortableSpec::kMaxStringBytes),
                path);
        }
        break;
    }
    default:
        break;
    }
}

void checkPathShapes(const PortableValue& value, const QString& path)
{
    switch (value.kind()) {
    case PortableValue::Kind::Obj:
        for (const auto& entry : value.entries()) {
            checkPathShapes(entry.value, childPath(path, entry.key));
        }
        break;
    case PortableValue::Kind::Arr:
        for (int index = 0; index < value.items().size(); ++index) {
            checkPathShapes(value.items().at(index), QStringLiteral("%1[%2]").arg(path).arg(index));
        }
        break;
    case PortableValue::Kind::Str:
        if (pathUnsafeRegex().match(value.text()).hasMatch()) {
            throw buildReject(PortableRejectCategory::PathUnsafe,
                QStringLiteral("string value looks like a filesystem path"), path);
        }
        break;
    default:
        break;
    }
}

void checkDuplicates(const PortableValue& root)
{
    for (const auto& collection : PortableSpec::collections()) {
        const auto* array = root.get(collection);
        if (array == nullptr || array->kind() != PortableValue::Kind::Arr) continue;
        QHash<QString, int> seenStable;
        QHash<QString, int> seenProblem;
        const auto& items = array->items();
        for (int index = 0; index < items.size(); ++index) {
            const auto& item = items.at(index);
            if (item.kind() != PortableValue::Kind::Obj) continue;
            const auto* stableId = item.get(QStringLiteral("stableId"));
            if (stableId != nullptr && stableId->kind() == PortableValue::Kind::Str) {
                if (seenStable.contains(stableId->text())) {
                    throw buildReject(PortableRejectCategory::DuplicateId,
                        QStringLiteral("duplicate stableId \"%1\" in collection %2").arg(stableId->text(), collection),
                        QStringLiteral("$.%1[%2].stableId").arg(collection).arg(index));
                }
                seenStable.insert(stableId->text(), index);
            }
            if (collection == QStringLiteral("notes")) {
                const auto* problemId = item.get(QStringLiteral("problemId"));
                if (problemId != nullptr && problemId->kind() == PortableValue::Kind::Str) {
                    if (seenProblem.contains(problemId->text())) {
                        throw buildReject(PortableRejectCategory::DuplicateId,
                            QStringLiteral("duplicate problemId \"%1\" in notes[] (one note per problem per package)").arg(problemId->text()),
                            QStringLiteral("$.%1[%2].problemId").arg(collection).arg(index));
                    }
                    seenProblem.insert(problemId->text(), index);
                }
            }
        }
    }
}

void checkReferences(const PortableValue& root)
{
    QSet<QString> problemIds;
    if (const auto* problems = root.get(QStringLiteral("problems"));
        problems != nullptr && problems->kind() == PortableValue::Kind::Arr) {
        for (const auto& record : problems->items()) {
            if (record.kind() != PortableValue::Kind::Obj) continue;
            const auto* stableId = record.get(QStringLiteral("stableId"));
            if (stableId != nullptr && stableId->kind() == PortableValue::Kind::Str) {
                problemIds.insert(stableId->text());
            }
        }
    }

    const QStringList referencing{QStringLiteral("attempts"), QStringLiteral("notes"), QStringLiteral("reviews")};
    for (const auto& collection : referencing) {
        const auto* array = root.get(collection);
        if (array == nullptr || array->kind() != PortableValue::Kind::Arr) continue;
        const auto& items = array->items();
        for (int index = 0; index < items.size(); ++index) {
            const auto& record = items.at(index);
            if (record.kind() != PortableValue::Kind::Obj) continue;
            const auto* problemId = record.get(QStringLiteral("problemId"));
            if (problemId == nullptr || problemId->kind() != PortableValue::Kind::Str) continue;
            if (!problemIds.contains(problemId->text())) {
                throw buildReject(PortableRejectCategory::MissingReference,
                    QStringLiteral("%1[%2] references problemId \"%3\" which does not exist in problems[].stableId")
                        .arg(collection).arg(index).arg(problemId->text()),
                    QStringLiteral("$.%1[%2].problemId").arg(collection).arg(index));
            }
        }
    }

    const auto* sessions = root.get(QStringLiteral("sessions"));
    if (sessions == nullptr || sessions->kind() != PortableValue::Kind::Arr) return;
    const auto& sessionItems = sessions->items();
    for (int index = 0; index < sessionItems.size(); ++index) {
        const auto& record = sessionItems.at(index);
        if (record.kind() != PortableValue::Kind::Obj) continue;
        const auto* references = record.get(QStringLiteral("problemIds"));
        if (references == nullptr || references->kind() != PortableValue::Kind::Arr) continue;
        const auto& referenceItems = references->items();
        for (int reference = 0; reference < referenceItems.size(); ++reference) {
            const auto& problemId = referenceItems.at(reference);
            if (problemId.kind() != PortableValue::Kind::Str) continue;
            if (!problemIds.contains(problemId.text())) {
                throw buildReject(PortableRejectCategory::MissingReference,
                    QStringLiteral("sessions[%1].problemIds[%2] references \"%3\" which does not exist in problems[].stableId")
                        .arg(index).arg(reference).arg(problemId.text()),
                    QStringLiteral("$.sessions[%1].problemIds[%2]").arg(index).arg(reference));
            }
        }
    }
}

void checkCounts(const PortableValue& root)
{
    const auto* counts = root.get(QStringLiteral("counts"));
    if (counts == nullptr || counts->kind() != PortableValue::Kind::Obj) return;
    for (const auto& collection : PortableSpec::collections()) {
        const auto* declared = counts->get(collection);
        if (declared == nullptr || declared->kind() != PortableValue::Kind::Num || !isIntegerLiteral(declared->text()))
            continue;
        // Overflow (a literal that cannot fit int64) skips the comparison: no
        // reference implementation can hold such a count, and the deterministic
        // whole-package reject happens later at manifest assembly.
        const auto declaredValue = tryParseInt64(declared->text());
        if (!declaredValue.has_value()) continue;
        const auto* array = root.get(collection);
        const auto actual = array != nullptr && array->kind() == PortableValue::Kind::Arr ? array->items().size() : 0;
        if (declaredValue.value() != static_cast<qint64>(actual)) {
            throw buildReject(PortableRejectCategory::CountsMismatch,
                QStringLiteral("counts.%1 declares %2 but the %1 array holds %3 records")
                    .arg(collection).arg(declaredValue.value()).arg(actual),
                QStringLiteral("$.counts.%1").arg(collection));
        }
    }
}

bool isSynthetic(const PortableValue& record)
{
    const auto* extra = record.get(QStringLiteral("extra"));
    if (extra == nullptr || extra->kind() != PortableValue::Kind::Obj) return false;
    const auto* synthetic = extra->get(QStringLiteral("synthetic"));
    return synthetic != nullptr && synthetic->kind() == PortableValue::Kind::Bool && synthetic->boolValue();
}

ValidatedPackage buildValidated(const PortableValue& root)
{
    const auto canonicalBytes = PortableJson::canonicalBytes(root);
    ValidatedPackage package;
    package.canonicalBytes = canonicalBytes;
    package.identity = QString::fromLatin1(QCryptographicHash::hash(canonicalBytes, QCryptographicHash::Sha256).toHex());
    const auto* scopeId = root.get(QStringLiteral("scopeId"));
    const auto* sourcePlatform = root.get(QStringLiteral("sourcePlatform"));
    const auto* generatorObj = root.get(QStringLiteral("generator"));
    // Schema validation guarantees these are present with the right shapes.
    package.scopeId = scopeId->text();
    package.sourcePlatform = sourcePlatform->text();
    const auto* generatorNameValue = generatorObj->get(QStringLiteral("name"));
    const auto* generatorVersionValue = generatorObj->get(QStringLiteral("version"));
    const auto generatorName = generatorNameValue->text();
    const auto generatorVersion = generatorVersionValue->text();
    package.generator = QStringLiteral("%1 %2").arg(generatorName, generatorVersion);
    package.generatorJson = QString::fromUtf8(PortableJson::canonicalBytes(*generatorObj));
    while (package.generatorJson.endsWith(QLatin1Char('\n'))) package.generatorJson.chop(1);
    package.createdAtEpochMs = parseRequiredInt64(
        root.get(QStringLiteral("createdAtEpochMs"))->text(), QStringLiteral("$.createdAtEpochMs"));
    const auto* countsObj = root.get(QStringLiteral("counts"));
    for (const auto& collection : PortableSpec::collections()) {
        const auto count = parseRequiredInt64(
            countsObj->get(collection)->text(), QStringLiteral("$.counts.%1").arg(collection));
        if (count > std::numeric_limits<int>::max()) {
            throw buildReject(PortableRejectCategory::InvalidInput,
                QStringLiteral("value out of range for a 32-bit count: %1").arg(count),
                QStringLiteral("$.counts.%1").arg(collection));
        }
        package.counts.insert(collection, static_cast<int>(count));
    }

    auto packageSynthetic = generatorName == QStringLiteral("synthetic-fixture-gen");
    for (const auto& collection : PortableSpec::collections()) {
        const auto& array = root.get(collection)->items();
        QList<ValidatedRecord> list;
        list.reserve(array.size());
        for (int index = 0; index < array.size(); ++index) {
            const auto& record = array.at(index);
            const auto recordSynthetic = isSynthetic(record);
            packageSynthetic = packageSynthetic || recordSynthetic;
            QString canonicalJson = QString::fromUtf8(PortableJson::canonicalBytes(record));
            while (canonicalJson.endsWith(QLatin1Char('\n'))) canonicalJson.chop(1);
            list.push_back(ValidatedRecord{
                collection,
                index,
                canonicalJson,
                record.get(QStringLiteral("stableId"))->text(),
                record.get(QStringLiteral("sourceScopeId"))->text(),
                record.get(QStringLiteral("origin"))->text(),
                recordSynthetic,
            });
        }
        package.records.insert(collection, std::move(list));
    }

    package.sourceTag = packageSynthetic ? QStringLiteral("SYNTHETIC-TEST-FIXTURE") : QStringLiteral("PORTABLE-V1");
    package.synthetic = packageSynthetic;
    package.root = root;
    return package;
}

} // namespace

ValidatedPackage PortableCodec::validate(const QByteArray& bytes) const
{
    // 1. SIZE_LIMIT (raw size, before decode).
    if (bytes.size() > PortableSpec::kMaxPackageBytes) {
        throw buildReject(PortableRejectCategory::SizeLimit,
            QStringLiteral("package is %1 bytes, limit is %2").arg(bytes.size()).arg(PortableSpec::kMaxPackageBytes),
            QStringLiteral("$"));
    }

    // 2. INVALID_INPUT (strict decode).
    PortableValue parsed;
    try {
        parsed = PortableJson::parse(bytes);
    } catch (const PortableContractException&) {
        throw;
    } catch (const std::exception& exception) {
        throw PortableContractException{PortableReject{
            PortableRejectCategory::InvalidInput, QString::fromUtf8(exception.what()), QStringLiteral("$")}};
    }

    if (parsed.kind() != PortableValue::Kind::Obj) {
        throw buildReject(PortableRejectCategory::InvalidInput,
            QStringLiteral("package root must be a JSON object, got %1").arg(typeName(parsed)),
            QStringLiteral("$"));
    }

    // 3. INVALID_INPUT / UNSUPPORTED_VERSION (version gate, before schema).
    const auto* formatVersion = parsed.get(QStringLiteral("formatVersion"));
    if (formatVersion == nullptr) {
        throw buildReject(PortableRejectCategory::InvalidInput,
            QStringLiteral("missing required manifest key \"formatVersion\""), QStringLiteral("$"));
    }
    if (formatVersion->kind() != PortableValue::Kind::Num || !isIntegerLiteral(formatVersion->text())) {
        throw buildReject(PortableRejectCategory::InvalidInput,
            QStringLiteral("formatVersion must be the integer 1, got %1").arg(jsonRepr(*formatVersion)),
            QStringLiteral("$.formatVersion"));
    }
    if (formatVersion->text() != QStringLiteral("1")) {
        throw buildReject(PortableRejectCategory::UnsupportedVersion,
            QStringLiteral("formatVersion %1 is not supported by this contract (expected 1); "
                "future majors are rejected whole-package, never best-effort decoded").arg(formatVersion->text()),
            QStringLiteral("$.formatVersion"));
    }

    // 4. UNKNOWN_FIELD (draft-07 subset) + credential-shaped keys.
    validateSchema(parsed, *schemaTables().rootSchema, QStringLiteral("$"));
    checkCredentials(parsed, QStringLiteral("$"));

    // 5-9. Contract rules beyond the schema, in documented precedence order.
    checkExtraDepth(parsed, QStringLiteral("$"));
    checkCollectionSizes(parsed);
    checkStringSizes(parsed, QStringLiteral("$"));
    checkPathShapes(parsed, QStringLiteral("$"));
    checkDuplicates(parsed);
    checkReferences(parsed);
    checkCounts(parsed);

    return buildValidated(parsed);
}

QByteArray PortableCodec::canonicalBytes(const PortableValue& value) const
{
    return PortableJson::canonicalBytes(value);
}

QString PortableCodec::identity(const QByteArray& canonicalBytes) const
{
    return QString::fromLatin1(QCryptographicHash::hash(canonicalBytes, QCryptographicHash::Sha256).toHex());
}

QList<TierDecision> PortableCodec::classify(const ValidatedPackage& package) const
{
    QList<TierDecision> decisions;
    for (const auto& collection : PortableSpec::collections()) {
        const auto editable = collection == QStringLiteral("problems") || collection == QStringLiteral("reviews");
        for (const auto& record : package.records.value(collection)) {
            decisions.push_back(TierDecision{
                collection,
                record.stableId,
                editable ? PortableTier::Editable : PortableTier::StoredOnly,
                editable ? PortableTierReason::LiveDomainUi : PortableTierReason::BoundedArchiveNoDomainUi,
            });
        }
    }
    return decisions;
}

PortablePreview PortableCodec::preview(const QByteArray& bytes) const
{
    const auto package = validate(bytes);
    const auto decisions = classify(package);
    QMap<QString, int> bySource;
    for (const auto& collection : PortableSpec::collections()) {
        for (const auto& record : package.records.value(collection)) {
            bySource[record.sourceScopeId] += 1;
        }
    }
    QList<SourceBreakdown> sources;
    for (auto it = bySource.cbegin(); it != bySource.cend(); ++it) {
        sources.push_back(SourceBreakdown{it.key(), it.value()});
    }
    int editable = 0;
    int storedOnly = 0;
    int rejected = 0;
    QList<TierDecision> skipped;
    for (const auto& decision : decisions) {
        if (decision.tier == PortableTier::Editable) ++editable;
        else if (decision.tier == PortableTier::StoredOnly) ++storedOnly;
        else { ++rejected; skipped.push_back(decision); }
    }
    PortablePreview result;
    result.scopeId = package.scopeId;
    result.sourcePlatform = package.sourcePlatform;
    result.generator = package.generator;
    result.createdAtEpochMs = package.createdAtEpochMs;
    result.counts = package.counts;
    result.sources = std::move(sources);
    result.decisions = std::move(decisions);
    result.conflicts = {};
    result.skipped = std::move(skipped);
    result.editableCount = editable;
    result.storedOnlyCount = storedOnly;
    result.rejectedCount = rejected;
    result.acceptAll = rejected == 0;
    result.canonicalBytes = package.canonicalBytes;
    result.identity = package.identity;
    return result;
}

} // namespace OjNexus::Portable
