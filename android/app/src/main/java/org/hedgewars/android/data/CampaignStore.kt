package org.hedgewars.android.data

import android.content.Context
import androidx.core.content.edit
import org.hedgewars.android.engine.GameConnection

/**
 * Persists campaign/mission variables the engine reads and writes over IPC
 * ('V'/'v' messages). The desktop frontend stores these in the team's .hwt
 * file; here a SharedPreferences file per (team, campaign/mission) scope
 * fills the same role.
 */
class CampaignStore(
    context: Context,
    private val teamName: String,
    /** Campaign name (e.g. "A_Classic_Fairytale") or mission script name. */
    private val scope: String,
) : GameConnection.MissionVarStore {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    override fun getVar(campaign: Boolean, name: String): String =
        prefs.getString(key(campaign, name), "") ?: ""

    override fun setVar(campaign: Boolean, name: String, value: String) {
        prefs.edit { putString(key(campaign, name), value) }
    }

    private fun key(campaign: Boolean, name: String): String {
        val kind = if (campaign) "Campaign" else "Mission"
        return "$teamName/$kind $scope/$name"
    }
}
