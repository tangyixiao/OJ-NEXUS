#include <QtTest/QtTest>

#include <QSqlDatabase>
#include <QSqlError>
#include <QSqlQuery>
#include <QTemporaryDir>
#include <QFile>
#include <QCryptographicHash>
#include <QDir>
#include <QFileInfo>

#include <unistd.h>

#include "ojnexus/core/store.h"
#include "../../src/core/storage_schema.h"

class SqliteStoreTest final : public QObject {
    Q_OBJECT

private slots:
    void createsSchemaAndStoresAccount();
    void rejectsExistingEmptyFileWithoutMutation();
    void rejectsMalformedExistingStores_data();
    void rejectsMalformedExistingStores();
    void acceptedIntegerRowidKeysSupportOperationLifecycle();
    void acceptsNonNullLiteralDefaultsForExtraRequiredColumns();
    void repeatedFailureClearsSchemaVersion();
    void rollsBackPartialInitialSchema();
    void firstUseDoesNotOverwriteCompetitor();
    void opensValidWalStoreWithLiveConnection();
    void recordsModulesOnlyBeforeClosingOperation();
    void persistsOperationErrorForHistory();
    void limitsCompletedHistoryWithoutDeletingRunningRows();
    void recoversRunningOperationsOnReopen();
    void keepsLiveOperationRunningAcrossRefreshInitialization();
    void migratesLegacyCacheWithoutDroppingOrGuessingOwners();
    void storesAndReadsLatestProfile();
    void storesAndReadsRecentSubmissionProjection();
    void rejectsUnsafeProfileHandleAtPersistenceBoundary();
    void rejectsUnsafeAccountsAtPersistenceBoundaries();
    void ignoresUnsafePersistedAccountRows();
    void ignoresUnsafePersistedOperationRows();
    void ignoresUnsafePersistedProfileRows();
    void supportsAtomicPayloadTransactions();
    void updatesAndRemovesAccounts();
    void removesProfileSnapshot();
    void changingHandleRetainsCachesButScopesPersonalRows();
    void preservesCachedDataWhenEquivalentLuoguHandleChanges();
    void preservesDisabledStateWhenEquivalentHandleChanges();
    void keepsOneShotHandleCacheSeparateFromConfiguredAccount();
};

void SqliteStoreTest::rejectsExistingEmptyFileWithoutMutation() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("existing-empty.db"));
    QFile file(path);
    QVERIFY(file.open(QIODevice::WriteOnly));
    file.close();
    auto bytes = [&] { QFile f(path); if (!f.open(QIODevice::ReadOnly)) return QByteArray{}; return f.readAll(); };
    const auto before = QCryptographicHash::hash(bytes(), QCryptographicHash::Sha256);
    for (int attempt = 0; attempt < 2; ++attempt) {
        {
            OjNexus::SqliteStore store(path);
            const auto result = store.initialize();
            QVERIFY(!result.has_value());
            QCOMPARE(result.error(), OjNexus::SyncError::Storage);
            QCOMPARE(store.schemaVersion(), 0);
        }
        QCOMPARE(QCryptographicHash::hash(bytes(), QCryptographicHash::Sha256), before);
    }
}

void SqliteStoreTest::repeatedFailureClearsSchemaVersion() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("state.db"));
    OjNexus::SqliteStore store(path);
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 2);
    const auto name = QStringLiteral("repeat-init-corruption");
    {
        auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
        db.setDatabaseName(path);
        QVERIFY(db.open());
        QSqlQuery query(db);
        QVERIFY(query.exec(QStringLiteral("UPDATE schema_metadata SET version=3")));
        db.close();
        db = {};
    }
    QSqlDatabase::removeDatabase(name);
    QVERIFY(!store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 0);
}

void SqliteStoreTest::rejectsMalformedExistingStores_data() {
    QTest::addColumn<QString>("kind");
    for (const auto& kind : {"non-sqlite", "truncated", "no-metadata", "empty-metadata", "duplicate-metadata",
                             "null-version", "text-version", "real-version", "future-version", "missing-table",
                             "missing-column", "missing-primary-key", "wrong-affinity",
                             "large-positive-version-1", "large-positive-version-2", "large-negative-version-1", "large-negative-version-2",
                             "operation-bigint-key", "operation-desc-key", "operation-without-rowid", "finished-not-null",
                             "error-default-missing", "owner-default-missing", "owner-nullable", "extra-null-default",
                             "extra-parenthesized-null-default", "handle-charint"})
        QTest::newRow(kind) << QString::fromLatin1(kind);
}

