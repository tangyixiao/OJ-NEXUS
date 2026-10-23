#include "ojnexus/cli/runner.h"

#include <algorithm>

#include <QCoreApplication>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTextStream>

#include "ojnexus/core/paths.h"
#include "ojnexus/core/sync_engine.h"

namespace OjNexus::Cli {
namespace {

QString humanJsonValue(const QJsonValue& value) {
    if (value.isArray()) return QString::fromUtf8(QJsonDocument(value.toArray()).toJson(QJsonDocument::Compact)).trimmed();
    if (value.isObject()) return QString::fromUtf8(QJsonDocument(value.toObject()).toJson(QJsonDocument::Compact)).trimmed();
    if (value.isBool()) return value.toBool() ? QStringLiteral("true") : QStringLiteral("false");
    if (value.isDouble()) return QString::number(value.toDouble(), 'g', 15);
    if (value.isNull() || value.isUndefined()) return QStringLiteral("null");
    return value.toString();
}

void emitJson(const QJsonObject& object, bool json, QTextStream& output) {
    if (json) {
        output << QJsonDocument(object).toJson(QJsonDocument::Compact) << Qt::endl;
        return;
    }
    for (auto it = object.constBegin(); it != object.constEnd(); ++it) output << it.key() << "=" << humanJsonValue(it.value()) << Qt::endl;
}

QJsonObject moduleJson(const SyncModuleOutcome& module) {
    return {{QStringLiteral("stage"), module.stage}, {QStringLiteral("status"), syncStatusToString(module.status)},
            {QStringLiteral("attempted"), module.attemptedCount}, {QStringLiteral("imported"), module.importedCount},
            {QStringLiteral("updated"), module.updatedCount}, {QStringLiteral("failureType"), module.failureType}};
}

QJsonObject submissionJson(const StoredSubmission& submission) {
    return {{QStringLiteral("id"), submission.externalId},
            {QStringLiteral("problemId"), submission.problemId},
            {QStringLiteral("problemName"), submission.problemName},
            {QStringLiteral("verdict"), submission.verdict},
            {QStringLiteral("language"), submission.language},
            {QStringLiteral("submittedAt"), submission.submittedAt.isValid()
                ? submission.submittedAt.toString(Qt::ISODateWithMs) : QString()}};
}

QJsonObject operationJson(const SyncOperation& operation, std::optional<SyncError> error = std::nullopt) {
    QJsonArray modules;
    for (const auto& module : operation.modules) modules.push_back(moduleJson(module));
    QJsonObject result{{QStringLiteral("id"), operation.id},
                       {QStringLiteral("judge"), judgeIdToString(operation.account.judge)},
                       {QStringLiteral("handle"), operation.account.handle},
                       {QStringLiteral("dataGeneration"), operation.dataGeneration},
                       {QStringLiteral("status"), syncStatusToString(operation.status)},
                       {QStringLiteral("startedAt"), operation.startedAt.toString(Qt::ISODateWithMs)},
                       {QStringLiteral("finishedAt"), operation.finishedAt ? operation.finishedAt->toString(Qt::ISODateWithMs) : QString()},
                       {QStringLiteral("modules"), modules}};
    if (error) result.insert(QStringLiteral("error"), syncErrorToString(*error));
    return result;
}

QList<JudgeAccount> selectedAccounts(SqliteStore& store, JudgeId::Value judge) {
    QList<JudgeAccount> result;
    for (const auto& account : store.accounts()) if (account.enabled && (judge == JudgeId::All || account.judge == judge)) result.push_back(account);
    return result;
}

} // namespace

int syncExitCode(const QList<SyncReport>& reports) {
    if (reports.isEmpty()) return UsageError;

    const auto all = [&reports](const auto& predicate) {
        return std::all_of(reports.cbegin(), reports.cend(), predicate);
    };
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Success;
        })) {
        return Ok;
    }
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Cancelled ||
                   report.error == SyncError::Cancelled;
        })) {
        return Cancelled;
    }
    if (all([](const SyncReport& report) {
            return report.error == SyncError::Authentication;
        })) {
        return AuthenticationError;
    }
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Offline ||
                   report.error == SyncError::Offline || report.error == SyncError::Network;
        })) {
        return NetworkError;
    }
    if (all([](const SyncReport& report) {
            return report.error == SyncError::Storage;
        })) {
        return StorageError;
    }
    return PartialFailure;
}

