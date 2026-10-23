#include <QtTest>

#include "viewmodel.h"
#include "ojnexus/core/store.h"

using namespace OjNexus;
using namespace OjNexus::Gui;

class MainViewModelTest final : public QObject {
    Q_OBJECT

private slots:
    void summarizesSyncCompletionStatus();
    void configuresPublicAccountFromGui();
    void rejectsInvalidPublicAccountFromGui();
    void exposesStoredProfileSummaryAfterRefresh();
    void exposesStoredRatingSummaryAfterRefresh();
    void exposesStoredSubmissionHistoryAfterRefresh();
    void matchesLuoguProfileToPrefixedAccount();
    void filtersHistoryByJudge();
    void managesConfiguredAccountFromGui();
    void preservesDisabledStateWhenReconfiguringEquivalentAccount();
    void exposesModuleFailureSummary();
    void exposesOperationErrorInHistory();
    void retriesHistoryOnlyForExactConfiguredAccount();
    void exposesLastSyncProjectionForConfiguredAccount();
    void matchesCodeforcesIdentityAcrossViewModelProjections();
    void clearsStaleProfileWhenHandleChanges();
    void exposesAdapterDeclaredCapabilityRows();
};

void MainViewModelTest::summarizesSyncCompletionStatus() {
    const auto report = [](SyncOperationStatus status, std::optional<SyncError> error = std::nullopt) {
        SyncReport result;
        result.operation.status = status;
        result.error = error;
        return result;
    };
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Success)}), QStringLiteral("SUCCESS / SYNC COMPLETE"));
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Offline), report(SyncOperationStatus::Offline)}),
             QStringLiteral("OFFLINE / RETRY WHEN ONLINE"));
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Error, SyncError::Authentication)}),
             QStringLiteral("AUTHENTICATION / PUBLIC ACCESS REQUIRED"));
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Error, SyncError::Api)}),
             QStringLiteral("ERROR / REVIEW SYNC HISTORY"));
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Success), report(SyncOperationStatus::Error)}),
             QStringLiteral("PARTIAL / REVIEW SYNC HISTORY"));
    QCOMPARE(syncCompletionStatusLine({report(SyncOperationStatus::Success), report(SyncOperationStatus::Cancelled)}),
             QStringLiteral("CANCELLED / SYNC STOPPED"));
}

void MainViewModelTest::configuresPublicAccountFromGui() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("  tourist  "));

    QCOMPARE(viewModel.accountSummary(), QStringLiteral("1 CONNECTOR"));
    QCOMPARE(viewModel.statusLine(), QStringLiteral("SUCCESS / ACCOUNT CONFIGURED"));
    QCOMPARE(viewModel.configuredAccounts().size(), 1);
    const auto account = viewModel.configuredAccounts().constFirst().toMap();
    QCOMPARE(account.value(QStringLiteral("judge")).toString(), QStringLiteral("CODEFORCES"));
    QCOMPARE(account.value(QStringLiteral("handle")).toString(), QStringLiteral("tourist"));
}

void MainViewModelTest::rejectsInvalidPublicAccountFromGui() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("unknown"), QStringLiteral("tourist"));

    QCOMPARE(viewModel.statusLine(), QStringLiteral("ERROR / INVALID CONNECTOR OR HANDLE"));
    QCOMPARE(viewModel.accountSummary(), QStringLiteral("0 CONNECTORS"));
    QVERIFY(viewModel.configuredAccounts().isEmpty());
}

void MainViewModelTest::exposesStoredProfileSummaryAfterRefresh() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertProfile(JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800},
                                            {QStringLiteral("rank"), QStringLiteral("legendary")}}).has_value());

    viewModel.refresh();

    const auto storedProfile = store.latestProfile(JudgeId::Codeforces);
    QVERIFY(storedProfile.has_value());
    QCOMPARE(viewModel.profileSummary(), QStringLiteral("CODEFORCES / tourist / RATING 3800 / LEGENDARY / UPDATED %1")
        .arg(storedProfile->updatedAt.toString(Qt::ISODateWithMs)));
    QCOMPARE(viewModel.configuredAccounts().constFirst().toMap().value(QStringLiteral("profileSummary")).toString(),
             viewModel.profileSummary());
}

