#include <QtTest/QtTest>

#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QTemporaryDir>
#include <QFile>

#include "ojnexus/core/store.h"
#include "ojnexus/core/portable_archive_store.h"

namespace {

OjNexus::PortableArchivePackageRow samplePackage(const QString& identity) {
    OjNexus::PortableArchivePackageRow row;
    row.identity = identity;
    row.scopeId = QStringLiteral("local-account");
    row.sourcePlatform = QStringLiteral("android");
    row.generatorJson = QStringLiteral("{\"name\":\"synthetic-fixture-gen\",\"version\":\"1.0\"}");
    row.createdAtEpochMs = 1760000000000;
    row.countsJson = QStringLiteral("{\"problems\":2,\"reviews\":1}");
    return row;
}

OjNexus::PortableArchiveRecordRow sampleRecord(const QString& collection, const QString& stableId, qint64 sortOrder) {
    OjNexus::PortableArchiveRecordRow row;
    row.collection = collection;
    row.stableId = stableId;
    row.sourceScopeId = QStringLiteral("local-account");
    row.origin = QStringLiteral("android");
    row.state = QStringLiteral("EDITABLE");
    row.payloadJson = QStringLiteral("{\"stableId\":\"") + stableId + QStringLiteral("\"}");
    row.sourceTag = QStringLiteral("synthetic");
    row.synthetic = true;
    row.sortOrder = sortOrder;
    return row;
}

/// Builds a legacy schema-v2 database (pre-phase-134 shapes, no portable tables).
bool createLegacyV2Database(const QString& databasePath, const QString& connectionName) {
    auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
    database.setDatabaseName(databasePath);
    if (!database.open()) return false;
    QSqlQuery query(database);
    const QStringList schema{
        QStringLiteral("CREATE TABLE schema_metadata(version INTEGER NOT NULL)"),
        QStringLiteral("INSERT INTO schema_metadata VALUES(2)"),
        QStringLiteral("CREATE TABLE accounts(judge TEXT PRIMARY KEY, handle TEXT NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)"),
        QStringLiteral("CREATE TABLE sync_operations(id INTEGER PRIMARY KEY AUTOINCREMENT, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '', owner_pid INTEGER NOT NULL DEFAULT 0, owner_boot_id TEXT NOT NULL DEFAULT '', owner_start_ticks INTEGER NOT NULL DEFAULT 0)"),
        QStringLiteral("CREATE TABLE sync_modules(operation_id INTEGER NOT NULL, stage TEXT NOT NULL, status TEXT NOT NULL, attempted_count INTEGER NOT NULL, imported_count INTEGER NOT NULL, updated_count INTEGER NOT NULL, failure_type TEXT NOT NULL DEFAULT '', PRIMARY KEY(operation_id, stage))"),
        QStringLiteral("CREATE INDEX sync_operations_judge_started ON sync_operations(judge, started_at DESC)"),
        QStringLiteral("CREATE TABLE profiles(scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle))"),
        QStringLiteral("CREATE TABLE rating_changes(scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE submissions(scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE problems(scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
        QStringLiteral("CREATE TABLE contests(scope TEXT NOT NULL, judge TEXT NOT NULL, handle TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope, judge, handle, external_id))"),
    };
    for (const auto& sql : schema) {
        if (!query.exec(sql)) return false;
    }
    database.close();
    QSqlDatabase::removeDatabase(connectionName);
    return true;
}

/// Writes an arbitrary schema version into an existing database file.
bool forceSchemaVersion(const QString& databasePath, const QString& connectionName, int version) {
    auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
    database.setDatabaseName(databasePath);
    if (!database.open()) return false;
    QSqlQuery query(database);
    const auto ok = query.exec(QStringLiteral("UPDATE schema_metadata SET version=%1").arg(version));
    database.close();
    QSqlDatabase::removeDatabase(connectionName);
    return ok;
}

} // namespace

class PortableArchiveStoreTest final : public QObject {
    Q_OBJECT

private slots:
    void freshStoreReportsSchemaVersion3();
    void getPackageOnEmptyArchiveReturnsNull();
    void replaceAllWritesPackageAndRecords();
    void replaceAllSwapsPreviousArchiveAtomically();
    void inducedFailureRollsBackToPreviousArchive();
    void cascadeDeleteRemovesRecordsWithPackage();
    void legacyVersion2StoreMigratesToV3AndAcceptsArchive();
    void legacyVersion1StoreMigratesToV3();
    void unknownSchemaVersionIsRejected();
};

void PortableArchiveStoreTest::freshStoreReportsSchemaVersion3() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath(QStringLiteral("fresh.db")));
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 3);
}

void PortableArchiveStoreTest::getPackageOnEmptyArchiveReturnsNull() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath(QStringLiteral("fresh.db")));
    QVERIFY(store.initialize().has_value());
    OjNexus::PortableArchiveStore archive(store);
    const auto package = archive.getPackage();
    QVERIFY(package.has_value());
    QVERIFY(!package.value().has_value());
}

