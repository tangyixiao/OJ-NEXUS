#include "ojnexus/core/portable_archive_store.h"

#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QVariant>

namespace OjNexus {
namespace {

Result<void> execStatement(QSqlDatabase& db, const QString& sql) {
    QSqlQuery query(db);
    if (!query.exec(sql) || query.lastError().type() != QSqlError::NoError) return SyncError::Storage;
    return {};
}

} // namespace

PortableArchiveStore::PortableArchiveStore(SqliteStore& store) : store_(store) {}

Result<void> PortableArchiveStore::replaceAll(const PortableArchivePackageRow& package, const QList<PortableArchiveRecordRow>& records) {
    QSqlDatabase& db = store_.database_;
    if (!db.transaction()) return SyncError::Storage;
    const auto clearedRecords = execStatement(db, QStringLiteral("DELETE FROM portable_archive_records"));
    if (!clearedRecords.has_value()) {
        db.rollback();
        return SyncError::Storage;
    }
    const auto clearedPackages = execStatement(db, QStringLiteral("DELETE FROM portable_archive_packages"));
    if (!clearedPackages.has_value()) {
        db.rollback();
        return SyncError::Storage;
    }

    QSqlQuery packageQuery(db);
    packageQuery.prepare(QStringLiteral(
        "INSERT INTO portable_archive_packages (identity, scope_id, source_platform, generator, created_at_epoch_ms, counts_json) "
        "VALUES (?, ?, ?, ?, ?, ?)"));
    packageQuery.addBindValue(package.identity);
    packageQuery.addBindValue(package.scopeId);
    packageQuery.addBindValue(package.sourcePlatform);
    packageQuery.addBindValue(package.generatorJson);
    packageQuery.addBindValue(package.createdAtEpochMs);
    packageQuery.addBindValue(package.countsJson);
    if (!packageQuery.exec() || packageQuery.lastError().type() != QSqlError::NoError) {
        db.rollback();
        return SyncError::Storage;
    }

    for (const auto& record : records) {
        QSqlQuery recordQuery(db);
        recordQuery.prepare(QStringLiteral(
            "INSERT INTO portable_archive_records "
            "(identity, collection, stable_id, source_scope_id, origin, state, payload_json, source_tag, synthetic, sort_order) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"));
        recordQuery.addBindValue(package.identity);
        recordQuery.addBindValue(record.collection);
        recordQuery.addBindValue(record.stableId);
        recordQuery.addBindValue(record.sourceScopeId);
        recordQuery.addBindValue(record.origin);
        recordQuery.addBindValue(record.state);
        recordQuery.addBindValue(record.payloadJson);
        recordQuery.addBindValue(record.sourceTag);
        recordQuery.addBindValue(record.synthetic ? 1 : 0);
        recordQuery.addBindValue(record.sortOrder);
        if (!recordQuery.exec() || recordQuery.lastError().type() != QSqlError::NoError) {
            db.rollback();
            return SyncError::Storage;
        }
    }

    if (!db.commit()) {
        db.rollback();
        return SyncError::Storage;
    }
    return {};
}

Result<std::optional<PortableArchivePackageRow>> PortableArchiveStore::getPackage() const {
    QSqlQuery query(store_.database_);
    query.prepare(QStringLiteral(
        "SELECT identity, scope_id, source_platform, generator, created_at_epoch_ms, counts_json "
        "FROM portable_archive_packages LIMIT 1"));
    if (!query.exec() || query.lastError().type() != QSqlError::NoError) return SyncError::Storage;
    if (!query.next()) return std::optional<PortableArchivePackageRow>{};
    PortableArchivePackageRow row;
    row.identity = query.value(0).toString();
    row.scopeId = query.value(1).toString();
    row.sourcePlatform = query.value(2).toString();
    row.generatorJson = query.value(3).toString();
    row.createdAtEpochMs = query.value(4).toLongLong();
    row.countsJson = query.value(5).toString();
    return std::optional<PortableArchivePackageRow>{std::move(row)};
}

Result<QList<PortableArchiveRecordRow>> PortableArchiveStore::getRecords(const QString& identity) const {
    if (identity.trimmed().isEmpty()) return SyncError::Storage;
    QSqlQuery query(store_.database_);
    query.prepare(QStringLiteral(
        "SELECT collection, stable_id, source_scope_id, origin, state, payload_json, source_tag, synthetic, sort_order "
        "FROM portable_archive_records WHERE identity = ? ORDER BY sort_order ASC"));
    query.addBindValue(identity);
    if (!query.exec() || query.lastError().type() != QSqlError::NoError) return SyncError::Storage;
    QList<PortableArchiveRecordRow> records;
    while (query.next()) {
        PortableArchiveRecordRow row;
        row.collection = query.value(0).toString();
        row.stableId = query.value(1).toString();
        row.sourceScopeId = query.value(2).toString();
        row.origin = query.value(3).toString();
        row.state = query.value(4).toString();
        row.payloadJson = query.value(5).toString();
        row.sourceTag = query.value(6).toString();
        row.synthetic = query.value(7).toLongLong() != 0;
        row.sortOrder = query.value(8).toLongLong();
        records.push_back(std::move(row));
    }
    return records;
}

Result<void> PortableArchiveStore::clear() {
    QSqlDatabase& db = store_.database_;
    if (!db.transaction()) return SyncError::Storage;
    const auto clearedRecords = execStatement(db, QStringLiteral("DELETE FROM portable_archive_records"));
    if (!clearedRecords.has_value()) {
        db.rollback();
        return SyncError::Storage;
    }
    const auto clearedPackages = execStatement(db, QStringLiteral("DELETE FROM portable_archive_packages"));
    if (!clearedPackages.has_value()) {
        db.rollback();
        return SyncError::Storage;
    }
    if (!db.commit()) {
        db.rollback();
        return SyncError::Storage;
    }
    return {};
}

} // namespace OjNexus
