#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>

#include "viewmodel.h"

int main(int argc, char* argv[]) {
    QGuiApplication application(argc, argv);
    OjNexus::Gui::MainViewModel viewModel;
    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty(QStringLiteral("viewModel"), &viewModel);
    const QUrl url(QStringLiteral("qrc:/ojnexus/Main.qml"));
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed, &application, [] { QCoreApplication::exit(2); }, Qt::QueuedConnection);
    engine.load(url);
    return application.exec();
}
