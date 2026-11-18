#include <stop_token>
#include <utility>

#include <QtTest/QtTest>
#include <QCryptographicHash>
#include <QJsonDocument>
#include <QFile>
#include <QTemporaryDir>
#include <QTextStream>

#include "ojnexus/cli/command_parser.h"
#include "ojnexus/cli/runner.h"
#include "ojnexus/core/paths.h"
#include "ojnexus/core/store.h"

class CommandParserTest final : public QObject {
    Q_OBJECT

private slots:
    void summarizesSyncResultsForAutomation();
    void exitCodesMatchTheSharedCrossPlatformContract();
    void rejectedStoreReturnsStorageExitCode();
    void cancelledSyncPersistsCancelledResult();
    void jsonSyncReportsInvalidConfiguration();
    void parsesSyncAll();
    void rejectsOptionsOutsideCommandScope();
    void rejectsCookieImportCommand();
    void historyDefaultsToAllJudges();
    void syncHandleCreatesEphemeralAccount();
    void forceIncludesDisabledConfiguredAccountsForSync();
    void configuresPublicHandleWithoutCredentials();
    void authLoginPreservesDisabledEquivalentAccount();
    void statusIncludesLastSyncAndProfileFreshness();
    void statusHumanOutputIncludesAccountDetails();
    void historyHumanOutputIncludesOperationDetails();
    void reportsStoredHandleWhenConfiguringEquivalentLuoguHandle();
    void parsesAuthRemove();
    void selectsDisabledAccountForRemoval();
    void selectsDisabledAccountForAuth();
    void rejectsUnconfiguredHandleForAuth();
    void selectsEquivalentLuoguHandleForAuth();
    void rejectsLogoutForUnconfiguredHandleWithoutChangingAccount();
    void authStatusReportsConfiguredAccountState();
    void loginClearsStaleProfileWhenHandleChanges();
    void parsesPortableVerbs();
    void rejectsPortableFlagsOutsideScope();
    void portableRequiresFile();
    void portableInspectEmitsPreviewJson();
    void portableImportWithoutYesWritesNothing();
    void portableImportConfirmAppliesAndExportRoundTrips();
    void portableRejectExitsZero();
    void portableImportTwiceIsIdenticalNoOp();
};

void CommandParserTest::rejectedStoreReturnsStorageExitCode() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    QFile invalid(paths.databaseFile);
    QVERIFY(invalid.open(QIODevice::WriteOnly));
    invalid.close();
    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::Status;
    QString inText, outText, errText;
    QTextStream input(&inText, QIODevice::ReadOnly), output(&outText, QIODevice::WriteOnly), error(&errText, QIODevice::WriteOnly);
    const auto result = OjNexus::Cli::execute(command, input, output, error);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(result, OjNexus::Cli::StorageError);
}

void CommandParserTest::exitCodesMatchTheSharedCrossPlatformContract() {
    // The same numbers as the Windows client; pinned here so a renumbering fails in CI
    // instead of in a user's script.
    QCOMPARE(static_cast<int>(OjNexus::Cli::Ok), 0);
    QCOMPARE(static_cast<int>(OjNexus::Cli::PartialFailure), 1);
    QCOMPARE(static_cast<int>(OjNexus::Cli::UsageError), 2);
    QCOMPARE(static_cast<int>(OjNexus::Cli::NetworkError), 3);
    QCOMPARE(static_cast<int>(OjNexus::Cli::AuthenticationError), 4);
    QCOMPARE(static_cast<int>(OjNexus::Cli::Cancelled), 5);
    QCOMPARE(static_cast<int>(OjNexus::Cli::StorageError), 6);
    QCOMPARE(static_cast<int>(OjNexus::Cli::GeneralError), 7);
}