QString syncAggregateStatus(const QList<SyncReport>& reports) {
    if (reports.isEmpty()) return QStringLiteral("ERROR");

    const auto all = [&reports](const auto& predicate) {
        return std::all_of(reports.cbegin(), reports.cend(), predicate);
    };
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Success;
        })) {
        return QStringLiteral("SUCCESS");
    }
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Cancelled ||
                   report.error == SyncError::Cancelled;
        })) {
        return QStringLiteral("CANCELLED");
    }
    if (all([](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Offline ||
                   report.error == SyncError::Offline;
        })) {
        return QStringLiteral("OFFLINE");
    }
    if (all([](const SyncReport& report) {
            return report.error == SyncError::Authentication;
        })) {
        return QStringLiteral("AUTHENTICATION");
    }
    if (std::any_of(reports.cbegin(), reports.cend(), [](const SyncReport& report) {
            return report.operation.status == SyncOperationStatus::Success;
        })) {
        return QStringLiteral("PARTIAL");
    }
    return QStringLiteral("ERROR");
}

Result<QList<JudgeAccount>> accountsForSync(const QList<JudgeAccount>& stored,
                                            JudgeId::Value judge,
                                            const QString& handle,
                                            bool force) {
    if (!handle.trimmed().isEmpty()) {
        if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
        const auto account = JudgeAccount::create(judge, handle);
        if (!account.has_value()) return account.error();
        return QList<JudgeAccount>{account.value()};
    }

    QList<JudgeAccount> result;
    for (const auto& account : stored) {
        if ((force || account.enabled) && (judge == JudgeId::All || account.judge == judge)) result.push_back(account);
    }
    return result;
}

Result<QList<JudgeAccount>> accountsForRemoval(const QList<JudgeAccount>& stored,
                                               JudgeId::Value judge,
                                               const QString& handle) {
    if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
    QList<JudgeAccount> result;
    for (const auto& account : stored) {
        if (account.judge == judge && (handle.trimmed().isEmpty() || judgeHandlesMatch(judge, account.handle, handle))) {
            result.push_back(account);
        }
    }
    return result;
}

Result<QList<JudgeAccount>> accountsForAuth(const QList<JudgeAccount>& stored,
                                            JudgeId::Value judge,
                                            const QString& handle) {
    if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
    const auto requestedHandle = handle.trimmed();
    QList<JudgeAccount> result;
    for (const auto& account : stored) {
        if (account.judge == judge && (requestedHandle.isEmpty() || judgeHandlesMatch(judge, account.handle, requestedHandle))) {
            result.push_back(account);
        }
    }
    return result;
}

