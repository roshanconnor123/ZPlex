package zechs.zplex.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_media.view.*
import zechs.zplex.R
import zechs.zplex.models.drive.File
import zechs.zplex.utils.Constants.TMDB_API_KEY
import zechs.zplex.utils.Constants.ZPLEX_IMAGE_REDIRECT
import zechs.zplex.utils.Constants.regexShow
import zechs.zplex.utils.GlideApp


class FilesAdapter : RecyclerView.Adapter<FilesAdapter.FilesViewHolder>() {

    inner class FilesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private val differCallback = object : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, differCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesViewHolder {
        return FilesViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_media, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        val file = differ.currentList[position]
        val nameSplit = regexShow.toRegex().find(file.name)?.destructured?.toList()

        if (nameSplit != null) {
            val mediaId = nameSplit[0]
            // val mediaName = nameSplit[2]
            val mediaType = nameSplit[4]

            val redirectImagePoster = if (mediaType == "TV") {
                Uri.parse("${ZPLEX_IMAGE_REDIRECT}/tvdb/$mediaId")
            } else {
                Uri.parse(
                    "${ZPLEX_IMAGE_REDIRECT}/tmdb/$mediaId?api_key=${
                        TMDB_API_KEY
                    }&language=en-US"
                )
            }

            holder.itemView.apply {
                GlideApp.with(this)
                    .load(redirectImagePoster)
                    .placeholder(R.drawable.no_poster)
                    .into(item_poster)
                setOnClickListener {
                    onItemClickListener?.let { it(file) }
                }
            }
        }
    }

    private var onItemClickListener: ((File) -> Unit)? = null

    fun setOnItemClickListener(listener: (File) -> Unit) {
        onItemClickListener = listener
    }
}