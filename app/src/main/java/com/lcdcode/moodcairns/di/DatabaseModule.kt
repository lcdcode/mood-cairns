package com.lcdcode.moodcairns.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
import com.lcdcode.moodcairns.data.db.AppDatabase
import com.lcdcode.moodcairns.data.db.Seed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        lateinit var db: AppDatabase
        db = Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(connection: SupportSQLiteDatabase) {
                    scope.launch {
                        db.scaleDao().insertAllIgnore(Seed.scales)
                        db.promptWindowDao().insertAllIgnore(Seed.windows)
                    }
                }
            })
            .build()
        return db
    }

    @Provides fun provideScaleDao(db: AppDatabase): ScaleDao = db.scaleDao()
    @Provides fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()
    @Provides fun providePromptWindowDao(db: AppDatabase): PromptWindowDao = db.promptWindowDao()
}
