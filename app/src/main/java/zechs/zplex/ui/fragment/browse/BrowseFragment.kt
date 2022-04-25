package zechs.zplex.ui.fragment.browse

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.constraintlayout.widget.Constraints
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import zechs.zplex.R
import zechs.zplex.adapter.SearchAdapter
import zechs.zplex.databinding.FragmentBrowseBinding
import zechs.zplex.models.enum.MediaType
import zechs.zplex.models.enum.Order
import zechs.zplex.models.enum.SortBy
import zechs.zplex.models.tmdb.entities.Media
import zechs.zplex.models.tmdb.search.SearchResponse
import zechs.zplex.ui.BaseFragment
import zechs.zplex.ui.activity.main.MainActivity
import zechs.zplex.ui.dialog.FiltersDialog
import zechs.zplex.ui.fragment.shared_viewmodels.FiltersViewModel
import zechs.zplex.utils.Resource

class BrowseFragment : BaseFragment() {

    override val enterTransitionListener: Transition.TransitionListener? = null

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private val filterModel by activityViewModels<FiltersViewModel>()
    private lateinit var browseViewModel: BrowseViewModel
    private lateinit var filtersDialog: FiltersDialog

    private val browseAdapter by lazy { SearchAdapter() }

    private var isLoading = true
    private var isLastPage = true
    private val thisTAG = "BrowseFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        browseViewModel = (activity as MainActivity).browseViewModel
        setupRecyclerView()
        setupFiltersObservers()
        setupBrowseObservers()

