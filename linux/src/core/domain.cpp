#include "ojnexus/core/domain.h"

#include <utility>

namespace OjNexus {

namespace {
bool isSafeCodeforcesHandle(const QString& handle) {
    for (const auto character : handle) {
        const auto code = character.unicode();
        const bool alphanumeric = (code >= '0' && code <= '9') ||
                                  (code >= 'A' && code <= 'Z') ||
                                  (code >= 'a' && code <= 'z');
        if (!alphanumeric && code != '-' && code != '.' && code != '_') return false;
    }
    return !handle.isEmpty();
}

bool isSafeAtCoderHandle(const QString& handle) {
    for (const auto character : handle) {
        const auto code = character.unicode();
        const bool alphanumeric = (code >= '0' && code <= '9') ||
                                  (code >= 'A' && code <= 'Z') ||
                                  (code >= 'a' && code <= 'z');
        if (!alphanumeric && code != '-' && code != '_') return false;
    }
    return !handle.isEmpty();
}

bool isSafeLuoguUid(QString handle) {
    handle = handle.trimmed();
    if (handle.startsWith(QStringLiteral("uid:"), Qt::CaseInsensitive)) handle.remove(0, 4);
    if (handle.isEmpty()) return false;
    for (const auto character : handle) {
        if (character < QChar('0') || character > QChar('9')) return false;
    }
    return true;
}
} // namespace

QString judgeIdToString(JudgeId::Value judge) {
    switch (judge) {
    case JudgeId::All: return QStringLiteral("all");
    case JudgeId::Codeforces: return QStringLiteral("codeforces");
    case JudgeId::AtCoder: return QStringLiteral("atcoder");
    case JudgeId::Luogu: return QStringLiteral("luogu");
    case JudgeId::Unknown: return QStringLiteral("unknown");
    }
    return QStringLiteral("unknown");
}

bool judgeHandlesMatch(JudgeId::Value judge, const QString& lhs, const QString& rhs) {
    const auto normalizedLuoguUid = [](QString handle) {
        handle = handle.trimmed();
        if (handle.startsWith(QStringLiteral("uid:"), Qt::CaseInsensitive)) handle.remove(0, 4);
        return handle;
    };
    switch (judge) {
    case JudgeId::Codeforces:
        return lhs.trimmed().compare(rhs.trimmed(), Qt::CaseInsensitive) == 0;
    case JudgeId::AtCoder:
        return lhs.trimmed() == rhs.trimmed();
    case JudgeId::Luogu:
        return normalizedLuoguUid(lhs) == normalizedLuoguUid(rhs);
    case JudgeId::All:
    case JudgeId::Unknown:
        return false;
    }
    return false;
}

JudgeId::Value JudgeId::fromString(const QString& value) {
    const auto normalized = value.trimmed().toLower();
    if (normalized == QStringLiteral("all")) return JudgeId::All;
    if (normalized == QStringLiteral("codeforces") || normalized == QStringLiteral("cf")) return JudgeId::Codeforces;
    if (normalized == QStringLiteral("atcoder") || normalized == QStringLiteral("ac")) return JudgeId::AtCoder;
    if (normalized == QStringLiteral("luogu") || normalized == QStringLiteral("lg")) return JudgeId::Luogu;
    return JudgeId::Unknown;
}

Result<JudgeAccount> JudgeAccount::create(JudgeId::Value judge, QString rawHandle, bool enabled) {
    const auto normalized = rawHandle.trimmed();
    if (judge == JudgeId::Unknown || judge == JudgeId::All || normalized.isEmpty() ||
        (judge == JudgeId::Codeforces && !isSafeCodeforcesHandle(normalized)) ||
        (judge == JudgeId::AtCoder && !isSafeAtCoderHandle(normalized)) ||
        (judge == JudgeId::Luogu && !isSafeLuoguUid(normalized))) {
        return SyncError::InvalidConfiguration;
    }
    return JudgeAccount{judge, normalized, enabled};
}

QString syncStatusToString(SyncOperationStatus status) {
    switch (status) {
    case SyncOperationStatus::Running: return QStringLiteral("RUNNING");
    case SyncOperationStatus::Success: return QStringLiteral("SUCCESS");
    case SyncOperationStatus::Partial: return QStringLiteral("PARTIAL");
    case SyncOperationStatus::Error: return QStringLiteral("ERROR");
    case SyncOperationStatus::Offline: return QStringLiteral("OFFLINE");
    case SyncOperationStatus::Cancelled: return QStringLiteral("CANCELLED");
    case SyncOperationStatus::Unavailable: return QStringLiteral("UNAVAILABLE");
    }
    return QStringLiteral("ERROR");
}

QString syncErrorToString(SyncError error) {
    switch (error) {
    case SyncError::InvalidConfiguration: return QStringLiteral("INVALID_CONFIGURATION");
    case SyncError::UnsupportedJudge: return QStringLiteral("UNSUPPORTED_JUDGE");
    case SyncError::Network: return QStringLiteral("NETWORK");
    case SyncError::Api: return QStringLiteral("API");
    case SyncError::Parse: return QStringLiteral("PARSE");
    case SyncError::Authentication: return QStringLiteral("AUTHENTICATION");
    case SyncError::Offline: return QStringLiteral("OFFLINE");
    case SyncError::Cancelled: return QStringLiteral("CANCELLED");
    case SyncError::Storage: return QStringLiteral("STORAGE");
    }
    return QStringLiteral("UNKNOWN");
}

std::optional<SyncError> syncErrorFromString(const QString& value) {
    if (value.isEmpty()) return std::nullopt;
    if (value == QStringLiteral("INVALID_CONFIGURATION")) return SyncError::InvalidConfiguration;
    if (value == QStringLiteral("UNSUPPORTED_JUDGE")) return SyncError::UnsupportedJudge;
    if (value == QStringLiteral("NETWORK")) return SyncError::Network;
    if (value == QStringLiteral("API")) return SyncError::Api;
    if (value == QStringLiteral("PARSE")) return SyncError::Parse;
    if (value == QStringLiteral("AUTHENTICATION")) return SyncError::Authentication;
    if (value == QStringLiteral("OFFLINE")) return SyncError::Offline;
    if (value == QStringLiteral("CANCELLED")) return SyncError::Cancelled;
    if (value == QStringLiteral("STORAGE")) return SyncError::Storage;
    return SyncError::Api;
}

} // namespace OjNexus