void SqliteStoreTest::rejectsMalformedExistingStores() {
    QFETCH(QString, kind);
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("malformed.db"));
    if (kind == QStringLiteral("non-sqlite") || kind == QStringLiteral("truncated")) {
        QFile file(path);
        QVERIFY(file.open(QIODevice::WriteOnly));
        file.write(kind == QStringLiteral("non-sqlite") ? QByteArray("not a database") : QByteArray("SQLite format 3\0broken", 22));
        file.close();
    } else {
        { OjNexus::SqliteStore valid(path); QVERIFY(valid.initialize().has_value()); }
        const auto name = QStringLiteral("malformed-fixture-%1").arg(kind);
        {
            auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
            db.setDatabaseName(path);
            QVERIFY(db.open());
            QSqlQuery q(db);
            QStringList statements;
            if (kind == "no-metadata") statements << "DROP TABLE schema_metadata";
            else if (kind == "empty-metadata") statements << "DELETE FROM schema_metadata";
            else if (kind == "duplicate-metadata") statements << "INSERT INTO schema_metadata(version) VALUES (2)";
            else if (kind == "null-version") statements << "CREATE TABLE metadata_bad(version INTEGER)" << "INSERT INTO metadata_bad VALUES (NULL)" << "DROP TABLE schema_metadata" << "ALTER TABLE metadata_bad RENAME TO schema_metadata";
            else if (kind == "text-version") statements << "UPDATE schema_metadata SET version='two'";
            else if (kind == "real-version") statements << "UPDATE schema_metadata SET version=2.5";
            else if (kind == "future-version") statements << "UPDATE schema_metadata SET version=99";
            else if (kind == "missing-table") statements << "DROP TABLE contests";
            else if (kind == "missing-column") statements << "ALTER TABLE accounts DROP COLUMN updated_at";
            else if (kind == "missing-primary-key") statements << "CREATE TABLE accounts_bad(judge TEXT NOT NULL, handle TEXT NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)" << "INSERT INTO accounts_bad SELECT * FROM accounts" << "DROP TABLE accounts" << "ALTER TABLE accounts_bad RENAME TO accounts";
            else if (kind == "wrong-affinity") statements << "CREATE TABLE accounts_bad(judge TEXT PRIMARY KEY, handle BLOB NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)" << "INSERT INTO accounts_bad SELECT * FROM accounts" << "DROP TABLE accounts" << "ALTER TABLE accounts_bad RENAME TO accounts";
            else if (kind == "large-positive-version-1") statements << "UPDATE schema_metadata SET version=4294967297";
            else if (kind == "large-positive-version-2") statements << "UPDATE schema_metadata SET version=4294967298";
            else if (kind == "large-negative-version-1") statements << "UPDATE schema_metadata SET version=-4294967295";
            else if (kind == "large-negative-version-2") statements << "UPDATE schema_metadata SET version=-4294967294";
            else if (kind.startsWith("operation-")) {
                QString idDefinition;
                QString suffix;
                if (kind == "operation-bigint-key") idDefinition = QStringLiteral("BIGINT PRIMARY KEY");
                else if (kind == "operation-desc-key") idDefinition = QStringLiteral("INTEGER PRIMARY KEY DESC");
                else { idDefinition = QStringLiteral("INTEGER PRIMARY KEY"); suffix = QStringLiteral(" WITHOUT ROWID"); }
                const auto replacement = QStringLiteral("CREATE TABLE operations_bad(id %1, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '', owner_pid INTEGER NOT NULL DEFAULT 0, owner_boot_id TEXT NOT NULL DEFAULT '', owner_start_ticks INTEGER NOT NULL DEFAULT 0)%2")
                    .arg(idDefinition, suffix);
                statements << replacement << "DROP TABLE sync_operations" << "ALTER TABLE operations_bad RENAME TO sync_operations";
            }
            else if (kind == "finished-not-null" || kind == "error-default-missing" || kind == "owner-default-missing" || kind == "owner-nullable") {
                const QString finished = kind == "finished-not-null" ? QStringLiteral("finished_at INTEGER NOT NULL") : QStringLiteral("finished_at INTEGER");
                const QString error = kind == "error-default-missing" ? QStringLiteral("error_type TEXT NOT NULL") : QStringLiteral("error_type TEXT NOT NULL DEFAULT ''");
                const QString ownerPid = kind == "owner-default-missing" ? QStringLiteral("owner_pid INTEGER NOT NULL") :
                    kind == "owner-nullable" ? QStringLiteral("owner_pid INTEGER DEFAULT 0") : QStringLiteral("owner_pid INTEGER NOT NULL DEFAULT 0");
                const auto replacement = QStringLiteral("CREATE TABLE operations_bad(id INTEGER PRIMARY KEY AUTOINCREMENT, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, %1, %2, %3, owner_boot_id TEXT NOT NULL DEFAULT '', owner_start_ticks INTEGER NOT NULL DEFAULT 0)")
                    .arg(finished, error, ownerPid);
                statements << replacement << "DROP TABLE sync_operations" << "ALTER TABLE operations_bad RENAME TO sync_operations";
            }
            else if (kind == "extra-null-default") statements << "ALTER TABLE accounts ADD COLUMN blocker TEXT NOT NULL DEFAULT NULL";
            else if (kind == "extra-parenthesized-null-default") statements << "ALTER TABLE accounts ADD COLUMN blocker TEXT NOT NULL DEFAULT (NULL)";
            else if (kind == "handle-charint") {
                statements << "CREATE TABLE accounts_bad(judge TEXT PRIMARY KEY, handle CHARINT NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
                           << "DROP TABLE accounts" << "ALTER TABLE accounts_bad RENAME TO accounts"
                           << "INSERT INTO accounts(judge, handle, enabled, updated_at) VALUES ('codeforces', '001234', 1, 0)";
            }
            for (const auto& statement : statements) QVERIFY2(q.exec(statement), qPrintable(q.lastError().text()));
            if (kind == "handle-charint") {
                QVERIFY(q.exec(QStringLiteral("SELECT typeof(handle), handle FROM accounts WHERE judge='codeforces'")));
                QVERIFY(q.next());
                QCOMPARE(q.value(0).toString(), QStringLiteral("integer"));
                QCOMPARE(q.value(1).toLongLong(), 1234LL);
            }
            db.close();
            db = {};
        }
        QSqlDatabase::removeDatabase(name);
    }
    auto fileBytes = [&] { QFile file(path); if (!file.open(QIODevice::ReadOnly)) return QByteArray{}; return file.readAll(); };
    const auto before = QCryptographicHash::hash(fileBytes(), QCryptographicHash::Sha256);
    for (int attempt = 0; attempt < 2; ++attempt) {
        OjNexus::SqliteStore store(path);
        const auto result = store.initialize();
        QVERIFY(!result.has_value());
        QCOMPARE(result.error(), OjNexus::SyncError::Storage);
        QCOMPARE(store.schemaVersion(), 0);
        QCOMPARE(QCryptographicHash::hash(fileBytes(), QCryptographicHash::Sha256), before);
    }
}

