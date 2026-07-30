package org.hedgewars.android.ui.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.hedgewars.android.data.PackContentIndex

/**
 * Small decoded-thumbnail cache. Map previews (256x128) and theme icons are
 * tiny, and the same handful scroll in and out of view repeatedly, so a modest
 * bitmap cache keeps grid scrolling smooth without re-reading from disk.
 */
private val thumbCache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private suspend fun decode(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    thumbCache.get(file.path)?.let { return@withContext it }
    if (!file.exists()) return@withContext null
    runCatching {
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { thumbCache.put(file.path, it) }
    }.getOrNull()
}

/**
 * Draws a PNG loaded from [file] (decoded off the main thread and cached).
 * While loading, or if the file is missing/unreadable, the [placeholder] is
 * shown instead so grids never flash blank tiles.
 */
@Composable
fun DiskImage(
    file: File,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = {},
) {
    val image by produceState<ImageBitmap?>(initialValue = null, file.path) {
        value = decode(file)
    }
    val img = image
    if (img != null) {
        Image(bitmap = img, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.fillMaxSize()) { placeholder() }
    }
}

/**
 * Like [DiskImage] for content that may come from a downloaded pack: tries
 * the system Data tree first, then the user content (loose file or zip entry
 * inside an installed .hwp, via [PackContentIndex]). Same cache, same
 * placeholder contract.
 */
@Composable
fun PackImage(
    rel: String,
    dataDir: File,
    index: PackContentIndex,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = {},
) {
    val image by produceState<ImageBitmap?>(initialValue = null, rel) {
        value = decode(File(dataDir, rel)) ?: decodePackEntry(index, rel)
    }
    val img = image
    if (img != null) {
        Image(bitmap = img, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.fillMaxSize()) { placeholder() }
    }
}

private suspend fun decodePackEntry(index: PackContentIndex, rel: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val key = "pack:$rel"
        thumbCache.get(key)?.let { return@withContext it }
        val bytes = index.readEntry(rel) ?: return@withContext null
        runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                ?.also { thumbCache.put(key, it) }
        }.getOrNull()
    }
