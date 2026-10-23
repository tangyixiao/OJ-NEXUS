#pragma once

#include <QAbstractListModel>
#include <QVariantList>
#include <QThread>
#include <memory>
#include <stop_token>

#include "ojnexus/core/paths.h"
#include "ojnexus/core/store.h"

namespace OjNexus::Gui {

QString syncCompletionStatusLine(const QList<SyncReport>& reports);

class HistoryModel final : public QAbstractListModel {
    Q_OBJECT
public:
    enum Role { OperationIdRole = Qt::UserRole + 1, JudgeRole, HandleRole, StatusRole, ErrorRole, StartedRole, ModuleCountRole, ModuleSummaryRole };
    explicit HistoryModel(QObject* parent = nullptr);
    int rowCount(const QModelIndex& parent = {}) const override;
    QVariant data(const QModelIndex& index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;
    void setOperations(QList<SyncOperation> operations);

private:
    QList<SyncOperation> operations_;
};

class MainViewModel final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString currentPage READ currentPage WRITE setCurrentPage NOTIFY currentPageChanged)
    Q_PROPERTY(QString statusLine READ statusLine NOTIFY statusLineChanged)
    Q_PROPERTY(QString accountSummary READ accountSummary NOTIFY accountSummaryChanged)
    Q_PROPERTY(QString profileSummary READ profileSummary NOTIFY profileSummaryChanged)
    Q_PROPERTY(QString historyFilter READ historyFilter WRITE setHistoryFilter NOTIFY historyFilterChanged)
    Q_PROPERTY(QVariantList configuredAccounts READ configuredAccounts NOTIFY configuredAccountsChanged)
    Q_PROPERTY(QVariantList capabilityRows READ capabilityRows NOTIFY capabilityRowsChanged)
    Q_PROPERTY(bool busy READ busy NOTIFY busyChanged)
    Q_PROPERTY(HistoryModel* history READ history CONSTANT)

public:
    explicit MainViewModel(QObject* parent = nullptr);
    ~MainViewModel() override;

    QString currentPage() const { return currentPage_; }
    void setCurrentPage(const QString& page);
    QString statusLine() const { return statusLine_; }
    QString accountSummary() const { return accountSummary_; }
    QString profileSummary() const { return profileSummary_; }
    QString historyFilter() const { return historyFilter_; }
    QVariantList configuredAccounts() const { return configuredAccounts_; }
    QVariantList capabilityRows() const { return capabilityRows_; }
    bool busy() const { return busy_; }
    HistoryModel* history() { return &history_; }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void configureAccount(const QString& judge, const QString& handle);
    Q_INVOKABLE bool setAccountEnabled(const QString& judge, bool enabled);
    Q_INVOKABLE bool removeAccount(const QString& judge);
    Q_INVOKABLE void setHistoryFilter(const QString& filter);
    Q_INVOKABLE void startSync(const QString& judge);
    Q_INVOKABLE void retryHistory(qint64 operationId);
    Q_INVOKABLE void cancel();

signals:
    void currentPageChanged();
    void statusLineChanged();
    void accountSummaryChanged();
    void profileSummaryChanged();
    void historyFilterChanged();
    void configuredAccountsChanged();
    void capabilityRowsChanged();
    void busyChanged();

private:
    void setBusy(bool value);
    void setStatusLine(const QString& value);
    void startSyncAccounts(const QList<JudgeAccount>& accounts, const QString& label);
    AppPaths paths_;
    SqliteStore store_;
    HistoryModel history_;
    QString currentPage_ = QStringLiteral("DASHBOARD");
    QString statusLine_ = QStringLiteral("READY / LOCAL STATE");
    QString accountSummary_ = QStringLiteral("0 CONNECTORS");
    QString profileSummary_ = QStringLiteral("NONE / NO PROFILE SNAPSHOT");
    QString historyFilter_ = QStringLiteral("all");
    QVariantList configuredAccounts_;
    QVariantList capabilityRows_;
    bool busy_ = false;
    QThread* syncThread_ = nullptr;
    std::shared_ptr<std::stop_source> stopSource_;
};

} // namespace OjNexus::Gui
