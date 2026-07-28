package org.hedgewars.android.config

/**
 * Positional facts about the 60 ammo types (TAmmoType order, hedgewars/uTypes.pas):
 * index i is character i of every [WeaponSet] string and frame i of the
 * ammo-menu sprite sheet. Display names live in the ammo_names string-array.
 */
object AmmoCatalog {
    /** amSkip: always infinite, never in crates; hidden in the editor. */
    const val SKIP = 6

    /** amCreeper: unfinished weapon the engine force-zeroes; hidden too. */
    const val CREEPER = 57

    val HIDDEN = setOf(SKIP, CREEPER)

    /** Editor rows, in ammo-string order. */
    val VISIBLE: List<Int> = (0 until WeaponSet.SIZE).filter { it !in HIDDEN }

    /**
     * Graphics/AmmoMenu/Ammos_base.png geometry: 32x32 cells filled
     * column-major, 16 per column (uRenderUtils.pas DrawSpriteFrame2Surf).
     */
    const val CELL = 32
    const val ROWS_PER_COLUMN = 16

    fun iconCol(index: Int): Int = index / ROWS_PER_COLUMN

    fun iconRow(index: Int): Int = index % ROWS_PER_COLUMN
}
