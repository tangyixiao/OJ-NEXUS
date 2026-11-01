#pragma once

#include <stop_token>

#include <QByteArray>
#include <QMap>
#include <QNetworkRequest>
#include <QNetworkReply>
#include <QUrl>

#include "ojnexus/core/domain.h"

namespace OjNexus {

bool isOfflineNetworkError(QNetworkReply::NetworkError error);

QNetworkRequest makeHttpRequest(const QUrl& url,
                                const QMap<QByteArray, QByteArray>& headers,
                                int timeoutMs);

struct HttpResponse {
    int statusCode = 0;
    QByteArray body;
    QMap<QByteArray, QByteArray> headers;
};

class IHttpTransport {
public:
    virtual ~IHttpTransport() = default;
    virtual Result<HttpResponse> get(const QUrl& url,
                                     const QMap<QByteArray, QByteArray>& headers,
                                     int timeoutMs,
                                     std::stop_token cancellation) = 0;
};

class QtHttpTransport final : public IHttpTransport {
public:
    Result<HttpResponse> get(const QUrl& url,
                             const QMap<QByteArray, QByteArray>& headers,
                             int timeoutMs,
                             std::stop_token cancellation) override;
};

} // namespace OjNexus
