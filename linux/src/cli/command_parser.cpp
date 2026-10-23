#include "ojnexus/cli/command_parser.h"

namespace OjNexus::Cli {
namespace {

JudgeId::Value parseJudge(const QString& value) {
    return JudgeId::fromString(value);
}

Result<Command> invalid() { return SyncError::InvalidConfiguration; }

} // namespace

Result<Command> CommandParser::parse(const QStringList& argv) {
    if (argv.size() < 2) return invalid();
    Command command;
    const auto root = argv.at(1).toLower();
    int index = 2;

    if (root == QStringLiteral("status")) command.kind = CommandKind::Status;
    else if (root == QStringLiteral("history")) command.kind = CommandKind::History;
    else if (root == QStringLiteral("config") && argv.size() > 2 && argv.at(2) == QStringLiteral("show")) {
        command.kind = CommandKind::ConfigShow;
        index = 3;
    } else if (root == QStringLiteral("sync")) command.kind = CommandKind::Sync;
    else if (root == QStringLiteral("auth") && argv.size() > 2) {
        const auto action = argv.at(2).toLower();
        if (action == QStringLiteral("login")) command.kind = CommandKind::AuthLogin;
        else if (action == QStringLiteral("status")) command.kind = CommandKind::AuthStatus;
        else if (action == QStringLiteral("logout")) command.kind = CommandKind::AuthLogout;
        else if (action == QStringLiteral("remove")) command.kind = CommandKind::AuthRemove;
        else return invalid();
        index = 3;
    } else return invalid();

    bool sawJudge = false;
    bool sawHandle = false;
    bool sawLimit = false;
    bool sawForce = false;
    for (; index < argv.size(); ++index) {
        const auto argument = argv.at(index);
        if (argument == QStringLiteral("--json")) command.json = true;
        else if (argument == QStringLiteral("--force")) { command.force = true; sawForce = true; }
        else if (argument == QStringLiteral("--judge") && index + 1 < argv.size()) {
            command.judge = parseJudge(argv.at(++index));
            if (command.judge == JudgeId::Unknown) return invalid();
            sawJudge = true;
        } else if (argument == QStringLiteral("--handle") && index + 1 < argv.size()) {
            command.handle = argv.at(++index).trimmed();
            if (command.handle.isEmpty()) return invalid();
            sawHandle = true;
        } else if (argument == QStringLiteral("--limit") && index + 1 < argv.size()) {
            bool ok = false;
            command.limit = argv.at(++index).toInt(&ok);
            if (!ok || command.limit < 1 || command.limit > 1000) return invalid();
            sawLimit = true;
        } else return invalid();
    }

    const auto authCommand = command.kind == CommandKind::AuthLogin ||
        command.kind == CommandKind::AuthLogout ||
        command.kind == CommandKind::AuthStatus || command.kind == CommandKind::AuthRemove;
    const auto judgeAllowed = command.kind == CommandKind::Sync || command.kind == CommandKind::History || authCommand;
    const auto handleAllowed = command.kind == CommandKind::Sync || authCommand;
    if ((sawJudge && !judgeAllowed) || (sawHandle && !handleAllowed) ||
        (sawLimit && command.kind != CommandKind::History) ||
        (sawForce && command.kind != CommandKind::Sync)) {
        return invalid();
    }
    if ((command.kind == CommandKind::Sync ||
         command.kind == CommandKind::AuthLogin ||
         command.kind == CommandKind::AuthLogout || command.kind == CommandKind::AuthStatus ||
         command.kind == CommandKind::AuthRemove) &&
        command.judge == JudgeId::Unknown) {
        return invalid();
    }
    return command;
}

} // namespace OjNexus::Cli
