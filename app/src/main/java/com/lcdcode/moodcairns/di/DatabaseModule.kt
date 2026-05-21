package com.lcdcode.moodcairns.di

import android.content.Context
import androidx.room.Room
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides only the plaintext [ScheduleDatabase] at the DI graph level.
 *
 * The SQLCipher-encrypted mood database lives behind
 * [com.lcdcode.moodcairns.data.db.MoodDatabaseHolder] (which is itself
 * @Singleton-provided by Hilt's constructor injection); it has no DAO bindings
 * here on purpose, since its lifecycle is tied to unlock state rather than to
 * application start. Consumers obtain DAOs by calling
 * `moodHolder.scaleDao()` / `moodHolder.entryDao()` — see [LockManager] for
 * when the holder is open.
 *
 * Seeding of default scales + prompt windows is also intentionally moved out
 * of the Room onCreate callback and into LockManager.completeSetup so that the
 * fresh-install path and the legacy-migration path don't race over seed rows.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScheduleDatabase(@ApplicationContext ctx: Context): ScheduleDatabase =
        Room.databaseBuilder(ctx, ScheduleDatabase::class.java, ScheduleDatabase.NAME)
            .build()

    @Provides
    fun providePromptWindowDao(db: ScheduleDatabase): PromptWindowDao = db.promptWindowDao()
}
