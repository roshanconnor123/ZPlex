package zechs.zplex.ui.episodes

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.Response
import zechs.zplex.data.local.offline.OfflineEpisodeDao
import zechs.zplex.data.local.offline.OfflineSeasonDao
import zechs.zplex.data.local.offline.OfflineShowDao
import zechs.zplex.data.model.drive.DriveFile
import zechs.zplex.data.model.drive.File
import zechs.zplex.data.model.entities.WatchedShow
import zechs.zplex.data.model.tmdb.entities.Episode
import zechs.zplex.data.model.tmdb.season.SeasonResponse
import zechs.zplex.data.repository.DriveRepository
import zechs.zplex.data.repository.TmdbRepository
import zechs.zplex.data.repository.WatchedRepository
import zechs.zplex.service.DownloadWorker
import zechs.zplex.service.OfflineDatabaseWorker
import zechs.zplex.ui.BaseAndroidViewModel
import zechs.zplex.ui.episodes.EpisodesFragment.Companion.TAG
import zechs.zplex.ui.player.PlaybackItem
import zechs.zplex.ui.player.Show
import zechs.zplex.utils.SessionManager
import zechs.zplex.utils.ext.deleteIfExistsSafely
import zechs.zplex.utils.ext.ifNullOrEmpty
import zechs.zplex.utils.state.Resource
import zechs.zplex.utils.state.ResourceExt.Companion.postError
import zechs.zplex.utils.util.Converter
import zechs.zplex.utils.util.DriveApiQueryBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