void SqliteStoreTest::acceptedIntegerRowidKeysSupportOperationLifecycle() {
    for (const bool withoutAutoincrement : {false, true}) {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        const auto path = dir.filePath(QStringLiteral("rowid.db"));
        { OjNexus::SqliteStore initial(path); QVERIFY(initial.initialize().has_value()); }
        if (withoutAutoincrement) {
            const auto name = QStringLiteral("rowid-key-shape");
            {
                auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
                db.setDatabaseName(path);
                QVERIFY(db.open());
                QSqlQuery q(db);
                QVERIFY(q.exec(QStringLiteral("CREATE TABLE operations_new(id INTEGER PRIMARY KEY, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '', owner_pid INTEGER NOT NULL DEFAULT 0, owner_boot_id TEXT NOT NULL DEFAULT '', owner_start_ticks INTEGER NOT NULL DEFAULT 0)")));
                QVERIFY(q.exec(QStringLiteral("DROP TABLE sync_operations")));
                QVERIFY(q.exec(QStringLiteral("ALTER TABLE operations_new RENAME TO sync_operations")));
                db.close();
                db = {};
            }
            QSqlDatabase::removeDatabase(name);
        }
        OjNexus::SqliteStore store(path);
        QVERIFY(store.initialize().has_value());
        const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, QStringLiteral("001234")).value();
        QVERIFY(store.upsertAccount(account).has_value());
        const auto operation = store.openOperation(account, QStringLiteral("rowid-key-test"));
        QVERIFY(operation.has_value());
        const auto rows = store.recentOperations(OjNexus::JudgeId::Codeforces, 1);
        QCOMPARE(rows.size(), 1);
        QCOMPARE(rows.first().id, operation.value());
        QCOMPARE(rows.first().account.handle, QStringLiteral("001234"));
        QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success, std::nullopt).has_value());
        QCOMPARE(store.recentOperations(OjNexus::JudgeId::Codeforces, 1).first().status, OjNexus::SyncOperationStatus::Success);
    }
}

void SqliteStoreTest::acceptsNonNullLiteralDefaultsForExtraRequiredColumns() {
    for (const auto& declaration : {QStringLiteral("TEXT NOT NULL DEFAULT 'local'"), QStringLiteral("INTEGER NOT NULL DEFAULT 7")}) {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        const auto path = dir.filePath(QStringLiteral("extra-default.db"));
        { OjNexus::SqliteStore initial(path); QVERIFY(initial.initialize().has_value()); }
        const auto name = QStringLiteral("extra-default-fixture");
        {
            auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
            db.setDatabaseName(path);
            QVERIFY(db.open());
            QSqlQuery query(db);
            QVERIFY2(query.exec(QStringLiteral("ALTER TABLE accounts ADD COLUMN extra_required %1").arg(declaration)), qPrintable(query.lastError().text()));
            db.close();
            db = {};
        }
        QSqlDatabase::removeDatabase(name);
        OjNexus::SqliteStore store(path);
        QVERIFY(store.initialize().has_value());
        const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::AtCoder, QStringLiteral("leading-zero-001")).value();
        QVERIFY(store.upsertAccount(account).has_value());
        QCOMPARE(store.accounts().first().handle, QStringLiteral("leading-zero-001"));
    }
}

