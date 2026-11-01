#include "ojnexus/core/store.h"
#include "storage_schema.h"

#include <algorithm>
#include <atomic>
#include <initializer_list>

#include <QSqlError>
#include <QSqlQuery>
#include <QJsonDocument>
#include <QFile>
#include <QFileInfo>
#include <QRegularExpression>
#include <cerrno>
#include <signal.h>
#include <unistd.h>

namespace OjNexus {
namespace {

std::atomic_uint connectionCounter{0};

struct ProcessOwner { qint64 pid = 0; QString bootId; qint64 startTicks = 0; };

QString bootId() {
    QFile file(QStringLiteral("/proc/sys/kernel/random/boot_id"));
    return file.open(QIODevice::ReadOnly) ? QString::fromLatin1(file.readAll()).trimmed() : QString{};
}

qint64 processStartTicks(qint64 pid) {
    QFile file(QStringLiteral("/proc/%1/stat").arg(pid));
    if (!file.open(QIODevice::ReadOnly)) return 0;
    const auto stat = QString::fromLatin1(file.readAll());
    const auto close = stat.lastIndexOf(')');
    if (close < 0) return 0;
    const auto fields = stat.mid(close + 1).simplified().split(' ');
    bool ok = false;
    const auto ticks = fields.value(19).toLongLong(&ok);
    return ok ? ticks : 0;
}

bool ownerDefinitelyStale(qint64 pid, const QString& ownerBoot, qint64 ownerTicks) {
    if (pid <= 0 || ownerBoot.isEmpty() || ownerTicks <= 0) return false;
    const auto currentBoot = bootId();
    if (currentBoot.isEmpty()) return false;
    if (ownerBoot != currentBoot) return true;
    if (::kill(static_cast<pid_t>(pid), 0) != 0) {
        if (errno == ESRCH) return true;
        return false;
    }
    const auto currentTicks = processStartTicks(pid);
    return currentTicks > 0 && currentTicks != ownerTicks;
}

SyncOperationStatus statusFromString(const QString& value) {
    if (value == QStringLiteral("SUCCESS")) return SyncOperationStatus::Success;
    if (value == QStringLiteral("PARTIAL")) return SyncOperationStatus::Partial;
    if (value == QStringLiteral("OFFLINE")) return SyncOperationStatus::Offline;
    if (value == QStringLiteral("CANCELLED")) return SyncOperationStatus::Cancelled;
    if (value == QStringLiteral("UNAVAILABLE")) return SyncOperationStatus::Unavailable;
    if (value == QStringLiteral("RUNNING")) return SyncOperationStatus::Running;
    return SyncOperationStatus::Error;
}

QDateTime fromMillis(const QVariant& value) {
    return QDateTime::fromMSecsSinceEpoch(value.toLongLong(), Qt::UTC);
}

QString jsonKey(const QJsonValue& value) {
    if (value.isString()) return value.toString();
    if (value.isDouble()) return QString::number(value.toVariant().toLongLong());
    return {};
}

QString firstPayloadString(const QJsonObject& payload, std::initializer_list<QString> keys) {
    for (const auto& key : keys) {
        const auto value = payload.value(key).toString().trimmed();
        if (!value.isEmpty()) return value;
    }
    return {};
}

QDateTime payloadDate(const QJsonObject& payload) {
    for (const auto& key : {QStringLiteral("creationTimeSeconds"), QStringLiteral("epoch_second"),
                            QStringLiteral("epochSecond"), QStringLiteral("timestamp"),
                            QStringLiteral("submitTime")}) {
        const auto value = payload.value(key);
        if (value.isDouble()) {
            const auto seconds = value.toVariant().toLongLong();
            if (seconds > 0) return QDateTime::fromSecsSinceEpoch(seconds, Qt::UTC);
        }
    }
    for (const auto& key : {QStringLiteral("submittedAt"), QStringLiteral("submitted_at")}) {
        const auto value = payload.value(key).toString().trimmed();
        if (value.isEmpty()) continue;
        auto parsed = QDateTime::fromString(value, Qt::ISODateWithMs);
        if (!parsed.isValid()) parsed = QDateTime::fromString(value, Qt::ISODate);
        if (parsed.isValid()) return parsed.toUTC();
    }
    return {};
}

StoredSubmission projectSubmission(const QString& externalId, const QJsonObject& payload,
                                   const QDateTime& updatedAt) {
    const auto problem = payload.value(QStringLiteral("problem")).toObject();
    QString problemId;
    const auto index = problem.value(QStringLiteral("index")).toString().trimmed();
    const auto contestId = problem.value(QStringLiteral("contestId"));
    if (!index.isEmpty() && contestId.isDouble() && contestId.toInteger() > 0) {
        problemId = QString::number(contestId.toInteger()) + index;
    }
    if (problemId.isEmpty()) {
        problemId = firstPayloadString(payload, {QStringLiteral("problem_id"), QStringLiteral("problemId"),
                                                 QStringLiteral("problemID"), QStringLiteral("pid"),
                                                 QStringLiteral("externalId")});
    }
    return StoredSubmission{
        externalId,
        problemId,
        firstPayloadString(problem, {QStringLiteral("name"), QStringLiteral("title")}).isEmpty()
            ? firstPayloadString(payload, {QStringLiteral("problemName"), QStringLiteral("problem_name")})
            : firstPayloadString(problem, {QStringLiteral("name"), QStringLiteral("title")}),
        firstPayloadString(payload, {QStringLiteral("verdict"), QStringLiteral("result")}).toUpper(),
        firstPayloadString(payload, {QStringLiteral("programmingLanguage"), QStringLiteral("language")}),
        payloadDate(payload),
        updatedAt,
    };
}

} // namespace

SqliteStore::SqliteStore(QString databasePath, int completedHistoryLimit)
    : databasePath_(std::move(databasePath)),
      connectionName_(QStringLiteral("ojnexus-%1").arg(++connectionCounter)),
      completedHistoryLimit_(qMax(1, completedHistoryLimit)),
      database_(QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName_)) {
    database_.setDatabaseName(databasePath_);
}

