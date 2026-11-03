package com.nevoit.cresto.feature.daily

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.nevoit.cresto.theme.GlasenseTheme
import com.nevoit.glasense.core.utility.clearBackground

class HabitDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
        if (habitId == null) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            GlasenseTheme {
                HabitDetailScreen(habitId)
            }
        }
        clearBackground()
    }

    companion object {
        private const val EXTRA_HABIT_ID = "habit_id"

        fun createIntent(context: Context, habitId: String): Intent {
            return Intent(context, HabitDetailActivity::class.java)
                .putExtra(EXTRA_HABIT_ID, habitId)
        }
    }
}