void SqliteStoreTest::rollsBackPartialInitialSchema() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("collision.db"));
    const auto name = QStringLiteral("schema-rollback-fixture");
    {
        auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
        db.setDatabaseName(path);
        QVERIFY(db.open());
        QSqlQuery q(db);
        QVERIFY(q.exec(QStringLiteral("CREATE TABLE profiles(marker TEXT NOT NULL)")));
        QVERIFY(q.exec(QStringLiteral("INSERT INTO profiles VALUES ('preserve-me')")));
        QVERIFY(!OjNexus::StorageSchema::createInitialSchema(db).has_value());
        QVERIFY(q.exec(QStringLiteral("SELECT marker FROM profiles")));
        QVERIFY(q.next());
        QCOMPARE(q.value(0).toString(), QStringLiteral("preserve-me"));
        QVERIFY(q.exec(QStringLiteral("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('schema_metadata','accounts','sync_operations','sync_modules')")));
        QVERIFY(!q.next());
        db.close();
        db = {};
    }
    QSqlDatabase::removeDatabase(name);
}

void SqliteStoreTest::firstUseDoesNotOverwriteCompetitor() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("reserved.db"));
    QFile competitor(path);
    QVERIFY(competitor.open(QIODevice::WriteOnly | QIODevice::NewOnly));
    const QByteArray marker("competitor-owned");
    QCOMPARE(competitor.write(marker), marker.size());
    competitor.close();
    OjNexus::SqliteStore losingInitializer(path);
    const auto result = losingInitializer.initialize();
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::Storage);
    QCOMPARE(losingInitializer.schemaVersion(), 0);
    QFile preserved(path);
    QVERIFY(preserved.open(QIODevice::ReadOnly));
    QCOMPARE(preserved.readAll(), marker);
}

void SqliteStoreTest::opensValidWalStoreWithLiveConnection() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto path = dir.filePath(QStringLiteral("live-wal.db"));
    OjNexus::SqliteStore initial(path);
    QVERIFY(initial.initialize().has_value());
    const auto name = QStringLiteral("live-wal-writer");
    {
        auto db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
        db.setDatabaseName(path);
        QVERIFY(db.open());
        QSqlQuery q(db);
        QVERIFY(q.exec(QStringLiteral("PRAGMA journal_mode=WAL")));
        QVERIFY(q.next());
        QCOMPARE(q.value(0).toString().toLower(), QStringLiteral("wal"));
        q.finish();
        QVERIFY(q.exec(QStringLiteral("INSERT INTO accounts(judge, handle, enabled, updated_at) VALUES ('codeforces', 'wal-user', 1, 0)")));
        q.finish();
        const QFileInfo wal(path + QStringLiteral("-wal"));
        const QFileInfo shm(path + QStringLiteral("-shm"));
        qInfo() << "WAL sidecars observed" << wal.exists() << shm.exists();
        OjNexus::SqliteStore concurrent(path);
        const auto initialized = concurrent.initialize();
        QVERIFY2(initialized.has_value(), "valid WAL store failed read-only preflight or writable reopen");
        QCOMPARE(concurrent.schemaVersion(), 2);
        QCOMPARE(concurrent.accounts().size(), 1);
        db.close();
        db = {};
    }
    QSqlDatabase::removeDatabase(name);
}

void SqliteStoreTest::createsSchemaAndStoresAccount() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 2);

    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "  demo  ");
    QVERIFY(account.has_value());
    QVERIFY(store.upsertAccount(account.value()).has_value());
    const auto accounts = store.accounts();
    QCOMPARE(accounts.size(), 1);
    QCOMPARE(accounts.first().handle, QStringLiteral("demo"));
}

void SqliteStoreTest::recordsModulesOnlyBeforeClosingOperation() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::AtCoder, "alice").value();
    QVERIFY(store.upsertAccount(account).has_value());
    const auto operation = store.openOperation(account, "generation-1");
    QVERIFY(operation.has_value());
    QVERIFY(store.appendModule(operation.value(), {"SUBMISSIONS", OjNexus::SyncOperationStatus::Success, 3, 3, 0, {}}).has_value());
    auto rows = store.recentOperations(std::nullopt, 10);
    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().status, OjNexus::SyncOperationStatus::Running);
    QCOMPARE(rows.first().modules.size(), 1);
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success, std::nullopt).has_value());
    rows = store.recentOperations(std::nullopt, 10);
    QCOMPARE(rows.first().status, OjNexus::SyncOperationStatus::Success);
}

void SqliteStoreTest::persistsOperationErrorForHistory() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:2").value();
    QVERIFY(store.upsertAccount(account).has_value());
    const auto operation = store.openOperation(account, "authentication-test");
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Error,
                                 OjNexus::SyncError::Authentication).has_value());

    const auto rows = store.recentOperations(std::nullopt, 1);
    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().error,
             std::optional<OjNexus::SyncError>(OjNexus::SyncError::Authentication));
}

