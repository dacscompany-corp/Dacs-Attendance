package com.dacs.attendance.di

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.OfflineAttendanceRepository
import com.dacs.attendance.data.repo.SupabaseAuthRepository
import com.dacs.attendance.widget.WidgetAwareAttendanceRepository
import com.dacs.attendance.widget.WidgetAwareAuthRepository
import com.dacs.attendance.widget.WidgetRefresher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The repositories the app sees, wrapped so the home-screen widget follows
 * every change to today. Replaces the plain binds that used to live in
 * RepositoryModule -- there must be exactly one binding for each.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideAttendanceRepository(
        offline: OfflineAttendanceRepository,
        widgets: WidgetRefresher
    ): AttendanceRepository = WidgetAwareAttendanceRepository(offline, widgets)

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabase: SupabaseAuthRepository,
        widgets: WidgetRefresher
    ): AuthRepository = WidgetAwareAuthRepository(supabase, widgets)
}
