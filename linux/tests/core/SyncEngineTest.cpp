#include <QtTest/QtTest>

#include <QSqlDatabase>
#include <QSqlQuery>
#include <QHash>
#include <QTemporaryDir>

#include "ojnexus/core/sync_engine.h"

class EngineTransport final : public OjNexus::IHttpTransport {
public:
    QHash<QString, QByteArray> responses;
    std::function<void()> beforeFirstRequest;
    int requestCount = 0;
    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl& url, const QMap<QByteArray, QByteArray>&, int, std::stop_token) override {
        ++requestCount;
        if (requestCount == 1 && beforeFirstRequest) beforeFirstRequest();
        const auto body = responses.value(url.toString());
        return body.isEmpty() ? OjNexus::Result<OjNexus::HttpResponse>(OjNexus::SyncError::Network)
                              : OjNexus::Result<OjNexus::HttpResponse>(OjNexus::HttpResponse{200, body, {}});
    }
};

class CancellationTransport final : public OjNexus::IHttpTransport {
public:
    explicit CancellationTransport(std::stop_source& source) : source_(source) {}

    int requestCount = 0;

    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl&, const QMap<QByteArray, QByteArray>&,
                                                int, std::stop_token) override {
        ++requestCount;
        source_.request_stop();
        return OjNexus::Result<OjNexus::HttpResponse>(OjNexus::SyncError::Cancelled);
    }

private:
    std::stop_source& source_;
};

class OfflineTransport final : public OjNexus::IHttpTransport {
public:
    int requestCount = 0;

    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl&, const QMap<QByteArray, QByteArray>&,
                                                int, std::stop_token) override {
        ++requestCount;
        return OjNexus::Result<OjNexus::HttpResponse>(OjNexus::SyncError::Offline);
    }
};

class AuthenticationTransport final : public OjNexus::IHttpTransport {
public:
    int requestCount = 0;

    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl&, const QMap<QByteArray, QByteArray>&,
                                                int, std::stop_token) override {
        ++requestCount;
        return OjNexus::HttpResponse{401, QByteArrayLiteral("authentication required"), {}};
    }
};

class InvalidProfilePersistenceTransport final : public OjNexus::IHttpTransport {
public:
    int requestCount = 0;

    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl&, const QMap<QByteArray, QByteArray>&,
                                                int, std::stop_token) override {
        ++requestCount;
        if (requestCount == 1) {
            return OjNexus::HttpResponse{
                200,
                QByteArrayLiteral(R"({"status":"OK","result":[{"handle":"tourist&handles=other"}]})"),
                {}};
        }
        return OjNexus::SyncError::Network;
    }
};

bool dropSyncModulesTable(const QString& path) {
    const QString connectionName = QStringLiteral("sync-engine-failure-injection");
    auto database = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connectionName);
    database.setDatabaseName(path);
    if (!database.open()) return false;
    QSqlQuery query(database);
    const bool dropped = query.exec(QStringLiteral("DROP TABLE sync_modules"));
    database.close();
    database = {};
    QSqlDatabase::removeDatabase(connectionName);
    return dropped;
}

class SyncEngineTest final : public QObject {
    Q_OBJECT

private slots:
    void closesSuccessfulOperationAndReturnsReport();
    void reportsStorageFailureWhenModuleLedgerCannotBeWritten();
    void recordsCancelledModuleWhenTransportStopsSync();
    void rollsBackCurrentStageWhenCancellationArrivesBeforeCommit();
    void reportsOfflineOperationWhenAllModulesAreOffline();
    void preservesAuthenticationErrorInSyncReport();
    void preservesProfilePersistenceValidationError();
};

void SyncEngineTest::closesSuccessfulOperationAndReturnsReport() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    EngineTransport transport;
    transport.beforeFirstRequest = [] { QThread::msleep(40); };
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist", R"({"status":"OK","result":[{"handle":"tourist"}]})");
    transport.responses.insert("https://codeforces.com/api/user.rating?handle=tourist", R"({"status":"OK","result":[]})");
    transport.responses.insert("https://codeforces.com/api/user.status?handle=tourist&from=1&count=1000", R"({"status":"OK","result":[]})");
    transport.responses.insert("https://codeforces.com/api/contest.list?gym=false", R"({"status":"OK","result":[]})");
    transport.responses.insert("https://codeforces.com/api/problemset.problems", R"({"status":"OK","result":{"problems":[],"problemStatistics":[]}})");
    OjNexus::SyncEngine engine(store, transport, 0);
    const auto report = engine.sync(account, "test", {}, {});
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Success);
    QCOMPARE(report.operation.modules.size(), 5);
    QVERIFY(report.operation.finishedAt.has_value());
    QVERIFY(report.operation.startedAt.msecsTo(report.operation.finishedAt.value()) >= 30);
    QCOMPARE(store.recentOperations(std::nullopt, 1).size(), 1);
    QCOMPARE(store.recentOperations(std::nullopt, 1).first().status, OjNexus::SyncOperationStatus::Success);
}

