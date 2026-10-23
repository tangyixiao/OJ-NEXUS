#pragma once

#include <optional>

#include <QSqlDatabase>
#include <QJsonObject>

#include "ojnexus/core/domain.h"

namespace OjNexus {

struct StoredProfile {
    QString handle;
    QJsonObject payload;
    QDateTime updatedAt;
};

struct StoredSubmission {
    QString externalId;
    QString problemId;
    QString problemName;
    QString verdict;
    QString language;
    QDateTime submittedAt;
    QDateTime updatedAt;
};

class SqliteStore final {
public:
    explicit SqliteStore(QString databasePath, int completedHistoryLimit = 20);
    ~SqliteStore();

    Result<void> initialize();
    int schemaVersion() const { return schemaVersion_; }

    Result<void> upsertAccount(const JudgeAccount& account);
    Result<void> configureAccount(const JudgeAccount& account);
    Result<void> setAccountEnabled(JudgeId::Value judge, bool enabled);
    Result<void> removeAccount(JudgeId::Value judge);
    QList<JudgeAccount> accounts() const;

    Result<void> beginTransaction();
    Result<void> commitTransaction();
    Result<void> rollbackTransaction();

    Result<qint64> openOperation(const JudgeAccount& account, const QString& dataGeneration);
    Result<void> appendModule(qint64 operationId, const SyncModuleOutcome& outcome);
    Result<void> closeOperation(qint64 operationId, SyncOperationStatus status, std::optional<SyncError> error);
    QList<SyncOperation> recentOperations(std::optional<JudgeId::Value> judge, int limit) const;

    Result<void> upsertProfile(JudgeId::Value judge, const QString& handle, const QJsonObject& profile);
    Result<void> removeProfile(JudgeId::Value judge);
    std::optional<StoredProfile> latestProfile(JudgeId::Value judge) const;
    Result<void> upsertRatingChange(JudgeId::Value judge, const QJsonObject& change);
    Result<void> upsertSubmission(JudgeId::Value judge, const QJsonObject& submission);
    QList<StoredSubmission> recentSubmissions(JudgeId::Value judge, int limit) const;
    Result<void> upsertProblem(JudgeId::Value judge, const QJsonObject& problem);
    Result<void> upsertContest(JudgeId::Value judge, const QJsonObject& contest);
    int profileCount(JudgeId::Value judge) const;
    int ratingChangeCount(JudgeId::Value judge) const;
    int submissionCount(JudgeId::Value judge) const;
    int problemCount(JudgeId::Value judge) const;
    int contestCount(JudgeId::Value judge) const;

private:
    Result<void> exec(const QString& sql) const;
    Result<void> recoverRunningOperations() const;
    Result<void> pruneCompleted(const JudgeId::Value judge) const;
    Result<void> upsertPayload(const QString& table, JudgeId::Value judge, const QString& key, const QJsonObject& payload);
    std::optional<SyncOperation> readOperation(class QSqlQuery& query) const;

    QString databasePath_;
    QString connectionName_;
    int completedHistoryLimit_ = 20;
    int schemaVersion_ = 0;
    QSqlDatabase database_;
};

} // namespace OjNexus