int execute(const Command& command, QTextStream& input, QTextStream& output, QTextStream& error,
            std::stop_token cancellation) {
    const auto paths = AppPaths::fromEnvironment();
    if (!paths.ensureDirectories().has_value()) { error << "storage unavailable" << Qt::endl; return StorageError; }
    SqliteStore store(paths.databaseFile);
    if (!store.initialize().has_value()) { error << "storage unavailable" << Qt::endl; return StorageError; }
    if (command.kind == CommandKind::ConfigShow) {
        QJsonArray adapters;
        for (const auto judge : {JudgeId::Codeforces, JudgeId::AtCoder, JudgeId::Luogu}) {
            const auto* adapter = SyncEngine::adapterFor(judge);
            adapters.push_back(QJsonObject{{QStringLiteral("judge"), judgeIdToString(judge)},
                                           {QStringLiteral("reliability"), adapter->reliability()},
                                           {QStringLiteral("capabilities"), QJsonArray::fromStringList(adapter->capabilities())}});
        }
        emitJson({{QStringLiteral("dataDirectory"), paths.dataDirectory}, {QStringLiteral("configFile"), paths.configFile},
                  {QStringLiteral("databaseFile"), paths.databaseFile}, {QStringLiteral("logFile"), paths.logFile},
                  {QStringLiteral("adapters"), adapters}}, command.json, output);
        return Ok;
    }

    if (command.kind == CommandKind::Status) {
        QJsonArray accounts;
        for (const auto& account : store.accounts()) {
            QJsonObject accountJson{{QStringLiteral("judge"), judgeIdToString(account.judge)},
                                    {QStringLiteral("handle"), account.handle},
                                    {QStringLiteral("enabled"), account.enabled},
                                    {QStringLiteral("lastSyncStatus"), QStringLiteral("NEVER")},
                                    {QStringLiteral("lastSyncStartedAt"), QString()},
                                    {QStringLiteral("lastSyncFinishedAt"), QString()},
                                    {QStringLiteral("lastSyncError"), QString()},
                                    {QStringLiteral("lastProfileUpdatedAt"), QString()},
                                    {QStringLiteral("ratingChangeCount"), store.ratingChangeCount(account.judge)},
                                    {QStringLiteral("submissionCount"), store.submissionCount(account.judge)}};
            const auto recent = store.recentOperations(account.judge, 1000);
            const auto last = std::find_if(recent.cbegin(), recent.cend(), [&account](const auto& operation) {
                return judgeHandlesMatch(account.judge, operation.account.handle, account.handle);
            });
            if (last != recent.cend()) {
                accountJson.insert(QStringLiteral("lastSyncStatus"), syncStatusToString(last->status));
                accountJson.insert(QStringLiteral("lastSyncStartedAt"), last->startedAt.toString(Qt::ISODateWithMs));
                accountJson.insert(QStringLiteral("lastSyncFinishedAt"),
                                   last->finishedAt ? last->finishedAt->toString(Qt::ISODateWithMs) : QString());
                if (last->error) accountJson.insert(QStringLiteral("lastSyncError"), syncErrorToString(*last->error));
            }
            const auto profile = store.latestProfile(account.judge);
            if (profile && judgeHandlesMatch(account.judge, profile->handle, account.handle)) {
                accountJson.insert(QStringLiteral("lastProfileUpdatedAt"), profile->updatedAt.toString(Qt::ISODateWithMs));
            }
            QJsonArray recentSubmissions;
            for (const auto& submission : store.recentSubmissions(account.judge, 20)) {
                recentSubmissions.push_back(submissionJson(submission));
            }
            accountJson.insert(QStringLiteral("recentSubmissions"), recentSubmissions);
            accounts.push_back(accountJson);
        }
        const QJsonObject status{{QStringLiteral("database"), QFileInfo(paths.databaseFile).exists()},
                                 {QStringLiteral("schemaVersion"), store.schemaVersion()},
                                 {QStringLiteral("accounts"), accounts}};
        if (command.json) {
            emitJson(status, true, output);
        } else {
            output << "DATABASE: " << (status.value(QStringLiteral("database")).toBool() ? "READY" : "MISSING") << Qt::endl;
            output << "SCHEMA VERSION: " << status.value(QStringLiteral("schemaVersion")).toInt() << Qt::endl;
            output << "ACCOUNTS: " << accounts.size() << Qt::endl;
            for (const auto& accountValue : accounts) {
                const auto account = accountValue.toObject();
                output << account.value(QStringLiteral("judge")).toString().toUpper() << " / "
                       << account.value(QStringLiteral("handle")).toString() << " / "
                       << (account.value(QStringLiteral("enabled")).toBool() ? "ENABLED" : "DISABLED")
                       << " / LAST SYNC " << account.value(QStringLiteral("lastSyncStatus")).toString();
                const auto finished = account.value(QStringLiteral("lastSyncFinishedAt")).toString();
                if (!finished.isEmpty()) output << " / FINISHED " << finished;
                const auto profileUpdated = account.value(QStringLiteral("lastProfileUpdatedAt")).toString();
                if (!profileUpdated.isEmpty()) output << " / PROFILE UPDATED " << profileUpdated;
                output << " / RATING CHANGES " << account.value(QStringLiteral("ratingChangeCount")).toInt();
                output << " / SUBMISSIONS " << account.value(QStringLiteral("submissionCount")).toInt();
                const auto lastError = account.value(QStringLiteral("lastSyncError")).toString();
                if (!lastError.isEmpty()) output << " / ERROR " << lastError;
                output << Qt::endl;
            }
        }
        return Ok;
    }

    if (command.kind == CommandKind::History) {
        QJsonArray operations;
        const auto judge = (command.judge == JudgeId::All || command.judge == JudgeId::Unknown)
            ? std::optional<JudgeId::Value>() : std::optional<JudgeId::Value>(command.judge);
        for (const auto& operation : store.recentOperations(judge, command.limit)) {
            operations.push_back(operationJson(operation, operation.error));
        }
        emitJson({{QStringLiteral("operations"), operations}}, command.json, output);
        return Ok;
    }

    if (command.kind == CommandKind::AuthLogin) {
        if (command.handle.isEmpty()) { error << "--handle is required" << Qt::endl; return UsageError; }
        const auto account = JudgeAccount::create(command.judge, command.handle);
        if (!account.has_value()) {
            error << "account configuration failed" << Qt::endl;
            return StorageError;
        }
        const auto configured = store.configureAccount(account.value());
        if (!configured.has_value()) {
            error << "account configuration failed" << Qt::endl;
            return StorageError;
        }
        const auto configuredAccount = accountsForAuth(store.accounts(), command.judge, command.handle);
        if (!configuredAccount.has_value() || configuredAccount->isEmpty()) {
            error << "account configuration failed" << Qt::endl;
            return StorageError;
        }
        const auto storedHandle = configuredAccount->first().handle;
        emitJson({{QStringLiteral("judge"), judgeIdToString(command.judge)},
                  {QStringLiteral("handle"), storedHandle},
                  {QStringLiteral("accountConfigured"), true}}, command.json, output);
        return Ok;
    }

    if (command.kind == CommandKind::AuthStatus || command.kind == CommandKind::AuthLogout || command.kind == CommandKind::AuthRemove) {
        QList<JudgeAccount> accounts;
        if (command.kind == CommandKind::AuthRemove) {
            const auto selected = accountsForRemoval(store.accounts(), command.judge, command.handle);
            if (!selected.has_value()) { error << "account selection failed" << Qt::endl; return UsageError; }
            accounts = selected.value();
        } else {
            const auto selected = accountsForAuth(store.accounts(), command.judge, command.handle);
            if (!selected.has_value()) { error << "account selection failed" << Qt::endl; return UsageError; }
            accounts = selected.value();
        }
        if (accounts.isEmpty()) { error << "account is not configured" << Qt::endl; return UsageError; }
        const auto handle = accounts.first().handle;
        if (command.kind == CommandKind::AuthRemove) {
            if (!store.removeAccount(command.judge).has_value()) { error << "account remove failed" << Qt::endl; return StorageError; }
            emitJson({{QStringLiteral("judge"), judgeIdToString(command.judge)},
                      {QStringLiteral("handle"), handle},
                      {QStringLiteral("accountRemoved"), true}}, command.json, output);
            return Ok;
        }
        if (command.kind == CommandKind::AuthLogout) {
            if (!store.setAccountEnabled(command.judge, false).has_value()) return StorageError;
            emitJson({{QStringLiteral("judge"), judgeIdToString(command.judge)}, {QStringLiteral("handle"), handle}}, command.json, output);
            return Ok;
        }
        emitJson({{QStringLiteral("judge"), judgeIdToString(command.judge)},
                  {QStringLiteral("handle"), handle},
                  {QStringLiteral("enabled"), accounts.first().enabled}}, command.json, output);
        return Ok;
    }

    if (command.kind == CommandKind::Sync) {
        const auto accounts = accountsForSync(store.accounts(), command.judge, command.handle, command.force);
        if (!accounts.has_value()) {
            if (command.json) {
                emitJson({{QStringLiteral("reports"), QJsonArray{}},
                          {QStringLiteral("status"), QStringLiteral("ERROR")},
                          {QStringLiteral("error"), syncErrorToString(accounts.error())}},
                         true, output);
            } else {
                error << "sync account configuration invalid" << Qt::endl;
            }
            return UsageError;
        }
        if (accounts->isEmpty()) {
            if (command.json) {
                emitJson({{QStringLiteral("reports"), QJsonArray{}},
                          {QStringLiteral("status"), QStringLiteral("ERROR")},
                          {QStringLiteral("error"), QStringLiteral("NO_ENABLED_ACCOUNT")}},
                         true, output);
            } else {
                error << "no enabled account configured" << Qt::endl;
            }
            return UsageError;
        }
        QtHttpTransport transport;
        SyncEngine engine(store, transport);
        QJsonArray reports;
        QList<SyncReport> syncReports;
        for (const auto& account : accounts.value()) {
            const auto report = engine.sync(account, QStringLiteral("linux-0.1"), {}, cancellation);
            reports.push_back(operationJson(report.operation, report.error));
            syncReports.push_back(report);
        }
        emitJson({{QStringLiteral("reports"), reports},
                  {QStringLiteral("status"), syncAggregateStatus(syncReports)}},
                 command.json, output);
        return syncExitCode(syncReports);
    }

    error << "unsupported command" << Qt::endl;
    return UsageError;
}

} // namespace OjNexus::Cli
