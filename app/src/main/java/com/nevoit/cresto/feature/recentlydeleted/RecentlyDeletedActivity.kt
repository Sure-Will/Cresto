package com.nevoit.cresto.feature.recentlydeleted

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.nevoit.cresto.data.todo.TodoViewModel
import com.nevoit.cresto.theme.GlasenseTheme
import com.nevoit.glasense.core.utility.clearBackground
import org.koin.androidx.viewmodel.ext.android.viewModel

class RecentlyDeletedActivity : AppCompatActivity() {

    private val todoViewModel: TodoViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            GlasenseTheme {
                RecentlyDeletedScreen(viewModel = todoViewModel)
            }
        }
        clearBackground()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, RecentlyDeletedActivity::class.java)
        }
    }
}
