#include <csignal>

#include <QCoreApplication>
#include <QTimer>
#include <QTextStream>

#include "ojnexus/cli/command_parser.h"
#include "ojnexus/cli/runner.h"

namespace {

volatile std::sig_atomic_t interruptRequested = 0;

void requestInterrupt(int) {
    interruptRequested = 1;
}

} // namespace

int main(int argc, char* argv[]) {
    QCoreApplication application(argc, argv);
    const auto parsed = OjNexus::Cli::CommandParser::parse(application.arguments());
    QTextStream input(stdin);
    QTextStream output(stdout);
    QTextStream error(stderr);
    if (!parsed.has_value()) {
        error << "invalid command; use --judge and --handle for public account operations" << Qt::endl;
        return OjNexus::Cli::UsageError;
    }

    std::signal(SIGINT, requestInterrupt);
    std::signal(SIGTERM, requestInterrupt);
    std::stop_source cancellation;
    QTimer cancellationPoll;
    QObject::connect(&cancellationPoll, &QTimer::timeout, &application, [&cancellation] {
        if (interruptRequested != 0) cancellation.request_stop();
    });
    cancellationPoll.start(50);

    const auto exitCode = OjNexus::Cli::execute(
        parsed.value(), input, output, error, cancellation.get_token());
    cancellationPoll.stop();
    return exitCode;
}