SqliteStore::~SqliteStore() {
    if (database_.isOpen()) database_.close();
    database_ = {};
    QSqlDatabase::removeDatabase(connectionName_);
}

Result<void> SqliteStore::exec(const QString& sql) const {
    QSqlQuery query(database_);
    if (!query.exec(sql)) return SyncError::Storage;
    return {};
}

QString SqliteStore::cacheHandle(JudgeId::Value judge, const QString& handle) const {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("SELECT handle FROM accounts WHERE judge=?"));
    query.addBindValue(judgeIdToString(judge));
    if (query.exec() && query.next()) {
        const auto configured = query.value(0).toString();
        if (judgeHandlesMatch(judge, configured, handle)) return configured;
    }
    return handle;
}

Result<void> SqliteStore::initialize() {
    schemaVersion_ = 0;
    if (database_.isOpen()) database_.close();

    const QFileInfo pathInfo(databasePath_);
    bool newlyReserved = false;
    if (!pathInfo.exists()) {
        QFile reservation(databasePath_);
        if (!reservation.open(QIODevice::WriteOnly | QIODevice::NewOnly)) return SyncError::Storage;
        reservation.close();
        newlyReserved = true;
    } else if (!pathInfo.isFile()) {
        return SyncError::Storage;
    }

    int validatedVersion = 0;
    if (!newlyReserved) {
        const auto probeName = QStringLiteral("ojnexus-probe-%1").arg(++connectionCounter);
        {
            auto probe = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), probeName);
            probe.setDatabaseName(databasePath_);
            probe.setConnectOptions(QStringLiteral("QSQLITE_OPEN_READONLY"));
            if (!probe.open()) {
                probe = {};
                QSqlDatabase::removeDatabase(probeName);
                return SyncError::Storage;
            }
            const auto checked = StorageSchema::validateExistingSchema(probe);
            if (checked.has_value()) validatedVersion = checked.value();
            probe.close();
            probe = {};
        }
        QSqlDatabase::removeDatabase(probeName);
        if (validatedVersion == 0) return SyncError::Storage;
    }

    database_.setConnectOptions(QString{});
    if (!database_.open()) return SyncError::Storage;
    if (!exec(QStringLiteral("PRAGMA foreign_keys = ON")).has_value() ||
        !exec(QStringLiteral("PRAGMA busy_timeout = 5000")).has_value()) return SyncError::Storage;

    if (newlyReserved) {
        if (!StorageSchema::createInitialSchema(database_).has_value()) return SyncError::Storage;
        validatedVersion = 2;
    } else {
        const auto revalidated = StorageSchema::validateExistingSchema(database_);
        if (!revalidated.has_value() || revalidated.value() != validatedVersion) return SyncError::Storage;
        if (validatedVersion == 1) {
            if (!database_.transaction()) return SyncError::Storage;
            const QStringList migrations{
                QStringLiteral("ALTER TABLE profiles RENAME TO profiles_v1"),
                QStringLiteral("CREATE TABLE profiles (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle))"),
                QStringLiteral("INSERT INTO profiles(scope, judge, handle, payload, updated_at) SELECT 'ACCOUNT', judge, handle, payload, updated_at FROM profiles_v1"),
                QStringLiteral("DROP TABLE profiles_v1"),
                QStringLiteral("ALTER TABLE rating_changes RENAME TO rating_changes_v1"),
                QStringLiteral("CREATE TABLE rating_changes (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
                QStringLiteral("INSERT INTO rating_changes(scope, judge, handle, external_id, payload, updated_at) SELECT 'UNASSIGNED', judge, '', external_id, payload, updated_at FROM rating_changes_v1"),
                QStringLiteral("DROP TABLE rating_changes_v1"),
                QStringLiteral("ALTER TABLE submissions RENAME TO submissions_v1"),
                QStringLiteral("CREATE TABLE submissions (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
                QStringLiteral("INSERT INTO submissions(scope, judge, handle, external_id, payload, updated_at) SELECT 'UNASSIGNED', judge, '', external_id, payload, updated_at FROM submissions_v1"),
                QStringLiteral("DROP TABLE submissions_v1"),
                QStringLiteral("ALTER TABLE problems RENAME TO problems_v1"),
                QStringLiteral("CREATE TABLE problems (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
                QStringLiteral("INSERT INTO problems(scope, judge, handle, external_id, payload, updated_at) SELECT 'GLOBAL', judge, '', external_id, payload, updated_at FROM problems_v1"),
                QStringLiteral("DROP TABLE problems_v1"),
                QStringLiteral("ALTER TABLE contests RENAME TO contests_v1"),
                QStringLiteral("CREATE TABLE contests (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
                QStringLiteral("INSERT INTO contests(scope, judge, handle, external_id, payload, updated_at) SELECT 'GLOBAL', judge, '', external_id, payload, updated_at FROM contests_v1"),
                QStringLiteral("DROP TABLE contests_v1"),
                QStringLiteral("ALTER TABLE sync_operations ADD COLUMN owner_pid INTEGER NOT NULL DEFAULT 0"),
                QStringLiteral("ALTER TABLE sync_operations ADD COLUMN owner_boot_id TEXT NOT NULL DEFAULT ''"),
                QStringLiteral("ALTER TABLE sync_operations ADD COLUMN owner_start_ticks INTEGER NOT NULL DEFAULT 0"),
                QStringLiteral("UPDATE schema_metadata SET version=2"),
            };
            bool failed = false;
            for (const auto& sql : migrations) if (!exec(sql).has_value()) { failed = true; break; }
            if (failed || !database_.commit()) { database_.rollback(); return SyncError::Storage; }
            validatedVersion = 2;
        }
        const auto finalCheck = StorageSchema::validateExistingSchema(database_);
        if (!finalCheck.has_value() || finalCheck.value() != 2) return SyncError::Storage;
    }
    if (newlyReserved) {
        const auto finalCheck = StorageSchema::validateExistingSchema(database_);
        if (!finalCheck.has_value() || finalCheck.value() != 2) return SyncError::Storage;
    }
    const auto recovery = recoverRunningOperations();
    if (!recovery.has_value()) return SyncError::Storage;
    schemaVersion_ = 2;
    return {};
}

Result<void> SqliteStore::recoverRunningOperations() const {
    QSqlQuery query(database_);
    if (!query.exec(QStringLiteral("SELECT id, owner_pid, owner_boot_id, owner_start_ticks FROM sync_operations WHERE status='RUNNING'"))) return SyncError::Storage;
    QList<qint64> stale;
    while (query.next()) {
        if (ownerDefinitelyStale(query.value(1).toLongLong(), query.value(2).toString(), query.value(3).toLongLong())) stale.push_back(query.value(0).toLongLong());
    }
    for (const auto id : stale) {
        QSqlQuery cancel(database_);
        cancel.prepare(QStringLiteral("UPDATE sync_operations SET status='CANCELLED', finished_at=?, error_type='CANCELLED' WHERE id=? AND status='RUNNING'"));
        cancel.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
        cancel.addBindValue(id);
        if (!cancel.exec()) return SyncError::Storage;
    }
    return {};
}

Result<void> SqliteStore::upsertAccount(const JudgeAccount& account) {
    const auto validatedAccount = JudgeAccount::create(account.judge, account.handle, account.enabled);
    if (!validatedAccount.has_value()) return validatedAccount.error();
    const auto canonicalAccount = validatedAccount.value();

    QSqlQuery query(database_);
    query.prepare(QStringLiteral("INSERT INTO accounts(judge, handle, enabled, updated_at) VALUES (?, ?, ?, ?) "
                                "ON CONFLICT(judge) DO UPDATE SET handle=excluded.handle, enabled=excluded.enabled, updated_at=excluded.updated_at"));
    query.addBindValue(judgeIdToString(canonicalAccount.judge));
    query.addBindValue(canonicalAccount.handle);
    query.addBindValue(canonicalAccount.enabled ? 1 : 0);
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::configureAccount(const JudgeAccount& account) {
    const auto validatedAccount = JudgeAccount::create(account.judge, account.handle, account.enabled);
    if (!validatedAccount.has_value()) return validatedAccount.error();
    const auto canonicalAccount = validatedAccount.value();

    if (!database_.transaction()) return SyncError::Storage;

    QSqlQuery existing(database_);
    existing.prepare(QStringLiteral("SELECT handle, enabled FROM accounts WHERE judge=?"));
    existing.addBindValue(judgeIdToString(canonicalAccount.judge));
    if (!existing.exec()) {
        database_.rollback();
        return SyncError::Storage;
    }
    const bool hasExistingAccount = existing.next();
    const QString existingHandle = hasExistingAccount ? existing.value(0).toString() : QString{};
    const bool existingEnabled = hasExistingAccount && existing.value(1).toInt() != 0;
    const bool sameIdentity = hasExistingAccount &&
        judgeHandlesMatch(canonicalAccount.judge, existingHandle, canonicalAccount.handle);
    QSqlQuery upsert(database_);
    upsert.prepare(QStringLiteral("INSERT INTO accounts(judge, handle, enabled, updated_at) VALUES (?, ?, ?, ?) "
                                 "ON CONFLICT(judge) DO UPDATE SET handle=excluded.handle, enabled=excluded.enabled, updated_at=excluded.updated_at"));
    upsert.addBindValue(judgeIdToString(canonicalAccount.judge));
    upsert.addBindValue(sameIdentity ? existingHandle : canonicalAccount.handle);
    upsert.addBindValue((sameIdentity ? existingEnabled : canonicalAccount.enabled) ? 1 : 0);
    upsert.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    if (!upsert.exec() || !database_.commit()) {
        database_.rollback();
        return SyncError::Storage;
    }
    return {};
}

Result<void> SqliteStore::setAccountEnabled(JudgeId::Value judge, bool enabled) {
    if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("UPDATE accounts SET enabled=?, updated_at=? WHERE judge=?"));
    query.addBindValue(enabled ? 1 : 0);
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    query.addBindValue(judgeIdToString(judge));
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::removeAccount(JudgeId::Value judge) {
    if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("DELETE FROM accounts WHERE judge=?"));
    query.addBindValue(judgeIdToString(judge));
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

QList<JudgeAccount> SqliteStore::accounts() const {
    QList<JudgeAccount> result;
    QSqlQuery query(database_);
    if (!query.exec(QStringLiteral("SELECT judge, handle, enabled FROM accounts ORDER BY judge"))) return result;
    while (query.next()) {
        const auto judge = JudgeId::fromString(query.value(0).toString());
        if (judge == JudgeId::Unknown || judge == JudgeId::All) continue;
        const auto account = JudgeAccount::create(judge, query.value(1).toString(), query.value(2).toInt() != 0);
        if (account.has_value()) result.push_back(account.value());
    }
    return result;
}

Result<void> SqliteStore::beginTransaction() {
    return database_.transaction() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::commitTransaction() {
    return database_.commit() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::rollbackTransaction() {
    return database_.rollback() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<qint64> SqliteStore::openOperation(const JudgeAccount& account, const QString& dataGeneration) {
    const auto validatedAccount = JudgeAccount::create(account.judge, account.handle, account.enabled);
    if (!validatedAccount.has_value()) return validatedAccount.error();
    const auto canonicalAccount = validatedAccount.value();

    QSqlQuery query(database_);
    const auto currentBoot = bootId();
    const auto currentTicks = processStartTicks(static_cast<qint64>(::getpid()));
    query.prepare(QStringLiteral("INSERT INTO sync_operations(judge, handle, data_generation, status, started_at, owner_pid, owner_boot_id, owner_start_ticks) VALUES (?, ?, ?, 'RUNNING', ?, ?, ?, ?)"));
    query.addBindValue(judgeIdToString(canonicalAccount.judge));
    query.addBindValue(canonicalAccount.handle);
    query.addBindValue(dataGeneration);
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    query.addBindValue(static_cast<qint64>(::getpid()));
    query.addBindValue(currentBoot);
    query.addBindValue(currentTicks);
    if (!query.exec()) return SyncError::Storage;
    return query.lastInsertId().toLongLong();
}

Result<void> SqliteStore::appendModule(qint64 operationId, const SyncModuleOutcome& outcome) {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("INSERT INTO sync_modules(operation_id, stage, status, attempted_count, imported_count, updated_count, failure_type) VALUES (?, ?, ?, ?, ?, ?, ?) "
                                "ON CONFLICT(operation_id, stage) DO UPDATE SET status=excluded.status, attempted_count=excluded.attempted_count, imported_count=excluded.imported_count, updated_count=excluded.updated_count, failure_type=excluded.failure_type"));
    query.addBindValue(operationId);
    query.addBindValue(outcome.stage);
    query.addBindValue(syncStatusToString(outcome.status));
    query.addBindValue(outcome.attemptedCount);
    query.addBindValue(outcome.importedCount);
    query.addBindValue(outcome.updatedCount);
    query.addBindValue(outcome.failureType.isNull() ? QStringLiteral("") : outcome.failureType);
    const bool ok = query.exec();
    return ok ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::closeOperation(qint64 operationId, SyncOperationStatus status, std::optional<SyncError> error) {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("UPDATE sync_operations SET status=?, finished_at=?, error_type=? WHERE id=?"));
    query.addBindValue(syncStatusToString(status));
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    query.addBindValue(error ? syncErrorToString(*error) : QStringLiteral(""));
    query.addBindValue(operationId);
    if (!query.exec()) return SyncError::Storage;

    QSqlQuery judgeQuery(database_);
    judgeQuery.prepare(QStringLiteral("SELECT judge FROM sync_operations WHERE id=?"));
    judgeQuery.addBindValue(operationId);
    if (!judgeQuery.exec() || !judgeQuery.next()) return SyncError::Storage;
    return pruneCompleted(JudgeId::fromString(judgeQuery.value(0).toString()));
}

Result<void> SqliteStore::pruneCompleted(const JudgeId::Value judge) const {
    const auto judgeName = judgeIdToString(judge);
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("DELETE FROM sync_operations WHERE judge=? AND status <> 'RUNNING' AND id NOT IN (SELECT id FROM sync_operations WHERE judge=? AND status <> 'RUNNING' ORDER BY started_at DESC LIMIT ?)"));
    query.addBindValue(judgeName);
    query.addBindValue(judgeName);
    query.addBindValue(completedHistoryLimit_);
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

std::optional<SyncOperation> SqliteStore::readOperation(QSqlQuery& query) const {
    const auto judge = JudgeId::fromString(query.value("judge").toString());
    const auto account = JudgeAccount::create(judge, query.value("handle").toString());
    if (!account.has_value()) return std::nullopt;
    SyncOperation operation{
        query.value("id").toLongLong(),
        account.value(),
        query.value("data_generation").toString(),
        fromMillis(query.value("started_at")),
        query.value("finished_at").isNull() ? std::nullopt : std::optional<QDateTime>(fromMillis(query.value("finished_at"))),
        statusFromString(query.value("status").toString()),
        syncErrorFromString(query.value("error_type").toString()),
        {},
    };
    QSqlQuery modules(database_);
    modules.prepare(QStringLiteral("SELECT stage, status, attempted_count, imported_count, updated_count, failure_type FROM sync_modules WHERE operation_id=? ORDER BY rowid"));
    modules.addBindValue(operation.id);
    if (modules.exec()) {
        while (modules.next()) {
            operation.modules.push_back({modules.value(0).toString(), statusFromString(modules.value(1).toString()), modules.value(2).toInt(), modules.value(3).toInt(), modules.value(4).toInt(), modules.value(5).toString()});
        }
    }
    return operation;
}

QList<SyncOperation> SqliteStore::recentOperations(std::optional<JudgeId::Value> judge, int limit) const {
    QList<SyncOperation> result;
    QSqlQuery query(database_);
    QString sql = QStringLiteral("SELECT id, judge, handle, data_generation, status, started_at, finished_at, error_type FROM sync_operations");
    if (judge) sql += QStringLiteral(" WHERE judge=?");
    sql += QStringLiteral(" ORDER BY started_at DESC LIMIT ?");
    query.prepare(sql);
    if (judge) query.addBindValue(judgeIdToString(*judge));
    query.addBindValue(qBound(1, limit, 1000));
    if (!query.exec()) return result;
    while (query.next()) {
        const auto operation = readOperation(query);
        if (operation.has_value()) result.push_back(operation.value());
    }
    return result;
}

Result<void> SqliteStore::upsertPayload(const QString& table, JudgeId::Value judge, const QString& scope, const QString& handle, const QString& key, const QJsonObject& payload) {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("INSERT INTO %1(scope, judge, handle, external_id, payload, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                                "ON CONFLICT(scope, judge, handle, external_id) DO UPDATE SET payload=excluded.payload, updated_at=excluded.updated_at").arg(table));
    query.addBindValue(scope);
    query.addBindValue(judgeIdToString(judge));
    query.addBindValue(handle);
    query.addBindValue(key);
    query.addBindValue(QString::fromUtf8(QJsonDocument(payload).toJson(QJsonDocument::Compact)));
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::upsertProfile(JudgeId::Value judge, const QString& handle, const QJsonObject& profile) {
    const auto account = JudgeAccount::create(judge, handle);
    if (!account.has_value()) return SyncError::InvalidConfiguration;

    QSqlQuery query(database_);
    query.prepare(QStringLiteral("INSERT INTO profiles(scope, judge, handle, payload, updated_at) VALUES ('ACCOUNT', ?, ?, ?, ?) "
                                "ON CONFLICT(scope, judge, handle) DO UPDATE SET payload=excluded.payload, updated_at=excluded.updated_at"));
    query.addBindValue(judgeIdToString(judge));
    query.addBindValue(cacheHandle(judge, account->handle));
    query.addBindValue(QString::fromUtf8(QJsonDocument(profile).toJson(QJsonDocument::Compact)));
    query.addBindValue(QDateTime::currentDateTimeUtc().toMSecsSinceEpoch());
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

Result<void> SqliteStore::removeProfile(JudgeId::Value judge) {
    if (judge == JudgeId::Unknown || judge == JudgeId::All) return SyncError::InvalidConfiguration;
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("DELETE FROM profiles WHERE scope='ACCOUNT' AND judge=?"));
    query.addBindValue(judgeIdToString(judge));
    return query.exec() ? Result<void>{} : Result<void>{SyncError::Storage};
}

std::optional<StoredProfile> SqliteStore::latestProfile(JudgeId::Value judge, const QString& handle) const {
    QString selectedHandle = handle;
    if (selectedHandle.isEmpty()) {
        QSqlQuery account(database_);
        account.prepare(QStringLiteral("SELECT handle FROM accounts WHERE judge=?"));
        account.addBindValue(judgeIdToString(judge));
        if (account.exec() && account.next()) selectedHandle = account.value(0).toString();
    }
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("SELECT handle, payload, updated_at FROM profiles WHERE scope='ACCOUNT' AND judge=? ORDER BY updated_at DESC"));
    query.addBindValue(judgeIdToString(judge));
    if (!query.exec()) return std::nullopt;
    while (query.next()) {
        const auto account = JudgeAccount::create(judge, query.value(0).toString());
        if (!account.has_value()) continue;
        if (!selectedHandle.isEmpty() && !judgeHandlesMatch(judge, selectedHandle, account->handle)) continue;
        const auto document = QJsonDocument::fromJson(query.value(1).toString().toUtf8());
        if (!document.isObject()) continue;
        return StoredProfile{account->handle, document.object(), fromMillis(query.value(2))};
    }
    return std::nullopt;
}

Result<void> SqliteStore::upsertRatingChange(JudgeId::Value judge, const QJsonObject& change) {
    return upsertRatingChange(judge, QString{}, change);
}

Result<void> SqliteStore::upsertRatingChange(JudgeId::Value judge, const QString& handle, const QJsonObject& change) {
    auto key = jsonKey(change.value(QStringLiteral("contestId")));
    if (key.isEmpty()) key = jsonKey(change.value(QStringLiteral("contest_id")));
    if (key.isEmpty()) key = jsonKey(change.value(QStringLiteral("id")));
    if (key.isEmpty() || key == QStringLiteral("0")) key = QString::fromUtf8(QJsonDocument(change).toJson(QJsonDocument::Compact).toBase64());
    if (handle.isEmpty()) return upsertPayload(QStringLiteral("rating_changes"), judge, QStringLiteral("UNASSIGNED"), QStringLiteral(""), key, change);
    const auto account = JudgeAccount::create(judge, handle);
    if (!account.has_value()) return SyncError::InvalidConfiguration;
    return upsertPayload(QStringLiteral("rating_changes"), judge, QStringLiteral("ACCOUNT"), cacheHandle(judge, account->handle), key, change);
}

Result<void> SqliteStore::upsertSubmission(JudgeId::Value judge, const QJsonObject& submission) {
    return upsertSubmission(judge, QString{}, submission);
}

Result<void> SqliteStore::upsertSubmission(JudgeId::Value judge, const QString& handle, const QJsonObject& submission) {
    auto key = jsonKey(submission.value(QStringLiteral("id")));
    if (key.isEmpty() || key == QStringLiteral("0")) key = QString::fromUtf8(QJsonDocument(submission).toJson(QJsonDocument::Compact).toBase64());
    if (handle.isEmpty()) return upsertPayload(QStringLiteral("submissions"), judge, QStringLiteral("UNASSIGNED"), QStringLiteral(""), key, submission);
    const auto account = JudgeAccount::create(judge, handle);
    if (!account.has_value()) return SyncError::InvalidConfiguration;
    return upsertPayload(QStringLiteral("submissions"), judge, QStringLiteral("ACCOUNT"), cacheHandle(judge, account->handle), key, submission);
}

QList<StoredSubmission> SqliteStore::recentSubmissions(JudgeId::Value judge, int limit, const QString& handle) const {
    QList<StoredSubmission> result;
    if (judge == JudgeId::Unknown || judge == JudgeId::All || limit <= 0) return result;

    QSqlQuery query(database_);
    const auto ownerHandle = handle.isEmpty() ? QString{} : cacheHandle(judge, handle);
    query.prepare(ownerHandle.isEmpty()
        ? QStringLiteral("SELECT external_id, payload, updated_at FROM submissions WHERE judge=?")
        : QStringLiteral("SELECT external_id, payload, updated_at FROM submissions WHERE scope='ACCOUNT' AND judge=? AND handle=?"));
    query.addBindValue(judgeIdToString(judge));
    if (!ownerHandle.isEmpty()) query.addBindValue(ownerHandle);
    if (!query.exec()) return result;
    while (query.next()) {
        const auto document = QJsonDocument::fromJson(query.value(1).toString().toUtf8());
        if (!document.isObject()) continue;
        result.append(projectSubmission(query.value(0).toString(), document.object(), fromMillis(query.value(2))));
    }
    std::sort(result.begin(), result.end(), [](const StoredSubmission& lhs, const StoredSubmission& rhs) {
        const bool lhsHasSubmittedAt = lhs.submittedAt.isValid();
        const bool rhsHasSubmittedAt = rhs.submittedAt.isValid();
        if (lhsHasSubmittedAt != rhsHasSubmittedAt) return lhsHasSubmittedAt;
        if (lhsHasSubmittedAt && lhs.submittedAt != rhs.submittedAt) {
            return lhs.submittedAt > rhs.submittedAt;
        }
        if (lhs.updatedAt != rhs.updatedAt) return lhs.updatedAt > rhs.updatedAt;
        return lhs.externalId > rhs.externalId;
    });
    if (result.size() > limit) result = result.mid(0, limit);
    return result;
}

Result<void> SqliteStore::upsertProblem(JudgeId::Value judge, const QJsonObject& problem) {
    QString key = problem.value(QStringLiteral("externalId")).toString();
    if (key.isEmpty()) key = jsonKey(problem.value(QStringLiteral("id")));
    if (key.isEmpty()) key = jsonKey(problem.value(QStringLiteral("pid")));
    if (key.isEmpty()) key = jsonKey(problem.value(QStringLiteral("problem_id")));
    if (key.isEmpty()) key = QString::fromUtf8(QJsonDocument(problem).toJson(QJsonDocument::Compact).toBase64());
    return upsertPayload(QStringLiteral("problems"), judge, QStringLiteral("GLOBAL"), QStringLiteral(""), key, problem);
}

Result<void> SqliteStore::upsertContest(JudgeId::Value judge, const QJsonObject& contest) {
    QString key = jsonKey(contest.value(QStringLiteral("id")));
    if (key.isEmpty() || key == QStringLiteral("0")) key = jsonKey(contest.value(QStringLiteral("contest_id")));
    if (key.isEmpty() || key == QStringLiteral("0")) key = contest.value(QStringLiteral("externalId")).toString();
    if (key.isEmpty()) key = QString::fromUtf8(QJsonDocument(contest).toJson(QJsonDocument::Compact).toBase64());
    return upsertPayload(QStringLiteral("contests"), judge, QStringLiteral("GLOBAL"), QStringLiteral(""), key, contest);
}

int SqliteStore::profileCount(JudgeId::Value judge) const {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("SELECT COUNT(*) FROM profiles WHERE judge=?"));
    query.addBindValue(judgeIdToString(judge));
    return query.exec() && query.next() ? query.value(0).toInt() : 0;
}

int SqliteStore::ratingChangeCount(JudgeId::Value judge, const QString& handle) const {
    QSqlQuery query(database_);
    const auto ownerHandle = handle.isEmpty() ? QString{} : cacheHandle(judge, handle);
    query.prepare(ownerHandle.isEmpty()
        ? QStringLiteral("SELECT COUNT(*) FROM rating_changes WHERE judge=?")
        : QStringLiteral("SELECT COUNT(*) FROM rating_changes WHERE scope='ACCOUNT' AND judge=? AND handle=?"));
    query.addBindValue(judgeIdToString(judge));
    if (!ownerHandle.isEmpty()) query.addBindValue(ownerHandle);
    return query.exec() && query.next() ? query.value(0).toInt() : 0;
}

int SqliteStore::submissionCount(JudgeId::Value judge, const QString& handle) const {
    QSqlQuery query(database_);
    const auto ownerHandle = handle.isEmpty() ? QString{} : cacheHandle(judge, handle);
    query.prepare(ownerHandle.isEmpty()
        ? QStringLiteral("SELECT COUNT(*) FROM submissions WHERE judge=?")
        : QStringLiteral("SELECT COUNT(*) FROM submissions WHERE scope='ACCOUNT' AND judge=? AND handle=?"));
    query.addBindValue(judgeIdToString(judge));
    if (!ownerHandle.isEmpty()) query.addBindValue(ownerHandle);
    return query.exec() && query.next() ? query.value(0).toInt() : 0;
}

int SqliteStore::problemCount(JudgeId::Value judge) const {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("SELECT COUNT(*) FROM problems WHERE judge=?"));
    query.addBindValue(judgeIdToString(judge));
    return query.exec() && query.next() ? query.value(0).toInt() : 0;
}

int SqliteStore::contestCount(JudgeId::Value judge) const {
    QSqlQuery query(database_);
    query.prepare(QStringLiteral("SELECT COUNT(*) FROM contests WHERE judge=?"));
    query.addBindValue(judgeIdToString(judge));
    return query.exec() && query.next() ? query.value(0).toInt() : 0;
}

} // namespace OjNexus