void CommandParserTest::summarizesSyncResultsForAutomation() {
    const auto report = [](OjNexus::SyncOperationStatus status,
                           std::optional<OjNexus::SyncError> error = std::nullopt) {
        OjNexus::SyncReport result;
        result.operation.status = status;
        result.error = error;
        return result;
    };

    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Success)}), OjNexus::Cli::Ok);
    QCOMPARE(OjNexus::Cli::syncAggregateStatus({report(OjNexus::SyncOperationStatus::Success)}),
             QStringLiteral("SUCCESS"));
    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Error,
                                                 OjNexus::SyncError::Authentication)}),
             OjNexus::Cli::AuthenticationError);
    QCOMPARE(OjNexus::Cli::syncAggregateStatus({report(OjNexus::SyncOperationStatus::Error,
                                                        OjNexus::SyncError::Authentication)}),
             QStringLiteral("AUTHENTICATION"));
    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Offline,
                                                 OjNexus::SyncError::Offline)}),
             OjNexus::Cli::NetworkError);
    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Cancelled,
                                                 OjNexus::SyncError::Cancelled)}),
             OjNexus::Cli::Cancelled);
    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Error,
                                                 OjNexus::SyncError::Storage)}),
             OjNexus::Cli::StorageError);
    QCOMPARE(OjNexus::Cli::syncAggregateStatus({report(OjNexus::SyncOperationStatus::Offline,
                                                        OjNexus::SyncError::Offline)}),
             QStringLiteral("OFFLINE"));
    QCOMPARE(OjNexus::Cli::syncExitCode({report(OjNexus::SyncOperationStatus::Success),
                                         report(OjNexus::SyncOperationStatus::Error)}),
             OjNexus::Cli::PartialFailure);
    QCOMPARE(OjNexus::Cli::syncAggregateStatus({report(OjNexus::SyncOperationStatus::Success),
                                                 report(OjNexus::SyncOperationStatus::Error)}),
             QStringLiteral("PARTIAL"));
    QCOMPARE(OjNexus::Cli::syncAggregateStatus({report(OjNexus::SyncOperationStatus::Cancelled,
                                                        OjNexus::SyncError::Cancelled)}),
             QStringLiteral("CANCELLED"));
}

void CommandParserTest::cancelledSyncPersistsCancelledResult() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::Sync;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral("tourist");
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);
    std::stop_source cancellation;
    cancellation.request_stop();

    const auto exitCode = OjNexus::Cli::execute(
        command, input, output, error, cancellation.get_token());

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    // The exit code must agree with the status this run already reports: a fully cancelled
    // batch is neither a partial nor an unclassified failure.
    QCOMPARE(exitCode, OjNexus::Cli::Cancelled);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    const auto document = QJsonDocument::fromJson(outputText.toUtf8());
    QVERIFY(document.isObject());
    QCOMPARE(document.object().value(QStringLiteral("status")).toString(), QStringLiteral("CANCELLED"));
    const auto reports = document.object().value(QStringLiteral("reports")).toArray();
    QCOMPARE(reports.size(), 1);
    const auto reportObject = reports.first().toObject();
    QCOMPARE(reportObject.value(QStringLiteral("status")).toString(), QStringLiteral("CANCELLED"));
    QCOMPARE(reportObject.value(QStringLiteral("error")).toString(), QStringLiteral("CANCELLED"));
}

void CommandParserTest::jsonSyncReportsInvalidConfiguration() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::Sync;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral("tourist&handles=other");
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    const auto exitCode = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(exitCode, OjNexus::Cli::UsageError);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    const auto document = QJsonDocument::fromJson(outputText.toUtf8());
    QVERIFY(document.isObject());
    QCOMPARE(document.object().value(QStringLiteral("status")).toString(), QStringLiteral("ERROR"));
    QCOMPARE(document.object().value(QStringLiteral("error")).toString(),
             QStringLiteral("INVALID_CONFIGURATION"));
    QVERIFY(document.object().value(QStringLiteral("reports")).toArray().isEmpty());
}

