package com.novelatlas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelatlas.app.ui.navigation.NovelAtlasRoot
import com.novelatlas.app.ui.theme.NovelAtlasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelAtlasTheme {
                NovelAtlasRoot((application as NovelAtlasApp).graph)
            }
        }
    }
}
