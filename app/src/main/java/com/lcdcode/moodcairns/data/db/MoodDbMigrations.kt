package com.lcdcode.moodcairns.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lcdcode.moodcairns.data.entity.Tag

/**
 * SQL for the v1 -> v2 migration, kept as plain strings (no Android deps) so
 * JVM tests can execute them against sqlite-jdbc and diff them against the
 * exported Room schema (schemas/.../2.json).
 *
 * The CREATE statements are copied verbatim from 2.json's createSql with
 * `${TABLE_NAME}` substituted. Any change to the Tag/EntryTag entities
 * regenerates the schema identity hash and MUST be mirrored here or Room will
 * reject the migrated database at open.
 */
internal object MoodDbMigrationSql {
    val V1_TO_V2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `tag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name_category` ON `tag` (`name`, `category`)",
        "CREATE TABLE IF NOT EXISTS `entry_tag` (`entryId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
            "PRIMARY KEY(`entryId`, `tagId`), " +
            "FOREIGN KEY(`entryId`) REFERENCES `entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_entry_tag_tagId` ON `entry_tag` (`tagId`)",
    )

    /**
     * INSERT OR IGNORE keeps the seeding idempotent: reruns and collisions with
     * user-created tags of the same name are silently skipped thanks to the
     * unique (name, category) index.
     */
    fun seedTagInserts(tags: List<Tag>): List<String> = tags.map { tag ->
        val name = tag.name.replace("'", "''")
        "INSERT OR IGNORE INTO tag (name, category, sortOrder) " +
            "VALUES ('$name', '${tag.category.name}', ${tag.sortOrder})"
    }
}

/**
 * Adds the tag and entry_tag tables and seeds the default tags, so upgrading
 * users get the same starter set as fresh installs.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MoodDbMigrationSql.V1_TO_V2.forEach(db::execSQL)
        MoodDbMigrationSql.seedTagInserts(SeedTags.tags).forEach(db::execSQL)
    }
}
