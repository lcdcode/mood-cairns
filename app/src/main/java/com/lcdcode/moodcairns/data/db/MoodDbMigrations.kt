package com.lcdcode.moodcairns.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

/**
 * SQL for the versioned migrations, kept as plain strings (no Android deps) so
 * JVM tests can execute them against sqlite-jdbc and diff them against the
 * exported Room schemas (schemas/.../N.json).
 *
 * The CREATE statements are copied verbatim from the exported schema's
 * createSql with `${TABLE_NAME}` substituted. Any change to the entities
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

    val V2_TO_V3: List<String> = listOf(
        "ALTER TABLE `scale` ADD COLUMN `inverted` INTEGER NOT NULL DEFAULT 0",
    )

    val V3_TO_V4: List<String> = listOf(
        "ALTER TABLE `scale` ADD COLUMN `defaultValue` REAL",
    )

    val V4_TO_V5: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `tag_category_label` (`category` TEXT NOT NULL, " +
            "`label` TEXT NOT NULL, PRIMARY KEY(`category`))",
    )

    /**
     * Replaces tag_category_label with tag_category: the built-ins are seeded in their
     * old fixed order and keep any custom name, except Other, which can no longer be
     * renamed. tag.category already stores the built-in names, which become the
     * category ids, so tag rows need no rewrite.
     */
    val V5_TO_V6: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `tag_category` (`id` TEXT NOT NULL, `label` TEXT, " +
            "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    ) + SeedTagCategories.all.map { category ->
        val label = if (category.id == TagCategory.OTHER_ID) {
            "NULL"
        } else {
            "(SELECT label FROM tag_category_label WHERE category = '${category.id}')"
        }
        "INSERT OR IGNORE INTO tag_category (id, label, sortOrder) " +
            "VALUES ('${category.id}', $label, ${category.sortOrder})"
    } + listOf(
        "DROP TABLE IF EXISTS `tag_category_label`",
        "ALTER TABLE `tag` ADD COLUMN `columnIndex` INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * INSERT OR IGNORE keeps the seeding idempotent: reruns and collisions with
     * user-created tags of the same name are silently skipped thanks to the
     * unique (name, category) index.
     */
    fun seedTagInserts(tags: List<Tag>): List<String> = tags.map { tag ->
        val name = tag.name.replace("'", "''")
        "INSERT OR IGNORE INTO tag (name, category, sortOrder) " +
            "VALUES ('$name', '${tag.categoryId}', ${tag.sortOrder})"
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

/**
 * Adds the scale.inverted flag ("lower is better"). Existing scales default to
 * the normal direction; users opt in per scale from the edit screen.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MoodDbMigrationSql.V2_TO_V3.forEach(db::execSQL)
    }
}

/**
 * Adds the optional scale.defaultValue (slider start for new entries; NULL
 * means the range midpoint) and seeds the new MOOD tag category.
 *
 * Fresh installs and v1 upgrades already get the mood tags from SeedTags.tags,
 * so this only backfills v2/v3 installs. Reseeding is harmless either way:
 * INSERT OR IGNORE plus the unique (name, category) index makes it a no-op.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MoodDbMigrationSql.V3_TO_V4.forEach(db::execSQL)
        MoodDbMigrationSql.seedTagInserts(SeedTags.moodTags).forEach(db::execSQL)
    }
}

/**
 * Adds tag_category_label for user-chosen category names. The table starts
 * empty, so every category keeps its translated default until renamed.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MoodDbMigrationSql.V4_TO_V5.forEach(db::execSQL)
    }
}

/**
 * Turns tag categories into rows so users can add and delete them, and adds
 * tag.columnIndex (default 0) for the upcoming column layout.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MoodDbMigrationSql.V5_TO_V6.forEach(db::execSQL)
    }
}