void PortableArchiveStoreTest::replaceAllWritesPackageAndRecords() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath(QStringLiteral("fresh.db")));
    QVERIFY(store.initialize().has_value());
    OjNexus::PortableArchiveStore archive(store);

    const auto records = QList<OjNexus::PortableArchiveRecordRow>{
        sampleRecord(QStringLiteral("problems"), QStringLiteral("1A"), 0),
        sampleRecord(QStringLiteral("problems"), QStringLiteral("1B"), 1),
        sampleRecord(QStringLiteral("reviews"), QStringLiteral("r-1"), 2),
    };
    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-1")), records).has_value());

    const auto package = archive.getPackage();
    QVERIFY(package.has_value() && package.value().has_value());
    QCOMPARE(package.value()->identity, QStringLiteral("pkg-1"));
    QCOMPARE(package.value()->scopeId, QStringLiteral("local-account"));
    QCOMPARE(package.value()->sourcePlatform, QStringLiteral("android"));
    QCOMPARE(package.value()->createdAtEpochMs, qlonglong{1760000000000});

    const auto stored = archive.getRecords(QStringLiteral("pkg-1"));
    QVERIFY(stored.has_value());
    QCOMPARE(stored.value().size(), 3);
    QCOMPARE(stored.value().at(0).stableId, QStringLiteral("1A"));
    QCOMPARE(stored.value().at(0).sortOrder, qlonglong{0});
    QCOMPARE(stored.value().at(1).sortOrder, qlonglong{1});
    QCOMPARE(stored.value().at(2).collection, QStringLiteral("reviews"));
    QCOMPARE(stored.value().at(2).synthetic, true);
}

void PortableArchiveStoreTest::replaceAllSwapsPreviousArchiveAtomically() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath(QStringLiteral("fresh.db")));
    QVERIFY(store.initialize().has_value());
    OjNexus::PortableArchiveStore archive(store);

    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-A")),
        QList<OjNexus::PortableArchiveRecordRow>{sampleRecord(QStringLiteral("problems"), QStringLiteral("old"), 0)}).has_value());
    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-B")),
        QList<OjNexus::PortableArchiveRecordRow>{sampleRecord(QStringLiteral("problems"), QStringLiteral("new"), 0)}).has_value());

    const auto package = archive.getPackage();
    QVERIFY(package.has_value() && package.value().has_value());
    QCOMPARE(package.value()->identity, QStringLiteral("pkg-B"));
    const auto stale = archive.getRecords(QStringLiteral("pkg-A"));
    QVERIFY(stale.has_value());
    QCOMPARE(stale.value().size(), 0);
}

void PortableArchiveStoreTest::inducedFailureRollsBackToPreviousArchive() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath(QStringLiteral("fresh.db")));
    QVERIFY(store.initialize().has_value());
    OjNexus::PortableArchiveStore archive(store);

    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-A")),
        QList<OjNexus::PortableArchiveRecordRow>{sampleRecord(QStringLiteral("problems"), QStringLiteral("kept"), 0)}).has_value());

    // Second record duplicates the first one's primary key mid-transaction, so
    // the replace must abort and roll back to the untouched previous archive.
    const auto doomed = QList<OjNexus::PortableArchiveRecordRow>{
        sampleRecord(QStringLiteral("problems"), QStringLiteral("x"), 0),
        sampleRecord(QStringLiteral("problems"), QStringLiteral("x"), 1),
    };
    const auto failed = archive.replaceAll(samplePackage(QStringLiteral("pkg-B")), doomed);
    QVERIFY(!failed.has_value());
    QCOMPARE(failed.error(), OjNexus::SyncError::Storage);

    const auto package = archive.getPackage();
    QVERIFY(package.has_value() && package.value().has_value());
    QCOMPARE(package.value()->identity, QStringLiteral("pkg-A"));
    const auto kept = archive.getRecords(QStringLiteral("pkg-A"));
    QVERIFY(kept.has_value());
    QCOMPARE(kept.value().size(), 1);
    QCOMPARE(kept.value().at(0).stableId, QStringLiteral("kept"));
}

void PortableArchiveStoreTest::cascadeDeleteRemovesRecordsWithPackage() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath(QStringLiteral("fresh.db"));
    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());
    OjNexus::PortableArchiveStore archive(store);
    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-1")),
        QList<OjNexus::PortableArchiveRecordRow>{sampleRecord(QStringLiteral("problems"), QStringLiteral("1A"), 0)}).has_value());

    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), QStringLiteral("cascade-fixture"));
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery pragmaQuery(database);
        QVERIFY2(pragmaQuery.exec(QStringLiteral("PRAGMA foreign_keys = ON")), qPrintable(pragmaQuery.lastError().text()));
        QSqlQuery query(database);
        QVERIFY2(query.exec(QStringLiteral("DELETE FROM portable_archive_packages")), qPrintable(query.lastError().text()));
        database.close();
    }
    QSqlDatabase::removeDatabase(QStringLiteral("cascade-fixture"));

    const auto records = archive.getRecords(QStringLiteral("pkg-1"));
    QVERIFY(records.has_value());
    QCOMPARE(records.value().size(), 0);
    const auto package = archive.getPackage();
    QVERIFY(package.has_value());
    QVERIFY(!package.value().has_value());
}

