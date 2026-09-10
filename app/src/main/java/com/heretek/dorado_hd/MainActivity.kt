package com.heretek.dorado_hd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.heretek.dorado_hd.ui.DoradoRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) requestLibraryRefresh()
    }

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
        requestMediaPermissionsIfNeeded()
        handleCloudRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCloudRedirect(intent)
    }

    private fun requiredMediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun requestMediaPermissionsIfNeeded() {
        val missing = requiredMediaPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            mediaPermissions.launch(missing.toTypedArray())
        }
    }

    /** Rebuild the collection once media access is available. */
    private fun requestLibraryRefresh() {
        lifecycleScope.launch {
            runCatching { (application as DoradoApp).graph.library.refresh() }
        }
    }

    /** Completes a suspended PKCE sign-in when the browser redirects back. */
    private fun handleCloudRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals("doradohd", ignoreCase = true)) return
        // Opaque URIs (e.g. `doradohd:foo`) are not hierarchical and
        // queryParameterNames() throws on them. Ignore anything that is not
        // a well-formed hierarchical redirect.
        if (!data.isHierarchical) return
        val params = runCatching {
            data.queryParameterNames.associateWith { data.getQueryParameter(it) ?: "" }
        }.getOrNull() ?: return
        (application as DoradoApp).graph.cloudSignInCallback.onRedirect(data.scheme, params)
    }
}
