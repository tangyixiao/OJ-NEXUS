#pragma once

#include <stop_token>

#include "ojnexus/cli/command_parser.h"
#include "ojnexus/core/store.h"

namespace OjNexus::Cli {

// Process exit codes shared with the Windows client: one number means one thing on every
// OJ NEXUS CLI. Renumbering is a breaking change for scripts and CI expectations, and a
// platform only emits the categories it can produce (GeneralError is declared to keep the
// numbering shared, not because this client has an internal-error path today).
enum ExitCode { Ok = 0, PartialFailure = 1, UsageError = 2, NetworkError = 3, AuthenticationError = 4, Cancelled = 5, StorageError = 6, GeneralError = 7 };

Result<QList<JudgeAccount>> accountsForSync(const QList<JudgeAccount>& stored,
                                            JudgeId::Value judge,
                                            const QString& handle,
                                            bool force = false);
Result<QList<JudgeAccount>> accountsForRemoval(const QList<JudgeAccount>& stored,
                                               JudgeId::Value judge,
                                               const QString& handle);
Result<QList<JudgeAccount>> accountsForAuth(const QList<JudgeAccount>& stored,
                                            JudgeId::Value judge,
                                            const QString& handle);
int syncExitCode(const QList<SyncReport>& reports);
QString syncAggregateStatus(const QList<SyncReport>& reports);

int execute(const Command& command, QTextStream& input, QTextStream& output, QTextStream& error,
            std::stop_token cancellation = {});

} // namespace OjNexus::Cli
