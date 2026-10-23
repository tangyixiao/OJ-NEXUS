#include <QtTest/QtTest>

#include <QHash>
#include <QHostAddress>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTemporaryDir>

#include "ojnexus/core/adapter.h"
#include "ojnexus/core/http_transport.h"
#include "ojnexus/core/store.h"

namespace {
class FakeTransport final : public OjNexus::IHttpTransport {
public:
    QHash<QString, QByteArray> responses;
    QList<QString> requestedUrls;

    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl& url, const QMap<QByteArray, QByteArray>&, int, std::stop_token) override {
        requestedUrls.push_back(url.toString());
        const auto body = responses.value(url.toString());
        if (body.isEmpty()) return OjNexus::SyncError::Network;
        return OjNexus::HttpResponse{200, body, {}};
    }
};

class UnauthorizedTransport final : public OjNexus::IHttpTransport {
public:
    OjNexus::Result<OjNexus::HttpResponse> get(const QUrl&, const QMap<QByteArray, QByteArray>&,
                                                int, std::stop_token) override {
        return OjNexus::HttpResponse{401, QByteArrayLiteral("unauthorized"), {}};
    }
};
} // namespace

class AdapterTest final : public QObject {
    Q_OBJECT

private slots:
    void codeforcesSyncPersistsFiveStages();
    void rejectsMismatchedCodeforcesProfile();
    void rejectsCodeforcesProfileWithoutReturnedIdentity();
    void rejectsMismatchedAtCoderProfile();
    void atcoderProblemsSyncPersistsFiveStages();
    void luoguSyncPersistsAllStages();
    void rejectsMismatchedLuoguProfile();
    void rejectsSuccessfulEnvelopeWithoutArrayPayload();
    void rejectsLuoguContentWithoutExpectedList();
    void malformedPayloadBecomesTypedStageFailure();
    void classifiesUnavailableNetworkErrorsAsOffline();
    void classifiesUnauthorizedResponsesAsAuthentication();
    void preservesHttpStatusForUnauthorizedResponses();
    void configuresBoundedHttp1Request();
    void stopsJsonRowPersistenceWhenCancellationArrives();
};

void AdapterTest::codeforcesSyncPersistsFiveStages() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    QVERIFY(store.upsertAccount(account).has_value());
    const auto operation = store.openOperation(account, "test");
    QVERIFY(operation.has_value());

    FakeTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist", R"({"status":"OK","result":[{"handle":"tourist","rating":3800,"rank":"legendary","solvedCount":400}]} )");
    transport.responses.insert("https://codeforces.com/api/user.rating?handle=tourist", R"({"status":"OK","result":[{"contestId":1,"contestName":"TEST","oldRating":3500,"newRating":3800,"rank":1,"ratingUpdateTimeSeconds":1700000000}]})");
    transport.responses.insert("https://codeforces.com/api/user.status?handle=tourist&from=1&count=1000", R"({"status":"OK","result":[{"id":9,"contestId":1,"creationTimeSeconds":1700000000,"verdict":"OK","programmingLanguage":"GNU C++20","problem":{"contestId":1,"index":"A","name":"TEST"}}]})");
    transport.responses.insert("https://codeforces.com/api/contest.list?gym=false", R"({"status":"OK","result":[{"id":1,"name":"TEST","phase":"FINISHED","startTimeSeconds":1700000000,"durationSeconds":7200}]})");
    transport.responses.insert("https://codeforces.com/api/problemset.problems", R"({"status":"OK","result":{"problems":[{"contestId":1,"index":"A","name":"TEST","rating":800,"tags":["implementation"]}],"problemStatistics":[{"contestId":1,"index":"A","solvedCount":10}]}})");

    OjNexus::SyncContext context{store, transport, operation.value(), 0, {}};
    const auto outcomes = OjNexus::CodeforcesAdapter().sync(account, context, {});
    QCOMPARE(outcomes.size(), 5);
    for (const auto& outcome : outcomes) QVERIFY2(outcome.status == OjNexus::SyncOperationStatus::Success,
                                                  qPrintable(outcome.stage + QStringLiteral(": ") + outcome.failureType));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::Codeforces), 1);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Codeforces), 1);
}

