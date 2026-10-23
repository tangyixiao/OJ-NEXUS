#pragma once

#include "ojnexus/core/domain.h"

namespace OjNexus {

struct AppPaths {
    QString dataDirectory;
    QString configDirectory;
    QString stateDirectory;
    QString databaseFile;
    QString configFile;
    QString logFile;

    static AppPaths fromEnvironment();
    Result<void> ensureDirectories() const;
};

} // namespace OjNexus
