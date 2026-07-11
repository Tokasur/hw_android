package org.hedgewars.android.data

import android.content.Context
import java.io.File

/**
 * Filesystem layout used by the port.
 *
 * The engine reads game data through --prefix (…/files/Data) and per-user
 * content/config through --user-prefix (…/files/user). Both live in internal
 * storage: no permissions needed, wiped on uninstall.
 */
class GamePaths(context: Context) {
    val root: File = context.filesDir
    val dataDir: File = File(root, "Data")
    val userDir: File = File(root, "user")
    val userConfigDir: File = File(userDir, "Config")
    val userDataDir: File = File(userDir, "Data")
    val userTeamsDir: File = File(userConfigDir, "Teams")
    val settingsIni: File = File(userConfigDir, "settings.ini")
    val versionMarker: File = File(root, ".data-version")

    fun ensureUserDirs() {
        userConfigDir.mkdirs()
        userDataDir.mkdirs()
        userTeamsDir.mkdirs()
    }
}