void AdapterTest::rejectsMismatchedCodeforcesProfile() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    const auto operation = store.openOperation(account, "test");
    QVERIFY(operation.has_value());

    FakeTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist",
                                R"({"status":"OK","result":[{"handle":"other","rating":3800}]})");
    OjNexus::SyncContext context{store, transport, operation.value(), 0, {}};

    const auto outcomes = OjNexus::CodeforcesAdapter().sync(account, context, {});

    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("API"));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);
}

void AdapterTest::rejectsCodeforcesProfileWithoutReturnedIdentity() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    const auto operation = store.openOperation(account, "test");
    QVERIFY(operation.has_value());

    FakeTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist",
                                R"({"status":"OK","result":[{"rating":3800}]})");
    OjNexus::SyncContext context{store, transport, operation.value(), 0, {}};

    const auto outcomes = OjNexus::CodeforcesAdapter().sync(account, context, {});

    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("API"));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Codeforces), 0);
}

void AdapterTest::rejectsMismatchedAtCoderProfile() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::AtCoder, "tourist").value();
    const auto operation = store.openOperation(account, "test").value();

    FakeTransport transport;
    transport.responses.insert("https://atcoder.jp/users/tourist",
                                R"(<html><title>other - AtCoder</title></html>)");
    OjNexus::SyncContext context{store, transport, operation, 0, {}};

    const auto outcomes = OjNexus::AtCoderAdapter().sync(account, context, {});

    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("API"));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::AtCoder), 0);
}

void AdapterTest::malformedPayloadBecomesTypedStageFailure() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist", "not-json");
    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::CodeforcesAdapter().sync(account, context, {});
    QCOMPARE(outcomes.size(), 5);
    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("PARSE"));
}

void AdapterTest::classifiesUnavailableNetworkErrorsAsOffline() {
    QVERIFY(OjNexus::isOfflineNetworkError(QNetworkReply::HostNotFoundError));
    QVERIFY(OjNexus::isOfflineNetworkError(QNetworkReply::TemporaryNetworkFailureError));
    QVERIFY(OjNexus::isOfflineNetworkError(QNetworkReply::NetworkSessionFailedError));
    QVERIFY(OjNexus::isOfflineNetworkError(QNetworkReply::UnknownNetworkError));
    QVERIFY(!OjNexus::isOfflineNetworkError(QNetworkReply::TimeoutError));
    QVERIFY(!OjNexus::isOfflineNetworkError(QNetworkReply::SslHandshakeFailedError));
}

void AdapterTest::classifiesUnauthorizedResponsesAsAuthentication() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:2").value();
    const auto operation = store.openOperation(account, "test").value();
    UnauthorizedTransport transport;
    OjNexus::SyncContext context{store, transport, operation, 0, {}};

    const auto outcomes = OjNexus::LuoguAdapter().sync(account, context, {});

    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("AUTHENTICATION"));
}

void AdapterTest::preservesHttpStatusForUnauthorizedResponses() {
    QTcpServer server;
    QVERIFY(server.listen(QHostAddress::LocalHost));
    QObject::connect(&server, &QTcpServer::newConnection, &server, [&server] {
        auto* socket = server.nextPendingConnection();
        QObject::connect(socket, &QTcpSocket::disconnected, socket, &QObject::deleteLater);
        socket->write("HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\n"
                      "Content-Length: 2\r\nConnection: close\r\n\r\n{}");
        socket->disconnectFromHost();
    });

    const auto url = QUrl(QStringLiteral("http://127.0.0.1:%1/protected").arg(server.serverPort()));
    const auto response = OjNexus::QtHttpTransport().get(url, {}, 1000, {});

    QVERIFY(response.has_value());
    QCOMPARE(response->statusCode, 401);
    QCOMPARE(response->body, QByteArrayLiteral("{}"));
}

