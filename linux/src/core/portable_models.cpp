#include "ojnexus/core/portable_models.h"

namespace OjNexus::Portable {

namespace {

QSet<QString> toSet(std::initializer_list<const char*> values)
{
    QSet<QString> set;
    for (const auto* value : values) set.insert(QString::fromLatin1(value));
    return set;
}

const QSet<QString> kCategories = toSet({
    "INVALID_INPUT",
    "UNSUPPORTED_VERSION",
    "UNKNOWN_FIELD",
    "DUPLICATE_ID",
    "MISSING_REFERENCE",
    "SIZE_LIMIT",
    "PATH_UNSAFE",
    "COUNTS_MISMATCH",
});

const QSet<QString> kCredentialTokens = toSet({
    "token", "password", "secret", "cookie", "credential", "apikey",
    "authorization", "sessionid", "privatekey", "accesskey",
});

const QSet<QString> kJudges = toSet({"codeforces", "atcoder", "luogu"});
const QSet<QString> kOrigins = toSet({"scope", "unassigned"});
const QSet<QString> kPlatforms = toSet({"android", "windows", "linux", "macos", "ios"});
const QSet<QString> kVerdicts = toSet({"AC", "WA", "TLE", "MLE", "RE", "CE", "PE", "OTHER"});
const QSet<QString> kLastResults = toSet({"PASS", "HARD", "FAIL", "SKIP"});
const QSet<QString> kSessionTypes = toSet({"PRACTICE", "FOCUS", "UPSOLVE", "REVIEW"});
const QSet<QString> kSessionStates = toSet({"PLANNED", "RUNNING", "PAUSED", "FINISHED", "CANCELLED"});

const QSet<QString> kRootKeys = toSet({
    "formatVersion", "scopeId", "sourcePlatform", "generator",
    "createdAtEpochMs", "counts", "problems", "attempts", "notes",
    "reviews", "sessions",
});

const QSet<QString> kEntityBaseKeys = toSet({"stableId", "sourceScopeId", "origin", "extra"});

QSet<QString> entityKeysFor(std::initializer_list<const char*> extra)
{
    auto keys = kEntityBaseKeys;
    for (const auto* value : extra) keys.insert(QString::fromLatin1(value));
    return keys;
}

const QMap<QString, QSet<QString>>& entityKeysMap()
{
    static const QMap<QString, QSet<QString>> map{
        {QStringLiteral("problems"), entityKeysFor({
            "judge", "externalId", "title", "solved", "dayKey", "sourceUrl",
            "firstSolvedAtEpochMs", "difficulty", "tags",
        })},
        {QStringLiteral("attempts"), entityKeysFor({
            "problemId", "timestampEpochMs", "dayKey", "verdict", "rawVerdict",
            "language", "durationMin", "externalSubmissionId", "note",
        })},
        {QStringLiteral("notes"), entityKeysFor({
            "problemId", "keyInsight", "implementationNotes", "complexity",
            "general", "updatedEpochMs", "dayKey",
        })},
        {QStringLiteral("reviews"), entityKeysFor({
            "problemId", "stage", "dueEpochMs", "dueDayKey", "lastResult",
            "lastReviewedEpochMs", "createdEpochMs", "dayKey",
        })},
        {QStringLiteral("sessions"), entityKeysFor({
            "type", "state", "startedEpochMs", "finishedEpochMs",
            "totalPausedMs", "targetDurationMin", "problemIds", "dayKey",
        })},
    };
    return map;
}

const QMap<QString, QSet<QString>>& entityRequiredMap()
{
    static const QMap<QString, QSet<QString>> map{
        {QStringLiteral("problems"), toSet({
            "stableId", "sourceScopeId", "origin", "judge", "externalId",
            "title", "solved", "dayKey",
        })},
        {QStringLiteral("attempts"), toSet({
            "stableId", "sourceScopeId", "origin", "problemId",
            "timestampEpochMs", "verdict", "dayKey",
        })},
        {QStringLiteral("notes"), toSet({
            "stableId", "sourceScopeId", "origin", "problemId", "keyInsight",
            "implementationNotes", "complexity", "general", "updatedEpochMs",
            "dayKey",
        })},
        {QStringLiteral("reviews"), toSet({
            "stableId", "sourceScopeId", "origin", "problemId", "stage",
            "dueEpochMs", "dueDayKey", "createdEpochMs", "dayKey",
        })},
        {QStringLiteral("sessions"), toSet({
            "stableId", "sourceScopeId", "origin", "type", "state",
            "startedEpochMs", "totalPausedMs", "dayKey",
        })},
    };
    return map;
}

} // namespace

const QStringList& PortableSpec::collections()
{
    static const QStringList list{
        QStringLiteral("problems"), QStringLiteral("attempts"), QStringLiteral("notes"),
        QStringLiteral("reviews"), QStringLiteral("sessions"),
    };
    return list;
}

const QSet<QString>& PortableSpec::categories() { return kCategories; }
const QSet<QString>& PortableSpec::credentialTokens() { return kCredentialTokens; }
const QSet<QString>& PortableSpec::judges() { return kJudges; }
const QSet<QString>& PortableSpec::origins() { return kOrigins; }
const QSet<QString>& PortableSpec::platforms() { return kPlatforms; }
const QSet<QString>& PortableSpec::verdicts() { return kVerdicts; }
const QSet<QString>& PortableSpec::lastResults() { return kLastResults; }
const QSet<QString>& PortableSpec::sessionTypes() { return kSessionTypes; }
const QSet<QString>& PortableSpec::sessionStates() { return kSessionStates; }
const QSet<QString>& PortableSpec::rootKeys() { return kRootKeys; }

const QSet<QString>& PortableSpec::entityKeys(const QString& collection)
{
    const auto& map = entityKeysMap();
    return map.value(collection, kEntityBaseKeys);
}

const QSet<QString>& PortableSpec::entityRequired(const QString& collection)
{
    static const QSet<QString> empty;
    const auto& map = entityRequiredMap();
    const auto it = map.constFind(collection);
    return it == map.cend() ? empty : it.value();
}

const QString& PortableSpec::stableIdPattern()
{
    static const QString pattern = QStringLiteral("^[A-Za-z0-9._:-]{1,128}$");
    return pattern;
}

const QString& PortableSpec::externalIdPattern()
{
    static const QString pattern = QStringLiteral("^[A-Za-z0-9._:-]{1,64}$");
    return pattern;
}

const QString& PortableSpec::dayKeyPattern()
{
    static const QString pattern = QStringLiteral("^\\d{4}-\\d{2}-\\d{2}$");
    return pattern;
}

const QString& PortableSpec::sourceUrlPrefix()
{
    static const QString pattern = QStringLiteral("https?://.*");
    return pattern;
}

QString portableRejectCategoryWire(PortableRejectCategory category)
{
    switch (category) {
    case PortableRejectCategory::InvalidInput: return QStringLiteral("INVALID_INPUT");
    case PortableRejectCategory::UnsupportedVersion: return QStringLiteral("UNSUPPORTED_VERSION");
    case PortableRejectCategory::UnknownField: return QStringLiteral("UNKNOWN_FIELD");
    case PortableRejectCategory::DuplicateId: return QStringLiteral("DUPLICATE_ID");
    case PortableRejectCategory::MissingReference: return QStringLiteral("MISSING_REFERENCE");
    case PortableRejectCategory::SizeLimit: return QStringLiteral("SIZE_LIMIT");
    case PortableRejectCategory::PathUnsafe: return QStringLiteral("PATH_UNSAFE");
    case PortableRejectCategory::CountsMismatch: return QStringLiteral("COUNTS_MISMATCH");
    }
    return QStringLiteral("INVALID_INPUT");
}

QString portableTierReasonWire(PortableTierReason reason)
{
    switch (reason) {
    case PortableTierReason::LiveDomainUi: return QStringLiteral("live_domain_ui");
    case PortableTierReason::BoundedArchiveNoDomainUi: return QStringLiteral("bounded_archive_no_domain_ui");
    case PortableTierReason::UnknownField: return QStringLiteral("unknown_field");
    case PortableTierReason::IncompatibleEnum: return QStringLiteral("incompatible_enum");
    case PortableTierReason::IncompatibleVersion: return QStringLiteral("incompatible_version");
    case PortableTierReason::IncompatibleReference: return QStringLiteral("incompatible_reference");
    }
    return QStringLiteral("bounded_archive_no_domain_ui");
}

} // namespace OjNexus::Portable
