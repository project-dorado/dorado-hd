package com.heretek.dorado_hd.social

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a rendered Zune Card to disk and hands it to the system.
 *
 * Share uses [FileProvider] (`ACTION_SEND` + `image/png`), so no storage
 * permission is required. "Save to pictures" is best-effort: on API 29+ it
 * inserts through MediaStore without a permission (the cheap path); on older
 * API levels it returns null rather than asking for WRITE_EXTERNAL_STORAGE.
 */
object CardExporter {

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** Writes [bitmap] into the app cache and returns a content Uri. */
    fun writeCache(context: Context, bitmap: Bitmap, name: String): Uri {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Builds the `ACTION_SEND` chooser intent carrying the card PNG. */
    fun shareIntent(context: Context, bitmap: Bitmap, name: String): Intent {
        val uri = writeCache(context, bitmap, name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "my zune card")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "zune card", uri)
        }
        return Intent.createChooser(send, "share card")
    }

    /**
     * Inserts the card into the shared Pictures collection (API 29+ only;
     * returns null on older platforms where it would need a storage grant).
     */
    suspend fun saveToPictures(context: Context, bitmap: Bitmap, name: String): Uri? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + PICTURES_DIR,
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext null
            runCatching {
                resolver.openOutputStream(uri).use { out ->
                    requireNotNull(out) { "no output stream" }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }.onFailure {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext null
            }
            uri
        }

    private const val CACHE_DIR = "cards"
    private const val PICTURES_DIR = "Dorado"
}
