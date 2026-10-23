#include <QtTest/QtTest>

#include <QSqlDatabase>
#include <QSqlQuery>
#include <QTemporaryDir>

#include "ojnexus/core/store.h"

class SqliteStoreTest final : public QObject {
    Q_OBJECT

private slots:
    void createsSchemaAndStoresAccount();
    void recordsModulesOnlyBeforeClosingOperation();
    void persistsOperationErrorForHistory();
    void limitsCompletedHistoryWithoutDeletingRunningRows();
    void recoversRunningOperationsOnReopen();
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
    void changingHandleClearsAllCachedJudgeDataButKeepsHistory();
    void preservesCachedDataWhenEquivalentLuoguHandleChanges();
    void preservesDisabledStateWhenEquivalentHandleChanges();
};

void SqliteStoreTest::createsSchemaAndStoresAccount() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    QCOMPARE(store.schemaVersion(), 1);

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
    }

    OjNexus::SqliteStore reopened(databasePath);
    QVERIFY(reopened.initialize().has_value());
    const auto rows = reopened.recentOperations(std::nullopt, 10);
    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().status, OjNexus::SyncOperationStatus::Cancelled);
    QVERIFY(rows.first().finishedAt.has_value());
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
            "INSERT INTO profiles(judge, handle, payload, updated_at) "
            "VALUES ('codeforces', 'tourist&handles=other', '{}', 0)")));
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

void SqliteStoreTest::changingHandleClearsAllCachedJudgeDataButKeepsHistory() {
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
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Codeforces,
                                     QJsonObject{{QStringLiteral("contestId"), 1}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Codeforces,
                                   QJsonObject{{QStringLiteral("id"), 2}}).has_value());
    QVERIFY(store.upsertProblem(OjNexus::JudgeId::Codeforces,
                                QJsonObject{{QStringLiteral("externalId"), QStringLiteral("1A")}}).has_value());
    QVERIFY(store.upsertContest(OjNexus::JudgeId::Codeforces,
                                QJsonObject{{QStringLiteral("id"), 3}}).has_value());

    QVERIFY(store.configureAccount(newAccount).has_value());

    QCOMPARE(store.accounts().first().handle, QStringLiteral("new-user"));
    QVERIFY(!store.latestProfile(OjNexus::JudgeId::Codeforces).has_value());
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces), 0);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces), 0);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::Codeforces), 0);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Codeforces), 0);
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
