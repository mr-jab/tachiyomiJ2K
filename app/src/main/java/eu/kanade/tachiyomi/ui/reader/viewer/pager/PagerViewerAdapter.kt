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
                newItems.addAll(prevPages.takeLast(2))
            }
        }

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

        subItems.filterIsInstance<InsertPage>().also { subItems.removeAll(it) }

        val cleanItems: MutableList<ReaderPage?> = newItems.filterIsInstance<ReaderPage>().filter { it.chapter == currentChapter }.toMutableList()
        (cleanItems.indices).reversed().forEach {
            if (cleanItems[it]?.fullPage == true) {
                cleanItems.add(it + 1, null)
            }
        }
        val currentPage = joinedItems.getOrNull(viewer.pager.currentItem)
        joinedItems.clear()
        joinedItems = cleanItems.chunked(2).mapNotNull {
            it.first()?.let { page ->
                return@mapNotNull Pair<Any, Any?>(page, it.getOrNull(1))
            }
            return@mapNotNull null
        }.toMutableList()

        // items = newItems
        subItems = newItems as MutableList<Any?>
        val prevInstance = newItems.find { it is ChapterTransition.Prev }
        val nextInstance = newItems.find { it is ChapterTransition.Next }
        if (prevInstance != null) {
            joinedItems.add(0, Pair(prevInstance, null))
        }
        if (nextInstance != null) {
            joinedItems.add(Pair(nextInstance, null))
        }

        val nextItems = newItems.filterIsInstance<ReaderPage>().filter { it.chapter == chapters.nextChapter }
        joinedItems.addAll(nextItems.chunked(2).map { Pair<Any, Any?>(it.first(), it.getOrNull(1)) })
        val prevItems = newItems.filterIsInstance<ReaderPage>().filter { it.chapter == chapters.prevChapter }
        joinedItems.addAll(0, prevItems.chunked(2).map { Pair<Any, Any?>(it.first(), it.getOrNull(1)) })

        if (viewer is R2LPagerViewer) {
            joinedItems.reverse()
        }
        // items = joinedItems.map { it.first }.toMutableList()

        notifyDataSetChanged()
        ((currentPage?.second ?: currentPage?.first) as? ReaderPage)?.let {
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
            val position = joinedItems.indexOfFirst { view.item == it.first }
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
        current.fullPage = true
        if (clazz.isAssignableFrom(R2LPagerViewer::class.java)) {
            joinedItems.reverse()
        }
        val currentIndex = subItems.indexOf(newPage)
        subItems.add(currentIndex, null)
        val cleanItems = subItems.filter { (it is ReaderPage && it.chapter == current.chapter) || it == null }.toMutableList()
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
        val joinedItems = cleanItems.chunked(2).map { Pair(it.first()!!, it.getOrNull(1)) }.toMutableList()

        val prevInstance = subItems.find { it is ChapterTransition.Prev }
        val nextInstance = subItems.find { it is ChapterTransition.Next }
        if (prevInstance != null) {
            joinedItems.add(0, Pair(prevInstance, null))
        }
        if (nextInstance != null) {
            joinedItems.add(Pair(nextInstance, null))
        }

        val nextItems = subItems.filterIsInstance<ReaderPage>().filter { it.chapter == nextTransition?.to }
        joinedItems.addAll(nextItems.chunked(2).map { Pair<Any, Any?>(it.first(), it.getOrNull(1)) })
        val prevItems = subItems.filterIsInstance<ReaderPage>().filter { it.chapter == prevTransition?.to }
        joinedItems.addAll(0, prevItems.chunked(2).map { Pair<Any, Any?>(it.first(), it.getOrNull(1)) })

        if (clazz.isAssignableFrom(R2LPagerViewer::class.java)) {
            joinedItems.reverse()
        }
        this.joinedItems = joinedItems
        notifyDataSetChanged()
        viewer.pager.post {
            viewer.onPageChange(viewer.pager.currentItem)
        }
    }
}
