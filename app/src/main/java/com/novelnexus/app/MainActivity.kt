package com.novelnexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelnexus.app.ui.navigation.NovelNexusRoot
import com.novelnexus.app.ui.theme.NovelNexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelNexusTheme {
                NovelNexusRoot((application as NovelNexusApp).graph)
            }
        }
    }
}
