package org.boringreport.boringlauncher

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.LauncherApps
import android.view.View
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect

/**
 * Launches [app] so the opening activity scales up out of [bounds] - the row the user actually
 * tapped - rather than sliding in from the right.
 *
 * The slide is the system's default activity-open transition, and it is what you get whenever
 * the caller supplies no animation of its own. A launcher is supposed to look like the app is
 * unfolding from the thing you touched, which means handing the system an explicit
 * [ActivityOptions] describing where on screen the launch came from.
 *
 * [bounds] is expected in root-view coordinates, i.e. what `boundsInRoot()` reports, which is
 * the same coordinate space [ActivityOptions.makeScaleUpAnimation] wants for [rootView].
 */
fun launchApp(context: Context, rootView: View, app: LauncherApp, bounds: Rect?) {
    val launcherApps = context.getSystemService(LauncherApps::class.java)

    // No measured bounds yet (the row has not been laid out): launch without an animation
    // rather than scaling up out of the top-left corner.
    if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
        launcherApps.startMainActivity(app.componentName, app.user, null, null)
        return
    }

    val options = ActivityOptions.makeScaleUpAnimation(
        rootView,
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.width.roundToInt(),
        bounds.height.roundToInt()
    )

    launcherApps.startMainActivity(
        app.componentName,
        app.user,
        bounds.onScreen(rootView),
        options.toBundle()
    )
}

/**
 * Translates root-view coordinates into the screen coordinates that `sourceBounds` expects.
 * Android 12+ uses it to grow the target app's splash-screen icon out of the same spot, so it
 * needs to agree with the scale-up animation.
 */
private fun Rect.onScreen(rootView: View): AndroidRect {
    val origin = IntArray(2)
    rootView.getLocationOnScreen(origin)

    return AndroidRect(
        origin[0] + left.roundToInt(),
        origin[1] + top.roundToInt(),
        origin[0] + right.roundToInt(),
        origin[1] + bottom.roundToInt()
    )
}
