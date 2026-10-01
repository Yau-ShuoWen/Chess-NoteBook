package com.shuowen.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shuowen.chess.ui.opening.NotebookApp
import com.shuowen.chess.ui.theme.ChessNotebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessNotebookTheme(dynamicColor = false) {
                NotebookApp()
            }
        }
    }
}
