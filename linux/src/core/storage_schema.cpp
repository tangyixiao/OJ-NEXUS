#include "storage_schema.h"

#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QMap>
#include <QStringList>
#include <QVariant>
#include <QVariantList>

namespace OjNexus::StorageSchema {
namespace {

bool run(QSqlDatabase& db, const QString& sql) {
    QSqlQuery query(db);
    return query.exec(sql);
}

struct Column { const char* name; const char* affinity; bool required; int primaryKeyOrder; };
struct Table { const char* name; const Column* columns; int count; };

constexpr Column metadata[]{{"version", "INTEGER", true, 0}};
constexpr Column accounts[]{{"judge", "TEXT", true, 1}, {"handle", "TEXT", true, 0}, {"enabled", "INTEGER", true, 0}, {"updated_at", "INTEGER", true, 0}};
constexpr Column operations[]{{"id", "INTEGER", true, 1}, {"judge", "TEXT", true, 0}, {"handle", "TEXT", true, 0}, {"data_generation", "TEXT", true, 0}, {"status", "TEXT", true, 0}, {"started_at", "INTEGER", true, 0}, {"finished_at", "INTEGER", false, 0}, {"error_type", "TEXT", true, 0}, {"owner_pid", "INTEGER", false, 0}, {"owner_boot_id", "TEXT", false, 0}, {"owner_start_ticks", "INTEGER", false, 0}};
constexpr Column modules[]{{"operation_id", "INTEGER", true, 1}, {"stage", "TEXT", true, 2}, {"status", "TEXT", true, 0}, {"attempted_count", "INTEGER", true, 0}, {"imported_count", "INTEGER", true, 0}, {"updated_count", "INTEGER", true, 0}, {"failure_type", "TEXT", true, 0}};
constexpr Column profilesV1[]{{"judge", "TEXT", true, 1}, {"handle", "TEXT", true, 0}, {"payload", "TEXT", true, 0}, {"updated_at", "INTEGER", true, 0}};
constexpr Column payloadV1[]{{"judge", "TEXT", true, 1}, {"external_id", "TEXT", true, 2}, {"payload", "TEXT", true, 0}, {"updated_at", "INTEGER", true, 0}};
constexpr Column profilesV2[]{{"scope", "TEXT", true, 1}, {"judge", "TEXT", true, 2}, {"handle", "TEXT", true, 3}, {"payload", "TEXT", true, 0}, {"updated_at", "INTEGER", true, 0}};
constexpr Column payloadV2[]{{"scope", "TEXT", true, 1}, {"judge", "TEXT", true, 2}, {"handle", "TEXT", true, 3}, {"external_id", "TEXT", true, 4}, {"payload", "TEXT", true, 0}, {"updated_at", "INTEGER", true, 0}};

bool hasTable(QSqlDatabase& db, const char* name) {
    QSqlQuery q(db);
    q.prepare(QStringLiteral("SELECT type FROM sqlite_master WHERE name=?"));
    q.addBindValue(QString::fromLatin1(name));
    return q.exec() && q.next() && q.value(0).toString() == QStringLiteral("table") && !q.next();
}

bool validateTable(QSqlDatabase& db, const Table& table) {
    if (!hasTable(db, table.name)) return false;
    QSqlQuery q(db);
    if (!q.exec(QStringLiteral("PRAGMA table_info(\"%1\")").arg(QString::fromLatin1(table.name)))) return false;
    struct Actual { QString type; bool notNull; int pk; QVariant defaultValue; };
    QMap<QString, Actual> actual;
    while (q.next()) actual.insert(q.value(1).toString(), {q.value(2).toString().toUpper(), q.value(3).toInt() != 0, q.value(5).toInt(), q.value(4)});
    for (int i = 0; i < table.count; ++i) {
        const auto& expected = table.columns[i];
        const auto found = actual.constFind(QString::fromLatin1(expected.name));
        if (found == actual.cend()) return false;
        const auto type = found->type;
        const bool affinityMatches = QString::fromLatin1(expected.affinity) == QStringLiteral("TEXT")
            ? type.contains(QStringLiteral("CHAR")) || type.contains(QStringLiteral("CLOB")) || type.contains(QStringLiteral("TEXT"))
            : type.contains(QStringLiteral("INT"));
        if (!affinityMatches || (expected.required && !found->notNull && found->pk == 0) || found->pk != expected.primaryKeyOrder) return false;
    }
    int expectedPrimaryKeySize = 0;
    for (int i = 0; i < table.count; ++i) expectedPrimaryKeySize = qMax(expectedPrimaryKeySize, table.columns[i].primaryKeyOrder);
    for (auto it = actual.cbegin(); it != actual.cend(); ++it) {
        if (it->pk > expectedPrimaryKeySize) return false;
        bool known = false;
        for (int i = 0; i < table.count; ++i) known = known || it.key() == QString::fromLatin1(table.columns[i].name);
        if (!known && it->notNull && it->defaultValue.isNull() && it->pk == 0) return false;
    }
    return true;
}

bool quickCheck(QSqlDatabase& db) {
    QSqlQuery q(db);
    if (!q.exec(QStringLiteral("PRAGMA quick_check"))) return false;
    int rows = 0;
    while (q.next()) { ++rows; if (q.value(0).toString() != QStringLiteral("ok")) return false; }
    return rows == 1 && q.lastError().type() == QSqlError::NoError;
}

} // namespace

Result<int> validateExistingSchema(QSqlDatabase& db) {
    if (!quickCheck(db) || !validateTable(db, {"schema_metadata", metadata, 1})) return SyncError::Storage;
    QSqlQuery version(db);
    if (!version.exec(QStringLiteral("SELECT version, typeof(version) FROM schema_metadata"))) return SyncError::Storage;
    if (!version.next() || version.value(1).toString() != QStringLiteral("integer")) return SyncError::Storage;
    const auto value = version.value(0).toInt();
    if (value != 1 && value != 2) return SyncError::Storage;
    if (version.next() || version.lastError().type() != QSqlError::NoError) return SyncError::Storage;
    const Table common[]{{"accounts", accounts, 4}, {"sync_operations", operations, value == 1 ? 8 : 11}, {"sync_modules", modules, 7}};
    for (const auto& table : common) if (!validateTable(db, table)) return SyncError::Storage;
    const Table payloadTables[]{{"profiles", value == 1 ? profilesV1 : profilesV2, value == 1 ? 4 : 5},
        {"rating_changes", value == 1 ? payloadV1 : payloadV2, value == 1 ? 4 : 6},
        {"submissions", value == 1 ? payloadV1 : payloadV2, value == 1 ? 4 : 6},
        {"problems", value == 1 ? payloadV1 : payloadV2, value == 1 ? 4 : 6},
        {"contests", value == 1 ? payloadV1 : payloadV2, value == 1 ? 4 : 6}};
    for (const auto& table : payloadTables) if (!validateTable(db, table)) return SyncError::Storage;
    return value;
}

Result<void> createInitialSchema(QSqlDatabase& db) {
    if (!db.transaction()) return SyncError::Storage;
    const QStringList schema{
        QStringLiteral("CREATE TABLE schema_metadata (version INTEGER NOT NULL)"),
        QStringLiteral("CREATE TABLE accounts (judge TEXT PRIMARY KEY, handle TEXT NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)"),
        QStringLiteral("CREATE TABLE sync_operations (id INTEGER PRIMARY KEY AUTOINCREMENT, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '', owner_pid INTEGER NOT NULL DEFAULT 0, owner_boot_id TEXT NOT NULL DEFAULT '', owner_start_ticks INTEGER NOT NULL DEFAULT 0)"),
        QStringLiteral("CREATE TABLE sync_modules (operation_id INTEGER NOT NULL REFERENCES sync_operations(id) ON DELETE CASCADE, stage TEXT NOT NULL, status TEXT NOT NULL, attempted_count INTEGER NOT NULL, imported_count INTEGER NOT NULL, updated_count INTEGER NOT NULL, failure_type TEXT NOT NULL DEFAULT '', PRIMARY KEY(operation_id, stage))"),
        QStringLiteral("CREATE INDEX sync_operations_judge_started ON sync_operations(judge, started_at DESC)"),
        QStringLiteral("CREATE TABLE profiles (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle))"),
        QStringLiteral("CREATE TABLE rating_changes (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE submissions (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE problems (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE contests (scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("INSERT INTO schema_metadata(version) VALUES (2)")};
    for (const auto& statement : schema) if (!run(db, statement)) { db.rollback(); return SyncError::Storage; }
    if (!db.commit()) { db.rollback(); return SyncError::Storage; }
    return {};
}

} // namespace OjNexus::StorageSchema
