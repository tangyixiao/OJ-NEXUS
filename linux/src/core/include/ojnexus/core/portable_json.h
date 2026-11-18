#pragma once

#include <QByteArray>
#include <QList>
#include <QString>

#include <memory>

#include "ojnexus/core/portable_models.h"

namespace OjNexus::Portable {

struct ObjectEntry;

/// A decoded portable JSON value. Order of object entries is document order.
class PortableValue {
public:
    enum class Kind { Null, Bool, Num, Str, Arr, Obj };

    PortableValue() = default;

    static PortableValue null() { return {}; }
    static PortableValue boolean(bool value);
    /// A number preserved verbatim - the exact source token text is the canonical form.
    static PortableValue number(QString literal);
    static PortableValue string(QString value);
    static PortableValue array(QList<PortableValue> items);
    static PortableValue object(QList<ObjectEntry> entries);

    Kind kind() const { return kind_; }
    bool isNull() const { return kind_ == Kind::Null; }
    bool boolValue() const { return bool_; }
    /// The Num literal (kind() == Num) or the string value (kind() == Str).
    const QString& text() const { return str_; }
    const QList<PortableValue>& items() const;
    const QList<ObjectEntry>& entries() const;
    /// Object lookup by exact key; nullptr when absent or not an object.
    const PortableValue* get(const QString& key) const;

private:
    struct Payload;
    Kind kind_ = Kind::Null;
    bool bool_ = false;
    QString str_;
    std::shared_ptr<Payload> payload_;
};

struct ObjectEntry {
    QString key;
    PortableValue value;
};

/// Strict portable JSON decode and canonical re-encode. The decode is stricter
/// than a permissive parser (duplicate keys, trailing data, comments, BOM,
/// NaN/Infinity, lone surrogates, invalid UTF-8 are all INVALID_INPUT) and the
/// writer reproduces the Python canonical form byte-for-byte:
/// json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n".
/// Numbers are preserved verbatim (mirroring Android's Num(literal)).
struct PortableJson {
    /// Strict decode; throws PortableContractException (INVALID_INPUT) on any violation.
    static PortableValue parse(const QByteArray& bytes);
    /// Canonical bytes: keys sorted by Unicode code point, compact separators,
    /// Python escaping, trailing newline.
    static QByteArray canonicalBytes(const PortableValue& value);
    /// Python sorted() orders by Unicode code point; UTF-16 ordinal misorders astral planes.
    static int compareCodePoints(const QString& left, const QString& right);
};

} // namespace OjNexus::Portable
