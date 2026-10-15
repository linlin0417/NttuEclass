package tw.edu.irika.nttueclass.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideSecureCredentialStorage(@ApplicationContext context: Context): SecureCredentialStorage {
        return SecureCredentialStorage(context)
    }

    @Provides
    @Singleton
    fun provideAuthLockoutManager(@ApplicationContext context: Context): AuthLockoutManager {
        return AuthLockoutManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthManager(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        cookieJar: NttuCookieJar,
        lockoutManager: AuthLockoutManager,
        storage: SecureCredentialStorage
    ): AuthManager {
        return AuthManager(
            context = context,
            client = client,
            cookieJar = cookieJar,
            lockoutManager = lockoutManager,
            secureStorage = storage
        )
    }
}
