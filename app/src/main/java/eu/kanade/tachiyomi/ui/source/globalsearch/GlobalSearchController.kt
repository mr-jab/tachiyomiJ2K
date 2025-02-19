package eu.kanade.tachiyomi.ui.source.globalsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePaddingRelative
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding.support.v7.widget.queryTextChangeEvents
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [GlobalSearchPresenter]
 * [GlobalSearchCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class GlobalSearchController(
    protected val initialQuery: String? = null,
    val extensionFilter: String? = null,
    bundle: Bundle? = null
) : NucleusController<SourceGlobalSearchControllerBinding, GlobalSearchPresenter>(bundle),
    FloatingSearchInterface,
    GlobalSearchAdapter.OnTitleClickListener,
    GlobalSearchCardAdapter.OnMangaClickListener {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: GlobalSearchAdapter? = null

    private var customTitle: String? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun createBinding(inflater: LayoutInflater) = SourceGlobalSearchControllerBinding.inflate(inflater)

    /**
     * Set the title of controller.
     *
     * @return title.
     */
    override fun getTitle(): String? {
        return customTitle ?: presenter.query
    }

    /**
     * Create the [GlobalSearchPresenter] used in controller.
     *
     * @return instance of [GlobalSearchPresenter]
     */
    override fun createPresenter(): GlobalSearchPresenter {
        return GlobalSearchPresenter(initialQuery, extensionFilter)
    }

    override fun onTitleClick(source: CatalogueSource) {
        preferences.lastUsedCatalogueSource().set(source.id)
        router.pushController(BrowseSourceController(source, presenter.query).withFadeTransaction())
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        router.pushController(MangaDetailsController(manga, true).withFadeTransaction())
    }

    /**
     * Called when manga in global search is long clicked.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaLongClick(position: Int, adapter: GlobalSearchCardAdapter) {
        val manga = adapter.getItem(position)?.manga ?: return

        val view = view ?: return
        val activity = activity ?: return
        snack?.dismiss()
        snack = manga.addOrRemoveToFavorites(
            presenter.db,
            preferences,
            view,
            activity,
            onMangaAdded = {
                adapter.notifyItemChanged(position)
                snack = view.snack(R.string.added_to_library)
            },
            onMangaMoved = { adapter.notifyItemChanged(position) },
            onMangaDeleted = { presenter.confirmDeletion(manga) }
        )
        if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
    }

    override fun showFloatingBar() =
        activity !is SearchActivity ||
            customTitle == null ||
            extensionFilter == null

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.catalogue_new_list, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchItem.isVisible = customTitle == null
        searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    searchView.onActionViewExpanded() // Required to show the query in the view
                    searchView.setQuery(presenter.query, false)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    return true
                }
            }
        )

        searchView.queryTextChangeEvents()
            .filter { it.isSubmitted }
            .subscribeUntilDestroy {
                presenter.search(it.queryText().toString())
                searchItem.collapseActionView()
                setTitle() // Update toolbar title
            }
    }

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        adapter = GlobalSearchAdapter(this)

        binding.recycler.updatePaddingRelative(
            top = (toolbarHeight ?: 0) +
                (activityBinding?.root?.rootWindowInsetsCompat?.getInsets(systemBars())?.top ?: 0)
        )

        // Create recycler and set adapter.
        binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
        scrollViewWith(binding.recycler, padBottom = true)
        if (extensionFilter != null) {
            customTitle = view.context?.getString(R.string.loading)
            setTitle()
        }
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        adapter?.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        adapter?.onRestoreInstanceState(savedViewState)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param source used to find holder containing source
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(source: CatalogueSource): GlobalSearchHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.flexibleAdapterPosition)
            if (item != null && source.id == item.source.id) {
                return holder as GlobalSearchHolder
            }
        }

        return null
    }

    /**
     * Add search result to adapter.
     *
     * @param searchResult result of search.
     */
    fun setItems(searchResult: List<GlobalSearchItem>) {
        if (extensionFilter != null) {
            val results = searchResult.firstOrNull()?.results
            if (results != null && results.size == 1) {
                val manga = results.first().manga
                router.replaceTopController(
                    MangaDetailsController(manga, true)
                        .withFadeTransaction()
                )
                return
            } else if (results != null) {
                (activity as? SearchActivity)?.setFloatingToolbar(true)
                customTitle = null
                setTitle()
                activity?.invalidateOptionsMenu()
            }
        }
        adapter?.updateDataSet(searchResult)
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(source: CatalogueSource, manga: Manga) {
        getHolder(source)?.setImage(manga)
    }
}
