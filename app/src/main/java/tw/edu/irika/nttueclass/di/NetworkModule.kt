package tw.edu.irika.nttueclass.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): NttuCookieJar {
        NttuHttpClient.cookieJar.initPersistence(context)
        return NttuHttpClient.cookieJar
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: NttuCookieJar): OkHttpClient = NttuHttpClient.client
}