void SqliteStoreTest::limitsCompletedHistoryWithoutDeletingRunningRows() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"), 2);
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:2").value();
    QVERIFY(store.upsertAccount(account).has_value());
    for (int i = 0; i < 3; ++i) {
        const auto operation = store.openOperation(account, QString::number(i));
        QVERIFY(operation.has_value());
        QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success, std::nullopt).has_value());
    }
    const auto running = store.openOperation(account, "running");
    QVERIFY(running.has_value());
    const auto rows = store.recentOperations(OjNexus::JudgeId::Luogu, 10);
    QCOMPARE(rows.size(), 3);
    QCOMPARE(rows.first().status, OjNexus::SyncOperationStatus::Running);
}

void SqliteStoreTest::recoversRunningOperationsOnReopen() {
    QTemporaryDir dir;
    const auto databasePath = dir.filePath("state.db");
    {
        OjNexus::SqliteStore store(databasePath);
        QVERIFY(store.initialize().has_value());
        const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
        QVERIFY(store.upsertAccount(account).has_value());
        QVERIFY(store.openOperation(account, "generation-1").has_value());
        QVERIFY(store.openOperation(account, "generation-2").has_value());
    }

    {
        const auto connectionName = QStringLiteral("stale-operation-fixture");
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QFile bootIdFile(QStringLiteral("/proc/sys/kernel/random/boot_id"));
        QVERIFY(bootIdFile.open(QIODevice::ReadOnly | QIODevice::Text));
        const QString bootId = QString::fromLatin1(bootIdFile.readAll()).trimmed();
        QVERIFY(!bootId.isEmpty());
        QSqlQuery query(database);
        query.prepare(QStringLiteral("UPDATE sync_operations SET owner_pid=?, owner_boot_id=?, owner_start_ticks=1 WHERE data_generation=?"));
        query.addBindValue(static_cast<qlonglong>(getpid()));
        query.addBindValue(bootId);
        query.addBindValue(QStringLiteral("generation-1"));
        QVERIFY(query.exec());
        query.prepare(QStringLiteral("UPDATE sync_operations SET owner_pid=?, owner_boot_id=?, owner_start_ticks=1 WHERE data_generation=?"));
        query.addBindValue(2147483647);
        query.addBindValue(bootId);
        query.addBindValue(QStringLiteral("generation-2"));
        QVERIFY(query.exec());
        database.close();
        database = {};
        QSqlDatabase::removeDatabase(connectionName);
    }

    OjNexus::SqliteStore reopened(databasePath);
    QVERIFY(reopened.initialize().has_value());
    const auto rows = reopened.recentOperations(std::nullopt, 10);
    QCOMPARE(rows.size(), 2);
    for (const auto& row : rows) {
        QCOMPARE(row.status, OjNexus::SyncOperationStatus::Cancelled);
        QVERIFY(row.finishedAt.has_value());
    }
}

void SqliteStoreTest::keepsLiveOperationRunningAcrossRefreshInitialization() {
    QTemporaryDir dir;
    const auto databasePath = dir.filePath("state.db");
    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(account).has_value());
    QVERIFY(store.openOperation(account, QStringLiteral("active-worker")).has_value());
    QVERIFY(store.initialize().has_value());
    const auto rows = store.recentOperations(std::nullopt, 10);
    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().status, OjNexus::SyncOperationStatus::Running);
}