void CommandParserTest::parsesSyncAll() {
    const QStringList argv{"ojnexus", "sync", "--judge", "all", "--force", "--json"};
    const auto parsed = OjNexus::Cli::CommandParser::parse(argv);
    QVERIFY(parsed.has_value());
    QCOMPARE(parsed->kind, OjNexus::Cli::CommandKind::Sync);
    QCOMPARE(parsed->judge, OjNexus::JudgeId::All);
    QVERIFY(parsed->force);
    QVERIFY(parsed->json);
}

void CommandParserTest::rejectsOptionsOutsideCommandScope() {
    QVERIFY(!OjNexus::Cli::CommandParser::parse({"ojnexus", "status", "--force"}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse({
        "ojnexus", "sync", "--judge", "codeforces", "--limit", "20"}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse({"ojnexus", "config", "show", "--handle", "tourist"}).has_value());
}

void CommandParserTest::rejectsCookieImportCommand() {
    const QStringList argv{"ojnexus", "auth", "import-cookie", "--judge", "luogu", "--handle", "1000", "--cookie-stdin"};
    const auto parsed = OjNexus::Cli::CommandParser::parse(argv);
    QVERIFY(!parsed.has_value());
    QCOMPARE(parsed.error(), OjNexus::SyncError::InvalidConfiguration);
}

void CommandParserTest::historyDefaultsToAllJudges() {
    const auto parsed = OjNexus::Cli::CommandParser::parse({"ojnexus", "history", "--json"});
    QVERIFY(parsed.has_value());
    QCOMPARE(parsed->judge, OjNexus::JudgeId::Unknown);
}

void CommandParserTest::syncHandleCreatesEphemeralAccount() {
    const QList<OjNexus::JudgeAccount> stored{
        {OjNexus::JudgeId::AtCoder, "alice", true},
    };

    const auto selected = OjNexus::Cli::accountsForSync(
        stored, OjNexus::JudgeId::Codeforces, "tourist");

    QVERIFY(selected.has_value());
    QCOMPARE(selected->size(), 1);
    QCOMPARE(selected->first().judge, OjNexus::JudgeId::Codeforces);
    QCOMPARE(selected->first().handle, QStringLiteral("tourist"));
    QVERIFY(selected->first().enabled);
}

void CommandParserTest::forceIncludesDisabledConfiguredAccountsForSync() {
    const QList<OjNexus::JudgeAccount> stored{
        {OjNexus::JudgeId::Codeforces, "tourist", false},
    };

    const auto rejected = OjNexus::Cli::accountsForSync(
        stored, OjNexus::JudgeId::Codeforces, {}, false);
    const auto forced = OjNexus::Cli::accountsForSync(
        stored, OjNexus::JudgeId::Codeforces, {}, true);

    QVERIFY(rejected.has_value());
    QVERIFY(rejected->isEmpty());
    QVERIFY(forced.has_value());
    QCOMPARE(forced->size(), 1);
    QCOMPARE(forced->first().handle, QStringLiteral("tourist"));
    QVERIFY(!forced->first().enabled);
}

void CommandParserTest::configuresPublicHandleWithoutCredentials() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("XDG_DATA_HOME", dir.path().toUtf8());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::AuthLogin;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = "tourist";
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    const auto code = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("XDG_DATA_HOME");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    const auto document = QJsonDocument::fromJson(outputText.toUtf8());
    QVERIFY(!document.isNull());
    QCOMPARE(document.object().value("accountConfigured").toBool(), true);
    QCOMPARE(document.object().value("handle").toString(), QStringLiteral("tourist"));
}

void CommandParserTest::authLoginPreservesDisabledEquivalentAccount() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    OjNexus::SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto disabled = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces,
                                                        QStringLiteral("tourist"), false);
    QVERIFY(disabled.has_value());
    QVERIFY(store.upsertAccount(disabled.value()).has_value());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::AuthLogin;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral(" Tourist ");
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    const auto code = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    const auto stored = store.accounts();
    QCOMPARE(stored.size(), 1);
    QCOMPARE(stored.first().handle, QStringLiteral("tourist"));
    QCOMPARE(stored.first().enabled, false);
}