void MainViewModelTest::exposesStoredRatingSummaryAfterRefresh() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertRatingChange(JudgeId::Codeforces,
                                     QJsonObject{{QStringLiteral("contestId"), 1},
                                                 {QStringLiteral("newRating"), 3800}}).has_value());

    viewModel.refresh();

    const auto projection = viewModel.configuredAccounts().constFirst().toMap();
    QCOMPARE(projection.value(QStringLiteral("ratingSummary")).toString(),
             QStringLiteral("RATING CHANGES 1"));
}

void MainViewModelTest::exposesStoredSubmissionHistoryAfterRefresh() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertSubmission(JudgeId::Codeforces,
                                   QJsonObject{
                                       {QStringLiteral("id"), 9},
                                       {QStringLiteral("creationTimeSeconds"), 1700000000},
                                       {QStringLiteral("verdict"), QStringLiteral("OK")},
                                       {QStringLiteral("programmingLanguage"), QStringLiteral("GNU C++20")},
                                       {QStringLiteral("problem"), QJsonObject{
                                           {QStringLiteral("contestId"), 1},
                                           {QStringLiteral("index"), QStringLiteral("A")},
                                       }},
                                   }).has_value());

    viewModel.refresh();

    const auto projection = viewModel.configuredAccounts().constFirst().toMap();
    QCOMPARE(projection.value(QStringLiteral("submissionSummary")).toString(),
             QStringLiteral("SUBMISSIONS 1 / LATEST OK / 1A"));
    const auto rows = projection.value(QStringLiteral("recentSubmissions")).toList();
    QCOMPARE(rows.size(), 1);
    QCOMPARE(rows.first().toMap().value(QStringLiteral("verdict")).toString(), QStringLiteral("OK"));
    QCOMPARE(rows.first().toMap().value(QStringLiteral("problemId")).toString(), QStringLiteral("1A"));
}

void MainViewModelTest::matchesLuoguProfileToPrefixedAccount() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("luogu"), QStringLiteral("uid:2"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertProfile(JudgeId::Luogu, QStringLiteral("2"),
                                QJsonObject{{QStringLiteral("elo"), 2000}}).has_value());

    viewModel.refresh();

    const auto storedProfile = store.latestProfile(JudgeId::Luogu);
    QVERIFY(storedProfile.has_value());
    const auto expected = QStringLiteral("LUOGU / uid:2 / RATING 2000 / UPDATED %1")
        .arg(storedProfile->updatedAt.toString(Qt::ISODateWithMs));
    QCOMPARE(viewModel.profileSummary(), expected);
    QCOMPARE(viewModel.configuredAccounts().constFirst().toMap().value(QStringLiteral("profileSummary")).toString(), expected);
}

void MainViewModelTest::filtersHistoryByJudge() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto codeforces = JudgeAccount::create(JudgeId::Codeforces, QStringLiteral("tourist")).value();
    const auto atcoder = JudgeAccount::create(JudgeId::AtCoder, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(codeforces).has_value());
    QVERIFY(store.upsertAccount(atcoder).has_value());
    const auto first = store.openOperation(codeforces, QStringLiteral("test"));
    const auto second = store.openOperation(atcoder, QStringLiteral("test"));
    QVERIFY(first.has_value());
    QVERIFY(second.has_value());
    QVERIFY(store.closeOperation(first.value(), SyncOperationStatus::Success, std::nullopt).has_value());
    QVERIFY(store.closeOperation(second.value(), SyncOperationStatus::Success, std::nullopt).has_value());

    viewModel.setHistoryFilter(QStringLiteral("atcoder"));

    QCOMPARE(viewModel.history()->rowCount(), 1);
    QCOMPARE(viewModel.history()->data(viewModel.history()->index(0, 0), HistoryModel::JudgeRole).toString(), QStringLiteral("ATCODER"));
}