void AdapterTest::atcoderProblemsSyncPersistsFiveStages() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::AtCoder, "tourist").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://atcoder.jp/users/tourist",
                                R"(<html><title>tourist - AtCoder</title><script>var rank_history=[{"EndTime":1700000000,"Rank":1,"Rating":1800,"Competed":true,"ContestName":"ABC001","ContestUrl":"/contests/abc001"}];</script></html>)");
    transport.responses.insert("https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=tourist&from_second=0",
                                R"([{"id":7,"contest_id":"abc001","problem_id":"abc001_a","epoch_second":1700000000,"result":"AC","language":"C++"}])");
    transport.responses.insert("https://kenkoooo.com/atcoder/resources/contests.json",
                                R"([{"id":"abc001","start_epoch_second":1700000000,"title":"ABC001","duration_second":7200}])");
    transport.responses.insert("https://kenkoooo.com/atcoder/resources/problems.json",
                                R"([{"id":"abc001_a","contest_id":"abc001","problem_index":"A","name":"A","title":"A"}])");
    transport.responses.insert("https://kenkoooo.com/atcoder/resources/problem-models.json",
                                R"({"abc001_a":{"difficulty":800,"slope":1.0}})");

    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::AtCoderAdapter().sync(account, context, {});
    QCOMPARE(outcomes.size(), 5);
    for (const auto& outcome : outcomes) QVERIFY2(outcome.status == OjNexus::SyncOperationStatus::Success,
                                                  qPrintable(outcome.stage + QStringLiteral(": ") + outcome.failureType));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::AtCoder), 1);
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::AtCoder), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::AtCoder), 1);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::AtCoder), 1);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::AtCoder), 1);
}

void AdapterTest::luoguSyncPersistsAllStages() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:1000").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://www.luogu.com.cn/api/user/info/1000",
                                R"({"user":{"uid":1000,"name":"demo","elo":2000,"ccfLevel":6}})");
    transport.responses.insert("https://www.luogu.com.cn/record/list?user=1000&page=1&_contentOnly=1",
                                R"(<!doctype html><script id="lentille-context" type="application/json">{"data":{"records":[{"id":11,"pid":"P1001","status":"Accepted","score":100}]}}</script>)");
    transport.responses.insert("https://www.luogu.com.cn/contest/list?page=1&_contentOnly=1",
                                R"(<!doctype html><script id="lentille-context" type="application/json">{"data":{"contests":{"result":[{"id":12,"name":"Demo Contest"}]}}}</script>)");
    transport.responses.insert("https://www.luogu.com.cn/problem/list?page=1&_contentOnly=1",
                                R"(<!doctype html><script id="lentille-context" type="application/json">{"data":{"problems":{"result":[{"pid":"P1001","name":"A+B Problem","difficulty":1}]}}}</script>)");

    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::LuoguAdapter().sync(account, context, {});
    QCOMPARE(outcomes.size(), 4);
    for (const auto& outcome : outcomes) QVERIFY2(outcome.status == OjNexus::SyncOperationStatus::Success,
                                                  qPrintable(outcome.stage + QStringLiteral(": ") + outcome.failureType));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.submissionCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.problemCount(OjNexus::JudgeId::Luogu), 1);
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Luogu), 1);
}

void AdapterTest::rejectsMismatchedLuoguProfile() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:1000").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://www.luogu.com.cn/api/user/info/1000",
                                R"({"user":{"uid":2000,"name":"other","elo":2000}})");

    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::LuoguAdapter().sync(account, context, {});

    QCOMPARE(outcomes.first().status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.first().failureType, QStringLiteral("API"));
    QCOMPARE(store.profileCount(OjNexus::JudgeId::Luogu), 0);
}

