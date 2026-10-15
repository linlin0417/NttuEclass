package tw.edu.irika.nttueclass.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.edu.irika.nttueclass.data.repository.AnnouncementRepository
import tw.edu.irika.nttueclass.data.repository.CourseMaterialRepository
import tw.edu.irika.nttueclass.data.repository.CourseRepository
import tw.edu.irika.nttueclass.data.repository.DataSyncCoordinator
import tw.edu.irika.nttueclass.data.repository.TaskRepository
import tw.edu.irika.nttueclass.data.repository.TimetableRepository
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseMaterialRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(
        impl: TimetableRepository
    ): ITimetableRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(
        impl: CourseRepository
    ): ICourseRepository

    @Binds
    @Singleton
    abstract fun bindAnnouncementRepository(
        impl: AnnouncementRepository
    ): IAnnouncementRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepository
    ): ITaskRepository

    @Binds
    @Singleton
    abstract fun bindCourseMaterialRepository(
        impl: CourseMaterialRepository
    ): ICourseMaterialRepository

    @Binds
    @Singleton
    abstract fun bindDataSyncCoordinator(
        impl: DataSyncCoordinator
    ): IDataSyncCoordinator
}
