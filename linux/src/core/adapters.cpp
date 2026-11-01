#include "ojnexus/core/adapter.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QHash>
#include <QRegularExpression>
#include <QThread>
#include <QUrlQuery>

namespace OjNexus {
namespace {

Result<QByteArray> fetchBody(IHttpTransport& transport, const QUrl& url, int intervalMs, std::stop_token cancellation,
                             const QMap<QByteArray, QByteArray>& headers = {}) {
    for (int waited = 0; waited < intervalMs && !cancellation.stop_requested(); waited += 25) QThread::msleep(25);
    if (cancellation.stop_requested()) return SyncError::Cancelled;
    const auto response = transport.get(url, headers, 30000, cancellation);
    if (!response.has_value()) return response.error();
    if (response.value().statusCode == 401 || response.value().statusCode == 403) return SyncError::Authentication;
    if (response.value().statusCode < 200 || response.value().statusCode >= 300) return SyncError::Api;
    return response.value().body;
}

Result<QJsonDocument> fetchJson(IHttpTransport& transport, const QUrl& url, int intervalMs, std::stop_token cancellation,
                                const QMap<QByteArray, QByteArray>& headers = {}) {
    const auto body = fetchBody(transport, url, intervalMs, cancellation, headers);
    if (!body.has_value()) return body.error();
    QJsonParseError parseError;
    const auto document = QJsonDocument::fromJson(body.value(), &parseError);
    if (parseError.error != QJsonParseError::NoError || document.isNull()) return SyncError::Parse;
    return document;
}

QString errorName(SyncError error) { return syncErrorToString(error); }

template <typename FetchAndPersist>
SyncModuleOutcome runStage(const QString& stage, SyncContext& context, std::stop_token cancellation, FetchAndPersist&& fetchAndPersist) {
    SyncModuleOutcome outcome{stage, SyncOperationStatus::Error, 0, 0, 0, {}};
    if (context.modulePersistenceFailed) {
        outcome.failureType = errorName(context.modulePersistenceError);
    } else if (cancellation.stop_requested()) {
        outcome.failureType = QStringLiteral("CANCELLED");
        outcome.status = SyncOperationStatus::Cancelled;
    } else if (!context.store.beginTransaction().has_value()) {
        context.modulePersistenceFailed = true;
        context.modulePersistenceError = SyncError::Storage;
        outcome.failureType = errorName(SyncError::Storage);
    } else {
        const auto result = fetchAndPersist(cancellation, outcome);
        if (result.has_value()) {
            if (cancellation.stop_requested()) {
                if (!context.store.rollbackTransaction().has_value()) {
                    context.modulePersistenceFailed = true;
                    context.modulePersistenceError = SyncError::Storage;
                    outcome.failureType = errorName(SyncError::Storage);
                } else {
                    outcome.status = SyncOperationStatus::Cancelled;
                    outcome.failureType = QStringLiteral("CANCELLED");
                }
            } else if (context.store.commitTransaction().has_value()) {
                outcome.status = SyncOperationStatus::Success;
                outcome.importedCount = result.value();
                outcome.attemptedCount = result.value();
            } else {
                context.modulePersistenceFailed = true;
                context.modulePersistenceError = SyncError::Storage;
                outcome.failureType = errorName(SyncError::Storage);
            }
        } else {
            if (!context.store.rollbackTransaction().has_value()) {
                context.modulePersistenceFailed = true;
                context.modulePersistenceError = SyncError::Storage;
                outcome.failureType = errorName(SyncError::Storage);
            } else {
                outcome.failureType = errorName(result.error());
                outcome.status = result.error() == SyncError::Cancelled ? SyncOperationStatus::Cancelled
                    : (result.error() == SyncError::Offline ? SyncOperationStatus::Offline : SyncOperationStatus::Error);
            }
        }
    }
    if (!context.modulePersistenceFailed) {
        const auto persisted = context.store.appendModule(context.operationId, outcome);
        if (!persisted.has_value()) {
            context.modulePersistenceFailed = true;
            context.modulePersistenceError = persisted.error();
            outcome.status = SyncOperationStatus::Error;
            outcome.failureType = errorName(persisted.error());
        }
    }
    if (context.onModule) context.onModule(outcome);
    return outcome;
}

Result<int> envelopeArray(IHttpTransport& transport, const QUrl& url, int intervalMs, std::stop_token cancellation,
                          QJsonArray& output, const QMap<QByteArray, QByteArray>& headers = {}) {
    const auto document = fetchJson(transport, url, intervalMs, cancellation, headers);
    if (!document.has_value()) return document.error();
    if (!document.value().isObject()) return SyncError::Parse;
    const auto object = document.value().object();
    if (object.value(QStringLiteral("status")).toString() != QStringLiteral("OK")) return SyncError::Api;
    if (!object.value(QStringLiteral("result")).isArray()) return SyncError::Parse;
    output = object.value(QStringLiteral("result")).toArray();
    return output.size();
}

Result<QJsonObject> contentObject(IHttpTransport& transport, const QUrl& url, int intervalMs, std::stop_token cancellation,
                                  const QMap<QByteArray, QByteArray>& headers = {}) {
    const auto body = fetchBody(transport, url, intervalMs, cancellation, headers);
    if (!body.has_value()) return body.error();
    QJsonParseError parseError;
    auto document = QJsonDocument::fromJson(body.value(), &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        const auto html = QString::fromUtf8(body.value());
        const auto match = QRegularExpression(QStringLiteral("<script\\s+id=\\\"lentille-context\\\"[^>]*>(.*?)</script>"),
                                               QRegularExpression::DotMatchesEverythingOption | QRegularExpression::CaseInsensitiveOption).match(html);
        if (!match.hasMatch()) return SyncError::Parse;
        document = QJsonDocument::fromJson(match.captured(1).toUtf8(), &parseError);
        if (parseError.error != QJsonParseError::NoError || !document.isObject()) return SyncError::Parse;
    }
    const auto root = document.object();
    if (root.value(QStringLiteral("currentData")).isObject()) return root.value(QStringLiteral("currentData")).toObject();
    if (root.value(QStringLiteral("data")).isObject()) return root.value(QStringLiteral("data")).toObject();
    return root;
}

Result<QJsonArray> directArray(IHttpTransport& transport, const QUrl& url, int intervalMs, std::stop_token cancellation,
                               const QMap<QByteArray, QByteArray>& headers = {}) {
    const auto document = fetchJson(transport, url, intervalMs, cancellation, headers);
    if (!document.has_value()) return document.error();
    if (!document.value().isArray()) return SyncError::Parse;
    return document.value().array();
}

Result<QJsonObject> parseAtCoderProfile(const QByteArray& body, const QString& handle) {
    const auto title = QRegularExpression(QStringLiteral("<title>\\s*([^<]+?)\\s*-\\s*AtCoder"), QRegularExpression::CaseInsensitiveOption).match(QString::fromUtf8(body));
    if (!title.hasMatch()) return SyncError::Parse;
    const auto returnedHandle = title.captured(1).trimmed();
    if (!judgeHandlesMatch(JudgeId::AtCoder, returnedHandle, handle)) return SyncError::Api;
    QJsonObject profile{{QStringLiteral("user_id"), handle}, {QStringLiteral("display_name"), returnedHandle}, {QStringLiteral("source"), QStringLiteral("atcoder.jp/users")}};
    const auto history = QRegularExpression(QStringLiteral("var\\s+rank_history\\s*=\\s*(\\[.*?\\]);\\s*</script>"), QRegularExpression::DotMatchesEverythingOption).match(QString::fromUtf8(body));
    if (history.hasMatch()) {
        QJsonParseError parseError;
        const auto document = QJsonDocument::fromJson(history.captured(1).toUtf8(), &parseError);
        if (parseError.error != QJsonParseError::NoError || !document.isArray()) return SyncError::Parse;
        const auto rows = document.array();
        if (!rows.isEmpty()) profile.insert(QStringLiteral("rating"), rows.last().toObject().value(QStringLiteral("Rating")));
    }
    return profile;
}

Result<QJsonArray> parseAtCoderRatingHistory(const QByteArray& body) {
    const auto match = QRegularExpression(QStringLiteral("var\\s+rank_history\\s*=\\s*(\\[.*?\\]);\\s*</script>"), QRegularExpression::DotMatchesEverythingOption).match(QString::fromUtf8(body));
    if (!match.hasMatch()) return SyncError::Parse;
    QJsonParseError parseError;
    const auto document = QJsonDocument::fromJson(match.captured(1).toUtf8(), &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isArray()) return SyncError::Parse;
    QJsonArray normalized;
    for (const auto& value : document.array()) {
        auto row = value.toObject();
        row.insert(QStringLiteral("contestId"), row.value(QStringLiteral("ContestName")));
        row.insert(QStringLiteral("contest_id"), row.value(QStringLiteral("ContestUrl")).toString().section('/', -1));
        row.insert(QStringLiteral("newRating"), row.value(QStringLiteral("Rating")));
        row.insert(QStringLiteral("rank"), row.value(QStringLiteral("Rank")));
        normalized.push_back(row);
    }
    return normalized;
}

Result<QJsonArray> arrayFromContent(const QJsonObject& content, const QString& name) {
    const auto value = content.value(name);
    if (!value.isArray()) return SyncError::Parse;
    return value.toArray();
}

QString luoguUserKey(QString handle) {
    handle = handle.trimmed();
    if (handle.startsWith(QStringLiteral("uid:"), Qt::CaseInsensitive)) handle.remove(0, 4);
    return handle;
}

Result<QJsonArray> listResult(const QJsonObject& content, const QString& name) {
    const auto value = content.value(name);
    if (value.isArray()) return value.toArray();
    if (value.isObject() && value.toObject().value(QStringLiteral("result")).isArray()) return value.toObject().value(QStringLiteral("result")).toArray();
    return SyncError::Parse;
}

} // namespace

Result<int> persistJsonRows(const QJsonArray& rows,
                            std::stop_token cancellation,
                            const std::function<Result<void>(const QJsonObject&)>& persist) {
    int persisted = 0;
    for (const auto& value : rows) {
        if (cancellation.stop_requested()) return SyncError::Cancelled;
        const auto result = persist(value.toObject());
        if (!result.has_value()) return result.error();
        ++persisted;
    }
    return persisted;
}

QStringList CodeforcesAdapter::capabilities() const {
    return {QStringLiteral("PROFILE"), QStringLiteral("RATING"), QStringLiteral("RATING_HISTORY"),
            QStringLiteral("SUBMISSIONS"), QStringLiteral("CONTESTS"), QStringLiteral("PROBLEM_CATALOG")};
}

QList<SyncModuleOutcome> CodeforcesAdapter::sync(const JudgeAccount& account, SyncContext& context, std::stop_token cancellation) {
    QList<SyncModuleOutcome> outcomes;
    const auto base = QStringLiteral("https://codeforces.com/api/");
    outcomes.push_back(runStage(QStringLiteral("PROFILE"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("user.info"));
        QUrlQuery query; query.addQueryItem(QStringLiteral("handles"), account.handle); url.setQuery(query);
        const auto document = fetchJson(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!document.has_value()) return document.error();
        const auto object = document.value().object();
        if (object.value(QStringLiteral("status")).toString() != QStringLiteral("OK")) return SyncError::Api;
        const auto users = object.value(QStringLiteral("result")).toArray();
        if (users.isEmpty()) return SyncError::Api;
        const auto returnedHandle = users.first().toObject().value(QStringLiteral("handle")).toString();
        if (returnedHandle.isEmpty() || !judgeHandlesMatch(judge(), returnedHandle, account.handle)) return SyncError::Api;
        const auto stored = context.store.upsertProfile(judge(), account.handle, users.first().toObject());
        if (!stored.has_value()) return stored.error();
        return 1;
    }));
    outcomes.push_back(runStage(QStringLiteral("RATING"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("user.rating")); QUrlQuery query; query.addQueryItem(QStringLiteral("handle"), account.handle); url.setQuery(query);
        QJsonArray rows; const auto count = envelopeArray(context.transport, url, context.requestIntervalMs, token, rows, context.requestHeaders);
        if (!count.has_value()) return count.error();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            return context.store.upsertRatingChange(judge(), account.handle, value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("SUBMISSIONS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("user.status")); QUrlQuery query; query.addQueryItem(QStringLiteral("handle"), account.handle); query.addQueryItem(QStringLiteral("from"), QStringLiteral("1")); query.addQueryItem(QStringLiteral("count"), QStringLiteral("1000")); url.setQuery(query);
        QJsonArray rows; const auto count = envelopeArray(context.transport, url, context.requestIntervalMs, token, rows, context.requestHeaders);
        if (!count.has_value()) return count.error();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            return context.store.upsertSubmission(judge(), account.handle, value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("CONTESTS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("contest.list")); QUrlQuery query; query.addQueryItem(QStringLiteral("gym"), QStringLiteral("false")); url.setQuery(query);
        QJsonArray rows; const auto count = envelopeArray(context.transport, url, context.requestIntervalMs, token, rows, context.requestHeaders);
        if (!count.has_value()) return count.error();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            return context.store.upsertContest(judge(), value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("PROBLEMSET"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("problemset.problems"));
        const auto document = fetchJson(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!document.has_value()) return document.error();
        const auto root = document.value().object();
        if (root.value(QStringLiteral("status")).toString() != QStringLiteral("OK")) return SyncError::Api;
        const auto result = root.value(QStringLiteral("result")).toObject();
        const auto problems = result.value(QStringLiteral("problems")).toArray();
        const auto statistics = result.value(QStringLiteral("problemStatistics")).toArray();
        QHash<QString, QJsonObject> stats;
        for (const auto& value : statistics) {
            if (token.stop_requested()) return SyncError::Cancelled;
            const auto row = value.toObject();
            stats.insert(QString::number(row.value(QStringLiteral("contestId")).toInt()) + QStringLiteral("/") + row.value(QStringLiteral("index")).toString(), row);
        }
        return persistJsonRows(problems, token, [&](const QJsonObject& value) {
            auto row = value;
            const auto key = QString::number(row.value(QStringLiteral("contestId")).toInt()) + QStringLiteral("/") + row.value(QStringLiteral("index")).toString();
            row.insert(QStringLiteral("externalId"), key);
            if (stats.contains(key)) row.insert(QStringLiteral("solvedCount"), stats.value(key).value(QStringLiteral("solvedCount")));
            return context.store.upsertProblem(judge(), row);
        });
    }));
    return outcomes;
}

QStringList AtCoderAdapter::capabilities() const {
    return {QStringLiteral("PROFILE_EXPERIMENTAL"), QStringLiteral("RATING_EXPERIMENTAL"),
            QStringLiteral("SUBMISSIONS"), QStringLiteral("CONTESTS"), QStringLiteral("PROBLEM_CATALOG"),
            QStringLiteral("DIFFICULTY")};
}

QList<SyncModuleOutcome> AtCoderAdapter::sync(const JudgeAccount& account, SyncContext& context, std::stop_token cancellation) {
    QList<SyncModuleOutcome> outcomes;
    const auto base = QStringLiteral("https://kenkoooo.com/atcoder/atcoder-api/v3/");
    outcomes.push_back(runStage(QStringLiteral("PROFILE"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        const auto body = fetchBody(context.transport, QUrl(QStringLiteral("https://atcoder.jp/users/") + account.handle), context.requestIntervalMs, token, context.requestHeaders);
        if (!body.has_value()) return body.error();
        const auto profile = parseAtCoderProfile(body.value(), account.handle);
        if (!profile.has_value()) return profile.error();
        const auto stored = context.store.upsertProfile(judge(), account.handle, profile.value());
        return stored.has_value() ? Result<int>(1) : Result<int>(stored.error());
    }));
    outcomes.push_back(runStage(QStringLiteral("RATING"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        const auto body = fetchBody(context.transport, QUrl(QStringLiteral("https://atcoder.jp/users/") + account.handle), context.requestIntervalMs, token, context.requestHeaders);
        if (!body.has_value()) return body.error();
        const auto result = parseAtCoderRatingHistory(body.value());
        if (!result.has_value()) return result.error();
        const auto rows = result.value();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            auto row = value;
            row.insert(QStringLiteral("contestId"), row.value(QStringLiteral("contest_id")));
            return context.store.upsertRatingChange(judge(), account.handle, row);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("SUBMISSIONS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(base + QStringLiteral("user/submissions"));
        QUrlQuery query; query.addQueryItem(QStringLiteral("user"), account.handle); query.addQueryItem(QStringLiteral("from_second"), QStringLiteral("0")); url.setQuery(query);
        const auto result = directArray(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!result.has_value()) return result.error();
        const auto rows = result.value();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            return context.store.upsertSubmission(judge(), account.handle, value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("CONTESTS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(QStringLiteral("https://kenkoooo.com/atcoder/resources/contests.json"));
        QJsonArray rows; const auto document = fetchJson(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!document.has_value()) return document.error();
        if (!document.value().isArray()) return SyncError::Parse;
        rows = document.value().array();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            return context.store.upsertContest(judge(), value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("PROBLEMSET"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        const auto problemsUrl = QUrl(QStringLiteral("https://kenkoooo.com/atcoder/resources/problems.json"));
        const auto modelsUrl = QUrl(QStringLiteral("https://kenkoooo.com/atcoder/resources/problem-models.json"));
        const auto problems = fetchJson(context.transport, problemsUrl, context.requestIntervalMs, token, context.requestHeaders);
        if (!problems.has_value() || !problems.value().isArray()) return problems.has_value() ? Result<int>(SyncError::Parse) : Result<int>(problems.error());
        const auto models = fetchJson(context.transport, modelsUrl, context.requestIntervalMs, token, context.requestHeaders);
        if (!models.has_value() || !models.value().isObject()) return models.has_value() ? Result<int>(SyncError::Parse) : Result<int>(models.error());
        const auto modelMap = models.value().object();
        const auto rows = problems.value().array();
        return persistJsonRows(rows, token, [&](const QJsonObject& value) {
            auto row = value;
            const auto id = row.value(QStringLiteral("id")).toString();
            row.insert(QStringLiteral("externalId"), id);
            const auto model = modelMap.value(id).toObject();
            if (!model.isEmpty()) row.insert(QStringLiteral("difficulty"), model.value(QStringLiteral("difficulty")));
            return context.store.upsertProblem(judge(), row);
        });
    }));
    return outcomes;
}

QStringList LuoguAdapter::capabilities() const {
    return {QStringLiteral("PUBLIC_PROFILE"), QStringLiteral("CONTESTS"),
            QStringLiteral("PROBLEM_CATALOG"), QStringLiteral("SUBMISSIONS_EXPERIMENTAL")};
}

QList<SyncModuleOutcome> LuoguAdapter::sync(const JudgeAccount& account, SyncContext& context, std::stop_token cancellation) {
    QList<SyncModuleOutcome> outcomes;
    const auto user = luoguUserKey(account.handle);
    const auto userBase = QStringLiteral("https://www.luogu.com.cn/");
    outcomes.push_back(runStage(QStringLiteral("PROFILE"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        const auto document = fetchJson(context.transport, QUrl(userBase + QStringLiteral("api/user/info/") + user), context.requestIntervalMs, token, context.requestHeaders);
        if (!document.has_value()) return document.error();
        if (!document.value().isObject()) return SyncError::Parse;
        const auto profile = document.value().object().value(QStringLiteral("user")).toObject();
        if (profile.isEmpty()) return SyncError::Parse;
        const auto returnedUid = profile.value(QStringLiteral("uid"));
        const auto returnedHandle = returnedUid.isString()
            ? returnedUid.toString()
            : (returnedUid.isDouble() ? QString::number(returnedUid.toInteger()) : QString());
        if (returnedHandle.isEmpty() || !judgeHandlesMatch(judge(), returnedHandle, user)) return SyncError::Api;
        const auto stored = context.store.upsertProfile(judge(), user, profile);
        return stored.has_value() ? Result<int>(1) : Result<int>(stored.error());
    }));
    outcomes.push_back(runStage(QStringLiteral("SUBMISSIONS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(userBase + QStringLiteral("record/list")); QUrlQuery query; query.addQueryItem(QStringLiteral("user"), user); query.addQueryItem(QStringLiteral("page"), QStringLiteral("1")); query.addQueryItem(QStringLiteral("_contentOnly"), QStringLiteral("1")); url.setQuery(query);
        const auto content = contentObject(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!content.has_value()) return content.error();
        const auto records = listResult(content.value(), QStringLiteral("records"));
        if (!records.has_value()) return records.error();
        return persistJsonRows(records.value(), token, [&](const QJsonObject& value) {
            return context.store.upsertSubmission(judge(), account.handle, value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("CONTESTS"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(userBase + QStringLiteral("contest/list")); QUrlQuery query; query.addQueryItem(QStringLiteral("page"), QStringLiteral("1")); query.addQueryItem(QStringLiteral("_contentOnly"), QStringLiteral("1")); url.setQuery(query);
        const auto content = contentObject(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!content.has_value()) return content.error();
        const auto rows = listResult(content.value(), QStringLiteral("contests"));
        if (!rows.has_value()) return rows.error();
        return persistJsonRows(rows.value(), token, [&](const QJsonObject& value) {
            return context.store.upsertContest(judge(), value);
        });
    }));
    outcomes.push_back(runStage(QStringLiteral("PROBLEMSET"), context, cancellation, [&](std::stop_token token, SyncModuleOutcome&) -> Result<int> {
        QUrl url(userBase + QStringLiteral("problem/list")); QUrlQuery query; query.addQueryItem(QStringLiteral("page"), QStringLiteral("1")); query.addQueryItem(QStringLiteral("_contentOnly"), QStringLiteral("1")); url.setQuery(query);
        const auto content = contentObject(context.transport, url, context.requestIntervalMs, token, context.requestHeaders);
        if (!content.has_value()) return content.error();
        const auto rows = listResult(content.value(), QStringLiteral("problems"));
        if (!rows.has_value()) return rows.error();
        return persistJsonRows(rows.value(), token, [&](const QJsonObject& value) {
            return context.store.upsertProblem(judge(), value);
        });
    }));
    return outcomes;
}

} // namespace OjNexus
