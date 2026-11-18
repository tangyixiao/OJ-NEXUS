#pragma once

#include <QByteArray>
#include <QHash>
#include <QList>
#include <QMap>
#include <QSet>
#include <QString>
#include <QStringList>

#include <stdexcept>

namespace OjNexus::Portable {

/// Frozen shared constants for Portable Workspace v1 (phase 134, ported from
/// Android PortableModels.kt via the Windows PortableModels.cs). Wire values
/// stay snake_case. Contract binding: contracts/portable/v1/schema.json +
/// check.py (phase 131).
struct PortableSpec {
    static constexpr int kMaxPackageBytes = 64 * 1024 * 1024;
    static constexpr int kMaxStringBytes = 16 * 1024;
    static constexpr int kMaxRecords = 50'000;
    static constexpr int kFormatVersion = 1;

    static const QStringList& collections();
    static const QSet<QString>& categories();
    static const QSet<QString>& credentialTokens();
    static const QSet<QString>& judges();
    static const QSet<QString>& origins();
    static const QSet<QString>& platforms();
    static const QSet<QString>& verdicts();
    static const QSet<QString>& lastResults();
    static const QSet<QString>& sessionTypes();
    static const QSet<QString>& sessionStates();
    static const QSet<QString>& rootKeys();
    static const QSet<QString>& entityKeys(const QString& collection);
    static const QSet<QString>& entityRequired(const QString& collection);

    static const QString& stableIdPattern();
    static const QString& externalIdPattern();
    static const QString& dayKeyPattern();
    static const QString& sourceUrlPrefix();
};

/// Every rejection carries exactly one of these categories (matching check.py).
enum class PortableRejectCategory {
    InvalidInput,
    UnsupportedVersion,
    UnknownField,
    DuplicateId,
    MissingReference,
    SizeLimit,
    PathUnsafe,
    CountsMismatch,
};

QString portableRejectCategoryWire(PortableRejectCategory category);

/// Why a record landed in a given capability tier.
enum class PortableTier {
    Editable,
    StoredOnly,
    Rejected,
};

enum class PortableTierReason {
    LiveDomainUi,
    BoundedArchiveNoDomainUi,
    UnknownField,
    IncompatibleEnum,
    IncompatibleVersion,
    IncompatibleReference,
};

QString portableTierReasonWire(PortableTierReason reason);

/// A single capability-tier decision, reported in the preview.
struct TierDecision {
    QString collection;
    QString stableId;
    PortableTier tier = PortableTier::StoredOnly;
    PortableTierReason reason = PortableTierReason::BoundedArchiveNoDomainUi;
};

/// Per-source-scope provenance counts shown in the preview.
struct SourceBreakdown {
    QString sourceScopeId;
    int recordCount = 0;
};

/// A conflict surfaced (never auto-resolved) in the preview.
struct PortableConflict {
    QString collection;
    QString stableId;
    QString kind;
    QString detail;
};

/// Read-only preview result. Producing it MUST NOT write to the database.
struct PortablePreview {
    QString scopeId;
    QString sourcePlatform;
    QString generator;
    qint64 createdAtEpochMs = 0;
    QMap<QString, int> counts;
    QList<SourceBreakdown> sources;
    QList<TierDecision> decisions;
    QList<PortableConflict> conflicts;
    QList<TierDecision> skipped;
    int editableCount = 0;
    int storedOnlyCount = 0;
    int rejectedCount = 0;
    bool acceptAll = false;
    /// Canonical bytes of the whole validated package, kept for the confirm step.
    QByteArray canonicalBytes;
    /// SHA-256 hex of canonicalBytes, for lossless round-trip verification.
    QString identity;
};

/// Result of a confirmed transactional apply.
struct PortableApplyResult {
    QString identity;
    int editableCount = 0;
    int storedOnlyCount = 0;
    int rejectedCount = 0;
    int rowsUpserted = 0;
    int rowsDeleted = 0;
    /// True when the incoming identity equals the stored one: the apply was a no-op.
    bool identical = false;
};

/// Wire-level rejection surfaced to the UI as a short status line.
struct PortableReject {
    PortableRejectCategory category = PortableRejectCategory::InvalidInput;
    QString detail;
    QString location;

    QString wire() const { return portableRejectCategoryWire(category); }
    QString toString() const
    {
        return QStringLiteral("%1: %2 @ %3").arg(wire(), detail, location);
    }
};

/// Thrown by the codec on contract violations; carries exactly one reject.
class PortableContractException final : public std::runtime_error {
public:
    explicit PortableContractException(PortableReject reject)
        : std::runtime_error(reject.toString().toStdString()), reject_(std::move(reject)) {}

    const PortableReject& reject() const { return reject_; }

private:
    PortableReject reject_;
};

} // namespace OjNexus::Portable
