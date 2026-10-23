#include "ojnexus/core/paths.h"

#include <QDir>
#include <QStandardPaths>

namespace OjNexus {

AppPaths AppPaths::fromEnvironment() {
    const auto explicitRoot = qEnvironmentVariable("OJ_NEXUS_DATA_DIRECTORY").trimmed();
    if (!explicitRoot.isEmpty()) {
        const auto root = QDir::cleanPath(explicitRoot);
        const auto config = root + QStringLiteral("/config");
        const auto state = root + QStringLiteral("/state");
        return {root, config, state, root + QStringLiteral("/state.db"),
                config + QStringLiteral("/config.json"), state + QStringLiteral("/ojnexus.log")};
    }

    const auto data = QDir::cleanPath(QStandardPaths::writableLocation(QStandardPaths::GenericDataLocation) + QStringLiteral("/ojnexus"));
    const auto config = QDir::cleanPath(QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation) + QStringLiteral("/ojnexus"));
    const auto stateRoot = qEnvironmentVariable("XDG_STATE_HOME").trimmed().isEmpty()
        ? data + QStringLiteral("/state")
        : qEnvironmentVariable("XDG_STATE_HOME") + QStringLiteral("/ojnexus");
    const auto state = QDir::cleanPath(stateRoot);
    return {data, config, state, data + QStringLiteral("/state.db"), config + QStringLiteral("/config.json"), state + QStringLiteral("/ojnexus.log")};
}

Result<void> AppPaths::ensureDirectories() const {
    QDir dir;
    if (!dir.mkpath(dataDirectory) || !dir.mkpath(configDirectory) || !dir.mkpath(stateDirectory)) return SyncError::Storage;
    return {};
}

} // namespace OjNexus
