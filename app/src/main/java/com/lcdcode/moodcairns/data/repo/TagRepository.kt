package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(private val holder: MoodDatabaseHolder) {
    private fun dao() = holder.tagDao()
    private fun categoryDao() = holder.tagCategoryDao()

    /** All tags in display order (category order, then tag order). */
    fun observeAll(): Flow<List<Tag>> = flow { emitAll(dao().observeAll()) }

    suspend fun byId(id: Long): Tag? = dao().byId(id)
    suspend fun upsert(tag: Tag) {
        if (tag.id == 0L) dao().insert(tag) else dao().update(tag)
    }
    suspend fun countEntriesUsing(id: Long): Int = dao().countEntriesUsing(id)
    suspend fun delete(id: Long) = dao().delete(id)
    suspend fun reorder(orderedIds: List<Long>) = dao().applySortOrder(orderedIds)
    suspend fun nextSortOrder(categoryId: String): Int = dao().maxSortOrder(categoryId) + 1

    /** Categories in display order. */
    fun observeCategories(): Flow<List<TagCategory>> = flow { emitAll(categoryDao().observeAll()) }

    suspend fun allCategories(): List<TagCategory> = categoryDao().all()

    /**
     * Renames a category. A blank [raw] restores a built-in's translated default;
     * user categories have no default, so a blank name is rejected. Other is the
     * catch-all for deleted categories and always keeps its own name.
     */
    suspend fun renameCategory(id: String, raw: String) {
        require(id != TagCategory.OTHER_ID) { "Category $id cannot be renamed" }
        val label = TagCategory.sanitizeLabel(raw)
        require(label != null || BuiltInTagCategory.fromId(id) != null) {
            "User category $id cannot have a blank name"
        }
        categoryDao().setLabel(id, label)
    }

    /** Adds a user category just before Other and returns its id. */
    suspend fun createCategory(raw: String): String {
        val label = requireNotNull(TagCategory.sanitizeLabel(raw)) {
            "A new category needs a non-blank name"
        }
        val id = TagCategory.newCustomId()
        categoryDao().insertBeforeOther(TagCategory(id = id, label = label, sortOrder = 0))
        return id
    }

    /** Number of tags that deleting [categoryId] would move to Other. */
    suspend fun countTagsIn(categoryId: String): Int = categoryDao().countTagsIn(categoryId)

    /** Deletes a category, moving its tags to Other. Other itself cannot be deleted. */
    suspend fun deleteCategory(id: String) = categoryDao().deleteMovingTagsToOther(id)
}