void MainViewModelTest::managesConfiguredAccountFromGui() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    QVERIFY(viewModel.setAccountEnabled(QStringLiteral("codeforces"), false));
    QCOMPARE(viewModel.configuredAccounts().constFirst().toMap().value(QStringLiteral("enabled")).toBool(), false);
    QVERIFY(viewModel.removeAccount(QStringLiteral("codeforces")));
    QVERIFY(viewModel.configuredAccounts().isEmpty());
}

void MainViewModelTest::preservesDisabledStateWhenReconfiguringEquivalentAccount() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("Tourist"));
    QVERIFY(viewModel.setAccountEnabled(QStringLiteral("codeforces"), false));

    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral(" tourist "));

    QCOMPARE(viewModel.configuredAccounts().constFirst().toMap().value(QStringLiteral("enabled")).toBool(), false);
    QCOMPARE(viewModel.configuredAccounts().constFirst().toMap().value(QStringLiteral("handle")).toString(), QStringLiteral("Tourist"));
}

void MainViewModelTest::exposesModuleFailureSummary() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto account = JudgeAccount::create(JudgeId::Codeforces, QStringLiteral("tourist")).value();
    const auto operation = store.openOperation(account, QStringLiteral("test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.appendModule(operation.value(), {QStringLiteral("PROFILE"), SyncOperationStatus::Success, 1, 1, 0, {}}).has_value());
    QVERIFY(store.appendModule(operation.value(), {QStringLiteral("SUBMISSIONS"), SyncOperationStatus::Offline, 1, 0, 0, QStringLiteral("OFFLINE")}).has_value());
    QVERIFY(store.closeOperation(operation.value(), SyncOperationStatus::Offline, SyncError::Offline).has_value());

    viewModel.refresh();

    QCOMPARE(viewModel.history()->data(viewModel.history()->index(0, 0), HistoryModel::ModuleSummaryRole).toString(),
             QStringLiteral("PROFILE:SUCCESS / SUBMISSIONS:OFFLINE (OFFLINE)"));
}

void MainViewModelTest::exposesOperationErrorInHistory() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto account = JudgeAccount::create(JudgeId::Luogu, QStringLiteral("uid:2")).value();
    const auto operation = store.openOperation(account, QStringLiteral("authentication-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), SyncOperationStatus::Error,
                                 SyncError::Authentication).has_value());

    viewModel.refresh();

    const auto index = viewModel.history()->index(0, 0);
    QCOMPARE(viewModel.history()->data(index, HistoryModel::ErrorRole).toString(),
             QStringLiteral("AUTHENTICATION"));
    QCOMPARE(viewModel.history()->roleNames().value(HistoryModel::ErrorRole), QByteArray("error"));
}

void MainViewModelTest::retriesHistoryOnlyForExactConfiguredAccount() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto historicalAccount = JudgeAccount::create(JudgeId::Codeforces, QStringLiteral("tourist")).value();
    QVERIFY(store.upsertAccount(historicalAccount).has_value());
    const auto operation = store.openOperation(historicalAccount, QStringLiteral("retry-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), SyncOperationStatus::Error, SyncError::Api).has_value());

    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("new-user"));
    viewModel.retryHistory(operation.value());

    QCOMPARE(viewModel.statusLine(), QStringLiteral("ERROR / ACCOUNT NOT CONFIGURED"));
    QVERIFY(!viewModel.busy());
    QCOMPARE(viewModel.history()->data(viewModel.history()->index(0, 0), HistoryModel::OperationIdRole).toLongLong(), operation.value());
    QCOMPARE(viewModel.history()->roleNames().value(HistoryModel::OperationIdRole), QByteArray("operationId"));
}

