package com.lcdcode.moodcairns.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lcdcode.moodcairns.data.dao.JournalSql

/**
 * Journal migration SQL, kept as plain strings so the JVM tests run exactly these statements
 * on sqlite-jdbc. CREATE statements are copied from the exported schemas in
 * app/schemas/com.lcdcode.moodcairns.data.db.JournalDatabase/.
 */
internal object JournalDbMigrationSql {

    private const val NEW_DOC_TABLE = "journal_doc_v2"

    /**
     * v1 -> v2, step 1: rebuild journal_doc with body as the last column and a new preview
     * column (filled afterwards), keeping every id so the FTS rowids still line up.
     */
    val V1_TO_V2_TABLE: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `$NEW_DOC_TABLE` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`preview` TEXT NOT NULL, `body` TEXT NOT NULL)",
        "INSERT INTO `$NEW_DOC_TABLE` (`id`, `day`, `createdAt`, `updatedAt`, `preview`, `body`) " +
            "SELECT `id`, `day`, `createdAt`, `updatedAt`, '', `body` FROM `journal_doc`",
        "DROP TABLE `journal_doc`",
        "ALTER TABLE `$NEW_DOC_TABLE` RENAME TO `journal_doc`",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_doc_day` ON `journal_doc` (`day`)",
    )

    /** v1 -> v2, step 2 (run per row from Kotlin): previews need Markdown stripping. */
    const val SELECT_BODIES = "SELECT `id`, `body` FROM `journal_doc`"
    const val UPDATE_PREVIEW = "UPDATE `journal_doc` SET `preview` = ? WHERE `id` = ?"

    /**
     * v1 -> v2, step 3: recreate the FTS sync triggers, then rebuild the index once from the
     * content table. On a device Room already drops these triggers before every migration and
     * recreates them afterwards (onPreMigrate/onPostMigrate, both IF NOT EXISTS); creating
     * them here keeps the migration self-contained, which the JVM test relies on.
     */
    val V1_TO_V2_FTS: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_journal_fts_BEFORE_UPDATE " +
            "BEFORE UPDATE ON `journal_doc` BEGIN DELETE FROM `journal_fts` " +
            "WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_journal_fts_BEFORE_DELETE " +
            "BEFORE DELETE ON `journal_doc` BEGIN DELETE FROM `journal_fts` " +
            "WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_journal_fts_AFTER_UPDATE " +
            "AFTER UPDATE ON `journal_doc` BEGIN INSERT INTO `journal_fts`(`docid`, `body`) " +
            "VALUES (NEW.`rowid`, NEW.`body`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_journal_fts_AFTER_INSERT " +
            "AFTER INSERT ON `journal_doc` BEGIN INSERT INTO `journal_fts`(`docid`, `body`) " +
            "VALUES (NEW.`rowid`, NEW.`body`); END",
        "INSERT INTO `journal_fts`(`journal_fts`) VALUES('rebuild')",
    )
}

val JOURNAL_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        JournalDbMigrationSql.V1_TO_V2_TABLE.forEach(db::execSQL)
        val previews = mutableListOf<Pair<Long, String>>()
        db.query(JournalDbMigrationSql.SELECT_BODIES).use { cursor ->
            while (cursor.moveToNext()) {
                previews += cursor.getLong(0) to JournalSql.previewOf(cursor.getString(1))
            }
        }
        previews.forEach { (id, preview) ->
            db.execSQL(JournalDbMigrationSql.UPDATE_PREVIEW, arrayOf(preview, id))
        }
        JournalDbMigrationSql.V1_TO_V2_FTS.forEach(db::execSQL)
    }
}
