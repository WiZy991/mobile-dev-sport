package com.example.staffapp

import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

object StaffUi {
    fun enableEdgeToEdge(activity: AppCompatActivity, lightStatusBar: Boolean = false) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor =
            ContextCompat.getColor(activity, R.color.fc_surface)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightStatusBar
            isAppearanceLightNavigationBars = true
        }
    }

    fun applyAppBarInsets(appBar: AppBarLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBars.top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
    }

    fun applyBottomInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun setupToolbar(
        toolbar: MaterialToolbar,
        title: String,
        showBack: Boolean,
        showLogout: Boolean,
        onBack: (() -> Unit)?,
        onLogout: (() -> Unit)?,
    ) {
        toolbar.title = title
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_staff_back)
            toolbar.setNavigationOnClickListener { onBack?.invoke() }
        } else {
            toolbar.navigationIcon = null
        }
        toolbar.menu.clear()
        if (showLogout) {
            toolbar.inflateMenu(R.menu.staff_toolbar)
            toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_logout) {
                    onLogout?.invoke()
                    true
                } else {
                    false
                }
            }
        } else {
            toolbar.setOnMenuItemClickListener(null)
        }
    }
}
