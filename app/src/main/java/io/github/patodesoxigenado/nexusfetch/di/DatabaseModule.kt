package io.github.patodesoxigenado.nexusfetch.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryDao
import io.github.patodesoxigenado.nexusfetch.data.local.db.NexusDatabase
import io.github.patodesoxigenado.nexusfetch.data.local.db.PendingQueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNexusDatabase(@ApplicationContext context: Context): NexusDatabase =
        Room.databaseBuilder(
            context,
            NexusDatabase::class.java,
            "nexusfetch.db",
        ).build()

    @Provides
    fun provideHistoryDao(db: NexusDatabase): HistoryDao = db.historyDao()

    @Provides
    fun providePendingQueueDao(db: NexusDatabase): PendingQueueDao = db.pendingQueueDao()
}
