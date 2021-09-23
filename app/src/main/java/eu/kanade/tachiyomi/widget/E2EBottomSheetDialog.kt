package eu.kanade.tachiyomi.widget

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R

/**
 * Edge to Edge BottomSheetDiolag that uses a custom theme and settings to extend pass the nav bar
 */
@Suppress("LeakingThis")
abstract class E2EBottomSheetDialog<VB : ViewBinding>(activity: Activity) :
    BottomSheetDialog(activity, R.style.BottomSheetDialogTheme) {
    protected val binding: VB

    protected val sheetBehavior: BottomSheetBehavior<*>
    protected open var recyclerView: RecyclerView? = null

    init {
        binding = createBinding(activity.layoutInflater)
        setContentView(binding.root)

        sheetBehavior = BottomSheetBehavior.from(binding.root.parent as ViewGroup)

        val contentView = binding.root

        val aWic = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        val isLight = aWic.isAppearanceLightStatusBars
        window?.let { window ->
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            window.navigationBarColor = activity.window.navigationBarColor
            wic.isAppearanceLightNavigationBars = isLight
        }
        val insets = activity.window.decorView.rootWindowInsets
        (contentView.parent as View).background = null
        contentView.post {
            (contentView.parent as View).background = null
        }
        contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.systemWindowInsetLeft
            rightMargin = insets.systemWindowInsetRight
        }
        contentView.requestLayout()
    }

    override fun onStart() {
        super.onStart()
        recyclerView?.let { recyclerView ->
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING
                    ) {
                        sheetBehavior.isDraggable = true
                    } else {
                        sheetBehavior.isDraggable = !recyclerView.canScrollVertically(-1)
                    }
                }
            })
        }
    }

    abstract fun createBinding(inflater: LayoutInflater): VB
}
