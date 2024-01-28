package zechs.zplex.ui.episodes.adapter

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import coil.load
import com.google.android.material.progressindicator.LinearProgressIndicator
import zechs.zplex.R
import zechs.zplex.data.model.PosterSize
import zechs.zplex.data.model.StillSize
import zechs.zplex.data.model.tmdb.entities.Episode
import zechs.zplex.databinding.ItemEpisodeBinding
import zechs.zplex.databinding.ItemEpisodeHeaderBinding
import zechs.zplex.utils.Constants.TMDB_IMAGE_PREFIX


sealed class EpisodesViewHolder(
    binding: ViewBinding
) : RecyclerView.ViewHolder(binding.root) {

    class HeaderViewHolder(
        private val itemBinding: ItemEpisodeHeaderBinding
    ) : EpisodesViewHolder(itemBinding) {
        fun bind(item: EpisodesDataModel.Header) {
            val posterUrl = if (item.seasonPosterPath != null) {
                "${TMDB_IMAGE_PREFIX}/${PosterSize.w780}${item.seasonPosterPath}"
            } else R.drawable.no_poster

            val overviewText = item.seasonOverview.ifEmpty { "No description" }

            itemBinding.apply {
                tvSeasonNumber.text = item.seasonNumber
                tvPlot.text = overviewText

                val tvSeasonNameTAG = "tvSeasonNameTAG"

                if (item.seasonName.isNullOrEmpty() || item.seasonName == item.seasonNumber) {
                    tvSeasonName.tag = tvSeasonNameTAG
                    tvSeasonName.isGone = true
                } else {
                    tvSeasonName.tag = null
                    tvSeasonName.text = item.seasonName
                }

                tvSeasonName.isGone = tvSeasonName.tag == tvSeasonNameTAG
                ivPoster.load(posterUrl) {
                    placeholder(R.drawable.no_poster)
                }
            }
        }
    }

    class EpisodeViewHolder(
        private val itemBinding: ItemEpisodeBinding,
        val episodesDataAdapter: EpisodesDataAdapter
    ) : EpisodesViewHolder(itemBinding) {
        fun bind(episode: EpisodesDataModel.Episode) {

            val episodeStillUrl = if (episode.still_path.isNullOrEmpty()) {
                R.drawable.no_thumb
            } else {
                "${TMDB_IMAGE_PREFIX}/${StillSize.original}${episode.still_path}"
            }

            val count = "Episode ${episode.episode_number}"
            val title = episode.name.ifEmpty { "No title" }

            itemBinding.apply {
                val watchProgressTAG = "watchProgressTAG"

                ivThumb.load(episodeStillUrl) {
                    placeholder(R.drawable.no_thumb)
                }
                tvEpisodeCount.text = count

                if (episode.progress == 0) {
                    watchProgress.isGone = true
                } else {
                    watchProgress.isGone = false
                    if (watchProgress.tag == null) {
                        animateProgress(watchProgress, episode.progress)
                        watchProgress.tag = watchProgressTAG
                    } else {
                        watchProgress.progress = episode.progress
                    }
                }

                tvTitle.text = title
                val isLastEpisode = episode.episode_number == episodesDataAdapter.itemCount
                root.setOnClickListener {
                    episodesDataAdapter.episodeOnClick.invoke(
                        Episode(
                            id = episode.id,
                            name = episode.name,
                            overview = episode.overview,
                            episode_number = episode.episode_number,
                            season_number = episode.season_number,
                            still_path = episode.still_path,
                            guest_stars = null,
                            fileId = episode.fileId
                        ),
                        isLastEpisode
                    )
                }
            }
        }

        private fun animateProgress(watchProgress: LinearProgressIndicator, targetProgress: Int) {
            val animator = ValueAnimator.ofInt(0, targetProgress)
            animator.duration = 500
            animator.interpolator = DecelerateInterpolator()

            animator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                watchProgress.progress = animatedValue
            }

            animator.start()
        }
    }
}