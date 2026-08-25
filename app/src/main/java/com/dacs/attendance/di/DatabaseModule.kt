package com.dacs.attendance.di

import android.content.Context
import androidx.room.Room
import com.dacs.attendance.data.local.AttendanceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No fallbackToDestructiveMigration, deliberately.
     *
     * This database holds submissions that have not reached the server.
     * Wiping it on a schema change would silently delete a worker's
     * recorded day. Schema changes here need real migrations, which is
     * why the schema is exported to app/schemas and committed.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AttendanceDatabase =
        Room.databaseBuilder(context, AttendanceDatabase::class.java, "attendance.db").build()
}
