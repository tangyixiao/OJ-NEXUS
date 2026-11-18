#include <QtTest/QtTest>

#include <QFile>
#include <QIODevice>
#include <QTemporaryDir>

#include "ojnexus/core/portable_repository.h"
#include "ojnexus/core/store.h"

#include <optional>

namespace {

QString goldenPath(const QString& name)
{
    return QStringLiteral(OJNEXUS_PORTABLE_GOLDEN_DIR) + QLatin1Char('/') + name;
}

QByteArray readGolden(const QString& name)
{
    QFile file(goldenPath(name));
    if (!file.open(QIODevice::ReadOnly)) {
        QTest::qFail(qPrintable(QStringLiteral("cannot open %1: %2").arg(goldenPath(name), file.errorString())),
            __FILE__, __LINE__);
        return {};
    }
    return file.readAll();
}

/// One initialized v3 store + repository over the same guarded store instance.
struct Fixture {
    QTemporaryDir dir;
    OjNexus::SqliteStore store;
    OjNexus::PortableArchiveStore archive;
    OjNexus::Portable::PortableRepository repository;

    explicit Fixture()
        : store(dir.filePath(QStringLiteral("repo.db"))),
          archive(store),
          repository(OjNexus::Portable::PortableArchiveStore(archive))
    {
        const auto initialized = store.initialize();
        if (!initialized.has_value()) {
            QTest::qFail("fixture store failed to initialize", __FILE__, __LINE__);
        }
    }
};

} // namespace

class PortableRepositoryTest final : public QObject {
    Q_OBJECT

private slots:
    void preview_IsPureReadAndNeverTouchesTheStore();
    void apply_SwapsTheArchiveWithTieredRows();
    void reimport_OfIdenticalIdentity_IsANoOp();
    void contractReject_LeavesTheStoreUntouched();
    void cancel_WithNoInFlightApply_IsANoOpThatNeverWrites();
    void export_RebuildsCanonicalBytesWithTheStoredIdentity();
    void export_OnAnEmptyArchive_ThrowsHonestly();
    void reimport_ThenExport_RoundTripsTheGoldenPackage();
};

void PortableRepositoryTest::preview_IsPureReadAndNeverTouchesTheStore()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto preview = fixture.repository.preview(golden);
    QCOMPARE(preview.scopeId, QStringLiteral("scope.alpha"));
    QVERIFY(preview.acceptAll);
    QVERIFY(preview.editableCount > 0);
    QVERIFY(!preview.identity.isEmpty());
    // An empty store after a preview proves the preview wrote nothing.
    const auto stored = fixture.archive.getPackage();
    QVERIFY(stored.has_value());
    QVERIFY(!stored.value().has_value());
}

void PortableRepositoryTest::apply_SwapsTheArchiveWithTieredRows()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto result = fixture.repository.apply(golden);
    QVERIFY(!result.identical);
    QVERIFY(result.rowsUpserted > 0);
    QCOMPARE(result.rowsDeleted, 0);

    const auto stored = fixture.archive.getPackage();
    QVERIFY(stored.has_value() && stored.value().has_value());
    QCOMPARE(stored.value()->identity, result.identity);
    const auto records = fixture.archive.getRecords(result.identity);
    QVERIFY(records.has_value());
    QCOMPARE(records.value().size(), result.rowsUpserted);

    // problems/reviews rows carry state "editable"; the rest "stored_only".
    int editableRows = 0;
    int storedOnlyRows = 0;
    for (const auto& record : records.value()) {
        if (record.state == QStringLiteral("editable")) ++editableRows;
        else if (record.state == QStringLiteral("stored_only")) ++storedOnlyRows;
    }
    QCOMPARE(editableRows, result.editableCount);
    QCOMPARE(storedOnlyRows, result.storedOnlyCount);
}

void PortableRepositoryTest::reimport_OfIdenticalIdentity_IsANoOp()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto first = fixture.repository.apply(golden);
    const auto second = fixture.repository.apply(golden);
    QVERIFY(second.identical);
    QCOMPARE(second.rowsUpserted, 0);
    QCOMPARE(second.rowsDeleted, 0);
    QCOMPARE(second.identity, first.identity);
    const auto records = fixture.archive.getRecords(first.identity);
    QVERIFY(records.has_value());
    QCOMPARE(records.value().size(), first.rowsUpserted);
}

void PortableRepositoryTest::contractReject_LeavesTheStoreUntouched()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto first = fixture.repository.apply(golden);
    QVERIFY(first.rowsUpserted > 0);
    // A contract-rejecting package must be rejected WITHOUT touching the store.
    const auto stored = fixture.archive.getPackage();
    QVERIFY(stored.has_value() && stored.value().has_value());
    const auto identityBefore = stored.value()->identity;

    const auto invalid = readGolden(QStringLiteral("pw-n01.json"));
    try {
        static_cast<void>(fixture.repository.apply(invalid));
        QFAIL("contract reject expected");
    } catch (const OjNexus::Portable::PortableContractException&) {
        // expected
    }
    const auto after = fixture.archive.getPackage();
    QVERIFY(after.has_value() && after.value().has_value());
    QCOMPARE(after.value()->identity, identityBefore);
}

void PortableRepositoryTest::cancel_WithNoInFlightApply_IsANoOpThatNeverWrites()
{
    Fixture fixture;
    fixture.repository.cancel();
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    // A cancel with nothing in flight does not poison the next apply: the
    // next apply reads a FRESH generation at its start.
    const auto result = fixture.repository.apply(golden);
    QVERIFY(!result.identical);
    QVERIFY(result.rowsUpserted > 0);
}

void PortableRepositoryTest::export_RebuildsCanonicalBytesWithTheStoredIdentity()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto preview = fixture.repository.preview(golden);
    static_cast<void>(fixture.repository.apply(golden));

    const auto exported = fixture.repository.exportArchive();
    const auto identity = QString::fromLatin1(
        QCryptographicHash::hash(exported, QCryptographicHash::Sha256).toHex());
    QCOMPARE(identity, preview.identity);
}

void PortableRepositoryTest::export_OnAnEmptyArchive_ThrowsHonestly()
{
    Fixture fixture;
    try {
        static_cast<void>(fixture.repository.exportArchive());
        QFAIL("empty-archive export expected to throw");
    } catch (const OjNexus::Portable::PortableExportException& exception) {
        QVERIFY(QString::fromStdString(exception.what())
                .contains(QStringLiteral("portable archive is empty")));
    }
}

void PortableRepositoryTest::reimport_ThenExport_RoundTripsTheGoldenPackage()
{
    Fixture fixture;
    const auto golden = readGolden(QStringLiteral("pw-p01.json"));
    const auto preview = fixture.repository.preview(golden);
    static_cast<void>(fixture.repository.apply(golden));

    const auto exported = fixture.repository.exportArchive();

    const auto identity = QString::fromLatin1(
        QCryptographicHash::hash(exported, QCryptographicHash::Sha256).toHex());
    QCOMPARE(identity, preview.identity);

    // The exported bytes revalidate cleanly (the contract is closed under
    // lossless export), and re-importing them is an identical no-op.
    const auto preview2 = fixture.repository.preview(exported);
    QCOMPARE(preview2.identity, preview.identity);
    const auto second = fixture.repository.apply(exported);
    QVERIFY(second.identical);
}

QTEST_MAIN(PortableRepositoryTest)
#include "PortableRepositoryTest.moc"
