package com.dacs.attendance.di

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.ProjectRepository
import com.dacs.attendance.data.repo.SupabaseAttendanceRepository
import com.dacs.attendance.data.repo.SupabaseAuthRepository
import com.dacs.attendance.data.repo.SupabaseProjectRepository
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

    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: SupabaseAttendanceRepository): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: SupabaseProjectRepository): ProjectRepository
}
