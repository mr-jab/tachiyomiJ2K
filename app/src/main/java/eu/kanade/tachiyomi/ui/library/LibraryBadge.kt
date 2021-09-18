package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.annotation.Dimension
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.UnreadDownloadBadgeBinding
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.updatePaddingRelative

class LibraryBadge @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private lateinit var binding: UnreadDownloadBadgeBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = UnreadDownloadBadgeBinding.bind(this)

        shapeAppearanceModel = shapeAppearanceModel.setCorners(radius, radius)
    }

    fun setUnreadDownload(unread: Int, downloads: Int, showTotalChapters: Boolean) {
        // Update the unread count and its visibility.

        val unreadBadgeBackground = if (showTotalChapters) {
            context.contextCompatColor(R.color.total_badge)
        } else context.getResourceColor(R.attr.colorSecondary)

        with(binding.unreadText) {
            isVisible = unread > 0 || unread == -1 || showTotalChapters
            if (!isVisible) {
                return@with
            }
            text = if (unread == -1) "0" else unread.toString()
            setTextColor(
                // hide the badge text when preference is only show badge
                when {
                    unread == -1 && !showTotalChapters -> unreadBadgeBackground
                    showTotalChapters -> context.contextCompatColor(R.color.total_badge_text)
                    else -> context.getResourceColor(R.attr.colorOnSecondary)
                }
            )
            setBackgroundColor(unreadBadgeBackground)
        }

        // Update the download count or local status and its visibility.
        with(binding.downloadText) {
            isVisible = downloads == -2 || downloads > 0
            if (!isVisible) {
                return@with
            }
            text = if (downloads == -2) {
                resources.getString(R.string.local)
            } else {
                downloads.toString()
            }

            setTextColor(context.getResourceColor(R.attr.colorOnTertiary))
            setBackgroundColor(context.getResourceColor(R.attr.colorTertiary))
        }

        if (binding.downloadText.isVisible) {
            binding.downloadText.background =
                MaterialShapeDrawable(shapeAppearanceModel.setCorners(topStart = radius)).apply {
                    this.fillColor =
                        ColorStateList.valueOf(context.getResourceColor(R.attr.colorTertiary))
                }
            binding.unreadText.background =
                MaterialShapeDrawable(shapeAppearanceModel.setCorners(bottomEnd = radius)).apply {
                    this.fillColor = ColorStateList.valueOf(unreadBadgeBackground)
                }
        } else {
            binding.unreadText.background =
                MaterialShapeDrawable(shapeAppearanceModel.setCorners(radius, radius)).apply {
                    this.fillColor = ColorStateList.valueOf(unreadBadgeBackground)
                }
        }

        // Show the badge card if unread or downloads exists
        isVisible = binding.downloadText.isVisible || binding.unreadText.isVisible

        // Show the angles divider if both unread and downloads exists
        binding.unreadAngle.isVisible =
            binding.downloadText.isVisible && binding.unreadText.isVisible

        binding.unreadAngle.setColorFilter(unreadBadgeBackground)
        if (binding.unreadAngle.isVisible) {
            binding.downloadText.updatePaddingRelative(end = 8.dpToPx)
            binding.unreadText.updatePaddingRelative(start = 2.dpToPx)
        } else {
            binding.downloadText.updatePaddingRelative(end = 5.dpToPx)
            binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
        }
    }

    fun setChapters(chapters: Int?) {
        setUnreadDownload(chapters ?: 0, 0, chapters != null)
    }

    fun setInLibrary(inLibrary: Boolean) {
        this.isVisible = inLibrary
        binding.unreadAngle.isVisible = false
        binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
        binding.unreadText.isVisible = inLibrary
        binding.unreadText.text = resources.getText(R.string.in_library)
        binding.unreadText.background =
            MaterialShapeDrawable(shapeAppearanceModel.setCorners(radius, radius)).apply {
                this.fillColor =
                    ColorStateList.valueOf(context.getResourceColor(R.attr.colorSecondary))
            }
    }

    private fun ShapeAppearanceModel.setCorners(
        @Dimension topStart: Float = 0f,
        @Dimension bottomEnd: Float = 0f
    ): ShapeAppearanceModel {
        return toBuilder()
            .setAllCornerSizes(0f)
            .apply {
                if (context.resources.isLTR) {
                    setTopLeftCorner(CornerFamily.ROUNDED, topStart)
                    setBottomRightCorner(CornerFamily.ROUNDED, bottomEnd)
                } else {
                    setTopRightCorner(CornerFamily.ROUNDED, topStart)
                    setBottomLeftCorner(CornerFamily.ROUNDED, bottomEnd)
                }
            }
            .build()
    }
}
