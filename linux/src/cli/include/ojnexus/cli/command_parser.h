#pragma once

#include <optional>

#include <QStringList>

#include "ojnexus/core/domain.h"

namespace OjNexus::Cli {

enum class CommandKind { Status, Sync, History, ConfigShow, AuthLogin, AuthStatus, AuthLogout, AuthRemove,
                          PortableInspect, PortableImport, PortableExport };

struct Command {
    CommandKind kind = CommandKind::Status;
    JudgeId::Value judge = JudgeId::Unknown;
    QString handle;
    QString file;
    int limit = 20;
    bool force = false;
    bool json = false;
    bool yes = false;
};

class CommandParser final {
public:
    static Result<Command> parse(const QStringList& argv);
};

} // namespace OjNexus::Cli
