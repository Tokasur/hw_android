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
    val userDataDir: File = File(userDir, "Data")
    // The engine mounts the user prefix AT the virtual path "/Config"
    // (uPhysFSLayer: pfsMount(userPrefix, '/Config')), so the files it opens
    // as /Config/settings.ini and /Config/Teams/x.hwt live at the user dir
    // ROOT — a physical Config/ subdirectory is invisible to it (the binds,
    // including the back-button quit and all gamepad bindings, were silently
    // never loaded when settings.ini sat in user/Config/).
    val userTeamsDir: File = File(userDir, "Teams")
    val settingsIni: File = File(userDir, "settings.ini")
    val versionMarker: File = File(root, ".data-version")

    fun ensureUserDirs() {
        userDataDir.mkdirs()
        userTeamsDir.mkdirs()
    }
}