void SqliteStoreTest::migratesLegacyCacheWithoutDroppingOrGuessingOwners() {
    QTemporaryDir dir;
    const auto databasePath = dir.filePath("legacy.db");
    const auto name = QStringLiteral("legacy-v1-fixture");
    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), name);
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery query(database);
        const QStringList schema{
            QStringLiteral("CREATE TABLE schema_metadata(version INTEGER NOT NULL)"),
            QStringLiteral("INSERT INTO schema_metadata VALUES(1)"),
            QStringLiteral("CREATE TABLE accounts(judge TEXT PRIMARY KEY, handle TEXT NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL)"),
            QStringLiteral("CREATE TABLE sync_operations(id INTEGER PRIMARY KEY AUTOINCREMENT, judge TEXT NOT NULL, handle TEXT NOT NULL, data_generation TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, error_type TEXT NOT NULL DEFAULT '')"),
            QStringLiteral("CREATE TABLE sync_modules(operation_id INTEGER NOT NULL, stage TEXT NOT NULL, status TEXT NOT NULL, attempted_count INTEGER NOT NULL, imported_count INTEGER NOT NULL, updated_count INTEGER NOT NULL, failure_type TEXT NOT NULL DEFAULT '', PRIMARY KEY(operation_id, stage))"),
            QStringLiteral("CREATE INDEX sync_operations_judge_started ON sync_operations(judge, started_at DESC)"),
            QStringLiteral("CREATE TABLE profiles(judge TEXT PRIMARY KEY, handle TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)"),
            QStringLiteral("CREATE TABLE rating_changes(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("CREATE TABLE submissions(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("CREATE TABLE problems(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("CREATE TABLE contests(judge TEXT NOT NULL, external_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(judge, external_id))"),
            QStringLiteral("INSERT INTO profiles VALUES('codeforces','tourist','{\"rating\":3800}',1)"),
            QStringLiteral("INSERT INTO rating_changes VALUES('codeforces','1','{\"contestId\":1}',1)"),
            QStringLiteral("INSERT INTO submissions VALUES('codeforces','2','{\"id\":2}',1)"),
            QStringLiteral("INSERT INTO problems VALUES('codeforces','1A','{\"id\":\"1A\"}',1)"),
            QStringLiteral("INSERT INTO contests VALUES('codeforces','3','{\"id\":3}',1)"),
            QStringLiteral("INSERT INTO sync_operations(judge, handle, data_generation, status, started_at) VALUES('codeforces','tourist','legacy-running','RUNNING',2)"),
        };
        for (const auto& sql : schema) QVERIFY2(query.exec(sql), qPrintable(query.lastError().text()));
        database.close();
    }
    QSqlDatabase::removeDatabase(name);

    OjNexus::SqliteStore migrated(databasePath);
    QVERIFY(migrated.initialize().has_value());
    QCOMPARE(migrated.schemaVersion(), 2);
    const auto profile = migrated.latestProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"));
    QVERIFY(profile.has_value());
    QCOMPARE(profile->payload.value(QStringLiteral("rating")).toInt(), 3800);
    QCOMPARE(migrated.ratingChangeCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(migrated.ratingChangeCount(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")), 0);
    QCOMPARE(migrated.submissionCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(migrated.submissionCount(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")), 0);
    QCOMPARE(migrated.problemCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(migrated.contestCount(OjNexus::JudgeId::Codeforces), 1);
    const auto operations = migrated.recentOperations(OjNexus::JudgeId::Codeforces, 10);
    QCOMPARE(operations.size(), 1);
    QCOMPARE(operations.first().status, OjNexus::SyncOperationStatus::Running);
}

void SqliteStoreTest::storesAndReadsLatestProfile() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const QJsonObject payload{{QStringLiteral("handle"), QStringLiteral("tourist")},
                              {QStringLiteral("rating"), 3800}};

    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"), payload).has_value());

    const auto profile = store.latestProfile(OjNexus::JudgeId::Codeforces);
    QVERIFY(profile.has_value());
    QCOMPARE(profile->handle, QStringLiteral("tourist"));
    QCOMPARE(profile->payload.value(QStringLiteral("rating")).toInt(), 3800);
    QVERIFY(profile->updatedAt.isValid());
    QVERIFY(!store.latestProfile(OjNexus::JudgeId::AtCoder).has_value());
}

void SqliteStoreTest::storesAndReadsRecentSubmissionProjection() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());

    QVERIFY(store.upsertSubmission(
        OjNexus::JudgeId::Codeforces,
        QJsonObject{
            {QStringLiteral("id"), 10},
            {QStringLiteral("creationTimeSeconds"), 1700000100},
            {QStringLiteral("verdict"), QStringLiteral("OK")},
            {QStringLiteral("programmingLanguage"), QStringLiteral("GNU C++20")},
            {QStringLiteral("problem"), QJsonObject{
                {QStringLiteral("contestId"), 1},
                {QStringLiteral("index"), QStringLiteral("B")},
                {QStringLiteral("name"), QStringLiteral("NEWER")},
            }},
        }).has_value());
    QVERIFY(store.upsertSubmission(
        OjNexus::JudgeId::Codeforces,
        QJsonObject{
            {QStringLiteral("id"), 9},
            {QStringLiteral("creationTimeSeconds"), 1700000000},
            {QStringLiteral("verdict"), QStringLiteral("OK")},
            {QStringLiteral("programmingLanguage"), QStringLiteral("GNU C++20")},
            {QStringLiteral("problem"), QJsonObject{
                {QStringLiteral("contestId"), 1},
                {QStringLiteral("index"), QStringLiteral("A")},
                {QStringLiteral("name"), QStringLiteral("OLDER")},
            }},
        }).has_value());

    const auto rows = store.recentSubmissions(OjNexus::JudgeId::Codeforces, 1);

    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().externalId, QStringLiteral("10"));
    QCOMPARE(rows.first().problemId, QStringLiteral("1B"));
    QCOMPARE(rows.first().problemName, QStringLiteral("NEWER"));
    QCOMPARE(rows.first().verdict, QStringLiteral("OK"));
    QCOMPARE(rows.first().language, QStringLiteral("GNU C++20"));
    QCOMPARE(rows.first().submittedAt,
             QDateTime::fromSecsSinceEpoch(1700000100, Qt::UTC));

    QVERIFY(store.upsertSubmission(
        OjNexus::JudgeId::AtCoder,
        QJsonObject{
            {QStringLiteral("id"), 7},
            {QStringLiteral("epoch_second"), 1700000001},
            {QStringLiteral("result"), QStringLiteral("AC")},
            {QStringLiteral("language"), QStringLiteral("C++")},
            {QStringLiteral("problem_id"), QStringLiteral("abc001_a")},
        }).has_value());
    const auto atcoderRows = store.recentSubmissions(OjNexus::JudgeId::AtCoder, 1);

    QCOMPARE(atcoderRows.size(), 1);
    QCOMPARE(atcoderRows.first().problemId, QStringLiteral("abc001_a"));
    QCOMPARE(atcoderRows.first().verdict, QStringLiteral("AC"));
    QCOMPARE(atcoderRows.first().language, QStringLiteral("C++"));
}

void SqliteStoreTest::rejectsUnsafeProfileHandleAtPersistenceBoundary() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());

    const auto result = store.upsertProfile(
        OjNexus::JudgeId::Codeforces,
        QStringLiteral("tourist&handles=other"),
        QJsonObject{{QStringLiteral("rating"), 3800}});

    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::InvalidConfiguration);
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);
}

