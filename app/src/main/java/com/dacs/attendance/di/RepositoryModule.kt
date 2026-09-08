package com.dacs.attendance.di

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.RewardRepository
import com.dacs.attendance.data.repo.OfflineAttendanceRepository
import com.dacs.attendance.data.repo.OfflineProjectRepository
import com.dacs.attendance.data.repo.SupabaseAuthRepository
import com.dacs.attendance.data.repo.SupabaseRewardRepository
import com.dacs.attendance.data.repo.SupabaseTermsRepository
import com.dacs.attendance.data.repo.TermsRepository
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
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTermsRepository(impl: SupabaseTermsRepository): TermsRepository

    // The OFFLINE implementations are what the app sees. They own the
    // queue and the mirrors, and call the Supabase ones underneath --
    // which is why those stay concrete classes rather than being bound
    // to these interfaces themselves.
    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: OfflineAttendanceRepository): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: OfflineProjectRepository): ProjectRepository

    // No offline wrapper, deliberately -- see SupabaseRewardRepository.
    // A locally computed reward could disagree with the server's frozen
    // one, and that disagreement is worse than an absent strip.
    @Binds
    @Singleton
    abstract fun bindRewardRepository(impl: SupabaseRewardRepository): RewardRepository
}
