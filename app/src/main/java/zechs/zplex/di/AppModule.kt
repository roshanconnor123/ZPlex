package zechs.zplex.di

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import zechs.zplex.data.local.WatchedMovieDao
import zechs.zplex.data.local.WatchedShowDao
import zechs.zplex.data.repository.DriveRepository
import zechs.zplex.data.repository.WatchedRepository
import zechs.zplex.service.DownloadWorkerFactory
import zechs.zplex.service.IndexingStateFlow
import zechs.zplex.utils.SessionManager
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideGson(): Gson {
        return Gson()
    }

    @Singleton
    @Provides
    fun provideSessionDataStore(
        @ApplicationContext appContext: Context,
        gson: Gson
    ): SessionManager = SessionManager(appContext, gson)

    @Singleton
    @Provides
    fun provideWatchedRepository(
        watchedShowDao: WatchedShowDao,
        watchedMovieDao: WatchedMovieDao
    ) = WatchedRepository(watchedShowDao, watchedMovieDao)


    @Singleton
    @Provides
    fun provideIndexingStateFlow() = IndexingStateFlow()

}