void SqliteStoreTest::rejectsUnsafeAccountsAtPersistenceBoundaries() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const OjNexus::JudgeAccount invalidAccount{
        OjNexus::JudgeId::Codeforces, QStringLiteral("tourist&handles=other"), true};

    QVERIFY(!store.upsertAccount(invalidAccount).has_value());
    QVERIFY(!store.configureAccount(invalidAccount).has_value());
    QVERIFY(!store.openOperation(invalidAccount, QStringLiteral("invalid-account-test")).has_value());

    QCOMPARE(store.accounts().size(), 0);
    QCOMPARE(store.recentOperations(std::nullopt, 1).size(), 0);
}

void SqliteStoreTest::ignoresUnsafePersistedAccountRows() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath("state.db");
    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());

    const auto connectionName = QStringLiteral("invalid-account-row-test");
    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery query(database);
        QVERIFY(query.exec(QStringLiteral(
            "INSERT INTO accounts(judge, handle, enabled, updated_at) "
            "VALUES ('codeforces', 'tourist&handles=other', 1, 0)")));
        database.close();
    }
    QSqlDatabase::removeDatabase(connectionName);

    QVERIFY(store.accounts().isEmpty());
}

void SqliteStoreTest::ignoresUnsafePersistedOperationRows() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath("state.db");
    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());

    const auto connectionName = QStringLiteral("invalid-operation-row-test");
    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery query(database);
        QVERIFY(query.exec(QStringLiteral(
            "INSERT INTO sync_operations(judge, handle, data_generation, status, started_at) "
            "VALUES ('codeforces', 'tourist&handles=other', 'test', 'ERROR', 0)")));
        database.close();
    }
    QSqlDatabase::removeDatabase(connectionName);

    QVERIFY(store.recentOperations(std::nullopt, 10).isEmpty());
}

void SqliteStoreTest::ignoresUnsafePersistedProfileRows() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    const auto databasePath = dir.filePath("state.db");
    OjNexus::SqliteStore store(databasePath);
    QVERIFY(store.initialize().has_value());

    const auto connectionName = QStringLiteral("invalid-profile-row-test");
    {
        auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
        database.setDatabaseName(databasePath);
        QVERIFY(database.open());
        QSqlQuery query(database);
        QVERIFY(query.exec(QStringLiteral(
            "INSERT INTO profiles(scope, judge, handle, payload, updated_at) "
            "VALUES ('ACCOUNT', 'codeforces', 'tourist&handles=other', '{}', 0)")));
        database.close();
    }
    QSqlDatabase::removeDatabase(connectionName);

    QVERIFY(!store.latestProfile(OjNexus::JudgeId::Codeforces).has_value());
}

void SqliteStoreTest::supportsAtomicPayloadTransactions() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());

    QVERIFY(store.beginTransaction().has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.rollbackTransaction().has_value());
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);

    QVERIFY(store.beginTransaction().has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.commitTransaction().has_value());
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 1);
}

void SqliteStoreTest::updatesAndRemovesAccounts() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    QVERIFY(store.upsertAccount(account).has_value());
    const auto operation = store.openOperation(account, QStringLiteral("lifecycle-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success, std::nullopt).has_value());

    QVERIFY(store.setAccountEnabled(OjNexus::JudgeId::Codeforces, false).has_value());
    QCOMPARE(store.accounts().first().enabled, false);
    QVERIFY(store.removeAccount(OjNexus::JudgeId::Codeforces).has_value());
    QVERIFY(store.accounts().isEmpty());
    QCOMPARE(store.recentOperations(std::nullopt, 10).size(), 1);
}

void SqliteStoreTest::removesProfileSnapshot() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.removeProfile(OjNexus::JudgeId::Codeforces).has_value());
    QVERIFY(!store.latestProfile(OjNexus::JudgeId::Codeforces).has_value());
}

