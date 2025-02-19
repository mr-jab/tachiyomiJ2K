package eu.kanade.tachiyomi.util.system

import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures
import androidx.core.view.WindowInsetsCompat.Type.systemBars

fun WindowInsetsCompat.getBottomGestureInsets(): Int {
    return getInsetsIgnoringVisibility(mandatorySystemGestures() or systemBars()).bottom
}

/** returns if device using gesture nav and supports true edge to edge */
fun WindowInsetsCompat.isBottomTappable() =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        getInsets(systemBars()).bottom != getInsets(mandatorySystemGestures()).bottom

val View.rootWindowInsetsCompat
    get() = rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it) }

fun WindowInsetsCompat.hasSideNavBar() =
    (getInsets(systemBars()).left > 0 || getInsets(systemBars()).right > 0) && !isBottomTappable() &&
        getInsets(systemBars()).bottom == 0

@RequiresApi(Build.VERSION_CODES.R)
fun WindowInsetsCompat.isImeVisible() = isVisible(WindowInsetsCompat.Type.ime())

fun WindowInsets.topCutoutInset() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    displayCutout?.safeInsetTop ?: 0
} else 0

fun WindowInsets.bottomCutoutInset() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    displayCutout?.safeInsetBottom ?: 0
} else 0