        binding.btnFilters.setOnClickListener {
            context?.let { it1 -> showFiltersDialog(it1) }
        }
    }

    private fun setupBrowseObservers() {
        browseViewModel.browse.observe(viewLifecycleOwner) { response ->
            when (response) {
                is Resource.Success -> response.data?.let { onSuccess(it) }
                is Resource.Error -> response.message?.let { onError(it) }
                is Resource.Loading -> isLoading = true
            }
        }
    }

    private fun onSuccess(showsResponse: SearchResponse) {
        binding.apply {
            pbBrowse.isVisible = false
            rvBrowse.isVisible = true
        }

        browseAdapter.differ.submitList(showsResponse.results.toList())
        isLastPage = showsResponse.page == showsResponse.total_pages

        isLoading = false

        when (filterModel.getFilter()?.mediaType) {
            MediaType.movie -> {
                binding.tvMediaType.text = getString(R.string.browsing_movies)
            }
            MediaType.tv -> {
                binding.tvMediaType.text = getString(R.string.browsing_tv)
            }
            else -> {}
        }

    }

    private fun onError(message: String) {
        val errorMsg = message.ifEmpty { resources.getString(R.string.something_went_wrong) }
        Log.e(thisTAG, errorMsg)
        binding.apply {
            pbBrowse.isVisible = true
            rvBrowse.isVisible = false
            errorView.root.isVisible = true
        }
        binding.errorView.apply {
            errorTxt.text = errorMsg
        }
        isLoading = false
    }

    private fun setupFiltersObservers() {
        filterModel.filterArgs.observe(viewLifecycleOwner) { filter ->
            TransitionManager.beginDelayedTransition(binding.root)
            browseViewModel.getBrowse(filter)
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy > 0) {
                val layoutManager = binding.rvBrowse.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.findLastCompletelyVisibleItemPosition() + 3
                val itemCount = layoutManager.itemCount
                val filterArgs = filterModel.getFilter()

                Log.d(
                    "onScrolled",
                    "visibleItemCount=$visibleItemCount, itemCount=$itemCount," +
                            " isLoading=$isLoading, isLastPage=$isLastPage," +
                            " filterArgs=${filterArgs == null}"
                )

                if (visibleItemCount >= itemCount && !isLoading && !isLastPage) {
                    (filterArgs?.let { browseViewModel.getBrowse(it) })
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvBrowse.apply {
            adapter = browseAdapter
            layoutManager = GridLayoutManager(activity, 3)
            addOnScrollListener(this@BrowseFragment.scrollListener)
        }
        browseAdapter.setOnItemClickListener { media, _, _ ->
            navigateMedia(media)
        }
    }

    private fun getMovieGenre(): LinkedHashMap<String, Int> {
        val genreMap = linkedMapOf<String, Int>()
        genreMap["Select genre"] = 0
        genreMap["Action"] = 28
        genreMap["Adventure"] = 12
        genreMap["Animation"] = 16
        genreMap["Comedy"] = 35
        genreMap["Crime"] = 80
        genreMap["Documentary"] = 99
        genreMap["Drama"] = 18
        genreMap["Family"] = 10751
        genreMap["Fantasy"] = 14
        genreMap["History"] = 36
        genreMap["Horror"] = 27
        genreMap["Music"] = 10402
        genreMap["Mystery"] = 9648
        genreMap["Romance"] = 10749
        genreMap["Sci-fi"] = 878
        genreMap["TV movie"] = 10770
        genreMap["Thriller"] = 53
        genreMap["War"] = 10752
        genreMap["Western"] = 37
        return genreMap
    }

    private fun getTvGenre(): LinkedHashMap<String, Int> {
        val genreMap = linkedMapOf<String, Int>()
        genreMap["Select genre"] = 0
        genreMap["Action"] = 10759
        genreMap["Animation"] = 16
        genreMap["Comedy"] = 35
        genreMap["Crime"] = 80
        genreMap["Documentary"] = 99
        genreMap["Drama"] = 18
        genreMap["Family"] = 10751
        genreMap["Kids"] = 10762
        genreMap["Mystery"] = 9648
        genreMap["News"] = 10763
        genreMap["Reality"] = 10764
        genreMap["Sci-fi"] = 10765
        genreMap["War"] = 10768
        genreMap["Western"] = 37
        return genreMap
    }

    private fun getSorts(): LinkedHashMap<String, String> {
        val sortMap = linkedMapOf<String, String>()
        sortMap["Popularity"] = "popularity"
        sortMap["Release date"] = "release_date"
        sortMap["Revenue"] = "revenue"
        sortMap["Title"] = "original_title"
        sortMap["Average vote"] = "vote_average"
        sortMap["Total vote"] = "vote_count"
        return sortMap
    }

    private fun getSort(key: String): String {
        return getSorts().filterValues { it == key }.keys.elementAt(0)
    }

    private fun getGenre(name: String, genres: LinkedHashMap<String, Int>) = genres[name] ?: -1

    private fun getGenre(key: Int, genres: LinkedHashMap<String, Int>): String {
        return genres.filterValues { it == key }.keys.elementAt(0)
    }

    private fun navigateMedia(media: Media) {
        val mediaType = when {
            media.name == null -> "movie"
            media.title == null -> "tv"
            else -> "tv"
        }
        val action = BrowseFragmentDirections.actionDiscoverFragmentToFragmentMedia(
            media.copy(media_type = mediaType)
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.apply {
            rvBrowse.adapter = null
        }
        _binding = null
    }


    private fun showFiltersDialog(context: Context) {
        filtersDialog = FiltersDialog(context)
        filtersDialog.show()

        filtersDialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                Constraints.LayoutParams.MATCH_PARENT,
                Constraints.LayoutParams.WRAP_CONTENT
            )
        }


        val dialogRoot = filtersDialog.findViewById<MaterialCardView>(R.id.dialog_root)

        val btnApply = filtersDialog.findViewById<MaterialButton>(R.id.btn_apply)
        val btnReset = filtersDialog.findViewById<MaterialButton>(R.id.btn_reset)
        val mediaChipGroup = filtersDialog.findViewById<ChipGroup>(R.id.chipGroup_media)
        val genreMenu = filtersDialog.findViewById<MaterialButton>(R.id.genre_menu)
        val sortMenu = filtersDialog.findViewById<MaterialButton>(R.id.sort_menu)

        val currentFilters = filterModel.getFilter()
        val moviesGenreList = getMovieGenre().keys.toList()
        val tvGenreList = getTvGenre().keys.toList()

        if (currentFilters != null) {
            when (currentFilters.mediaType) {
                MediaType.movie -> {
                    val movieChip = filtersDialog.findViewById<Chip>(R.id.chip_movie)
                    val tvChip = filtersDialog.findViewById<Chip>(R.id.chip_tv)
                    movieChip.isChecked = true
                    tvChip.isChecked = false
                    setupGenresMenu(context, filtersDialog, moviesGenreList)
                    if (currentFilters.withGenres == null) {
                        genreMenu.text = getString(R.string.select_genre)
                    } else {
                        genreMenu.text = getGenre(currentFilters.withGenres, getMovieGenre())
                    }
                }
                MediaType.tv -> {
                    val movieChip = filtersDialog.findViewById<Chip>(R.id.chip_movie)
                    val tvChip = filtersDialog.findViewById<Chip>(R.id.chip_tv)
                    movieChip.isChecked = false
                    tvChip.isChecked = true
                    setupGenresMenu(context, filtersDialog, tvGenreList)
                    if (currentFilters.withGenres == null) {
                        genreMenu.text = getString(R.string.select_genre)
                    } else {
                        genreMenu.text = getGenre(currentFilters.withGenres, getTvGenre())
                    }
                }
            }
            sortMenu.text = getSort(currentFilters.sortBy.name)
        }

        setupSortMenu(context, filtersDialog)

        mediaChipGroup.setOnCheckedChangeListener { group, checkedId ->
            TransitionManager.beginDelayedTransition(dialogRoot)
            val mediaChecked = group.findViewById<Chip>(
                checkedId
            ).text.toString()

            genreMenu.text = getString(R.string.select_genre)

            currentFilters?.let {
                when (mediaChecked) {
                    "Movies" -> setupGenresMenu(context, filtersDialog, moviesGenreList)
                    "TV Shows" -> setupGenresMenu(context, filtersDialog, tvGenreList)
                    else -> {}
                }
            }
        }

        btnReset.setOnClickListener {
            filterModel.setFilter(
                mediaType = MediaType.movie,
                sortBy = SortBy.popularity,
                order = Order.desc,
                page = 1,
                withKeyword = null,
                withGenres = null
            )
            filtersDialog.dismiss()
        }

        btnApply.setOnClickListener {
            val mediaChecked = mediaChipGroup.findViewById<Chip>(
                mediaChipGroup.checkedChipId
            ).text.toString()

            val movieGenre = getGenre(genreMenu.text.toString(), getMovieGenre())
            val tvGenre = getGenre(genreMenu.text.toString(), getTvGenre())

            val genre = when (mediaChecked) {
                "Movies" -> if (movieGenre == -1 || movieGenre == 0) null else movieGenre
                "TV Shows" -> if (tvGenre == -1 || tvGenre == 0) null else tvGenre
                else -> null
            }
            val sortBy = SortBy.valueOf(getSorts()[sortMenu.text.toString()] ?: "Popularity")

            val mediaType = when (mediaChecked) {
                "Movies" -> MediaType.movie
                "TV Shows" -> MediaType.tv
                else -> MediaType.movie
            }

            filterModel.setFilter(
                mediaType = mediaType,
                sortBy = sortBy,
                order = Order.desc,
                page = 1,
                withKeyword = null,
                withGenres = genre
            )
            filtersDialog.dismiss()
        }


    }

    private fun setupGenresMenu(
        context: Context, filtersDialog: FiltersDialog,
        genreList: List<String>,
    ) {

        val dialogRoot = filtersDialog.findViewById<MaterialCardView>(R.id.dialog_root)
        val genreMenu = filtersDialog.findViewById<MaterialButton>(R.id.genre_menu)

        val listPopupGenres = ListPopupWindow(
            context, null,
            R.attr.listPopupWindowStyle
        )

        listPopupGenres.setAdapter(null)

        val adapter = ArrayAdapter(
            context,
            R.layout.item_dropdown,
            genreList
        )

        listPopupGenres.apply {
            isModal = true
            width = 450
            height = 700
            anchorView = genreMenu
            setAdapter(adapter)

            listPopupGenres.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                TransitionManager.beginDelayedTransition(dialogRoot)
                genreMenu.text = genreList[position]
                listPopupGenres.dismiss()
            }
        }

        genreMenu.setOnClickListener { listPopupGenres.show() }
    }

    private fun setupSortMenu(context: Context, filtersDialog: FiltersDialog) {

        val sortList = getSorts().keys.toList()

        val dialogRoot = filtersDialog.findViewById<MaterialCardView>(R.id.dialog_root)
        val sortMenu = filtersDialog.findViewById<MaterialButton>(R.id.sort_menu)

        val listPopupSortBy = ListPopupWindow(
            context, null,
            R.attr.listPopupWindowStyle
        )

        listPopupSortBy.setAdapter(null)

        val adapter = ArrayAdapter(
            context,
            R.layout.item_dropdown,
            sortList
        )

        listPopupSortBy.apply {
            isModal = true
            width = 450
            height = 700
            anchorView = sortMenu
            setAdapter(adapter)

            listPopupSortBy.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                TransitionManager.beginDelayedTransition(dialogRoot)
                sortMenu.text = sortList[position]
                listPopupSortBy.dismiss()
            }
        }

        sortMenu.setOnClickListener { listPopupSortBy.show() }
    }
}