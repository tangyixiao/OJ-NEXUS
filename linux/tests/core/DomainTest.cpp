#include <QtTest/QtTest>

#include "ojnexus/core/domain.h"

class DomainTest final : public QObject {
    Q_OBJECT

private slots:
    void parsesJudgeIdsCaseInsensitively();
    void rejectsBlankHandles();
    void rejectsCodeforcesQueryDelimiters();
    void rejectsAtCoderCharactersOutsideTheURLBoundary();
    void rejectsAtCoderPathDelimiters();
    void rejectsNonNumericLuoguUid();
    void matchesHandlesUsingJudgeIdentityRules();
    void preservesModuleOrder();
};

void DomainTest::parsesJudgeIdsCaseInsensitively() {
    QCOMPARE(OjNexus::JudgeId::fromString("CoDeFoRcEs"), OjNexus::JudgeId::Codeforces);
    QCOMPARE(OjNexus::JudgeId::fromString("atcoder"), OjNexus::JudgeId::AtCoder);
    QCOMPARE(OjNexus::JudgeId::fromString("LUOGU"), OjNexus::JudgeId::Luogu);
}

void DomainTest::rejectsBlankHandles() {
    const auto result = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Codeforces, "  ");
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::InvalidConfiguration);
}

void DomainTest::rejectsCodeforcesQueryDelimiters() {
    const auto ampersand = OjNexus::JudgeAccount::create(
        OjNexus::JudgeId::Codeforces, "tourist&handles=other");
    const auto fragment = OjNexus::JudgeAccount::create(
        OjNexus::JudgeId::Codeforces, "tourist#fragment");
    QVERIFY(!ampersand.has_value());
    QVERIFY(!fragment.has_value());
    QCOMPARE(ampersand.error(), OjNexus::SyncError::InvalidConfiguration);
    QCOMPARE(fragment.error(), OjNexus::SyncError::InvalidConfiguration);
}

void DomainTest::rejectsAtCoderCharactersOutsideTheURLBoundary() {
    const auto result = OjNexus::JudgeAccount::create(
        OjNexus::JudgeId::AtCoder, "tourist.user");
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::InvalidConfiguration);
}

void DomainTest::rejectsAtCoderPathDelimiters() {
    const auto result = OjNexus::JudgeAccount::create(OjNexus::JudgeId::AtCoder, "tourist/other");
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::InvalidConfiguration);
}

void DomainTest::rejectsNonNumericLuoguUid() {
    const auto result = OjNexus::JudgeAccount::create(OjNexus::JudgeId::Luogu, "uid:abc");
    QVERIFY(!result.has_value());
    QCOMPARE(result.error(), OjNexus::SyncError::InvalidConfiguration);
}

void DomainTest::matchesHandlesUsingJudgeIdentityRules() {
    QVERIFY(OjNexus::judgeHandlesMatch(OjNexus::JudgeId::Codeforces,
                                       QStringLiteral("Tourist"), QStringLiteral("tourist")));
    QVERIFY(!OjNexus::judgeHandlesMatch(OjNexus::JudgeId::AtCoder,
                                        QStringLiteral("Tourist"), QStringLiteral("tourist")));
    QVERIFY(OjNexus::judgeHandlesMatch(OjNexus::JudgeId::Luogu,
                                       QStringLiteral("uid:2"), QStringLiteral("2")));
    QVERIFY(!OjNexus::judgeHandlesMatch(OjNexus::JudgeId::Codeforces,
                                        QStringLiteral("tourist"), QStringLiteral("tourist2")));
}

void DomainTest::preservesModuleOrder() {
    const OjNexus::JudgeAccount account{OjNexus::JudgeId::Codeforces, "tourist", true};
    const QList<OjNexus::SyncModuleOutcome> modules{
        {"PROFILE", OjNexus::SyncOperationStatus::Success, 1, 1, 0, {}},
        {"RATING", OjNexus::SyncOperationStatus::Partial, 2, 1, 1, "NETWORK"},
    };
    const OjNexus::SyncOperation operation{
        7, account, "generation-1", {}, {}, OjNexus::SyncOperationStatus::Partial, std::nullopt, modules,
    };
    QCOMPARE(operation.modules.at(0).stage, QStringLiteral("PROFILE"));
    QCOMPARE(operation.modules.at(1).stage, QStringLiteral("RATING"));
}

QTEST_MAIN(DomainTest)
#include "DomainTest.moc"