void CommandParserTest::statusIncludesLastSyncAndProfileFreshness() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    OjNexus::SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(account).has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, account.handle,
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());
    QVERIFY(store.upsertRatingChange(OjNexus::JudgeId::Codeforces, account.handle,
                                     QJsonObject{{QStringLiteral("contestId"), 1},
                                                 {QStringLiteral("newRating"), 3800}}).has_value());
    QVERIFY(store.upsertSubmission(OjNexus::JudgeId::Codeforces, account.handle,
                                   QJsonObject{
                                       {QStringLiteral("id"), 9},
                                       {QStringLiteral("creationTimeSeconds"), 1700000000},
                                       {QStringLiteral("verdict"), QStringLiteral("OK")},
                                       {QStringLiteral("programmingLanguage"), QStringLiteral("GNU C++20")},
                                       {QStringLiteral("problem"), QJsonObject{
                                           {QStringLiteral("contestId"), 1},
                                           {QStringLiteral("index"), QStringLiteral("A")},
                                           {QStringLiteral("name"), QStringLiteral("TEST")},
                                       }},
                                   }).has_value());
    const auto operation = store.openOperation(account, QStringLiteral("status-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Success,
                                 std::nullopt).has_value());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::Status;
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);
    const auto code = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    const auto accountJson = QJsonDocument::fromJson(outputText.toUtf8()).object()
        .value(QStringLiteral("accounts")).toArray().first().toObject();
    QCOMPARE(accountJson.value(QStringLiteral("lastSyncStatus")).toString(), QStringLiteral("SUCCESS"));
    QVERIFY(!accountJson.value(QStringLiteral("lastSyncFinishedAt")).toString().isEmpty());
    QVERIFY(!accountJson.value(QStringLiteral("lastProfileUpdatedAt")).toString().isEmpty());
    QCOMPARE(accountJson.value(QStringLiteral("ratingChangeCount")).toInt(), 1);
    QCOMPARE(accountJson.value(QStringLiteral("submissionCount")).toInt(), 1);
    const auto submissions = accountJson.value(QStringLiteral("recentSubmissions")).toArray();
    QCOMPARE(submissions.size(), 1);
    QCOMPARE(submissions.first().toObject().value(QStringLiteral("problemId")).toString(), QStringLiteral("1A"));
    QCOMPARE(submissions.first().toObject().value(QStringLiteral("verdict")).toString(), QStringLiteral("OK"));
}

void CommandParserTest::statusHumanOutputIncludesAccountDetails() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    OjNexus::SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertAccount({OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"), false}).has_value());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::Status;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);
    const auto code = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    QVERIFY(outputText.contains(QStringLiteral("DATABASE: READY")));
    QVERIFY(outputText.contains(QStringLiteral("SCHEMA VERSION: 3")));
    QVERIFY(outputText.contains(QStringLiteral("ACCOUNTS: 1")));
    QVERIFY(outputText.contains(QStringLiteral("CODEFORCES / tourist / DISABLED")));
    QVERIFY(outputText.contains(QStringLiteral("LAST SYNC NEVER")));
}

void CommandParserTest::historyHumanOutputIncludesOperationDetails() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    OjNexus::SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto account = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(account).has_value());
    const auto operation = store.openOperation(account, QStringLiteral("history-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), OjNexus::SyncOperationStatus::Error,
                                 OjNexus::SyncError::Network).has_value());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::History;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);
    const auto code = OjNexus::Cli::execute(command, input, output, error);

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY2(errorText.isEmpty(), qPrintable(errorText));
    QVERIFY(outputText.contains(QStringLiteral("operations=")));
    QVERIFY(outputText.contains(QStringLiteral("tourist")));
    QVERIFY(outputText.contains(QStringLiteral("ERROR")));
    QVERIFY(outputText.contains(QStringLiteral("NETWORK")));
}

