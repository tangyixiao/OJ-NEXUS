#pragma once

#include <QList>
#include <optional>
#include <QString>

#include "ojnexus/core/domain.h"
#include "ojnexus/core/store.h"

namespace OjNexus {

/// One archived portable package header. The archive holds a single package
/// at a time. generatorJson is the canonical generator-object JSON.
struct PortableArchivePackageRow {
    QString identity;
    QString scopeId;
    QString sourcePlatform;
    QString generatorJson;
    qint64 createdAtEpochMs = 0;
    QString countsJson;
};

/// One archived record row; payloadJson is the canonical record JSON, stored verbatim.
struct PortableArchiveRecordRow {
    QString collection;
    QString stableId;
    QString sourceScopeId;
    QString origin;
    QString state;
    QString payloadJson;
    QString sourceTag;
    bool synthetic = false;
    qint64 sortOrder = 0;
};

/// Bounded portable archive over the schema v3 tables. The repository owns
/// contract semantics; this store only persists whole packages transactionally.
/// Replaces are all-or-nothing: any failure before commit leaves the previous
/// archive untouched, and cancel/failure can never leave partial rows.
class PortableArchiveStore final {
public:
    /// The store must be initialized (guarded startup) before archive use.
    explicit PortableArchiveStore(SqliteStore& store);

    /// Swaps the whole archive to the given package and records in one transaction.
    Result<void> replaceAll(const PortableArchivePackageRow& package, const QList<PortableArchiveRecordRow>& records);
    /// Read-only read of the archived package header, or null when the archive is empty.
    Result<std::optional<PortableArchivePackageRow>> getPackage() const;
    /// Read-only read of the archived records for identity, in insertion order.
    Result<QList<PortableArchiveRecordRow>> getRecords(const QString& identity) const;
    /// Removes every archived row. Used by tests; production flow replaces wholesale instead.
    Result<void> clear();

private:
    SqliteStore& store_;
};

} // namespace OjNexus
