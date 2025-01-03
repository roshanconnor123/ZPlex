package zechs.zplex.ui.myshows

import android.app.Application
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import zechs.zplex.data.local.offline.OfflineShowDao
import zechs.zplex.data.model.entities.Movie
import zechs.zplex.data.model.entities.Show
import zechs.zplex.data.repository.TmdbRepository
import zechs.zplex.ui.BaseAndroidViewModel
import javax.inject.Inject

@HiltViewModel
class MyShowsViewModel @Inject constructor(
    app: Application,
    private val tmdbRepository: TmdbRepository,
    offlineShowDao: OfflineShowDao
) : BaseAndroidViewModel(app) {

    fun saveShow(show: Show) = viewModelScope.launch {
        tmdbRepository.upsertShow(show)
    }

    fun deleteShow(tmdbId: Int) = viewModelScope.launch {
        tmdbRepository.deleteShow(tmdbId)
    }

    fun saveMovie(movie: Movie) = viewModelScope.launch {
        tmdbRepository.upsertMovie(movie)
    }

    fun deleteMovie(tmdbId: Int) = viewModelScope.launch {
        tmdbRepository.deleteMovie(tmdbId)
    }

    val movies = tmdbRepository.getSavedMovies()
    val shows =
        if (hasInternetConnection()) {
            tmdbRepository.getSavedShows()
        } else {
            offlineShowDao.getAllShows().map { it.map { tv -> tv.toShow() } }
        }

}