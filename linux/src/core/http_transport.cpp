#include "ojnexus/core/http_transport.h"

#include <QEventLoop>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QTimer>

namespace OjNexus {

bool isOfflineNetworkError(const QNetworkReply::NetworkError error) {
    switch (error) {
    case QNetworkReply::HostNotFoundError:
    case QNetworkReply::TemporaryNetworkFailureError:
    case QNetworkReply::NetworkSessionFailedError:
    case QNetworkReply::UnknownNetworkError:
        return true;
    default:
        return false;
    }
}

QNetworkRequest makeHttpRequest(const QUrl& url,
                                const QMap<QByteArray, QByteArray>& headers,
                                int timeoutMs) {
    QNetworkRequest request(url);
    request.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
    request.setTransferTimeout(timeoutMs);
    request.setRawHeader(QByteArrayLiteral("Accept"), QByteArrayLiteral("application/json, text/html;q=0.9"));
    request.setRawHeader(QByteArrayLiteral("User-Agent"), QByteArrayLiteral("OJ-NEXUS/0.1"));
    for (auto it = headers.cbegin(); it != headers.cend(); ++it) request.setRawHeader(it.key(), it.value());
    return request;
}

Result<HttpResponse> QtHttpTransport::get(const QUrl& url,
                                          const QMap<QByteArray, QByteArray>& headers,
                                          int timeoutMs,
                                          std::stop_token cancellation) {
    QNetworkAccessManager manager;
    const auto request = makeHttpRequest(url, headers, timeoutMs);
    auto* reply = manager.get(request);
    QEventLoop loop;
    QTimer timeout;
    timeout.setSingleShot(true);
    timeout.start(timeoutMs);
    QTimer cancellationPoll;
    bool timedOut = false;
    bool cancelled = false;
    QObject::connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    QObject::connect(&timeout, &QTimer::timeout, &loop, [reply, &loop, &timedOut] {
        timedOut = true;
        reply->abort();
        loop.quit();
    });
    QObject::connect(&cancellationPoll, &QTimer::timeout, &loop, [reply, &loop, &cancelled, cancellation] {
        if (cancellation.stop_requested()) {
            cancelled = true;
            reply->abort();
            loop.quit();
        }
    });
    cancellationPoll.start(50);
    loop.exec();
    if (cancelled || cancellation.stop_requested()) {
        reply->deleteLater();
        return SyncError::Cancelled;
    }
    if (timedOut) {
        reply->deleteLater();
        return SyncError::Network;
    }
    const auto statusCode = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    if (reply->error() != QNetworkReply::NoError && statusCode == 0) {
        const auto error = isOfflineNetworkError(reply->error()) ? SyncError::Offline : SyncError::Network;
        reply->deleteLater();
        return error;
    }
    HttpResponse response;
    response.statusCode = statusCode;
    response.body = reply->readAll();
    const auto rawHeaders = reply->rawHeaderPairs();
    for (const auto& pair : rawHeaders) response.headers.insert(pair.first, pair.second);
    reply->deleteLater();
    return response;
}

} // namespace OjNexus
