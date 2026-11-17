package com.ojnexus.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v15 → v16 (Phase 132): the bounded domain-archive tables, additive only.
 *
 * Two new tables, no column changes, no backfill, no table rebuilds — every v15 row is
 * preserved untouched. The DDL mirrors what Room expects for
 * `PortableArchivePackageEntity` / `PortableArchiveRecordEntity`; the exported
 * `app/schemas/…/16.json` is the source of truth and `MigrationTest` validates the
 * migrated file against Room's own expectations.
 */
val MIGRATION_15_16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `portable_archive_packages` (" +
                "`identity` TEXT NOT NULL, " +
                "`format_version` INTEGER NOT NULL, " +
                "`scope_id` TEXT NOT NULL, " +
                "`source_platform` TEXT NOT NULL, " +
                "`generator_json` TEXT NOT NULL, " +
                "`created_at_epoch_ms` INTEGER NOT NULL, " +
                "`canonical_bytes` BLOB NOT NULL, " +
                "`source_tag` TEXT NOT NULL, " +
                "`imported_at_epoch_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`identity`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `portable_archive_records` (" +
                "`collection` TEXT NOT NULL, " +
                "`stable_id` TEXT NOT NULL, " +
                "`source_scope_id` TEXT NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`canonical_json` TEXT NOT NULL, " +
                "`tier` TEXT NOT NULL, " +
                "`tier_reason` TEXT NOT NULL, " +
                "`synthetic` INTEGER NOT NULL, " +
                "`source_tag` TEXT NOT NULL, " +
                "`package_identity` TEXT NOT NULL, " +
                "`ordinal` INTEGER NOT NULL, " +
                "PRIMARY KEY(`collection`, `stable_id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_portable_archive_records_package_identity` " +
                "ON `portable_archive_records` (`package_identity`)",
        )
    }
}