void SyncEngineTest::reportsStorageFailureWhenModuleLedgerCannotBeWritten() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    EngineTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist", R"({"status":"OK","result":[{"handle":"tourist"}]})");
    bool dropped = false;
    transport.beforeFirstRequest = [&] { dropped = dropSyncModulesTable(dir.filePath("state.db")); };

    OjNexus::SyncEngine engine(store, transport, 0);
    const auto report = engine.sync(account, "test", {}, {});

    QVERIFY(dropped);
    QCOMPARE(transport.requestCount, 1);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(report.error, std::optional<OjNexus::SyncError>(OjNexus::SyncError::Storage));
    QCOMPARE(report.operation.modules.first().failureType, QStringLiteral("STORAGE"));
    QCOMPARE(store.recentOperations(std::nullopt, 1).first().status, OjNexus::SyncOperationStatus::Error);
}

void SyncEngineTest::recordsCancelledModuleWhenTransportStopsSync() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    std::stop_source source;
    CancellationTransport transport(source);
    OjNexus::SyncEngine engine(store, transport, 0);

    const auto report = engine.sync(account, "test", {}, source.get_token());

    QCOMPARE(transport.requestCount, 1);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Cancelled);
    QCOMPARE(report.error, std::optional<OjNexus::SyncError>(OjNexus::SyncError::Cancelled));
    QCOMPARE(report.operation.modules.first().status, OjNexus::SyncOperationStatus::Cancelled);
    QCOMPARE(report.operation.modules.first().failureType, QStringLiteral("CANCELLED"));
}

void SyncEngineTest::rollsBackCurrentStageWhenCancellationArrivesBeforeCommit() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    std::stop_source source;
    EngineTransport transport;
    transport.beforeFirstRequest = [&source] { source.request_stop(); };
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist",
                               R"({"status":"OK","result":[{"handle":"tourist"}]})");

    OjNexus::SyncEngine engine(store, transport, 0);
    const auto report = engine.sync(account, "test", {}, source.get_token());

    QCOMPARE(transport.requestCount, 1);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Cancelled);
    QCOMPARE(report.operation.modules.first().status, OjNexus::SyncOperationStatus::Cancelled);
    QCOMPARE(report.operation.modules.first().failureType, QStringLiteral("CANCELLED"));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);
}

void SyncEngineTest::reportsOfflineOperationWhenAllModulesAreOffline() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    OfflineTransport transport;
    OjNexus::SyncEngine engine(store, transport, 0);

    const auto report = engine.sync(account, "test", {}, {});

    QCOMPARE(transport.requestCount, 5);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Offline);
    QCOMPARE(report.error, std::optional<OjNexus::SyncError>(OjNexus::SyncError::Offline));
    QCOMPARE(report.operation.modules.first().status, OjNexus::SyncOperationStatus::Offline);
    QCOMPARE(report.operation.modules.first().failureType, QStringLiteral("OFFLINE"));
}

void SyncEngineTest::preservesAuthenticationErrorInSyncReport() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:2").value();
    AuthenticationTransport transport;
    OjNexus::SyncEngine engine(store, transport, 0);

    const auto report = engine.sync(account, "test", {}, {});

    QCOMPARE(transport.requestCount, 4);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(report.error, std::optional<OjNexus::SyncError>(OjNexus::SyncError::Authentication));
    QCOMPARE(report.operation.modules.first().failureType, QStringLiteral("AUTHENTICATION"));
}

void SyncEngineTest::preservesProfilePersistenceValidationError() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const OjNexus::JudgeAccount invalidAccount{
        OjNexus::JudgeId::Codeforces, QStringLiteral("tourist&handles=other"), true};
    InvalidProfilePersistenceTransport transport;
    OjNexus::SyncEngine engine(store, transport, 0);

    const auto report = engine.sync(invalidAccount, "test", {}, {});

    QCOMPARE(transport.requestCount, 0);
    QCOMPARE(report.operation.status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(report.error, std::optional<OjNexus::SyncError>(OjNexus::SyncError::InvalidConfiguration));
    QVERIFY(report.operation.modules.isEmpty());
    QCOMPARE(store.recentOperations(std::nullopt, 1).size(), 0);
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);
}

QTEST_MAIN(SyncEngineTest)
#include "SyncEngineTest.moc"
