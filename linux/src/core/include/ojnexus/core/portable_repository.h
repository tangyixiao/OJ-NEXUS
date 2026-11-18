#pragma once

#include <atomic>

#include <QByteArray>

#include "ojnexus/core/portable_archive_store.h"
#include "ojnexus/core/portable_codec.h"

namespace OjNexus::Portable {

/// Thrown when a confirmed apply is cancelled at a pre-write checkpoint; no
/// write has happened when this escapes.
class PortableApplyCancelled final : public std::runtime_error {
public:
    PortableApplyCancelled() : std::runtime_error("portable apply was cancelled before any write") {}
};

/// Thrown when the guarded store reports a storage failure during apply; the
/// transactional store has rolled back, so no partial rows exist.
class PortableStorageFailure final : public std::runtime_error {
public:
    PortableStorageFailure() : std::runtime_error("portable apply failed on storage") {}
};

/// Thrown by exportArchive on an empty archive or an identity mismatch.
class PortableExportException final : public std::runtime_error {
public:
    explicit PortableExportException(const QString& message)
        : std::runtime_error(message.toStdString()) {}
};

/// Orchestration between the portable codec and the bounded archive store:
/// preview() is a pure read; apply() revalidates from the bytes (never
/// trusting a prior preview), no-ops on an identical identity and otherwise
/// swaps the whole archive in the store's single transaction; exportArchive()
/// rebuilds canonical bytes from archived rows and asserts the rebuilt
/// identity; cancel() aborts an in-flight apply at cooperative pre-write
/// checkpoints and never writes.
class PortableRepository {
public:
    explicit PortableRepository(PortableArchiveStore store);

    /// Read-only validation + preview. Performs zero store calls.
    PortablePreview preview(const QByteArray& bytes) const;
    /// User-confirmed transactional apply of one package.
    PortableApplyResult apply(const QByteArray& bytes);
    /// Rebuild the whole package from archived rows. The rebuilt SHA-256 must
    /// equal the stored identity, proving no accepted record was lost.
    QByteArray exportArchive() const;
    /// Aborts an in-flight apply at its next pre-write checkpoint. Never writes.
    void cancel();

private:
    void ensureNotCancelled(int generation) const;

    PortableCodec codec_;
    PortableArchiveStore store_;
    std::atomic<int> applyGeneration_{0};
};

} // namespace OjNexus::Portable
