#include "ojnexus/core/sync_engine.h"

namespace OjNexus {

SyncEngine::SyncEngine(SqliteStore& store, IHttpTransport& transport, int requestIntervalMs)
    : store_(store), transport_(transport), requestIntervalMs_(qMax(0, requestIntervalMs)) {}

IJudgeAdapter* SyncEngine::adapterFor(JudgeId::Value judge) {
    static CodeforcesAdapter codeforces;
    static AtCoderAdapter atcoder;
    static LuoguAdapter luogu;
    switch (judge) {
    case JudgeId::Codeforces: return &codeforces;
    case JudgeId::AtCoder: return &atcoder;
    case JudgeId::Luogu: return &luogu;
    default: return nullptr;
    }
}

SyncReport SyncEngine::sync(const JudgeAccount& account, const QString& dataGeneration,
                            const QMap<QByteArray, QByteArray>& requestHeaders,
                            std::stop_token cancellation) {
    const auto validatedAccount = JudgeAccount::create(account.judge, account.handle, account.enabled);
    if (!validatedAccount.has_value()) {
        const auto now = QDateTime::currentDateTimeUtc();
        return {SyncOperation{0, account, dataGeneration, now, now, SyncOperationStatus::Error,
                              SyncError::InvalidConfiguration, {}}, SyncError::InvalidConfiguration};
    }
    const auto canonicalAccount = validatedAccount.value();
    auto* adapter = adapterFor(account.judge);
    if (!adapter) return {SyncOperation{0, account, dataGeneration, QDateTime::currentDateTimeUtc(), std::nullopt, SyncOperationStatus::Unavailable, SyncError::UnsupportedJudge, {}}, SyncError::UnsupportedJudge};
    const auto startedAt = QDateTime::currentDateTimeUtc();
    const auto opened = store_.openOperation(canonicalAccount, dataGeneration);
    if (!opened.has_value()) return {SyncOperation{0, account, dataGeneration, QDateTime::currentDateTimeUtc(), std::nullopt, SyncOperationStatus::Error, SyncError::Storage, {}}, SyncError::Storage};

    SyncContext context{store_, transport_, opened.value(), requestIntervalMs_, {}, requestHeaders};
    const auto modules = adapter->sync(canonicalAccount, context, cancellation);
    bool anySuccess = false;
    bool anyFailure = false;
    bool anyNonOfflineFailure = false;
    bool anyOffline = false;
    std::optional<SyncError> firstModuleError;
    bool cancelled = cancellation.stop_requested();
    for (const auto& module : modules) {
        anySuccess = anySuccess || module.status == SyncOperationStatus::Success;
        anyFailure = anyFailure || module.status != SyncOperationStatus::Success;
        anyOffline = anyOffline || module.status == SyncOperationStatus::Offline;
        anyNonOfflineFailure = anyNonOfflineFailure ||
            (module.status != SyncOperationStatus::Success && module.status != SyncOperationStatus::Offline);
        if (!firstModuleError && module.status != SyncOperationStatus::Success) {
            firstModuleError = syncErrorFromString(module.failureType);
        }
        cancelled = cancelled || module.status == SyncOperationStatus::Cancelled;
    }
    const auto finalStatus = cancelled ? SyncOperationStatus::Cancelled
        : (anySuccess && anyFailure ? SyncOperationStatus::Partial
           : (anySuccess ? SyncOperationStatus::Success
              : (anyOffline && !anyNonOfflineFailure ? SyncOperationStatus::Offline : SyncOperationStatus::Error)));
    std::optional<SyncError> finalError;
    if (finalStatus != SyncOperationStatus::Success) {
        if (context.modulePersistenceFailed) finalError = context.modulePersistenceError;
        else if (finalStatus == SyncOperationStatus::Cancelled) finalError = SyncError::Cancelled;
        else if (finalStatus == SyncOperationStatus::Offline) finalError = SyncError::Offline;
        else finalError = firstModuleError.value_or(SyncError::Api);
    }
    const auto finishedAt = QDateTime::currentDateTimeUtc();
    const auto closeResult = store_.closeOperation(opened.value(), finalStatus, finalError);
    SyncOperation operation{opened.value(), canonicalAccount, dataGeneration, startedAt, finishedAt, finalStatus, finalError, modules};
    if (!closeResult.has_value()) return {operation, SyncError::Storage};
    return {operation, finalError};
}

} // namespace OjNexus
