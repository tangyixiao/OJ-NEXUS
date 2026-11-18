#pragma once

#include <QByteArray>
#include <QList>
#include <QMap>
#include <QString>

#include "ojnexus/core/portable_json.h"
#include "ojnexus/core/portable_models.h"

namespace OjNexus::Portable {

/// One validated record in document order, with its canonical JSON payload.
struct ValidatedRecord {
    QString collection;
    int ordinal = 0;
    QString canonicalJson;
    QString stableId;
    QString sourceScopeId;
    QString origin;
    bool synthetic = false;
};

/// A fully validated package: manifest data plus per-collection records.
struct ValidatedPackage {
    PortableValue root;
    QByteArray canonicalBytes;
    QString identity;
    QString scopeId;
    QString sourcePlatform;
    QString generator;
    QString generatorJson;
    qint64 createdAtEpochMs = 0;
    QMap<QString, int> counts;
    QString sourceTag;
    bool synthetic = false;
    QMap<QString, QList<ValidatedRecord>> records;
};

/// Validates and classifies portable packages. Stateless; safe to call from a
/// preview (read-only) or from an apply (before any store write).
class PortableCodec {
public:
    /// Canonical bytes of a parsed value (delegates to the frozen writer).
    QByteArray canonicalBytes(const PortableValue& value) const;
    /// SHA-256 hex identity of canonical bytes.
    QString identity(const QByteArray& canonicalBytes) const;
    /// Validate one package. Throws PortableContractException with exactly one
    /// category on the first failure. Performs NO writes of any kind.
    ValidatedPackage validate(const QByteArray& bytes) const;
    /// Capability tiers for a validated package.
    QList<TierDecision> classify(const ValidatedPackage& package) const;
    /// Read-only preview: assemble counts, sources, decisions and conflicts.
    PortablePreview preview(const QByteArray& bytes) const;
};

} // namespace OjNexus::Portable
