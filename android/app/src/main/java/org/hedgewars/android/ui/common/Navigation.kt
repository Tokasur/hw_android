package org.hedgewars.android.ui.common

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/**
 * Pops the back stack only when the current entry is actually on screen.
 *
 * Tapping the ← arrow (or a Save button that pops) several times in a row used
 * to pop once per tap: the extra pops walked past Home and left the user on an
 * empty NavHost. While a pop transition runs, the entry that owns the tapped
 * arrow is no longer RESUMED, so this swallows the repeats.
 *
 * Not for pops fired from a `LaunchedEffect` during the *entering* transition
 * (an editor bailing out on a missing preset): those legitimately run before
 * RESUMED and must use [NavController.popBackStack] directly.
 */
fun NavController.safeBack(): Boolean =
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    } else {
        false
    }
