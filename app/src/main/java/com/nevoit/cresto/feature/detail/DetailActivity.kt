package com.nevoit.cresto.feature.detail

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.nevoit.cresto.data.todo.EXTRA_TODO_ID
import com.nevoit.cresto.data.todo.TodoViewModel
import com.nevoit.cresto.theme.GlasenseTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class DetailActivity : AppCompatActivity() {

    private val todoViewModel: TodoViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val todoId = intent.getIntExtra(EXTRA_TODO_ID, -1)

        setContent {
            GlasenseTheme {
                DetailScreen(todoId = todoId, viewModel = todoViewModel)
            }
        }
        window.setBackgroundDrawable(null)
    }
}