void PortableArchiveStoreTest::legacyVersion2StoreMigratesToV3AndAcceptsArchive() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath(QStringLiteral("legacy-v2.db"));
    QVERIFY(createLegacyV2Database(databasePath, QStringLiteral("legacy-v2-fixture")));

    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 3);

    OjNexus::PortableArchiveStore archive(store);
    QVERIFY(archive.replaceAll(samplePackage(QStringLiteral("pkg-m")),
        QList<OjNexus::PortableArchiveRecordRow>{sampleRecord(QStringLiteral("problems"), QStringLiteral("p"), 0)}).has_value());
    const auto stored = archive.getRecords(QStringLiteral("pkg-m"));
    QVERIFY(stored.has_value());
    QCOMPARE(stored.value().size(), 1);
}

void PortableArchiveStoreTest::legacyVersion1StoreMigratesToV3() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath(QStringLiteral("legacy-v1.db"));
    QVERIFY(createLegacyV2Database(databasePath, QStringLiteral("legacy-v1-fixture")));

    // Demote the fixture to a genuine v1 store: drop scope columns and owner
    // columns, reset the recorded version, and restore the v1 payload keys.
    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), QStringLiteral("legacy-v1-demote"));
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery query(database);
        const QStringList demotions{
            QStringLiteral("ALTER TABLE sync_operations RENAME TO operations_v2"),
            QStringLiteral("CREATE TABLE sync_operations(id INTEGER PRIMARY KEY AUTOINCREMENT, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '')"),
            QStringLiteral("INSERT INTO sync_operations(id, judge, handle, data_generation, status, started_at, finished_at, error_type) SELECT id, judge, handle, data_generation, status, started_at, finished_at, error_type FROM operations_v2"),
            QStringLiteral("DROP TABLE operations_v2"),
            QStringLiteral("ALTER TABLE profiles RENAME TO profiles_v2"),
            QStringLiteral("CREATE TABLE profiles(judge TEXT PRIMARY KEY, handle TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)"),
            QStringLiteral("INSERT INTO profiles(judge, handle, payload, updated_at) SELECT judge, handle, payload, updated_at FROM profiles_v2"),
            QStringLiteral("DROP TABLE profiles_v2"),
            QStringLiteral("ALTER TABLE rating_changes RENAME TO rating_changes_v2"),
            QStringLiteral("CREATE TABLE rating_changes(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("INSERT INTO rating_changes(judge, external_id, payload, updated_at) SELECT judge, external_id, payload, updated_at FROM rating_changes_v2"),
            QStringLiteral("DROP TABLE rating_changes_v2"),
            QStringLiteral("ALTER TABLE submissions RENAME TO submissions_v2"),
            QStringLiteral("CREATE TABLE submissions(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("INSERT INTO submissions(judge, external_id, payload, updated_at) SELECT judge, external_id, payload, updated_at FROM submissions_v2"),
            QStringLiteral("DROP TABLE submissions_v2"),
            QStringLiteral("ALTER TABLE problems RENAME TO problems_v2"),
            QStringLiteral("CREATE TABLE problems(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("INSERT INTO problems(judge, external_id, payload, updated_at) SELECT judge, external_id, payload, updated_at FROM problems_v2"),
            QStringLiteral("DROP TABLE problems_v2"),
            QStringLiteral("ALTER TABLE contests RENAME TO contests_v2"),
            QStringLiteral("CREATE TABLE contests(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("INSERT INTO contests(judge, external_id, payload, updated_at) SELECT judge, external_id, payload, updated_at FROM contests_v2"),
            QStringLiteral("DROP TABLE contests_v2"),
            QStringLiteral("UPDATE schema_metadata SET version=1"),
        };
        for (const auto& sql : demotions) QVERIFY2(query.exec(sql), qPrintable(query.lastError().text()));
        database.close();
    }
    QSqlDatabase::removeDatabase(QStringLiteral("legacy-v1-demote"));

    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 3);
}

void PortableArchiveStoreTest::unknownSchemaVersionIsRejected() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath(QStringLiteral("fresh.db"));
    {
        OjNexus::SqliteStore store(databasePath);
        QVERIFY(store.initialize().has_value());
    }
    QVERIFY(forceSchemaVersion(databasePath, QStringLiteral("unknown-version-fixture"), 9));

    OjNexus::SqliteStore store(databasePath);
    const auto result = store.initialize();
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::Storage);
    QCOMPARE(store.schemaVersion(), 0);
}

QTEST_MAIN(PortableArchiveStoreTest)
#include "PortableArchiveStoreTest.moc"