void CommandParserTest::reportsStoredHandleWhenConfiguringEquivalentLuoguHandle() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());

    auto executeLogin = [](const QString& handle) {
        OjNexus::Cli::Command command;
        command.kind = OjNexus::Cli::CommandKind::AuthLogin;
        command.judge = OjNexus::JudgeId::Luogu;
        command.handle = handle;
        command.json = true;
        QString inputText;
        QString outputText;
        QString errorText;
        QTextStream input(&inputText, QIODevice::ReadOnly);
        QTextStream output(&outputText, QIODevice::WriteOnly);
        QTextStream error(&errorText, QIODevice::WriteOnly);
        const auto exitCode = OjNexus::Cli::execute(command, input, output, error);
        return std::pair{exitCode, QJsonDocument::fromJson(outputText.toUtf8())};
    };

    const auto first = executeLogin(QStringLiteral("uid:2"));
    QCOMPARE(first.first, OjNexus::Cli::Ok);
    QCOMPARE(first.second.object().value(QStringLiteral("handle")).toString(), QStringLiteral("uid:2"));
    const auto second = executeLogin(QStringLiteral("2"));
    QCOMPARE(second.first, OjNexus::Cli::Ok);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(second.second.object().value(QStringLiteral("handle")).toString(), QStringLiteral("uid:2"));
}

void CommandParserTest::parsesAuthRemove() {
    const auto parsed = OjNexus::Cli::CommandParser::parse({
        "ojnexus", "auth", "remove", "--judge", "codeforces", "--handle", "tourist", "--json"});

    QVERIFY(parsed.has_value());
    QCOMPARE(parsed->kind, OjNexus::Cli::CommandKind::AuthRemove);
    QCOMPARE(parsed->judge, OjNexus::JudgeId::Codeforces);
    QCOMPARE(parsed->handle, QStringLiteral("tourist"));
    QVERIFY(parsed->json);
}

void CommandParserTest::selectsDisabledAccountForRemoval() {
    const QList<OjNexus::JudgeAccount> stored{{OjNexus::JudgeId::Codeforces, "tourist", false}};

    const auto selected = OjNexus::Cli::accountsForRemoval(
        stored, OjNexus::JudgeId::Codeforces, {});

    QVERIFY(selected.has_value());
    QCOMPARE(selected->size(), 1);
    QCOMPARE(selected->first().handle, QStringLiteral("tourist"));
    QCOMPARE(selected->first().enabled, false);
}

void CommandParserTest::selectsDisabledAccountForAuth() {
    const QList<OjNexus::JudgeAccount> stored{{OjNexus::JudgeId::Codeforces, "tourist", false}};

    const auto selected = OjNexus::Cli::accountsForAuth(
        stored, OjNexus::JudgeId::Codeforces, {});

    QVERIFY(selected.has_value());
    QCOMPARE(selected->size(), 1);
    QCOMPARE(selected->first().handle, QStringLiteral("tourist"));
    QCOMPARE(selected->first().enabled, false);
}

void CommandParserTest::rejectsUnconfiguredHandleForAuth() {
    const QList<OjNexus::JudgeAccount> stored{{OjNexus::JudgeId::Codeforces, "tourist", true}};

    const auto selected = OjNexus::Cli::accountsForAuth(
        stored, OjNexus::JudgeId::Codeforces, QStringLiteral("other"));

    QVERIFY(selected.has_value());
    QVERIFY(selected->isEmpty());
}

void CommandParserTest::selectsEquivalentLuoguHandleForAuth() {
    const QList<OjNexus::JudgeAccount> stored{{OjNexus::JudgeId::Luogu, "uid:2", true}};

    const auto selected = OjNexus::Cli::accountsForAuth(
        stored, OjNexus::JudgeId::Luogu, QStringLiteral("2"));

    QVERIFY(selected.has_value());
    QCOMPARE(selected->size(), 1);
    QCOMPARE(selected->first().handle, QStringLiteral("uid:2"));
}

