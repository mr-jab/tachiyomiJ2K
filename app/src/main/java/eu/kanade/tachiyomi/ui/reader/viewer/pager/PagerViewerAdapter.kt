package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import timber.log.Timber
import kotlin.math.max

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    // private var items: MutableList<Any> = mutableListOf()

    var subItems: MutableList<Any?> = mutableListOf()
        private set

    var nextTransition: ChapterTransition.Next? = null
        private set

    var prevTransition: ChapterTransition.Prev? = null
        private set

    var prevChapter: ReaderChapter? = null
    var nextChapter: ReaderChapter? = null

    /**
     * List of currently set items.
     */
    var joinedItems: MutableList<Pair<Any, Any?>> = mutableListOf()
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Add previous chapter pages and transition.
        if (chapters.prevChapter != null) {
            // We only need to add the last few pages of the previous chapter, because it'll be
            // selected as the current chapter when one of those pages is selected.
            val prevPages = chapters.prevChapter.pages
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(if (prevPages.size % 2 == 0) 2 else 3))
            }
        }

        prevChapter = chapters.prevChapter

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            prevTransition = ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter)
            newItems.add(prevTransition!!)
        }

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            newItems.addAll(currPages)
        }

        currentChapter = chapters.currChapter
        nextChapter = chapters.nextChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        if (chapters.nextChapter != null) {
            // Add at most two pages, because this chapter will be selected before the user can
            // swap more pages.
            val nextPages = chapters.nextChapter.pages
            if (nextPages != null) {
                newItems.addAll(nextPages.take(2))
            }
        }

        subItems = newItems.toMutableList()

        var currentPage = joinedItems.getOrNull(viewer.pager.currentItem)
        setJoinedItems(chapters.currChapter, (currentPage?.second ?: currentPage?.first) as? ReaderPage)
        currentPage = joinedItems.getOrNull(viewer.pager.currentItem)
        (currentPage?.first as? ReaderPage)?.let {
            viewer.moveToPage(it, false)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return joinedItems.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        val item = joinedItems[position].first
        val item2 = joinedItems[position].second
        return when (item) {
            is InsertPage -> PagerPageHolder(viewer, item)
            is ReaderPage -> PagerPageHolder(viewer, item, item2 as? ReaderPage)
            is ChapterTransition -> PagerTransitionHolder(viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = joinedItems.indexOfFirst {
                view.item == (it.first to it.second)
            }
            if (position != -1) {
                return position
            } else {
                Timber.d("Position for ${view.item} not found")
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(current: Any?, newPage: ReaderPage, clazz: Class<out PagerViewer>) {
        if (current !is ReaderPage) return
        val currentIndex = subItems.indexOf(newPage)
        subItems.add(currentIndex, null)
        setJoinedItems(current.chapter, current)

        viewer.pager.post {
            viewer.onPageChange(viewer.pager.currentItem)
        }
    }

    private fun setJoinedItems(currentChapter: ReaderChapter, currentPage: ReaderPage? = null) {
        val cleanItems: MutableList<ReaderPage?> = subItems.filterIsInstance<ReaderPage>().filter { it.chapter == currentChapter }.toMutableList()
        (cleanItems.indices).reversed().forEach {
            if (cleanItems[it]?.fullPage == true) {
                cleanItems.add(it + 1, null)
            }
        }
        if (viewer.config.shiftDoublePage) {
            run loop@{
                val index = cleanItems.indexOf(currentPage)
                val fullPageBeforeIndex = max(
                    0,
                    (if (index > -1) cleanItems.subList(0, index).indexOfFirst { it?.fullPage == true } else -1)
                )
                (fullPageBeforeIndex until cleanItems.size).forEach {
                    if (cleanItems[it]?.shiftedPage == true) {
                        return@loop
                    }
                    if (cleanItems[it]?.fullPage == false) {
                        cleanItems[it]?.shiftedPage = true
                        cleanItems.add(it + 1, null)
                        return@loop
                    }
                }
            }
        } else {
            cleanItems.forEach {
                it?.shiftedPage = false
            }
        }
        var indexToAdd = -1
        cleanItems.forEachIndexed { index, any ->
            if (any == null && index > 1 && cleanItems[index - 2] != null && index % 2 == 0) {
                indexToAdd = index - 1
                return@forEachIndexed
            }
        }
        if (indexToAdd > -1) {
            cleanItems.add(indexToAdd, null)
        }
        val joinedItems: MutableList<Pair<Any, Any?>> = cleanItems.chunked(2).map { Pair(it.first()!!, it.getOrNull(1)) }.toMutableList()

        val prevInstance = subItems.find { it is ChapterTransition.Prev }
        val nextInstance = subItems.find { it is ChapterTransition.Next }
        if (prevInstance != null) {
            joinedItems.add(0, Pair(prevInstance, null))
        }
        if (nextInstance != null) {
            joinedItems.add(Pair(nextInstance, null))
        }

        val nextItems: MutableList<ReaderPage?> = subItems.filterIsInstance<ReaderPage>().filter { it.chapter == nextChapter }.toMutableList()
        (nextItems.indices).reversed().forEach {
            if (nextItems[it]?.fullPage == null) {
                nextItems.add(it + 1, null)
            }
        }
        joinedItems.addAll(
            nextItems.chunked(2).mapNotNull {
                it.first()?.let { page ->
                    Pair<Any, Any?>(page, it.getOrNull(1))
                }
            }
        )
        val prevItems: MutableList<ReaderPage?> = subItems.filterIsInstance<ReaderPage>().filter { it.chapter == prevChapter }.toMutableList()
        (prevItems.indices).reversed().forEach {
            if (prevItems[it]?.fullPage == null) {
                prevItems.add(it + 1, null)
            }
        }
        joinedItems.addAll(
            0,
            prevItems.chunked(2).mapNotNull {
                it.first()?.let { page ->
                    Pair<Any, Any?>(page, it.getOrNull(1))
                }
            }
        )
        if (viewer is R2LPagerViewer) {
            joinedItems.reverse()
        }
        this.joinedItems = joinedItems
        notifyDataSetChanged()
    }
}
