#include <QCryptographicHash>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTextStream>

#include "ojnexus/cli/runner.h"
#include "ojnexus/core/portable_repository.h"

namespace OjNexus::Cli {
namespace {

void emitJsonObject(const QJsonObject& object, QTextStream& output) {
    output << QString::fromUtf8(QJsonDocument(object).toJson(QJsonDocument::Compact)) << Qt::endl;
}

QJsonObject previewJson(const Portable::PortablePreview& preview) {
    QJsonArray sources;
    for (const auto& source : preview.sources) {
        sources.push_back(QJsonObject{{QStringLiteral("sourceScopeId"), source.sourceScopeId},
                                      {QStringLiteral("recordCount"), source.recordCount}});
    }
    QJsonObject counts;
    for (auto it = preview.counts.cbegin(); it != preview.counts.cend(); ++it) {
        counts.insert(it.key(), it.value());
    }
    return QJsonObject{{QStringLiteral("status"), QStringLiteral("preview")},
                       {QStringLiteral("identity"), preview.identity},
                       {QStringLiteral("scopeId"), preview.scopeId},
                       {QStringLiteral("sourcePlatform"), preview.sourcePlatform},
                       {QStringLiteral("generator"), preview.generator},
                       {QStringLiteral("counts"), counts},
                       {QStringLiteral("sources"), sources},
                       {QStringLiteral("editable"), preview.editableCount},
                       {QStringLiteral("storedOnly"), preview.storedOnlyCount},
                       {QStringLiteral("rejected"), preview.rejectedCount},
                       {QStringLiteral("acceptAll"), preview.acceptAll}};
}

QString previewHuman(const Portable::PortablePreview& preview) {
    QStringList lines;
    lines << QStringLiteral("IDENTITY %1").arg(preview.identity);
    lines << QStringLiteral("SCOPE %1 PLATFORM %2").arg(preview.scopeId, preview.sourcePlatform);
    lines << QStringLiteral("GENERATOR %1").arg(preview.generator);
    lines << QStringLiteral("COUNTS:");
    for (const auto& collection : Portable::PortableSpec::collections()) {
        lines << QStringLiteral("  %1 %2").arg(collection).arg(preview.counts.value(collection));
    }
    lines << QStringLiteral("SOURCES:");
    if (preview.sources.isEmpty()) {
        lines << QStringLiteral("  NONE");
    } else {
        for (const auto& source : preview.sources) {
            lines << QStringLiteral("  %1 %2").arg(source.sourceScopeId).arg(source.recordCount);
        }
    }
    lines << QStringLiteral("CAPABILITY EDITABLE %1 / STORED-ONLY %2 / REJECTED %3")
        .arg(preview.editableCount).arg(preview.storedOnlyCount).arg(preview.rejectedCount);
    lines << QStringLiteral("STORED-ONLY RECORDS KEEP SAVED ARCHIVE STATE");
    if (!preview.skipped.isEmpty()) {
        lines << QStringLiteral("SKIPPED:");
        for (const auto& skipped : preview.skipped) {
            lines << QStringLiteral("  %1 %2").arg(skipped.collection, skipped.stableId);
        }
    }
    if (!preview.conflicts.isEmpty()) {
        lines << QStringLiteral("CONFLICTS:");
        for (const auto& conflict : preview.conflicts) {
            lines << QStringLiteral("  %1 %2 %3 %4").arg(conflict.collection, conflict.stableId, conflict.kind, conflict.detail);
        }
    }
    return lines.join(QLatin1Char('\n'));
}

void printRejected(const Portable::PortableReject& reject, bool json, QTextStream& output) {
    if (json) {
        emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("rejected")},
                                   {QStringLiteral("category"), reject.wire()},
                                   {QStringLiteral("detail"), reject.detail},
                                   {QStringLiteral("location"), reject.location}}, output);
    } else {
        output << QStringLiteral("REJECTED %1: %2 @ %3").arg(reject.wire(), reject.detail, reject.location) << Qt::endl;
    }
}

/// Reads the package file; on failure prints the storage-io error lines and
/// returns false (the caller maps that to exit code 6).
bool readPackageBytes(const QString& path, QByteArray& bytes, bool json, QTextStream& output) {
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly)) {
        if (json) {
            emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("error")},
                                       {QStringLiteral("error"), QStringLiteral("STORAGE_IO_FAILURE")}}, output);
        } else {
            output << QStringLiteral("PACKAGE READ UNAVAILABLE: STORAGE_IO_FAILURE") << Qt::endl;
        }
        return false;
    }
    bytes = file.readAll();
    return true;
}

} // namespace

