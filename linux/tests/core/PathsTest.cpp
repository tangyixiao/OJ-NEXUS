#include <QtTest/QtTest>
#include <QCoreApplication>

#include "ojnexus/core/paths.h"

class PathsTest final : public QObject {
    Q_OBJECT

private slots:
    void usesExplicitDataDirectoryOverride();
    void usesXdgRootsAndCreatesDirectories();
    void defaultPathsAreIndependentOfApplicationNameAndHonorXdgOverride();
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

void PathsTest::defaultPathsAreIndependentOfApplicationNameAndHonorXdgOverride() {
    QTemporaryDir home;
    QVERIFY(home.isValid());
    qunsetenv("OJ_NEXUS_DATA_DIRECTORY");
    qunsetenv("XDG_DATA_HOME");
    qunsetenv("XDG_CONFIG_HOME");
    qunsetenv("XDG_STATE_HOME");
    qputenv("HOME", home.path().toUtf8());
    QCoreApplication::setApplicationName(QStringLiteral("ojnexus"));
    const auto cli = OjNexus::AppPaths::fromEnvironment();
    QCoreApplication::setApplicationName(QStringLiteral("ojnexus-gui"));
    const auto gui = OjNexus::AppPaths::fromEnvironment();
    QCOMPARE(cli.databaseFile, gui.databaseFile);
    QCOMPARE(cli.configFile, gui.configFile);
    QCOMPARE(cli.logFile, gui.logFile);

    qputenv("XDG_DATA_HOME", (home.path() + "/xdg-data").toUtf8());
    qputenv("XDG_CONFIG_HOME", (home.path() + "/xdg-config").toUtf8());
    const auto overridden = OjNexus::AppPaths::fromEnvironment();
    QVERIFY(overridden.databaseFile.startsWith(home.path() + "/xdg-data/ojnexus/"));
    QVERIFY(overridden.configFile.startsWith(home.path() + "/xdg-config/ojnexus/"));
    qunsetenv("HOME");
    qunsetenv("XDG_DATA_HOME");
    qunsetenv("XDG_CONFIG_HOME");
}

QTEST_MAIN(PathsTest)
#include "PathsTest.moc"
