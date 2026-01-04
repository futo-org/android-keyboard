package org.futo.inputmethod.latin.qs

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KEYBOARD_SUPPRESSED
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSetting

@RequiresApi(Build.VERSION_CODES.N)
class HideKeyboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileStateFromPrefs()
    }

    override fun onClick() {
        super.onClick()
        val newSuppressed = !applicationContext.getSettingBlocking(KEYBOARD_SUPPRESSED)

        CoroutineScope(Dispatchers.IO).launch {
            applicationContext.setSetting(KEYBOARD_SUPPRESSED, newSuppressed)
        }

        updateTile(newSuppressed)

        val message = if (newSuppressed) {
            getString(R.string.qs_tile_keyboard_suppressed_toast)
        } else {
            getString(R.string.qs_tile_keyboard_enabled_toast)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun syncTileStateFromPrefs() {
        updateTile(applicationContext.getSettingBlocking(KEYBOARD_SUPPRESSED))
    }

    private fun updateTile(suppressed: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (suppressed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (suppressed) {
            getString(R.string.qs_tile_keyboard_suppressed)
        } else {
            getString(R.string.qs_tile_keyboard_normal)
        }
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}
