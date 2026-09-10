package com.heretek.dorado_hd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.heretek.dorado_hd.ui.DoradoRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as DoradoApp).graph
        setContent {
            CompositionLocalProvider(
                com.heretek.dorado_hd.ui.LocalDoradoGraph provides graph,
            ) {
                DoradoRoot()
            }
        }
    }
}