void CommandParserTest::rejectsLogoutForUnconfiguredHandleWithoutChangingAccount() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("XDG_DATA_HOME", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    {
        OjNexus::SqliteStore store(paths.databaseFile);
        QVERIFY(store.initialize().has_value());
        QVERIFY(store.upsertAccount({OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"), true}).has_value());
    }

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::AuthLogout;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral("other");
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    QCOMPARE(OjNexus::Cli::execute(command, input, output, error), OjNexus::Cli::UsageError);

    OjNexus::SqliteStore reopened(paths.databaseFile);
    QVERIFY(reopened.initialize().has_value());
    QCOMPARE(reopened.accounts().size(), 1);
    QCOMPARE(reopened.accounts().first().handle, QStringLiteral("tourist"));
    QCOMPARE(reopened.accounts().first().enabled, true);
    qunsetenv("XDG_DATA_HOME");
}

void CommandParserTest::authStatusReportsConfiguredAccountState() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("XDG_DATA_HOME", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    {
        OjNexus::SqliteStore store(paths.databaseFile);
        QVERIFY(store.initialize().has_value());
        QVERIFY(store.upsertAccount({OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"), false}).has_value());
    }

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::AuthStatus;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral("tourist");
    command.json = true;
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    QCOMPARE(OjNexus::Cli::execute(command, input, output, error), OjNexus::Cli::Ok);
    const auto document = QJsonDocument::fromJson(outputText.toUtf8());
    QVERIFY(document.isObject());
    QVERIFY(document.object().contains(QStringLiteral("enabled")));
    QCOMPARE(document.object().value(QStringLiteral("enabled")).toBool(), false);
    QCOMPARE(document.object().value(QStringLiteral("handle")).toString(), QStringLiteral("tourist"));
    qunsetenv("XDG_DATA_HOME");
}

void CommandParserTest::loginClearsStaleProfileWhenHandleChanges() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("XDG_DATA_HOME", dir.path().toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.ensureDirectories().has_value());
    OjNexus::SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto oldAccount = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(oldAccount).has_value());
    QVERIFY(store.upsertProfile(OjNexus::JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());

    OjNexus::Cli::Command command;
    command.kind = OjNexus::Cli::CommandKind::AuthLogin;
    command.judge = OjNexus::JudgeId::Codeforces;
    command.handle = QStringLiteral("new-user");
    QString inputText;
    QString outputText;
    QString errorText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);

    QCOMPARE(OjNexus::Cli::execute(command, input, output, error), OjNexus::Cli::Ok);
    QVERIFY(!store.latestProfile(OjNexus::JudgeId::Codeforces).has_value());
    QCOMPARE(store.accounts().first().handle, QStringLiteral("new-user"));
}

namespace {

QByteArray portableGolden(const QString& name) {
    QFile file(QStringLiteral(OJNEXUS_PORTABLE_GOLDEN_DIR) + QLatin1Char('/') + name);
    if (!file.open(QIODevice::ReadOnly)) return {};
    return file.readAll();
}

QString copyGoldenToTemp(const QTemporaryDir& dir, const QString& name) {
    const auto bytes = portableGolden(name);
    const auto path = dir.filePath(name);
    QFile out(path);
    if (!out.open(QIODevice::WriteOnly)) return {};
    out.write(bytes);
    return path;
}

OjNexus::Cli::Command portableCommand(OjNexus::Cli::CommandKind kind, const QString& file, bool yes = false, bool json = false) {
    OjNexus::Cli::Command command;
    command.kind = kind;
    command.file = file;
    command.yes = yes;
    command.json = json;
    return command;
}

int run(OjNexus::Cli::Command command, QString& outputText, QString& errorText) {
    QString inputText;
    QTextStream input(&inputText, QIODevice::ReadOnly);
    QTextStream output(&outputText, QIODevice::WriteOnly);
    QTextStream error(&errorText, QIODevice::WriteOnly);
    return OjNexus::Cli::execute(command, input, output, error);
}

} // namespace

void CommandParserTest::parsesPortableVerbs() {
    const auto inspect = OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("inspect"),
                    QStringLiteral("--file"), QStringLiteral("pkg.json"), QStringLiteral("--json")});
    QVERIFY(inspect.has_value());
    QCOMPARE(inspect.value().kind, OjNexus::Cli::CommandKind::PortableInspect);
    QCOMPARE(inspect.value().file, QStringLiteral("pkg.json"));
    QVERIFY(inspect.value().json);
    QVERIFY(!inspect.value().yes);

    const auto import = OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("import"),
                    QStringLiteral("--file"), QStringLiteral("pkg.json"), QStringLiteral("--yes")});
    QVERIFY(import.has_value());
    QCOMPARE(import.value().kind, OjNexus::Cli::CommandKind::PortableImport);
    QVERIFY(import.value().yes);

    const auto exportCommand = OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("export"),
                    QStringLiteral("--file"), QStringLiteral("out.json")});
    QVERIFY(exportCommand.has_value());
    QCOMPARE(exportCommand.value().kind, OjNexus::Cli::CommandKind::PortableExport);
}

