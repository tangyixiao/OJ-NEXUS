#pragma once

#include <stop_token>

#include "ojnexus/core/adapter.h"

namespace OjNexus {

class SyncEngine final {
public:
    SyncEngine(SqliteStore& store, IHttpTransport& transport, int requestIntervalMs = 2100);

    SyncReport sync(const JudgeAccount& account,
                    const QString& dataGeneration,
                    const QMap<QByteArray, QByteArray>& requestHeaders,
                    std::stop_token cancellation);

    static IJudgeAdapter* adapterFor(JudgeId::Value judge);

private:
    SqliteStore& store_;
    IHttpTransport& transport_;
    int requestIntervalMs_ = 2100;
};

} // namespace OjNexus
