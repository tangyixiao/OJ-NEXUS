#include "viewmodel.h"

#include <algorithm>

#include <QMetaObject>
#include <QStringList>

#include "ojnexus/core/sync_engine.h"

namespace OjNexus::Gui {

namespace {

QString moduleSummary(const QList<SyncModuleOutcome>& modules) {
    if (modules.isEmpty()) return QStringLiteral("NO MODULES");
    QStringList summaries;
    for (const auto& module : modules) {
        auto summary = module.stage + QStringLiteral(":") + syncStatusToString(module.status);
        if (!module.failureType.trimmed().isEmpty()) {
            summary += QStringLiteral(" (") + module.failureType.trimmed() + QStringLiteral(")");
        }
        summaries.append(summary);
    }
    return summaries.join(QStringLiteral(" / "));
}

} // namespace

QString syncCompletionStatusLine(const QList<SyncReport>& reports) {
    if (reports.isEmpty()) return QStringLiteral("ERROR / NO SYNC REPORT");

    bool hasSuccess = false;
    bool hasFailure = false;
    bool allOffline = true;
    bool allAuthentication = true;
    bool hasAuthentication = false;
    for (const auto& report : reports) {
        const auto status = report.operation.status;
        hasSuccess = hasSuccess || status == SyncOperationStatus::Success;
        hasFailure = hasFailure || status != SyncOperationStatus::Success;
        allOffline = allOffline && status == SyncOperationStatus::Offline;
        const auto authentication = report.error == SyncError::Authentication;
        hasAuthentication = hasAuthentication || authentication;
        allAuthentication = allAuthentication && authentication;
    }
    if (std::any_of(reports.cbegin(), reports.cend(), [](const auto& report) {
            return report.operation.status == SyncOperationStatus::Cancelled;
        })) {
        return QStringLiteral("CANCELLED / SYNC STOPPED");
    }
    if (allOffline) return QStringLiteral("OFFLINE / RETRY WHEN ONLINE");
    if (!hasFailure) return QStringLiteral("SUCCESS / SYNC COMPLETE");
    if (allAuthentication && hasAuthentication) return QStringLiteral("AUTHENTICATION / PUBLIC ACCESS REQUIRED");
    if (hasSuccess) return QStringLiteral("PARTIAL / REVIEW SYNC HISTORY");
    return QStringLiteral("ERROR / REVIEW SYNC HISTORY");
}

HistoryModel::HistoryModel(QObject* parent) : QAbstractListModel(parent) {}

int HistoryModel::rowCount(const QModelIndex& parent) const { return parent.isValid() ? 0 : operations_.size(); }

QVariant HistoryModel::data(const QModelIndex& index, int role) const {
    if (!index.isValid() || index.row() >= operations_.size()) return {};
    const auto& operation = operations_.at(index.row());
    switch (role) {
    case OperationIdRole: return operation.id;
    case JudgeRole: return judgeIdToString(operation.account.judge).toUpper();
    case HandleRole: return operation.account.handle;
    case StatusRole: return syncStatusToString(operation.status);
    case ErrorRole: return operation.error ? syncErrorToString(*operation.error) : QString();
    case StartedRole: return operation.startedAt.toString(Qt::ISODateWithMs);
    case ModuleCountRole: return operation.modules.size();
    case ModuleSummaryRole: return moduleSummary(operation.modules);
    default: return {};
    }
}

QHash<int, QByteArray> HistoryModel::roleNames() const {
    return {{OperationIdRole, "operationId"}, {JudgeRole, "judge"}, {HandleRole, "handle"}, {StatusRole, "status"}, {ErrorRole, "error"}, {StartedRole, "startedAt"},
            {ModuleCountRole, "moduleCount"}, {ModuleSummaryRole, "moduleSummary"}};
}

void HistoryModel::setOperations(QList<SyncOperation> operations) {
    beginResetModel();
    operations_ = std::move(operations);
    endResetModel();
}

MainViewModel::MainViewModel(std::shared_ptr<IHttpTransport> transport, QObject* parent)
    : QObject(parent), paths_(AppPaths::fromEnvironment()), store_(paths_.databaseFile), transport_(std::move(transport)) {
    refresh();
}

MainViewModel::~MainViewModel() {
    cancel();
    if (syncThread_) {
        syncThread_->quit();
        syncThread_->wait();
        delete syncThread_;
    }
}

void MainViewModel::setCurrentPage(const QString& page) {
    if (currentPage_ == page) return;
    currentPage_ = page;
    emit currentPageChanged();
}

void MainViewModel::setBusy(bool value) {
    if (busy_ == value) return;
    busy_ = value;
    emit busyChanged();
}

void MainViewModel::setStatusLine(const QString& value) {
    if (statusLine_ == value) return;
    statusLine_ = value;
    emit statusLineChanged();
}

void MainViewModel::refresh() {
    if (!paths_.ensureDirectories().has_value() || !store_.initialize().has_value()) {
        setStatusLine(QStringLiteral("ERROR / LOCAL STORE UNAVAILABLE"));
        return;
    }
    const auto accounts = store_.accounts();
    accountSummary_ = QStringLiteral("%1 CONNECTOR%2").arg(accounts.size()).arg(accounts.size() == 1 ? QString() : QStringLiteral("S"));
    emit accountSummaryChanged();
    capabilityRows_.clear();
    for (const auto judge : {JudgeId::Codeforces, JudgeId::AtCoder, JudgeId::Luogu}) {
        const auto* adapter = SyncEngine::adapterFor(judge);
        if (!adapter) continue;
        QStringList fields{adapter->reliability()};
        fields.append(adapter->capabilities());
        capabilityRows_.append(QVariantMap{
            {QStringLiteral("judge"), judgeIdToString(judge).toUpper()},
            {QStringLiteral("summary"), fields.join(QStringLiteral(" / "))},
        });
    }
    emit capabilityRowsChanged();
    const auto profileSummaryFor = [this](const JudgeAccount& account) {
        const auto profile = store_.latestProfile(account.judge, account.handle);
        if (!profile.has_value()) return QStringLiteral("NO PROFILE SNAPSHOT");
        if (!judgeHandlesMatch(account.judge, profile->handle, account.handle)) {
            return QStringLiteral("NO PROFILE SNAPSHOT");
        }
        QStringList fields{
            judgeIdToString(account.judge).toUpper(),
            account.handle,
        };
        const auto rating = profile->payload.value(QStringLiteral("rating"));
        const auto normalizedRating = rating.isDouble()
            ? rating
            : profile->payload.value(QStringLiteral("elo"));
        if (normalizedRating.isDouble()) fields.append(QStringLiteral("RATING %1").arg(normalizedRating.toInt()));
        const auto rank = profile->payload.value(QStringLiteral("rank")).toString().trimmed();
        if (!rank.isEmpty()) fields.append(rank.toUpper());
        fields.append(QStringLiteral("UPDATED %1").arg(profile->updatedAt.toString(Qt::ISODateWithMs)));
        return fields.join(QStringLiteral(" / "));
    };
    const auto lastOperationFor = [this](const JudgeAccount& account) -> std::optional<SyncOperation> {
        const auto operations = store_.recentOperations(account.judge, 20);
        const auto match = std::find_if(operations.cbegin(), operations.cend(), [&account](const auto& operation) {
            return judgeHandlesMatch(account.judge, operation.account.handle, account.handle);
        });
        return match == operations.cend() ? std::nullopt : std::optional<SyncOperation>(*match);
    };
    const auto recentSubmissionRowsFor = [this](const JudgeAccount& account) {
        QVariantList rows;
        for (const auto& submission : store_.recentSubmissions(account.judge, 20, account.handle)) {
            rows.append(QVariantMap{
                {QStringLiteral("id"), submission.externalId},
                {QStringLiteral("problemId"), submission.problemId},
                {QStringLiteral("problemName"), submission.problemName},
                {QStringLiteral("verdict"), submission.verdict},
                {QStringLiteral("language"), submission.language},
                {QStringLiteral("submittedAt"), submission.submittedAt.isValid()
                    ? submission.submittedAt.toString(Qt::ISODateWithMs) : QString()},
            });
        }
        return rows;
    };
    configuredAccounts_.clear();
    for (const auto& account : accounts) {
        const auto lastSync = lastOperationFor(account);
        QString lastSyncSummary = QStringLiteral("LAST SYNC NEVER");
        QString lastSyncFinishedAt;
        if (lastSync.has_value()) {
            lastSyncSummary = QStringLiteral("LAST SYNC ") + syncStatusToString(lastSync->status) +
                QStringLiteral(" / ") + moduleSummary(lastSync->modules);
                if (lastSync->finishedAt.has_value()) lastSyncFinishedAt = lastSync->finishedAt->toString(Qt::ISODateWithMs);
        }
        const auto recentSubmissionRows = recentSubmissionRowsFor(account);
        QString submissionSummary = QStringLiteral("NO SUBMISSIONS");
        if (!recentSubmissionRows.isEmpty()) {
            const auto latest = recentSubmissionRows.first().toMap();
            QStringList fields{QStringLiteral("SUBMISSIONS %1").arg(store_.submissionCount(account.judge, account.handle))};
            const auto verdict = latest.value(QStringLiteral("verdict")).toString();
            if (!verdict.isEmpty()) fields.append(QStringLiteral("LATEST %1").arg(verdict));
            const auto problemId = latest.value(QStringLiteral("problemId")).toString();
            if (!problemId.isEmpty()) fields.append(problemId);
            submissionSummary = fields.join(QStringLiteral(" / "));
        }
        configuredAccounts_.append(QVariantMap{
            {QStringLiteral("judge"), judgeIdToString(account.judge).toUpper()},
            {QStringLiteral("handle"), account.handle},
            {QStringLiteral("enabled"), account.enabled},
            {QStringLiteral("profileSummary"), profileSummaryFor(account)},
            {QStringLiteral("ratingSummary"), QStringLiteral("RATING CHANGES %1")
                .arg(store_.ratingChangeCount(account.judge, account.handle))},
            {QStringLiteral("submissionSummary"), submissionSummary},
            {QStringLiteral("recentSubmissions"), recentSubmissionRows},
            {QStringLiteral("lastSyncSummary"), lastSyncSummary},
            {QStringLiteral("lastSyncFinishedAt"), lastSyncFinishedAt},
        });
    }
    emit configuredAccountsChanged();
    profileSummary_ = QStringLiteral("NONE / NO PROFILE SNAPSHOT");
    for (const auto& account : accounts) {
        const auto summary = profileSummaryFor(account);
        if (summary == QStringLiteral("NO PROFILE SNAPSHOT")) continue;
        profileSummary_ = summary;
        break;
    }
    emit profileSummaryChanged();
    std::optional<JudgeId::Value> historyJudge;
    const auto selectedHistoryJudge = JudgeId::fromString(historyFilter_);
    if (selectedHistoryJudge != JudgeId::All && selectedHistoryJudge != JudgeId::Unknown) {
        historyJudge = selectedHistoryJudge;
    }
    history_.setOperations(store_.recentOperations(historyJudge, 40));
    setStatusLine(busy_ ? QStringLiteral("SYNCING / NETWORK CONTROLLED BY CORE") : QStringLiteral("READY / LOCAL STATE"));
}

void MainViewModel::setHistoryFilter(const QString& filter) {
    const auto selected = JudgeId::fromString(filter);
    const auto normalized = selected == JudgeId::Unknown || selected == JudgeId::All
        ? QStringLiteral("all")
        : judgeIdToString(selected);
    if (historyFilter_ == normalized) return;
    historyFilter_ = normalized;
    emit historyFilterChanged();
    refresh();
}

void MainViewModel::configureAccount(const QString& judge, const QString& handle) {
    const auto selected = JudgeId::fromString(judge);
    const auto account = JudgeAccount::create(selected, handle);
    if (!account.has_value()) {
        setStatusLine(QStringLiteral("ERROR / INVALID CONNECTOR OR HANDLE"));
        return;
    }
    if (!paths_.ensureDirectories().has_value() || !store_.initialize().has_value()) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT SAVE FAILED"));
        return;
    }
    auto accountToStore = account.value();
    const auto existingAccounts = store_.accounts();
    const auto existing = std::find_if(existingAccounts.cbegin(), existingAccounts.cend(), [&accountToStore](const auto& candidate) {
        return candidate.judge == accountToStore.judge &&
               judgeHandlesMatch(candidate.judge, candidate.handle, accountToStore.handle);
    });
    if (existing != existingAccounts.cend()) accountToStore = *existing;
    if (!store_.configureAccount(accountToStore).has_value()) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT SAVE FAILED"));
        return;
    }
    refresh();
    setStatusLine(QStringLiteral("SUCCESS / ACCOUNT CONFIGURED"));
}

