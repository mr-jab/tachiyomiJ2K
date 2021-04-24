package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import rx.Observable
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 *
 * @param context the application context.
 */
class DownloadManager(val context: Context) {

    /**
     * The sources manager.
     */
    private val sourceManager by injectLazy<SourceManager>()

    /**
     * Downloads provider, used to retrieve the folders where the chapters are or should be stored.
     */
    private val provider = DownloadProvider(context)

    /**
     * Cache of downloaded chapters.
     */
    private val cache = DownloadCache(context, provider, sourceManager)

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = Downloader(context, provider, cache, sourceManager)

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    /**
     * Downloads queue, where the pending chapters are stored.
     */
    val queue: DownloadQueue
        get() = downloader.queue

    /**
     * Subject for subscribing to downloader status.
     */
    val runningRelay: BehaviorRelay<Boolean>
        get() = downloader.runningRelay

    /**
     * Tells the downloader to begin downloads.
     *
     * @return true if it's started, false otherwise (empty queue).
     */
    fun startDownloads(): Boolean {
        return downloader.start()
    }

    /**
     * Tells the downloader to stop downloads.
     *
     * @param reason an optional reason for being stopped, used to notify the user.
     */
    fun stopDownloads(reason: String? = null) {
        downloader.stop(reason)
    }

    fun setPlaceholder() {
        downloader.setPlaceholder()
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
    }

    /**
     * Empties the download queue.
     *
     * @param isNotification value that determines if status is set (needed for view updates)
     */
    fun clearQueue(isNotification: Boolean = false) {
        deletePendingDownloads(*downloader.queue.toTypedArray())
        downloader.clearQueue(isNotification)
        DownloadService.callListeners(false)
    }

    fun startDownloadNow(chapter: Chapter) {
        val download = downloader.queue.find { it.chapter.id == chapter.id } ?: return
        val queue = downloader.queue.toMutableList()
        queue.remove(download)
        queue.add(0, download)
        reorderQueue(queue)
        if (isPaused()) {
            if (DownloadService.isRunning(context)) {
                downloader.start()
            } else {
                DownloadService.start(context)
            }
        }
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        val wasPaused = isPaused()
        if (downloads.isEmpty()) {
            DownloadService.stop(context)
            downloader.queue.clear()
            return
        }
        downloader.pause()
        downloader.queue.clear()
        downloader.queue.addAll(downloads)
        if (!wasPaused) {
            downloader.start()
        }
    }

    fun isPaused() = downloader.isPaused()