void CommandParserTest::rejectsPortableFlagsOutsideScope() {
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("inspect"),
                    QStringLiteral("--file"), QStringLiteral("p"), QStringLiteral("--yes")}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("status"), QStringLiteral("--file"), QStringLiteral("p")}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("status"), QStringLiteral("--yes")}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("inspect"),
                    QStringLiteral("--judge"), QStringLiteral("codeforces"), QStringLiteral("--file"), QStringLiteral("p")}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("rebase")}).has_value());
}

void CommandParserTest::portableRequiresFile() {
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("inspect")}).has_value());
    QVERIFY(!OjNexus::Cli::CommandParser::parse(
        QStringList{QStringLiteral("ojnexus"), QStringLiteral("portable"), QStringLiteral("import"), QStringLiteral("--file"), QString()}).has_value());
}

void CommandParserTest::portableInspectEmitsPreviewJson() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    QVERIFY(OjNexus::AppPaths::fromEnvironment().ensureDirectories().has_value());
    const auto packagePath = copyGoldenToTemp(dir, QStringLiteral("pw-p01.json"));
    QVERIFY(!packagePath.isEmpty());

    QString out, err;
    const auto code = run(portableCommand(OjNexus::Cli::CommandKind::PortableInspect, packagePath, false, true), out, err);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY(err.isEmpty());
    QVERIFY(out.contains(QStringLiteral("\"status\":\"preview\"")));
    QVERIFY(out.contains(QStringLiteral("81bb13c1746381f2")));
}

void CommandParserTest::portableImportWithoutYesWritesNothing() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    QVERIFY(OjNexus::AppPaths::fromEnvironment().ensureDirectories().has_value());
    const auto packagePath = copyGoldenToTemp(dir, QStringLiteral("pw-p01.json"));
    QVERIFY(!packagePath.isEmpty());
    const auto exportPath = dir.filePath(QStringLiteral("roundtrip.json"));

    QString out, err;
    const auto code = run(portableCommand(OjNexus::Cli::CommandKind::PortableImport, packagePath), out, err);
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY(out.contains(QStringLiteral("CONFIRMATION REQUIRED: RERUN WITH --yes")));

    // The confirm gate must have written nothing: the archive is still empty.
    QString exportOut, exportErr;
    const auto exportCode = run(portableCommand(OjNexus::Cli::CommandKind::PortableExport, exportPath), exportOut, exportErr);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(exportCode, OjNexus::Cli::GeneralError);
    QVERIFY(exportOut.contains(QStringLiteral("ARCHIVE EMPTY")));
}

