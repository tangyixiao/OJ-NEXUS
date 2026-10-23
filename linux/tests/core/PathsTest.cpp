#include <QtTest/QtTest>

#include "ojnexus/core/paths.h"

class PathsTest final : public QObject {
    Q_OBJECT

private slots:
    void usesExplicitDataDirectoryOverride();
    void usesXdgRootsAndCreatesDirectories();
};

void PathsTest::usesExplicitDataDirectoryOverride() {
    QTemporaryDir root;
    QVERIFY(root.isValid());
    qputenv("OJ_NEXUS_DATA_DIRECTORY", root.path().toUtf8());
    qputenv("XDG_DATA_HOME", (root.path() + "/ignored-data").toUtf8());
    qputenv("XDG_CONFIG_HOME", (root.path() + "/ignored-config").toUtf8());
    qputenv("XDG_STATE_HOME", (root.path() + "/ignored-state").toUtf8());

    const auto paths = OjNexus::AppPaths::fromEnvironment();

    QCOMPARE(paths.dataDirectory, root.path());
    QCOMPARE(paths.databaseFile, root.path() + "/state.db");
    QCOMPARE(paths.configDirectory, root.path() + "/config");
    QCOMPARE(paths.configFile, root.path() + "/config/config.json");
    QCOMPARE(paths.stateDirectory, root.path() + "/state");
    QCOMPARE(paths.logFile, root.path() + "/state/ojnexus.log");

    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    qunsetenv("XDG_DATA_HOME");
    qunsetenv("XDG_CONFIG_HOME");
    qunsetenv("XDG_STATE_HOME");
}

void PathsTest::usesXdgRootsAndCreatesDirectories() {
    QTemporaryDir root;
    QVERIFY(root.isValid());
    qputenv("XDG_DATA_HOME", (root.path() + "/data").toUtf8());
    qputenv("XDG_CONFIG_HOME", (root.path() + "/config").toUtf8());
    qputenv("XDG_STATE_HOME", (root.path() + "/state").toUtf8());
    const auto paths = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(paths.databaseFile.endsWith("/data/ojnexus/state.db"));
    QVERIFY(paths.configFile.endsWith("/config/ojnexus/config.json"));
    QVERIFY(paths.ensureDirectories().has_value());
    QVERIFY(QDir(paths.dataDirectory).exists());
    QVERIFY(QDir(paths.configDirectory).exists());
    QVERIFY(QDir(paths.stateDirectory).exists());
}

QTEST_MAIN(PathsTest)
#include "PathsTest.moc"
