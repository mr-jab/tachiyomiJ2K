package eu.kanade.tachiyomi.ui.reader.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
    var bg: Drawable? = null,
    var bgType: Int? = null,
    var fullPage: Boolean = false
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter
}