void CommandParserTest::portableImportConfirmAppliesAndExportRoundTrips() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    QVERIFY(OjNexus::AppPaths::fromEnvironment().ensureDirectories().has_value());
    const auto packagePath = copyGoldenToTemp(dir, QStringLiteral("pw-p01.json"));
    QVERIFY(!packagePath.isEmpty());
    const auto exportPath = dir.filePath(QStringLiteral("roundtrip.json"));

    QString inspectOut, err;
    QCOMPARE(run(portableCommand(OjNexus::Cli::CommandKind::PortableInspect, packagePath, false, true), inspectOut, err),
             OjNexus::Cli::Ok);
    const auto document = QJsonDocument::fromJson(inspectOut.toUtf8());
    QVERIFY(document.object().value(QStringLiteral("status")).toString() == QStringLiteral("preview"));
    const auto identity = document.object().value(QStringLiteral("identity")).toString();
    QVERIFY(!identity.isEmpty());

    QString applyOut, applyErr;
    QCOMPARE(run(portableCommand(OjNexus::Cli::CommandKind::PortableImport, packagePath, true, true), applyOut, applyErr),
             OjNexus::Cli::Ok);
    QVERIFY(applyOut.contains(QStringLiteral("\"status\":\"applied\"")));

    QString exportOut, exportErr;
    QCOMPARE(run(portableCommand(OjNexus::Cli::CommandKind::PortableExport, exportPath, false, true), exportOut, exportErr),
             OjNexus::Cli::Ok);
    QVERIFY(exportOut.contains(QStringLiteral("\"status\":\"exported\"")));
    QVERIFY(exportOut.contains(identity.left(12)));

    // The exported file's bytes hash to the same identity: lossless round trip.
    QFile exported(exportPath);
    QVERIFY(exported.open(QIODevice::ReadOnly));
    const auto exportedBytes = exported.readAll();
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    const auto exportedIdentity = QString::fromLatin1(
        QCryptographicHash::hash(exportedBytes, QCryptographicHash::Sha256).toHex());
    QCOMPARE(exportedIdentity, identity);
}

void CommandParserTest::portableRejectExitsZero() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    QVERIFY(OjNexus::AppPaths::fromEnvironment().ensureDirectories().has_value());
    const auto invalidPath = copyGoldenToTemp(dir, QStringLiteral("pw-n01.json"));
    QVERIFY(!invalidPath.isEmpty());

    QString out, err;
    const auto code = run(portableCommand(OjNexus::Cli::CommandKind::PortableImport, invalidPath, true, true), out, err);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(code, OjNexus::Cli::Ok);
    QVERIFY(out.contains(QStringLiteral("\"status\":\"rejected\"")));
    QVERIFY(out.contains(QStringLiteral("UNKNOWN_FIELD")));
}

void CommandParserTest::portableImportTwiceIsIdenticalNoOp() {
    QTemporaryDir dir;
    QVERIFY(dir.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", dir.path().toUtf8());
    QVERIFY(OjNexus::AppPaths::fromEnvironment().ensureDirectories().has_value());
    const auto packagePath = copyGoldenToTemp(dir, QStringLiteral("pw-p01.json"));
    QVERIFY(!packagePath.isEmpty());

    QString firstOut, firstErr;
    QCOMPARE(run(portableCommand(OjNexus::Cli::CommandKind::PortableImport, packagePath, true, true), firstOut, firstErr),
             OjNexus::Cli::Ok);

    QString secondOut, secondErr;
    const auto secondCode = run(portableCommand(OjNexus::Cli::CommandKind::PortableImport, packagePath, true, true), secondOut, secondErr);
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    QCOMPARE(secondCode, OjNexus::Cli::Ok);
    QVERIFY(secondOut.contains(QStringLiteral("\"identical\":true")));
}

QTEST_MAIN(CommandParserTest)
#include "CommandParserTest.moc"
