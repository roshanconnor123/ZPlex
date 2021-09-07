package zechs.zplex.ui.fragment

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.android.synthetic.main.fragment_home.*
import zechs.zplex.adapter.FilesAdapter
import zechs.zplex.adapter.LogsAdapter
import zechs.zplex.databinding.FragmentHomeBinding
import zechs.zplex.models.drive.File
import zechs.zplex.ui.FileViewModel
import zechs.zplex.ui.ReleaseLogViewModel
import zechs.zplex.ui.activity.ZPlexActivity
import zechs.zplex.utils.Constants.Companion.ZPLEX
import zechs.zplex.utils.Resource
import java.net.IDN
import java.net.URI
import java.net.URL


class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding

    private lateinit var viewModel: FileViewModel
    private lateinit var viewModel2: FileViewModel

    private lateinit var filesAdapter: FilesAdapter
    private lateinit var filesAdapter2: FilesAdapter

    private lateinit var logsViewModel: ReleaseLogViewModel
    private lateinit var logsAdapter: LogsAdapter

    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater)
        enterTransition = MaterialFadeThrough()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (activity as ZPlexActivity).viewModel
        viewModel2 = (activity as ZPlexActivity).viewModel
        logsViewModel = (activity as ZPlexActivity).logsViewModel

        setupRecyclerView()
        appBarLayout.setPadding(0, getStatusBarHeight(), 0, 0)

        viewModel.filesList.observe(viewLifecycleOwner, { response ->
            when (response) {
                is Resource.Success -> {
                    homeView(true)
                    response.data?.let { filesResponse ->
                        filesAdapter.differ.submitList(filesResponse.files.toList())
                    }
                }
                is Resource.Error -> {
                    homeView(false)
                    response.message?.let { message ->
                        Toast.makeText(
                            context,
                            "An error occurred: $message",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "An error occurred: $message")
                    }
                }
                is Resource.Loading -> {
                }
            }
        })

        logsViewModel.logsList.observe(viewLifecycleOwner, { response ->
            when (response) {
                is Resource.Success -> {
                    recentView(true)
                    response.data?.let { logsResponse ->
                        logsAdapter.differ.submitList(logsResponse.releasesLog.toList())
                    }
                }
                is Resource.Error -> {
                    recentView(false)
                    response.message?.let { message ->
                        Toast.makeText(
                            context,
                            "An error occurred: $message",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "An error occurred: $message")
                    }
                }
                is Resource.Loading -> {
                }
            }
        })

        viewModel2.getSavedFiles().observe(viewLifecycleOwner, { files ->
            if (files.toList().isNotEmpty()) {
                binding.myShows.visibility = View.VISIBLE
                binding.rvMyShowsHome.visibility = View.VISIBLE
                filesAdapter2.differ.submitList(files)
            } else {
                binding.myShows.visibility = View.GONE
                binding.rvMyShowsHome.visibility = View.GONE
            }
        })
    }

    private fun recentView(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        TransitionManager.beginDelayedTransition(binding.root)
        binding.recentlyAdded.visibility = visibility
        binding.rvNewEpisodes.visibility = visibility
    }

    private fun homeView(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        TransitionManager.beginDelayedTransition(binding.root, MaterialFadeThrough())
        binding.rvHome.visibility = visibility
        binding.recentlyAdded.visibility = visibility
    }

    private fun setupRecyclerView() {
        filesAdapter = FilesAdapter()
        filesAdapter2 = FilesAdapter()

        binding.rvHome.apply {
            adapter = filesAdapter
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        }

        filesAdapter.setOnItemClickListener {
            getDetails(it)
        }

        logsAdapter = LogsAdapter()
        binding.rvNewEpisodes.apply {
            adapter = logsAdapter
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        }

        logsAdapter.setOnItemClickListener {
            val playUrl = URL("${ZPLEX}${it.folder}/${it.file}")
            val playURI = URI(
                playUrl.protocol,
                playUrl.userInfo,
                IDN.toASCII(playUrl.host),
                playUrl.port,
                playUrl.path,
                playUrl.query,
                playUrl.ref
            )

            try {
                println(playURI.toASCIIString())
                val vlcIntent = Intent(Intent.ACTION_VIEW)
                vlcIntent.setPackage("org.videolan.vlc")
                vlcIntent.component = ComponentName(
                    "org.videolan.vlc",
                    "org.videolan.vlc.gui.video.VideoPlayerActivity"
                )
                vlcIntent.setDataAndTypeAndNormalize(Uri.parse(playURI.toASCIIString()), "video/*")
                vlcIntent.putExtra("title", it.file.dropLast(4))
                vlcIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                startActivity(vlcIntent)
            } catch (notFoundException: ActivityNotFoundException) {
                notFoundException.printStackTrace()
                Toast.makeText(
                    context,
                    "VLC not found, Install VLC from Play Store",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.rvMyShowsHome.apply {
            adapter = filesAdapter2
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            filesAdapter2.setOnItemClickListener {
                getDetails(it)
            }
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun getDetails(it: File) {
        try {

            val posterURL = URL("${ZPLEX}${it.name}/poster.jpg")
            val posterUri = URI(
                posterURL.protocol,
                posterURL.userInfo,
                IDN.toASCII(posterURL.host),
                posterURL.port,
                posterURL.path,
                posterURL.query,
                posterURL.ref
            )
            val seriesId = (it.name.split(" - ").toTypedArray()[0]).toInt()
            val name = it.name.split(" - ").toTypedArray()[1]
            val type = it.name.split(" - ").toTypedArray()[2]

            val action = HomeFragmentDirections.actionHomeFragmentToAboutFragment(
                it,
                seriesId,
                type,
                name,
                posterUri.toASCIIString()
            )
            findNavController().navigate(action)
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "TVDB id not found", LENGTH_LONG).show()
        }
    }
}