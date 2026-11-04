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
import java.time.LocalDate

class StudyLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date == null) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            GlasenseTheme {
                StudyLogScreen(date)
            }
        }
        clearBackground()
    }

    companion object {
        private const val EXTRA_DATE = "date"

        fun createIntent(context: Context, date: LocalDate): Intent {
            return Intent(context, StudyLogActivity::class.java)
                .putExtra(EXTRA_DATE, date.toString())
        }
    }
}
