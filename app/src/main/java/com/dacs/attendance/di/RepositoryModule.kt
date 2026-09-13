package com.dacs.attendance.di

import com.dacs.attendance.data.local.LocationProvider
import com.dacs.attendance.data.local.LocationSource
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.RewardRepository
import com.dacs.attendance.data.repo.OfflineProjectRepository
import com.dacs.attendance.data.repo.SupabaseRewardRepository
import com.dacs.attendance.data.repo.SupabaseTermsRepository
import com.dacs.attendance.work.SubmissionScheduler
import com.dacs.attendance.work.UploadScheduler
import com.dacs.attendance.data.repo.TermsRepository
import com.dacs.attendance.widget.GlanceWidgetRefresher
import com.dacs.attendance.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTermsRepository(impl: SupabaseTermsRepository): TermsRepository

    // The OFFLINE implementation is what the app sees -- it owns the
    // project cache and calls the Supabase one underneath. Attendance and
    // auth are provided in WidgetModule, wrapped so the widget follows them.
    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: OfflineProjectRepository): ProjectRepository

    // No offline wrapper, deliberately -- see SupabaseRewardRepository.
    // A locally computed reward could disagree with the server's frozen
    // one, and that disagreement is worse than an absent strip.
    @Binds
    @Singleton
    abstract fun bindRewardRepository(impl: SupabaseRewardRepository): RewardRepository

    @Binds
    @Singleton
    abstract fun bindLocationSource(impl: LocationProvider): LocationSource

    @Binds
    @Singleton
    abstract fun bindUploadScheduler(impl: SubmissionScheduler): UploadScheduler

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
