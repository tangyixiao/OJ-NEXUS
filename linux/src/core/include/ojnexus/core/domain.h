#pragma once

#include <optional>
#include <utility>
#include <variant>

#include <QDateTime>
#include <QList>
#include <QString>

namespace OjNexus {

enum class SyncError {
    InvalidConfiguration,
    UnsupportedJudge,
    Network,
    Api,
    Parse,
    Authentication,
    Offline,
    Cancelled,
    Storage,
};

struct JudgeId {
    enum Value { All, Codeforces, AtCoder, Luogu, Unknown };
    static Value fromString(const QString& value);
};

QString judgeIdToString(JudgeId::Value judge);
bool judgeHandlesMatch(JudgeId::Value judge, const QString& lhs, const QString& rhs);

namespace detail {
template <typename T>
class Result {
public:
    Result(const T& value) : value_(value) {}
    Result(T&& value) : value_(std::move(value)) {}
    Result(SyncError error) : value_(error) {}

    bool has_value() const { return std::holds_alternative<T>(value_); }
    const T& value() const { return std::get<T>(value_); }
    T& value() { return std::get<T>(value_); }
    const T* operator->() const { return &value(); }
    T* operator->() { return &value(); }
    SyncError error() const { return std::get<SyncError>(value_); }

private:
    std::variant<T, SyncError> value_;
};

template <>
class Result<void> {
public:
    Result() : error_(std::nullopt) {}
    Result(SyncError error) : error_(error) {}

    bool has_value() const { return !error_.has_value(); }
    SyncError error() const { return error_.value(); }

private:
    std::optional<SyncError> error_;
};
} // namespace detail

template <typename T>
using Result = detail::Result<T>;

struct JudgeAccount {
    JudgeId::Value judge = JudgeId::Unknown;
    QString handle;
    bool enabled = true;

    static Result<JudgeAccount> create(JudgeId::Value judge, QString rawHandle, bool enabled = true);
};

enum class SyncOperationStatus { Running, Success, Partial, Error, Offline, Cancelled, Unavailable };

struct SyncModuleOutcome {
    QString stage;
    SyncOperationStatus status = SyncOperationStatus::Error;
    int attemptedCount = 0;
    int importedCount = 0;
    int updatedCount = 0;
    QString failureType;
};

struct SyncOperation {
    qint64 id = 0;
    JudgeAccount account;
    QString dataGeneration;
    QDateTime startedAt;
    std::optional<QDateTime> finishedAt;
    SyncOperationStatus status = SyncOperationStatus::Running;
    std::optional<SyncError> error;
    QList<SyncModuleOutcome> modules;
};

struct SyncReport {
    SyncOperation operation;
    std::optional<SyncError> error;
};

QString syncStatusToString(SyncOperationStatus status);
QString syncErrorToString(SyncError error);
std::optional<SyncError> syncErrorFromString(const QString& value);

} // namespace OjNexus