bool MainViewModel::setAccountEnabled(const QString& judge, bool enabled) {
    const auto selected = JudgeId::fromString(judge);
    if (selected == JudgeId::Unknown || selected == JudgeId::All) {
        setStatusLine(QStringLiteral("ERROR / INVALID CONNECTOR"));
        return false;
    }
    if (!store_.initialize().has_value() || !store_.setAccountEnabled(selected, enabled).has_value()) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT UPDATE FAILED"));
        return false;
    }
    refresh();
    setStatusLine(enabled ? QStringLiteral("SUCCESS / ACCOUNT ENABLED") : QStringLiteral("SUCCESS / ACCOUNT DISABLED"));
    return true;
}

bool MainViewModel::removeAccount(const QString& judge) {
    const auto selected = JudgeId::fromString(judge);
    if (selected == JudgeId::Unknown || selected == JudgeId::All) {
        setStatusLine(QStringLiteral("ERROR / INVALID CONNECTOR"));
        return false;
    }
    if (!store_.initialize().has_value() || !store_.removeAccount(selected).has_value()) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT REMOVE FAILED"));
        return false;
    }
    refresh();
    setStatusLine(QStringLiteral("SUCCESS / ACCOUNT REMOVED"));
    return true;
}

void MainViewModel::startSync(const QString& judge) {
    if (busy_) return;
    const auto selected = JudgeId::fromString(judge);
    if (selected == JudgeId::Unknown) { setStatusLine(QStringLiteral("ERROR / UNKNOWN CONNECTOR")); return; }
    if (!store_.initialize().has_value()) { setStatusLine(QStringLiteral("ERROR / LOCAL STORE UNAVAILABLE")); return; }
    QList<JudgeAccount> accounts;
    for (const auto& account : store_.accounts()) {
        if (account.enabled && (selected == JudgeId::All || selected == account.judge)) accounts.append(account);
    }
    if (accounts.isEmpty()) { setStatusLine(QStringLiteral("EMPTY / CONFIGURE A CONNECTOR FIRST")); return; }
    startSyncAccounts(accounts, judge.toUpper());
}

