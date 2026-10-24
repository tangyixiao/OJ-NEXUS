#pragma once

#include <ojnexus/core/domain.h>

class QSqlDatabase;

namespace OjNexus::StorageSchema {

Result<int> validateExistingSchema(QSqlDatabase& database);
Result<void> createInitialSchema(QSqlDatabase& database);

} // namespace OjNexus::StorageSchema
