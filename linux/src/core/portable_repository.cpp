#include "ojnexus/core/portable_repository.h"

#include <QCryptographicHash>
#include <QStringList>

namespace OjNexus::Portable {
namespace {

int countTier(const ValidatedPackage& package, PortableTier tier)
{
    const PortableCodec codec;
    int count = 0;
    for (const auto& decision : codec.classify(package)) {
        if (decision.tier == tier) ++count;
    }
    return count;
}

/// Compact wire form of the manifest counts, keys in collections order
/// (mirrors System.Text.Json's compact dictionary output on the references).
QString countsJson(const QMap<QString, int>& counts)
{
    QStringList pairs;
    for (const auto& collection : PortableSpec::collections()) {
        pairs << QStringLiteral("\"%1\":%2").arg(collection).arg(counts.value(collection));
    }
    return QStringLiteral("{%1}").arg(pairs.join(QLatin1Char(',')));
}

} // namespace

PortableRepository::PortableRepository(PortableArchiveStore store) : store_(std::move(store)) {}

PortablePreview PortableRepository::preview(const QByteArray& bytes) const
{
    return codec_.preview(bytes);
}

PortableApplyResult PortableRepository::apply(const QByteArray& bytes)
{
    const int generation = applyGeneration_.load();
    // PortableContractException propagates: whole-package reject, zero writes.
    const auto package = codec_.validate(bytes);

    const auto storedResult = store_.getPackage();
    if (!storedResult.has_value()) {
        // Guarded-store read failure (SyncError::Storage); nothing was written.
        throw PortableStorageFailure{};
    }
    ensureNotCancelled(generation);
    const auto& stored = storedResult.value();

    int previousRows = 0;
    if (stored.has_value()) {
        const auto previous = store_.getRecords(stored->identity);
        if (previous.has_value()) previousRows = previous.value().size();
    }

    if (stored.has_value() && stored->identity == package.identity) {
        PortableApplyResult result;
        result.identity = package.identity;
        result.editableCount = countTier(package, PortableTier::Editable);
        result.storedOnlyCount = countTier(package, PortableTier::StoredOnly);
        result.rejectedCount = 0;
        result.rowsUpserted = 0;
        result.rowsDeleted = 0;
        result.identical = true;
        return result;
    }

    PortableArchivePackageRow header;
    header.identity = package.identity;
    header.scopeId = package.scopeId;
    header.sourcePlatform = package.sourcePlatform;
    header.generatorJson = package.generatorJson;
    header.createdAtEpochMs = package.createdAtEpochMs;
    header.countsJson = countsJson(package.counts);

    QList<PortableArchiveRecordRow> rows;
    qint64 ordinal = 0;
    for (const auto& collection : PortableSpec::collections()) {
        const auto editable = collection == QStringLiteral("problems") || collection == QStringLiteral("reviews");
        for (const auto& record : package.records.value(collection)) {
            PortableArchiveRecordRow row;
            row.collection = collection;
            row.stableId = record.stableId;
            row.sourceScopeId = record.sourceScopeId;
            row.origin = record.origin;
            row.state = editable ? QStringLiteral("editable") : QStringLiteral("stored_only");
            row.payloadJson = record.canonicalJson;
            row.sourceTag = package.sourceTag;
            row.synthetic = record.synthetic;
            row.sortOrder = ordinal++;
            rows.push_back(std::move(row));
        }
    }

    ensureNotCancelled(generation); // last pre-write checkpoint.
    const auto replaced = store_.replaceAll(header, rows);
    if (!replaced.has_value()) {
        // The store rolled back; no partial rows exist. Surface the storage
        // failure through the same channel as the read failure.
        throw PortableStorageFailure{};
    }

    int editableCount = 0;
    int storedOnlyCount = 0;
    for (const auto& row : rows) {
        if (row.state == QStringLiteral("editable")) ++editableCount;
        else ++storedOnlyCount;
    }
    PortableApplyResult result;
    result.identity = package.identity;
    result.editableCount = editableCount;
    result.storedOnlyCount = storedOnlyCount;
    result.rejectedCount = 0;
    result.rowsUpserted = rows.size();
    result.rowsDeleted = previousRows;
    result.identical = false;
    return result;
}

QByteArray PortableRepository::exportArchive() const
{
    const auto storedResult = store_.getPackage();
    if (!storedResult.has_value() || !storedResult.value().has_value()) {
        throw PortableExportException{QStringLiteral("portable archive is empty; nothing to export")};
    }
    const auto& stored = *storedResult.value();
    const auto recordsResult = store_.getRecords(stored.identity);
    if (!recordsResult.has_value()) {
        throw PortableExportException{QStringLiteral("portable archive identity mismatch")};
    }
    const auto& records = recordsResult.value();

    QList<ObjectEntry> entries;
    entries.push_back(ObjectEntry{QStringLiteral("formatVersion"), PortableValue::number(QStringLiteral("1"))});
    entries.push_back(ObjectEntry{QStringLiteral("scopeId"), PortableValue::string(stored.scopeId)});
    entries.push_back(ObjectEntry{QStringLiteral("sourcePlatform"), PortableValue::string(stored.sourcePlatform)});
    entries.push_back(ObjectEntry{QStringLiteral("generator"),
        PortableJson::parse(stored.generatorJson.toUtf8())});
    entries.push_back(ObjectEntry{QStringLiteral("createdAtEpochMs"),
        PortableValue::number(QString::number(stored.createdAtEpochMs))});

    QList<ObjectEntry> countsEntries;
    QList<ObjectEntry> arrayEntries;
    for (const auto& collection : PortableSpec::collections()) {
        QList<PortableValue> collectionRecords;
        for (const auto& record : records) {
            if (record.collection == collection) collectionRecords.push_back(
                PortableJson::parse(record.payloadJson.toUtf8()));
        }
        countsEntries.push_back(ObjectEntry{collection,
            PortableValue::number(QString::number(static_cast<qint64>(collectionRecords.size())))});
        arrayEntries.push_back(ObjectEntry{collection, PortableValue::array(std::move(collectionRecords))});
    }

    entries.push_back(ObjectEntry{QStringLiteral("counts"), PortableValue::object(std::move(countsEntries))});
    for (auto& array : arrayEntries) {
        entries.push_back(std::move(array));
    }

    const auto canonical = PortableJson::canonicalBytes(PortableValue::object(std::move(entries)));
    const auto identity = QString::fromLatin1(
        QCryptographicHash::hash(canonical, QCryptographicHash::Sha256).toHex());
    if (identity != stored.identity) {
        throw PortableExportException{QStringLiteral("portable archive identity mismatch")};
    }
    return canonical;
}

void PortableRepository::cancel()
{
    applyGeneration_.fetch_add(1);
}

void PortableRepository::ensureNotCancelled(int generation) const
{
    if (applyGeneration_.load() != generation) {
        throw PortableApplyCancelled{};
    }
}

} // namespace OjNexus::Portable
