package com.ahmedalsinan.dumbcallerid

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// Quick settings tile that turns the popup on or off without opening the app. An "active" tile
// (see the manifest): Android only asks for its state when refresh() is called or the tile is tapped.
class PopupTile : TileService() {
    override fun onTileAdded() = render()

    override fun onStartListening() = render()

    override fun onClick() {
        Prefs.setEnabled(this, !Prefs.isEnabled(this))
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val enabled = Prefs.isEnabled(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(if (enabled) R.string.status_active else R.string.status_off)
        tile.updateTile()
    }

    companion object {
        // Call after changing the setting elsewhere, so the tile shows it.
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, PopupTile::class.java))
    }
}
