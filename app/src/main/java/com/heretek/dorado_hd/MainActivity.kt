package com.heretek.dorado_hd

import android.content.Intent
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
        handleCloudRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCloudRedirect(intent)
    }

    /** Completes a suspended PKCE sign-in when the browser redirects back. */
    private fun handleCloudRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals("doradohd", ignoreCase = true)) return
        val params = data.queryParameterNames.associateWith { data.getQueryParameter(it) ?: "" }
        (application as DoradoApp).graph.cloudSignInCallback.onRedirect(data.scheme, params)
    }
}