void SqliteStoreTest::changingHandleRetainsCachesButScopesPersonalRows() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto oldAccount = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "old-user").value();
    const auto newAccount = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "new-user").value();
    QVERIFY(store.upsertAccount(oldAccount).has_value());
    const auto operation = store.openOperation(oldAccount, QStringLiteral("handle-change-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success, std::nullopt).has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, oldAccount.handle,
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Codeforces, oldAccount.handle,
                                     QJsonObject{{QStringLiteral("contestId"), 1}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Codeforces, oldAccount.handle,
                                   QJsonObject{{QStringLiteral("id"), 2}}).has_value());
    QVERIFY(store.upsertProblem(OjNexus::JudgeId::Codeforces,
                                QJsonObject{{QStringLiteral("externalId"), QStringLiteral("1A")}}).has_value());
    QVERIFY(store.upsertContest(OjNexus::JudgeId::Codeforces,
                                QJsonObject{{QStringLiteral("id"), 3}}).has_value());

    QVERIFY(store.configureAccount(newAccount).has_value());

    QCOMPARE(store.accounts().first().handle, QStringLiteral("new-user"));
    QVERIFY(!store.latestProfile(OjNexus::JudgeId::Codeforces, newAccount.handle).has_value());
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces, newAccount.handle), 0);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces, newAccount.handle), 0);
    QVERIFY(store.latestProfile(OjNexus::JudgeId::Codeforces, oldAccount.handle).has_value());
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces, oldAccount.handle), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces, oldAccount.handle), 1);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.recentOperations(std::nullopt, 10).size(), 1);
}

void SqliteStoreTest::preservesCachedDataWhenEquivalentLuoguHandleChanges() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto oldAccount = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:2").value();
    const auto equivalentAccount = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "2").value();
    QVERIFY(store.upsertAccount(oldAccount).has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Luogu, oldAccount.handle,
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Luogu,
                                     QJsonObject{{QStringLiteral("contestId"), 1}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Luogu,
                                   QJsonObject{{QStringLiteral("id"), 2}}).has_value());
    QVERIFY(store.upsertProblem(OjNexus::JudgeId::Luogu,
                                QJsonObject{{QStringLiteral("externalId"), QStringLiteral("P1001")}}).has_value());
    QVERIFY(store.upsertContest(OjNexus::JudgeId::Luogu,
                                QJsonObject{{QStringLiteral("id"), 3}}).has_value());

    QVERIFY(store.configureAccount(equivalentAccount).has_value());

    QCOMPARE(store.accounts().first().handle, QStringLiteral("uid:2"));
    QVERIFY(store.latestProfile(OjNexus::JudgeId::Luogu).has_value());
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Luogu), 1);
}

void SqliteStoreTest::keepsOneShotHandleCacheSeparateFromConfiguredAccount() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto configured = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "account-a").value();
    QVERIFY(store.configureAccount(configured).has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, configured.handle,
                                QJsonObject{{QStringLiteral("handle"), configured.handle},
                                            {QStringLiteral("rating"), 1200}}).has_value());
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Codeforces, configured.handle,
                                     QJsonObject{{QStringLiteral("contestId"), 10}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Codeforces, configured.handle,
                                   QJsonObject{{QStringLiteral("id"), 11}}).has_value());

    const auto oneShot = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "account-b").value();
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, oneShot.handle,
                                QJsonObject{{QStringLiteral("handle"), oneShot.handle},
                                            {QStringLiteral("rating"), 2400}}).has_value());
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Codeforces, oneShot.handle,
                                     QJsonObject{{QStringLiteral("contestId"), 20}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Codeforces, oneShot.handle,
                                   QJsonObject{{QStringLiteral("id"), 21}}).has_value());

    QCOMPARE(store.accounts().first().handle, QStringLiteral("account-a"));
    const auto configuredProfile = store.latestProfile(OjNexus::JudgeId::Codeforces);
    QVERIFY(configuredProfile.has_value());
    QCOMPARE(configuredProfile->handle, QStringLiteral("account-a"));
    QCOMPARE(configuredProfile->payload.value(QStringLiteral("rating")).toInt(), 1200);
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces, configured.handle), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces, configured.handle), 1);
    QCOMPARE(store.latestProfile(OjNexus::JudgeId::Codeforces, oneShot.handle)->payload.value(QStringLiteral("rating")).toInt(), 2400);
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces, oneShot.handle), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces, oneShot.handle), 1);
    QVERIFY(store.removeAccount(OjNexus::JudgeId::Codeforces).has_value());
    QVERIFY(store.latestProfile(OjNexus::JudgeId::Codeforces, configured.handle).has_value());
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces, configured.handle), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces, configured.handle), 1);
    QVERIFY(store.configureAccount(oneShot).has_value());
    QCOMPARE(store.latestProfile(OjNexus::JudgeId::Codeforces)->handle, QStringLiteral("account-b"));
}
void SqliteStoreTest::preservesDisabledStateWhenEquivalentHandleChanges() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto disabled = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist", false).value();
    const auto equivalent = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, " Tourist ").value();
    QVERIFY(store.upsertAccount(disabled).has_value());

    QVERIFY(store.configureAccount(equivalent).has_value());

    QCOMPARE(store.accounts().first().handle, QStringLiteral("tourist"));
    QCOMPARE(store.accounts().first().enabled, false);
}

QTEST_MAIN(SqliteStoreTest)
#include "SqliteStoreTest.moc"