int runPortable(const Command& command, SqliteStore& store, QTextStream& output, QTextStream& error) {
    PortableArchiveStore archiveStore(store);
    Portable::PortableRepository repository(archiveStore);

    if (command.kind == CommandKind::PortableExport) {
        QByteArray bytes;
        try {
            bytes = repository.exportArchive();
        } catch (const Portable::PortableExportException& exception) {
            if (command.json) {
                emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("error")},
                                           {QStringLiteral("error"), QStringLiteral("ARCHIVE_EMPTY")},
                                           {QStringLiteral("detail"), QString::fromUtf8(exception.what())}}, output);
            } else {
                output << QStringLiteral("ARCHIVE EMPTY: %1").arg(QString::fromUtf8(exception.what())) << Qt::endl;
            }
            return GeneralError;
        }

        QFile target(command.file);
        if (!target.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
            if (command.json) {
                emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("error")},
                                           {QStringLiteral("error"), QStringLiteral("STORAGE_IO_FAILURE")}}, output);
            } else {
                output << QStringLiteral("EXPORT WRITE UNAVAILABLE: STORAGE_IO_FAILURE") << Qt::endl;
            }
            return StorageError;
        }
        if (target.write(bytes) != bytes.size()) {
            if (command.json) {
                emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("error")},
                                           {QStringLiteral("error"), QStringLiteral("STORAGE_IO_FAILURE")}}, output);
            } else {
                output << QStringLiteral("EXPORT WRITE UNAVAILABLE: STORAGE_IO_FAILURE") << Qt::endl;
            }
            return StorageError;
        }

        const auto identity = QString::fromLatin1(
            QCryptographicHash::hash(bytes, QCryptographicHash::Sha256).toHex());
        if (command.json) {
            emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("exported")},
                                       {QStringLiteral("file"), command.file},
                                       {QStringLiteral("identity"), identity},
                                       {QStringLiteral("bytes"), bytes.size()}}, output);
        } else {
            output << QStringLiteral("EXPORTED %1 (%2 BYTES) -> %3")
                .arg(identity.left(12)).arg(bytes.size()).arg(command.file) << Qt::endl;
        }
        return Ok;
    }

    QByteArray bytes;
    if (!readPackageBytes(command.file, bytes, command.json, output)) return StorageError;

    try {
        if (command.kind == CommandKind::PortableInspect) {
            const auto preview = repository.preview(bytes);
            if (command.json) emitJsonObject(previewJson(preview), output);
            else output << previewHuman(preview) << Qt::endl;
            return Ok;
        }

        // Import.
        const auto preview = repository.preview(bytes);
        if (!command.yes) {
            // The confirm gate: printing the preview writes nothing.
            if (command.json) emitJsonObject(previewJson(preview), output);
            else {
                output << previewHuman(preview) << Qt::endl;
                output << QStringLiteral("CONFIRMATION REQUIRED: RERUN WITH --yes") << Qt::endl;
            }
            return Ok;
        }

        const auto result = repository.apply(bytes);
        if (command.json) {
            emitJsonObject(QJsonObject{{QStringLiteral("status"), QStringLiteral("applied")},
                                       {QStringLiteral("identity"), result.identity},
                                       {QStringLiteral("rowsUpserted"), result.rowsUpserted},
                                       {QStringLiteral("rowsDeleted"), result.rowsDeleted},
                                       {QStringLiteral("identical"), result.identical},
                                       {QStringLiteral("editable"), result.editableCount},
                                       {QStringLiteral("storedOnly"), result.storedOnlyCount},
                                       {QStringLiteral("rejected"), result.rejectedCount}}, output);
        } else if (result.identical) {
            output << QStringLiteral("ARCHIVE ALREADY MATCHES PACKAGE. NO CHANGES APPLIED.") << Qt::endl;
        } else {
            output << QStringLiteral("APPLIED %1 UPSERTED / %2 DELETED").arg(result.rowsUpserted).arg(result.rowsDeleted) << Qt::endl;
        }
        return Ok;
    } catch (const Portable::PortableContractException& exception) {
        // A whole-package reject is a valid outcome: nothing was written.
        printRejected(exception.reject(), command.json, output);
        return Ok;
    } catch (const Portable::PortableApplyCancelled&) {
        error << QStringLiteral("portable apply cancelled before any write") << Qt::endl;
        return Cancelled;
    } catch (const Portable::PortableStorageFailure&) {
        error << QStringLiteral("storage unavailable") << Qt::endl;
        return StorageError;
    }
}

} // namespace OjNexus::Cli