typealias seasonResponseTmdb = zechs.zplex.data.model.tmdb.season.SeasonResponse

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    app: Application,
    private val tmdbRepository: TmdbRepository,
    private val watchedRepository: WatchedRepository,
    private val driveRepository: DriveRepository,
    private val sessionManager: SessionManager,
    private val offlineShowDao: OfflineShowDao,
    private val offlineSeasonDao: OfflineSeasonDao,
    private val offlineEpisodeDao: OfflineEpisodeDao,
    private val workManager: WorkManager
) : BaseAndroidViewModel(app) {

    var hasLoaded: Boolean = false

    var tmdbId = 0
        private set

    var showName: String? = null
        private set

    var showPoster: String? = null
        private set

    var hasLoggedIn = false
        private set

    fun updateStatus() = viewModelScope.launch {
        hasLoggedIn = getLoginStatus()
    }

    private suspend fun getLoginStatus(): Boolean {
        sessionManager.fetchClient() ?: return false
        sessionManager.fetchRefreshToken() ?: return false
        return true
    }

    private val _episodesResponse = MutableLiveData<Resource<List<Episode>>>(Resource.Loading())

    private val _episodesWithWatched = MediatorLiveData<Resource<List<Episode>>>()
    val episodesWithWatched: LiveData<Resource<List<Episode>>>
        get() = _episodesWithWatched

    private val _playlist = mutableListOf<PlaybackItem>()

    val playlist: List<PlaybackItem>
        get() = _playlist.toList()

    fun getSeasonWithWatched(
        tmdbId: Int,
        showName: String,
        showPoster: String?,
        seasonNumber: Int
    ) = viewModelScope.launch {
        this@EpisodesViewModel.showName = showName
        this@EpisodesViewModel.showPoster = showPoster
        this@EpisodesViewModel.tmdbId = tmdbId

        getSeason(tmdbId, seasonNumber)

        val watchedSeason = watchedRepository.getWatchedSeason(tmdbId, seasonNumber)

        _episodesWithWatched.addSource(_episodesResponse) { episodes ->
            _episodesWithWatched.value =
                combineSeasonWithWatched(episodes, watchedSeason)
        }

        _episodesWithWatched.addSource(
            watchedRepository.getWatchedSeasonLive(tmdbId, seasonNumber)
        ) { watched ->
            _episodesWithWatched.value =
                combineSeasonWithWatched(_episodesResponse.value!!, watched)
        }
    }

    private fun combineSeasonWithWatched(
        episodes: Resource<List<Episode>>,
        watched: List<WatchedShow>
    ): Resource<List<Episode>> {
        if (episodes is Resource.Success) {
            val episodesDataModel = episodes.data!!.toMutableList()

            episodesDataModel.forEachIndexed { index, episode ->
                watched.firstOrNull { it.episodeNumber == episode.episode_number }
                    ?.let { watchedShow ->
                        val newProgress = watchedShow.watchProgress()
                        Log.d(TAG, "Updating watched progress for ${episode.name} to $newProgress")
                        episodesDataModel[index] = episode.copy(progress = newProgress)
                    }
                (episodesDataModel[index])
                    .takeIf { it.fileId != null }
                    ?.let {
                        _playlist.add(
                            Show(
                                tmdbId = tmdbId,
                                title = showName!!,
                                posterPath = showPoster,
                                fileId = it.fileId!!,
                                // ideally this should be check to filesDir
                                offline = it.fileId.startsWith(context.filesDir.path),
                                episode = episode,
                                seasonNumber = it.season_number,
                                episodeNumber = it.episode_number,
                                episodeTitle = it.name
                            )
                        )
                    }
            }

            Log.d(TAG, "Combined episodes with watched successfully")
            return Resource.Success(episodesDataModel.toList())
        }

        Log.d(TAG, "Episodes resource is not Success")
        return episodes
    }

    private fun getSeason(
        tmdbId: Int,
        seasonNumber: Int
    ) = viewModelScope.launch(Dispatchers.IO) {
        _episodesResponse.postValue((Resource.Loading()))
        try {
            if (hasInternetConnection()) {
                val tmdbSeason = tmdbRepository.getSeason(tmdbId, seasonNumber)
                _episodesResponse.postValue((handleSeasonResponse(tmdbId, tmdbSeason)))
                getLastWatchedEpisode(tmdbId, seasonNumber)
            } else {
                val offlineSeason = offlineSeasonDao.getSeasonById(tmdbId, seasonNumber)
                if (offlineSeason != null) {
                    val gson = GsonBuilder()
                        .serializeNulls()
                        .create()
                    val type = object : TypeToken<SeasonResponse?>() {}.type
                    val season: SeasonResponse = gson.fromJson(offlineSeason.json, type)
                    val responseSeason = Response.success(season)
                    _episodesResponse.postValue((handleSeasonResponse(tmdbId, responseSeason)))
                    getLastWatchedEpisode(tmdbId, seasonNumber)
                } else {
                    _episodesResponse.postValue((Resource.Error("No internet connection")))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _episodesResponse.postValue(postError(e))
        }
    }

    data class SeasonHeader(
        val seasonNumber: String,
        val seasonName: String?,
        val seasonPosterPath: String?,
        val seasonOverview: String
    )

    private val _seasonHeader = MutableLiveData<SeasonHeader>()
    val seasonHeader: LiveData<SeasonHeader>
        get() = _seasonHeader

    private suspend fun handleSeasonResponse(
        tmdbId: Int,
        response: Response<seasonResponseTmdb>
    ): Resource<List<Episode>> {
        if (response.body() != null) {
            val result = response.body()!!
            val seasonDataModel = mutableListOf<Episode>()

            _seasonHeader.postValue(createSeasonHeader(result))

            if (!result.episodes.isNullOrEmpty()) {
                val savedShow = tmdbRepository.fetchShowById(tmdbId)
                if (savedShow?.fileId != null) {
                    handleSeasonFolder(result, savedShow.fileId, seasonDataModel)
                } else {
                    handleDefaultMapping(result.episodes, result.season_number!!, seasonDataModel)
                }
            }

            return Resource.Success(seasonDataModel.toList())
        }
        return Resource.Error(response.message())
    }

    private fun createSeasonHeader(result: seasonResponseTmdb): SeasonHeader {
        val overviewBuilder = StringBuilder("Season ${result.season_number} of $showName")

        result.episodes?.size?.let { numberOfEpisodes ->
            overviewBuilder.append(if (numberOfEpisodes == 1) " with 1 episode" else " with $numberOfEpisodes episodes")
        }

        result.air_date?.let { date ->
            val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
            val parsedDate = Converter.parseDate(date)
            if (localDate.isAfter(LocalDate.now())) {
                overviewBuilder.append(" is scheduled to premiere on $parsedDate")
            } else {
                overviewBuilder.append(" premiered on $parsedDate")
            }
        } ?: run { overviewBuilder.append(" is scheduled to premiere soon") }

        Log.d(TAG, "Overview: $overviewBuilder")
        return SeasonHeader(
            seasonNumber = "Season ${result.season_number}",
            seasonName = result.name,
            seasonPosterPath = result.poster_path,
            seasonOverview = result.overview.ifNullOrEmpty { overviewBuilder.toString() }
        )
    }

    private suspend fun handleSeasonFolder(
        result: seasonResponseTmdb,
        showFolderId: String,
        seasonDataModel: MutableList<Episode>
    ) {
        val seasonFolderName = "Season ${result.season_number}"
        val seasonFolder = findSeasonFolder(showFolderId, seasonFolderName)

        if (seasonFolder != null) {
            handleEpisodesInFolder(
                result.episodes!!,
                result.season_number!!,
                seasonFolder.id,
                seasonDataModel
            )
        } else {
            Log.d(TAG, "No folder found with name \"$seasonFolderName\"")
            handleDefaultMapping(result.episodes!!, result.season_number!!, seasonDataModel)
        }
    }

    private suspend fun findSeasonFolder(
        showFolderId: String,
        seasonFolderName: String
    ): DriveFile? {
        val filesInShowFolder = driveRepository.getAllFilesInFolder(
            queryBuilder = DriveApiQueryBuilder()
                .inParents(showFolderId)
                .mimeTypeEquals("application/vnd.google-apps.folder")
                .trashed(false)
        )

        if (filesInShowFolder is Resource.Success && filesInShowFolder.data != null) {
            return filesInShowFolder.data
                .firstOrNull { it.name.equals(seasonFolderName, true) }
                ?.toDriveFile()
        }
        return null
    }

    private suspend fun handleEpisodesInFolder(
        episodes: List<Episode>,
        seasonNumber: Int,
        seasonFolderId: String,
        seasonDataModel: MutableList<Episode>
    ) {
        val episodesInFolder = driveRepository.getAllFilesInFolder(
            queryBuilder = DriveApiQueryBuilder()
                .inParents(seasonFolderId)
                .mimeTypeNotEquals("application/vnd.google-apps.folder")
                .trashed(false)
        )

        if (episodesInFolder is Resource.Success && episodesInFolder.data != null) {
            processMatchingEpisodes(episodes, seasonNumber, episodesInFolder.data, seasonDataModel)
        } else {
            Log.d(TAG, "No files found in season folder")
            handleDefaultMapping(episodes, seasonNumber, seasonDataModel)
        }
    }

    private fun processMatchingEpisodes(
        episodes: List<Episode>,
        seasonNumber: Int,
        filesInFolder: List<File>,
        seasonDataModel: MutableList<Episode>
    ) {
        val episodeMap = buildEpisodeMap(
            filesInFolder.map { it.toDriveFile() }.filter { it.isVideoFile }
        )

        val offlineEpisodes = offlineEpisodeDao
            .getAllEpisodes(tmdbId, seasonNumber)
            .associate { episode ->
                "S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber) to episode.filePath
            }

        var match = 0
        var offline = 0
        episodes.forEach { episode ->
            val offlineFile = offlineEpisodes[getEpisodePattern(episode)]
            if (offlineFile != null) {
                Log.d(TAG, "Found matching file for episode ${getEpisodePattern(episode)}")
                // java.io.File(offlineFile).length()
                seasonDataModel.add(episode.copy(fileId = offlineFile, offline = true))
                offline++
            } else {
                val matchingEpisode = findMatchingEpisode(episode, episodeMap)
                if (matchingEpisode == null) {
                    Log.d(TAG, "No matching file found for episode ${getEpisodePattern(episode)}")
                    seasonDataModel.add(episode.copy(fileId = null))
                } else {
                    Log.d(TAG, "Found matching file for episode ${getEpisodePattern(episode)}")
                    seasonDataModel.add(
                        episode.copy(
                            fileId = matchingEpisode.id,
                            fileSize = matchingEpisode.humanSize
                        )
                    )
                    match++
                }
            }
        }
        Log.d(TAG, "Matched $match out of ${episodes.size} episodes and $offline were offline.")
    }


    private fun buildEpisodeMap(foundEpisodes: List<DriveFile>): Map<String, DriveFile> {
        val episodeMap = mutableMapOf<String, DriveFile>()
        for (file in foundEpisodes) {
            extractEpisodeFormat(file.name)?.let {
                episodeMap[it] = file
            }
        }
        return episodeMap
    }

    private fun findMatchingEpisode(
        episode: Episode,
        episodeMap: Map<String, DriveFile>
    ): DriveFile? {
        val episodePattern = getEpisodePattern(episode)
        return episodeMap[episodePattern]
    }

    fun getEpisodePattern(episode: Episode): String {
        return "S%02dE%02d".format(episode.season_number, episode.episode_number)
    }

    private fun extractEpisodeFormat(fileName: String): String? {
        try {
            val regex = Regex("""S(\d{2})E(\d+)""", RegexOption.IGNORE_CASE)
            val matchResult = regex.find(fileName)
            return matchResult?.value
        } catch (e: IndexOutOfBoundsException) {
            Log.d(TAG, "No match found for $fileName")
            e.printStackTrace()
        }
        return null
    }

    private fun handleDefaultMapping(
        episodes: List<Episode>,
        seasonNumber: Int,
        seasonDataModel: MutableList<Episode>
    ) {
        Log.d(TAG, "Mapping attempt failed, using default")
        val offlineEpisodes = offlineEpisodeDao
            .getAllEpisodes(tmdbId, seasonNumber)
            .associate { episode ->
                "S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber) to episode.filePath
            }
        episodes.forEach { episode ->
            val offlineFile = offlineEpisodes[getEpisodePattern(episode)]
            if (offlineFile == null) {
                seasonDataModel.add(episode.copy(fileId = null))
            } else {
                seasonDataModel.add(episode.copy(fileId = offlineFile, offline = true))
            }
        }
    }

    private val _lastEpisode = MutableStateFlow<Episode?>(null)
    val lastEpisode = _lastEpisode.asStateFlow()

    private fun getLastWatchedEpisode(tmdbId: Int, seasonNumber: Int) = viewModelScope.launch {
        watchedRepository.getLastWatchedEpisode(tmdbId, seasonNumber)
            .stateIn(viewModelScope)
            .collect { last ->
                last?.let {
                    _lastEpisode.value = _episodesResponse.value?.data
                        ?.firstOrNull { it.episode_number == last.episodeNumber }
                        ?.takeIf { it.fileId != null }
                }
            }
    }

    fun startDownload(episode: Episode, title: String) {
        episode.fileId ?: kotlin.run {
            Log.d(TAG, "EpisodesViewModel.startDownload requires fileId")
        }
        val data = Data.Builder()
            .putInt(DownloadWorker.TMDB_ID, tmdbId)
            .putInt(DownloadWorker.EPISODE_NUMBER, episode.episode_number)
            .putInt(DownloadWorker.SEASON_NUMBER, episode.season_number)
            .putString(DownloadWorker.FILE_ID, episode.fileId!!)
            .putString(DownloadWorker.FILE_TITLE, title)
            .build()


        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .addTag(episode.fileId)
            .build()

        val offlineRequest = OneTimeWorkRequestBuilder<OfflineDatabaseWorker>().build()

        workManager.beginWith(downloadRequest)
            .then(offlineRequest)
            .enqueue()
    }

    fun removeOffline(episode: Episode) = viewModelScope.launch(Dispatchers.IO) {
        offlineEpisodeDao.deleteEpisode(tmdbId, episode.season_number, episode.episode_number)
        java.io.File(episode.fileId!!).deleteIfExistsSafely()
        val offlineEpisodes = offlineEpisodeDao.getAllEpisodes(tmdbId, episode.season_number)
        if (offlineEpisodes.isEmpty()) {
            offlineSeasonDao.deleteSeason(tmdbId, episode.season_number)
            val offlineSeasons = offlineSeasonDao.getAllSeasons(tmdbId)
            if (offlineSeasons.isEmpty()) {
                offlineShowDao.deleteShowById(tmdbId)
            }
        }
    }
}