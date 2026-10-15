package tw.edu.irika.nttueclass.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.dao.RollCallRecordDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideTimetableDao(database: AppDatabase): TimetableDao = database.timetableDao()

    @Provides
    @Singleton
    fun provideCourseDao(database: AppDatabase): CourseDao = database.courseDao()

    @Provides
    @Singleton
    fun provideAnnouncementDao(database: AppDatabase): AnnouncementDao = database.announcementDao()

    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    @Singleton
    fun provideCourseMaterialDao(database: AppDatabase): CourseMaterialDao = database.courseMaterialDao()

    @Provides
    @Singleton
    fun provideRollCallRecordDao(database: AppDatabase): RollCallRecordDao = database.rollCallRecordDao()
}
