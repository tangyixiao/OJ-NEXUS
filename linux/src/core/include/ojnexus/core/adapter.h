#pragma once

#include <functional>
#include <stop_token>

#include <QJsonArray>

#include "ojnexus/core/http_transport.h"
#include "ojnexus/core/store.h"

namespace OjNexus {

struct SyncContext {
    SqliteStore& store;
    IHttpTransport& transport;
    qint64 operationId = 0;
    int requestIntervalMs = 2100;
    std::function<void(const SyncModuleOutcome&)> onModule;
    QMap<QByteArray, QByteArray> requestHeaders;
    bool modulePersistenceFailed = false;
    SyncError modulePersistenceError = SyncError::Storage;
};

Result<int> persistJsonRows(const QJsonArray& rows,
                            std::stop_token cancellation,
                            const std::function<Result<void>(const QJsonObject&)>& persist);

class IJudgeAdapter {
public:
    virtual ~IJudgeAdapter() = default;
    virtual JudgeId::Value judge() const = 0;
    virtual QString reliability() const = 0;
    virtual QStringList capabilities() const = 0;
    virtual QList<SyncModuleOutcome> sync(const JudgeAccount& account,
                                          SyncContext& context,
                                          std::stop_token cancellation) = 0;
};

class CodeforcesAdapter final : public IJudgeAdapter {
public:
    JudgeId::Value judge() const override { return JudgeId::Codeforces; }
    QString reliability() const override { return QStringLiteral("OFFICIAL"); }
    QStringList capabilities() const override;
    QList<SyncModuleOutcome> sync(const JudgeAccount& account,
                                  SyncContext& context,
                                  std::stop_token cancellation) override;
};

class AtCoderAdapter final : public IJudgeAdapter {
public:
    JudgeId::Value judge() const override { return JudgeId::AtCoder; }
    QString reliability() const override { return QStringLiteral("COMMUNITY_SOURCE_WITH_EXPERIMENTAL_PROFILE_RATING"); }
    QStringList capabilities() const override;
    QList<SyncModuleOutcome> sync(const JudgeAccount& account,
                                  SyncContext& context,
                                  std::stop_token cancellation) override;
};

class LuoguAdapter final : public IJudgeAdapter {
public:
    JudgeId::Value judge() const override { return JudgeId::Luogu; }
    QString reliability() const override { return QStringLiteral("PUBLIC_SITE_WITH_EXPERIMENTAL_SUBMISSIONS"); }
    QStringList capabilities() const override;
    QList<SyncModuleOutcome> sync(const JudgeAccount& account,
                                  SyncContext& context,
                                  std::stop_token cancellation) override;
};

} // namespace OjNexus
