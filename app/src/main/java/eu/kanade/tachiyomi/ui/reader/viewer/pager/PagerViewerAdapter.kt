package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
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

    var subItems: MutableList<Any> = mutableListOf()
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

    private var shifted = viewer.config.shiftDoublePage
    private var doubledUp = viewer.config.doublePages
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
            // We will take an even number of pages if the page count if even
            // however we should take account full pages when deciding
            val numberOfFullPages =
                (
                    chapters.prevChapter.pages?.count { it.fullPage || it.isolatedPage }
                        ?: 0
                    )
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(if ((prevPages.size + numberOfFullPages) % 2 == 0) 2 else 3))
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

        val chapterChange = currentChapter != chapters.currChapter

        if (chapterChange && currentChapter != null) {
            viewer.doublePageShift = true
        }

        prevChapter = chapters.prevChapter
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

        val currentPage = joinedItems.getOrNull(viewer.pager.currentItem)
        var pageToUse = currentPage?.second ?: currentPage?.first

        if (shifted != viewer.config.shiftDoublePage || (doubledUp != viewer.config.doublePages && doubledUp)) {
            if (!shifted || doubledUp) {
                pageToUse = currentPage?.first
            }
            shifted = viewer.config.shiftDoublePage
        }
        doubledUp = viewer.config.doublePages
        setJoinedItems(
            pageToUse as? ReaderPage,
            chapterChange
        )
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

    fun onPageSplit(current: ReaderPage) {
        setJoinedItems(current)

//        viewer.pager.post {
//            viewer.onPageChange(viewer.pager.currentItem)
//        }
    }

    private fun setJoinedItems(currentPage: ReaderPage? = null, chapterChange: Boolean = false) {
        // If not in double mode, set up items like before
        val oldCurrent = joinedItems.getOrNull(viewer.pager.currentItem)
        if (!viewer.config.doublePages) {
            subItems.forEach {
                (it as? ReaderPage)?.shiftedPage = false
            }
            this.joinedItems = subItems.map { Pair<Any, Any?>(it, null) }.toMutableList()
            if (viewer is R2LPagerViewer) {
                joinedItems.reverse()
            }
        } else {
            val pagedItems = mutableListOf<MutableList<ReaderPage?>>()
            val otherItems = mutableListOf<Any>()
            var pagedIndex = 0
            pagedItems.add(mutableListOf())
            subItems.forEach {
                if (it is ReaderPage) {
                    pagedItems.last().add(it)
                } else {
                    otherItems.add(it)
                    pagedItems.add(mutableListOf())
                }
            }
            pagedIndex = 0
            val subJoinedItems = mutableListOf<Pair<Any, Any?>>()
            pagedItems.forEach { items ->
                if (items.size > 1 && items[0]?.isolatedPage == false && items[1]?.fullPage == true) {
                    items[0]?.isolatedPage = true
                }

                if (viewer.config.shiftDoublePage) {
                    run loop@{
                        // for shifting a page
                        val index = items.indexOf(currentPage)
                        if (currentPage?.fullPage != true) {
                            val fullPageBeforeIndex = max(
                                0,
                                (
                                    if (index > -1) (
                                        items.subList(0, index)
                                            .indexOfFirst { it?.fullPage == true }
                                        ) else -1
                                    )
                            )
                            (fullPageBeforeIndex until items.size).forEach {
                                if (items[it]?.shiftedPage == true) {
                                    return@loop
                                }
                                if (items[it]?.fullPage == false && (it + 1 == items.size || items[it + 1] != null)) {
                                    items[it]?.shiftedPage = true
                                    return@loop
                                }
                            }
                        }
                    }
                } else {
                    items.forEach {
                        it?.shiftedPage = false
                    }
                }

                var itemIndex = 0
                while (itemIndex < items.size) {
                    items[itemIndex]?.isolatedPage = false
                    if (items[itemIndex]?.fullPage == true || items[itemIndex]?.shiftedPage == true) {
                        // Add a 'blank' page after each full page. It will be used when chunked to solo a page
                        items.add(itemIndex + 1, null)
                        if (items[itemIndex]?.fullPage == true && itemIndex > 0 &&
                            items[itemIndex - 1] != null && (itemIndex - 1) % 2 == 0
                        ) {
                            items[itemIndex - 1]?.isolatedPage = true
                            items.add(itemIndex, null)
                            itemIndex++
                        }
                        itemIndex++
                    }
                    itemIndex++
                }

                if (items.isNotEmpty()) {
                    subJoinedItems.addAll(
                        items.chunked(2).map { Pair(it.first()!!, it.getOrNull(1)) }
                    )
                }
                otherItems.getOrNull(pagedIndex)?.let {
                    subJoinedItems.add(Pair(it, null))
                    pagedIndex++
                }
            }
            if (viewer is R2LPagerViewer) {
                subJoinedItems.reverse()
            }

            this.joinedItems = subJoinedItems
        }
        notifyDataSetChanged()

        if (currentPage != null /*&& !chapterChange && !viewer.lockPageMovement*/) {
            if (oldCurrent?.first == currentPage || oldCurrent?.second == currentPage) {
                val index = joinedItems.indexOfFirst { it.first == currentPage || it.second == currentPage }
                viewer.pager.setCurrentItem(index, false)
            } else {
                val newPage = oldCurrent?.first ?: currentPage
                val index = joinedItems.indexOfFirst { it.first == newPage || it.second == newPage }
                viewer.pager.setCurrentItem(index, false)
                // viewer.moveToPage(oldCurrent?.first ?: currentPage)
            }
        }
    }
}
