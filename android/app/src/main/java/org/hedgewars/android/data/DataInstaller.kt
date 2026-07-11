package org.hedgewars.android.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Copies the game data (Data/ tree) from APK/asset-pack assets to
 * [GamePaths.dataDir] on first launch and after app updates.
 *
 * With Play Asset Delivery the "hw_data" install-time pack is merged into the
 * app's AssetManager automatically, so the same code path serves both the
 * bundle install and the fat sideload APK (-PdataInApp).
 */
class DataInstaller(private val context: Context, private val paths: GamePaths) {

    sealed class State {
        data object NotInstalled : State()
        data class Installing(val filesDone: Int, val filesTotal: Int) : State()
        data object Ready : State()
        data class Failed(val error: String) : State()
    }

    private val expectedVersion: String by lazy {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        "v${pkg.versionCode}"
    }

    fun isInstalled(): Boolean =
        paths.versionMarker.takeIf { it.exists() }?.readText() == expectedVersion &&
            File(paths.dataDir, "Themes").isDirectory

    /**
     * Performs the copy, reporting progress through [onProgress].
     * Call from a worker thread/coroutine.
     */
    @Throws(IOException::class)
    fun install(onProgress: (done: Int, total: Int) -> Unit) {
        val assets = context.assets
        // prepare-data.sh writes one relative path per line; reading it avoids
        // thousands of slow AssetManager.list() calls.
        val files = try {
            assets.open(MANIFEST).bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: IOException) {
            throw IOException(
                "Game data assets not found. The APK/AAB was built without " +
                    "the Data tree (run android/scripts/prepare-data.sh first).", e
            )
        }
        paths.versionMarker.delete()
        paths.dataDir.deleteRecursively()
        paths.ensureUserDirs()
        Log.i(TAG, "installing ${files.size} data files")

        val buf = ByteArray(256 * 1024)
        files.forEachIndexed { index, path ->
            val target = File(paths.root, path)
            target.parentFile?.mkdirs()
            assets.open(path).use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
            if (index % 64 == 0) onProgress(index + 1, files.size)
        }
        onProgress(files.size, files.size)
        paths.versionMarker.writeText(expectedVersion)
    }

    companion object {
        private const val TAG = "HWDataInstaller"
        private const val MANIFEST = "Data.manifest"
    }
}