void AdapterTest::rejectsSuccessfulEnvelopeWithoutArrayPayload() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "tourist").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://codeforces.com/api/user.info?handles=tourist",
                                R"({"status":"OK","result":[{"handle":"tourist"}]})");
    transport.responses.insert("https://codeforces.com/api/user.rating?handle=tourist",
                                R"({"status":"OK"})");

    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::CodeforcesAdapter().sync(account, context, {});

    QCOMPARE(outcomes.at(1).status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.at(1).failureType, QStringLiteral("PARSE"));
    QCOMPARE(store.ratingChangeCount(OjNexus::JudgeId::Codeforces), 0);
}

void AdapterTest::rejectsLuoguContentWithoutExpectedList() {
    QTemporaryDir dir;
    OjNexus::SqliteStore store(dir.filePath("state.db"));
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:1000").value();
    const auto operation = store.openOperation(account, "test").value();
    FakeTransport transport;
    transport.responses.insert("https://www.luogu.com.cn/api/user/info/1000",
                                R"({"user":{"uid":1000,"name":"demo"}})");
    transport.responses.insert("https://www.luogu.com.cn/record/list?user=1000&page=1&_contentOnly=1",
                                R"(<!doctype html><script id="lentille-context" type="application/json">{"data":{"records":[{"id":11}]}}</script>)");
    transport.responses.insert("https://www.luogu.com.cn/contest/list?page=1&_contentOnly=1",
                                R"(<!doctype html><script id="lentille-context" type="application/json">{"data":{}}</script>)");

    OjNexus::SyncContext context{store, transport, operation, 0, {}};
    const auto outcomes = OjNexus::LuoguAdapter().sync(account, context, {});

    QCOMPARE(outcomes.at(2).status, OjNexus::SyncOperationStatus::Error);
    QCOMPARE(outcomes.at(2).failureType, QStringLiteral("PARSE"));
    QCOMPARE(store.contestCount(OjNexus::JudgeId::Luogu), 0);
}

void AdapterTest::configuresBoundedHttp1Request() {
    const auto request = OjNexus::makeHttpRequest(
        QUrl(QStringLiteral("https://example.com/")),
        {{QByteArrayLiteral("X-Test"), QByteArrayLiteral("value")}}, 1234);

    QCOMPARE(request.transferTimeout(), 1234);
    QVERIFY(!request.attribute(QNetworkRequest::Http2AllowedAttribute).toBool());
    QCOMPARE(request.rawHeader(QByteArrayLiteral("X-Test")), QByteArrayLiteral("value"));
    QCOMPARE(request.rawHeader(QByteArrayLiteral("Accept")),
             QByteArrayLiteral("application/json, text/html;q=0.9"));
    QCOMPARE(request.rawHeader(QByteArrayLiteral("User-Agent")), QByteArrayLiteral("OJ-NEXUS/0.1"));

    const auto overridden = OjNexus::makeHttpRequest(
        QUrl(QStringLiteral("https://example.com/")),
        {{QByteArrayLiteral("Accept"), QByteArrayLiteral("text/plain")}}, 1234);
    QCOMPARE(overridden.rawHeader(QByteArrayLiteral("Accept")), QByteArrayLiteral("text/plain"));
}

void AdapterTest::stopsJsonRowPersistenceWhenCancellationArrives() {
    QJsonArray rows;
    rows.push_back(QJsonObject{{QStringLiteral("id"), 1}});
    rows.push_back(QJsonObject{{QStringLiteral("id"), 2}});

    std::stop_source source;
    int persisted = 0;
    const auto result = OjNexus::persistJsonRows(rows, source.get_token(), [&](const QJsonObject&) {
        ++persisted;
        if (persisted == 1) source.request_stop();
        return OjNexus::Result<void>();
    });

    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::Cancelled);
    QCOMPARE(persisted, 1);
}

QTEST_MAIN(AdapterTest)
#include "AdapterTest.moc"