void MainViewModelTest::exposesLastSyncProjectionForConfiguredAccount() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto account = JudgeAccount::create(JudgeId::Codeforces, QStringLiteral("tourist")).value();
    const auto operation = store.openOperation(account, QStringLiteral("test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.appendModule(operation.value(), {QStringLiteral("PROFILE"), SyncOperationStatus::Offline,
                                                   1, 0, 0, QStringLiteral("OFFLINE")}).has_value());
    QVERIFY(store.closeOperation(operation.value(), SyncOperationStatus::Offline, SyncError::Offline).has_value());

    viewModel.refresh();

    const auto projection = viewModel.configuredAccounts().constFirst().toMap();
    QCOMPARE(projection.value(QStringLiteral("lastSyncSummary")).toString(),
             QStringLiteral("LAST SYNC OFFLINE / PROFILE:OFFLINE (OFFLINE)"));
    QVERIFY(!projection.value(QStringLiteral("lastSyncFinishedAt")).toString().isEmpty());
}

void MainViewModelTest::matchesCodeforcesIdentityAcrossViewModelProjections() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    const auto historicalAccount = JudgeAccount::create(JudgeId::Codeforces, QStringLiteral("Tourist")).value();
    const auto operation = store.openOperation(historicalAccount, QStringLiteral("case-folding-test"));
    QVERIFY(operation.has_value());
    QVERIFY(store.closeOperation(operation.value(), SyncOperationStatus::Success, std::nullopt).has_value());
    QVERIFY(store.upsertProfile(JudgeId::Codeforces, QStringLiteral("Tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());

    viewModel.refresh();

    const auto projection = viewModel.configuredAccounts().constFirst().toMap();
    QVERIFY(projection.value(QStringLiteral("lastSyncSummary")).toString().startsWith(QStringLiteral("LAST SYNC SUCCESS")));
    QVERIFY(projection.value(QStringLiteral("profileSummary")).toString().startsWith(QStringLiteral("CODEFORCES / tourist / RATING 3800")));
}

void MainViewModelTest::clearsStaleProfileWhenHandleChanges() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;
    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("tourist"));
    const auto paths = AppPaths::fromEnvironment();
    SqliteStore store(paths.databaseFile);
    QVERIFY(store.initialize().has_value());
    QVERIFY(store.upsertProfile(JudgeId::Codeforces, QStringLiteral("tourist"),
                                QJsonObject{{QStringLiteral("rating"), 3800}}).has_value());

    viewModel.configureAccount(QStringLiteral("codeforces"), QStringLiteral("new-user"));

    QCOMPARE(viewModel.profileSummary(), QStringLiteral("NONE / NO PROFILE SNAPSHOT"));
    QVERIFY(!store.latestProfile(JudgeId::Codeforces).has_value());
}

void MainViewModelTest::exposesAdapterDeclaredCapabilityRows() {
    QTemporaryDir dataHome;
    QVERIFY(dataHome.isValid());
    qputenv("XDG_DATA_HOME", dataHome.path().toUtf8());

    MainViewModel viewModel;

    QCOMPARE(viewModel.capabilityRows().size(), 3);
    const auto codeforces = viewModel.capabilityRows().at(0).toMap();
    QCOMPARE(codeforces.value(QStringLiteral("judge")).toString(), QStringLiteral("CODEFORCES"));
    QVERIFY(codeforces.value(QStringLiteral("summary")).toString().contains(QStringLiteral("RATING_HISTORY")));
    const auto atcoder = viewModel.capabilityRows().at(1).toMap();
    QVERIFY(atcoder.value(QStringLiteral("summary")).toString().contains(QStringLiteral("PROBLEM_CATALOG")));
    const auto luogu = viewModel.capabilityRows().at(2).toMap();
    QVERIFY(!luogu.value(QStringLiteral("summary")).toString().contains(QStringLiteral("RATING_HISTORY")));
}

QTEST_MAIN(MainViewModelTest)
#include "MainViewModelTest.moc"