    fun hasQueue() = downloader.queue.isNotEmpty()

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param manga the manga of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {
        downloader.queueChapters(manga, chapters, autoStart)
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param source the source of the chapter.
     * @param manga the manga of the chapter.
     * @param chapter the downloaded chapter.
     * @return an observable containing the list of pages from the chapter.
     */
    fun buildPageList(source: Source, manga: Manga, chapter: Chapter): Observable<List<Page>> {
        return buildPageList(provider.findChapterDir(chapter, manga, source))
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param chapterDir the file where the chapter is downloaded.
     * @return an observable containing the list of pages from the chapter.
     */
    private fun buildPageList(chapterDir: UniFile?): Observable<List<Page>> {
        return Observable.fromCallable {
            val files = chapterDir?.listFiles().orEmpty()
                .filter { "image" in it.type.orEmpty() }

            if (files.isEmpty()) {
                throw Exception("Page list is empty")
            }

            files.sortedBy { it.name }
                .mapIndexed { i, file ->
                    Page(i, uri = file.uri).apply { status = Page.READY }
                }
        }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param manga the manga of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(chapter: Chapter, manga: Manga, skipCache: Boolean = false): Boolean {
        return cache.isChapterDownloaded(chapter, manga, skipCache)
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return cache.getDownloadCount(manga)
    }

    /*fun renameCache(from: String, to: String, source: Long) {
        cache.renameFolder(from, to, source)
    }*/

    /**
     * Calls delete chapter, which deletes temp downloads
     *  @param downloads list of downloads to cancel
     */
    fun deletePendingDownloads(vararg downloads: Download) {
        val downloadsByManga = downloads.groupBy { it.manga.id }
        downloadsByManga.map { entry ->
            val manga = entry.value.first().manga
            val source = entry.value.first().source
            deleteChapters(entry.value.map { it.chapter }, manga, source)
        }
    }

    /**
     * Deletes the directories of a list of partially downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source) {
        GlobalScope.launch(Dispatchers.IO) {
            val wasPaused = isPaused()
            if (chapters.isEmpty()) {
                DownloadService.stop(context)
                downloader.queue.clear()
                return@launch
            }
            downloader.pause()
            downloader.queue.remove(chapters)
            if (!wasPaused && downloader.queue.isNotEmpty()) {
                downloader.start()
            } else if (downloader.queue.isEmpty() && DownloadService.isRunning(context)) {
                DownloadService.stop(context)
            } else if (downloader.queue.isEmpty()) {
                DownloadService.callListeners(false)
                downloader.stop()
            }
            queue.remove(chapters)
            val chapterDirs =
                provider.findChapterDirs(chapters, manga, source) + provider.findTempChapterDirs(
                    chapters,
                    manga,
                    source
                )
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(chapters, manga)
            if (cache.getDownloadCount(manga, true) == 0) { // Delete manga directory if empty
                chapterDirs.firstOrNull()?.parentFile?.delete()
            }
            queue.updateListeners()
        }
    }

    /**
     * return the list of all manga folders
     */
    fun getMangaFolders(source: Source): List<UniFile> {
        return provider.findSourceDir(source)?.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Deletes the directories of chapters that were read or have no match
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun cleanupChapters(allChapters: List<Chapter>, manga: Manga, source: Source, removeRead: Boolean, removeNonFavorite: Boolean): Int {
        var cleaned = 0

        if (removeNonFavorite && !manga.favorite) {
            val mangaFolder = provider.getMangaDir(manga, source)
            cleaned += 1 + (mangaFolder.listFiles()?.size ?: 0)
            mangaFolder.delete()
            cache.removeManga(manga)
            return cleaned
        }

        val filesWithNoChapter = provider.findUnmatchedChapterDirs(allChapters, manga, source)
        cleaned += filesWithNoChapter.size
        cache.removeFolders(filesWithNoChapter.mapNotNull { it.name }, manga)
        filesWithNoChapter.forEach { it.delete() }

        if (removeRead) {
            val readChapters = allChapters.filter { it.read }
            val readChapterDirs = provider.findChapterDirs(readChapters, manga, source)
            readChapterDirs.forEach { it.delete() }
            cleaned += readChapterDirs.size
            cache.removeChapters(readChapters, manga)
        }

        if (cache.getDownloadCount(manga) == 0) {
            val mangaFolder = provider.getMangaDir(manga, source)
            val size = mangaFolder.listFiles()?.size ?: 0
            if (size == 0) {
                mangaFolder.delete()
                cache.removeManga(manga)
            } else {
                Timber.e("Cache and download folder doesn't match for %s", manga.title)
            }
        }
        return cleaned
    }

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     */
    fun deleteManga(manga: Manga, source: Source) {
        downloader.clearQueue(manga, true)
        queue.remove(manga)
        provider.findMangaDir(manga, source)?.delete()
        cache.removeManga(manga)
        queue.updateListeners()
    }

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     */
    fun enqueueDeleteChapters(chapters: List<Chapter>, manga: Manga) {
        pendingDeleter.addChapters(chapters, manga)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param manga the manga of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        val oldName = provider.getChapterDirName(oldChapter)
        val newName = provider.getChapterDirName(newChapter)
        val mangaDir = provider.getMangaDir(manga, source)

        val oldFolder = mangaDir.findFile(oldName)
        if (oldFolder?.renameTo(newName) == true) {
            cache.removeChapters(listOf(oldChapter), manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            Timber.e("Could not rename downloaded chapter: %s.", oldName)
        }
    }

    fun addListener(listener: DownloadQueue.DownloadListener) = queue.addListener(listener)
    fun removeListener(listener: DownloadQueue.DownloadListener) = queue.removeListener(listener)
}
