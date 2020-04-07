package com.zourw.libqrcode_ktx

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

/**
 * Created by Zourw on 2020/4/2.
 */
fun dp2px(context: Context, dpValue: Float) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    dpValue,
    context.resources.displayMetrics
)

fun Int.withAlpha(alpha: Float) = Color.argb((alpha * 255.0f + 0.5f).toInt(), red, green, blue)

fun isPortrait(context: Context) =
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).run {
        val screenResolution = Point()
        defaultDisplay.getSize(screenResolution)
        screenResolution.y > screenResolution.x
    }

fun makeStatusBarTransparent(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        activity.window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility =
                decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}