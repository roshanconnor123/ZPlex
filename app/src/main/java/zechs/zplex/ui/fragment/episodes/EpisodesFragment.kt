package zechs.zplex.ui.fragment.episodes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.Constraints
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.Transition
import zechs.zplex.R
import zechs.zplex.adapter.EpisodesAdapter
import zechs.zplex.adapter.streams.StreamsDataModel
import zechs.zplex.databinding.FragmentEpisodeBinding
import zechs.zplex.models.tmdb.PosterSize
import zechs.zplex.models.tmdb.entities.Episode
import zechs.zplex.models.tmdb.season.SeasonResponse
import zechs.zplex.models.witch.DashVideoResponseItem
import zechs.zplex.ui.BaseFragment
import zechs.zplex.ui.activity.ZPlexActivity
import zechs.zplex.ui.activity.player.PlayerActivity
import zechs.zplex.ui.dialog.StreamsDialog
import zechs.zplex.ui.fragment.image.BigImageViewModel
import zechs.zplex.ui.fragment.viewmodels.SeasonViewModel
import zechs.zplex.utils.Constants.TMDB_IMAGE_PREFIX
import zechs.zplex.utils.GlideApp
import zechs.zplex.utils.Resource


class EpisodesFragment : BaseFragment() {

    override val enterTransitionListener: Transition.TransitionListener? = null

    private var _binding: FragmentEpisodeBinding? = null
    private val binding get() = _binding!!

    private val seasonViewModel by activityViewModels<SeasonViewModel>()
    private val bigImageViewModel: BigImageViewModel by activityViewModels()
    private lateinit var episodesViewModel: EpisodesViewModel
    private val episodesAdapter by lazy { EpisodesAdapter() }

    private lateinit var streamsDialog: StreamsDialog

    private val thisTAG = "EpisodesFragment"
    private var tmdbId = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEpisodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEpisodeBinding.bind(view)

        episodesViewModel = (activity as ZPlexActivity).episodesViewModel

        binding.rvEpisodes.apply {
            adapter = episodesAdapter
            layoutManager = LinearLayoutManager(
                activity, LinearLayoutManager.VERTICAL, false
            )
            itemAnimator = null
        }

        binding.seasonToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        seasonViewModel.showId.observe(viewLifecycleOwner) { showSeason ->
            episodesViewModel.getSeason(
                tvId = showSeason.tmdbId,
                seasonNumber = showSeason.seasonNumber
            )

            val posterUrl = if (showSeason?.posterPath == null) {
                R.drawable.no_poster
            } else {
                "$TMDB_IMAGE_PREFIX/${PosterSize.w500}${showSeason.posterPath}"
            }

            val seasonText = "Season ${showSeason.seasonNumber}"

            binding.apply {
                if (showSeason.seasonName == seasonText) {
                    seasonToolbar.title = seasonText
                    seasonToolbar.subtitle = showSeason.showName
                } else {
                    seasonToolbar.title = seasonText
                    seasonToolbar.subtitle = showSeason.seasonName
                }

                GlideApp.with(ivPoster)
                    .load(posterUrl)
                    .placeholder(R.drawable.no_poster)
                    .into(ivPoster)

                ivPoster.setOnClickListener {
                    openImageFullSize(showSeason?.posterPath, binding.ivPoster)
                }

            }

            tmdbId = showSeason.tmdbId
        }