void MainViewModel::retryHistory(qint64 operationId) {
    if (busy_) return;
    if (!store_.initialize().has_value()) { setStatusLine(QStringLiteral("ERROR / LOCAL STORE UNAVAILABLE")); return; }
    const auto operations = store_.recentOperations(std::nullopt, 1000);
    const auto operation = std::find_if(operations.cbegin(), operations.cend(), [operationId](const auto& candidate) {
        return candidate.id == operationId;
    });
    if (operation == operations.cend()) {
        setStatusLine(QStringLiteral("ERROR / HISTORY OPERATION NOT FOUND"));
        return;
    }
    const auto accounts = store_.accounts();
    const auto account = std::find_if(accounts.cbegin(), accounts.cend(), [&operation](const auto& candidate) {
        return candidate.judge == operation->account.judge &&
               judgeHandlesMatch(candidate.judge, candidate.handle, operation->account.handle);
    });
    if (account == accounts.cend()) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT NOT CONFIGURED"));
        return;
    }
    if (!account->enabled) {
        setStatusLine(QStringLiteral("ERROR / ACCOUNT DISABLED"));
        return;
    }
    startSyncAccounts({*account}, judgeIdToString(account->judge).toUpper());
}

void MainViewModel::startSyncAccounts(const QList<JudgeAccount>& accounts, const QString& label) {
    if (accounts.isEmpty()) return;

    stopSource_ = std::make_shared<std::stop_source>();
    const auto stop = stopSource_;
    const auto dbPath = paths_.databaseFile;
    setBusy(true);
    setStatusLine(QStringLiteral("SYNCING / %1").arg(label));
    syncThread_ = QThread::create([this, dbPath, accounts, stop, transport = transport_] {
        SqliteStore workerStore(dbPath);
        if (!workerStore.initialize().has_value()) {
            QMetaObject::invokeMethod(this, [this] { setStatusLine(QStringLiteral("ERROR / LOCAL STORE UNAVAILABLE")); setBusy(false); }, Qt::QueuedConnection);
            return;
        }
        QtHttpTransport defaultTransport;
        auto& selectedTransport = transport ? *transport : static_cast<IHttpTransport&>(defaultTransport);
        SyncEngine engine(workerStore, selectedTransport);
        QList<SyncReport> reports;
        for (const auto& account : accounts) {
            const auto report = engine.sync(account, QStringLiteral("linux-0.1"), {}, stop->get_token());
            reports.push_back(report);
        }
        QMetaObject::invokeMethod(this, [this, reports] {
            setBusy(false);
            refresh();
            setStatusLine(syncCompletionStatusLine(reports));
        }, Qt::QueuedConnection);
    });
    connect(syncThread_, &QThread::finished, this, [this] { syncThread_->deleteLater(); syncThread_ = nullptr; stopSource_.reset(); });
    syncThread_->start();
}

void MainViewModel::cancel() {
    if (stopSource_) stopSource_->request_stop();
    if (busy_) setStatusLine(QStringLiteral("CANCEL REQUESTED / CORE WILL CLOSE THE OPERATION"));
}

} // namespace OjNexus::Gui