        setupDashStreamsObserver()
        episodesViewModel.season.observe(viewLifecycleOwner) { responseMedia ->
            when (responseMedia) {
                is Resource.Success -> {
                    responseMedia.data?.let { seasonResponse ->
                        doOnSuccess(seasonResponse)
                    }
                }

                is Resource.Error -> {
                    responseMedia.message?.let { message ->
                        val errorMsg = message.ifEmpty {
                            resources.getString(R.string.something_went_wrong)
                        }
                        Log.e(thisTAG, errorMsg)
                        binding.apply {
                            appBarLayout.isInvisible = true
                            rvEpisodes.isInvisible = true
                            pbEpisodes.isVisible = true
                        }
                        binding.errorView.apply {
                            errorTxt.text = errorMsg
                        }
                    }
                    episodesAdapter.setOnItemClickListener { }
                }

                is Resource.Loading -> {
                    binding.apply {
                        rvEpisodes.isInvisible = true
                        pbEpisodes.isVisible = true
                        errorView.root.isVisible = false
                    }
                    episodesAdapter.setOnItemClickListener { }
                }
            }
        }

    }

    private fun openImageFullSize(posterPath: String?, imageView: ImageView) {
        imageView.transitionName = posterPath
        this.exitTransition = null
        bigImageViewModel.setImagePath(posterPath)

        val action = EpisodesFragmentDirections.actionEpisodesListFragmentToBigImageFragment()
        val extras = FragmentNavigatorExtras(
            imageView to imageView.transitionName
        )
        findNavController().navigate(action, extras)
        Log.d("navigateToMedia", imageView.transitionName)
    }


    private fun doOnSuccess(seasonResponse: SeasonResponse) {

        val episodesList = seasonResponse.episodes?.toList() ?: listOf()
        episodesAdapter.differ.submitList(episodesList)

        if (episodesList.isEmpty()) {
            val errorMsg = getString(R.string.no_episodes_found)
            Log.e(thisTAG, errorMsg)
            binding.apply {
                rvEpisodes.isInvisible = true
                pbEpisodes.isInvisible = true
                errorView.root.isVisible = true
            }
            binding.errorView.apply {
                errorTxt.text = errorMsg
                retryBtn.isInvisible = true
            }
        } else {
            binding.apply {
                rvEpisodes.isVisible = true
                pbEpisodes.isInvisible = true
                errorView.root.isVisible = false
            }
        }
        episodesAdapter.setOnItemClickListener {
            context?.let { c -> showStreamsDialog(c, it) }
        }
    }


    private fun showStreamsDialog(context: Context, episode: Episode) {
        streamsDialog = StreamsDialog(context)
        streamsDialog.show()

        streamsDialog.window?.apply {

            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                Constraints.LayoutParams.MATCH_PARENT,
                Constraints.LayoutParams.WRAP_CONTENT
            )
        }
        val streamsList = mutableListOf<StreamsDataModel>()
        episode.fileId?.let { id ->
            streamsList.add(
                StreamsDataModel.Original(
                    title = "Original (${episode.fileSize!!})",
                    id = id
                )
            )
            episodesViewModel.getDashVideos(episode.fileId)
        }

        streamsList.add(StreamsDataModel.Loading)

        streamsDialog.streamsDataAdapter.differ.submitList(streamsList.toList())
        streamsDialog.streamsDataAdapter.setOnItemClickListener {
            when (it) {
                is StreamsDataModel.Original -> playEpisode(episode, null, null)
                is StreamsDataModel.Stream -> {
                    playEpisode(episode, it.cookie, it.url)
                }
                else -> {}
            }
        }

    }


    private fun playEpisode(episode: Episode, cookie: String?, stream: String?) {
        episode.fileId?.let { id ->
            val title = if (episode.name.isNullOrEmpty()) {
                "No title"
            } else "Episode ${episode.episode_number} - ${episode.name}"
            val intent = Intent(activity, PlayerActivity::class.java)
            intent.putExtra("fileId", id)
            intent.putExtra("title", title)
            cookie?.let { intent.putExtra("cookie", it) }
            stream?.let { intent.putExtra("dash_url", it) }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            activity?.startActivity(intent)
        }
    }


    private fun setupDashStreamsObserver() {
        episodesViewModel.dashVideo.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { streamsResponse ->
                handleDashStreamsResponse(streamsResponse)
            }
        }
    }

    private fun handleDashStreamsResponse(
        streamsResponse: Resource<List<DashVideoResponseItem>>
    ) {
        if (this::streamsDialog.isInitialized) {
            when (streamsResponse) {
                is Resource.Success -> {
                    streamsResponse.data?.let {
                        handleStreamsSuccess(it)
                    }
                }
                else -> {
                    val adapterDiff = streamsDialog.streamsDataAdapter.differ
                    val currentList = adapterDiff.currentList
                    val streamsList = mutableListOf<StreamsDataModel>()
                    streamsList.add(currentList.filterIsInstance<StreamsDataModel.Original>()[0])
                    adapterDiff.submitList(streamsList.toList())
                }
            }
        }

    }

    private fun handleStreamsSuccess(streams: List<DashVideoResponseItem>) {
        val adapterDiff = streamsDialog.streamsDataAdapter.differ
        val currentList = adapterDiff.currentList

        val streamsList = mutableListOf<StreamsDataModel>()
        streamsList.add(currentList.filterIsInstance<StreamsDataModel.Original>()[0])

        for (stream in streams) {
            streamsList.add(
                StreamsDataModel.Stream(
                    name = "${stream.quality} (${stream.humanSize})",
                    url = stream.url,
                    cookie = stream.drive_stream
                )
            )

        }
        adapterDiff.submitList(streamsList.toList())

    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rvEpisodes.adapter = null
        _binding = null
    }
}