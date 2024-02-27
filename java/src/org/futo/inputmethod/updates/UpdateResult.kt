package org.futo.inputmethod.updates

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.BuildConfig

@Serializable
data class UpdateResult(
    val nextVersion: Int,
    val apkUrl: String,
    val nextVersionString: String
) {
    fun isNewer(): Boolean {
        return nextVersion > currentVersion()
    }

    companion object {
        fun currentVersion(): Int {
            return BuildConfig.VERSION_CODE
        }

        fun currentVersionString(): String {
            return BuildConfig.VERSION_NAME
        }

        fun fromString(value: String): UpdateResult? {
            if(value.isEmpty()) {
                return null
            }

            try {
                return Json.decodeFromString<UpdateResult>(value)
            } catch(e: SerializationException) {
                Log.e("UpdateResult", "Failed to deserialize UpdateResult value $value")
                e.printStackTrace()
                return null
            } catch(e: IllegalArgumentException) {
                Log.e("UpdateResult", "Failed to deserialize UpdateResult value $value")
                e.printStackTrace()
                return null
            }
        }
    }
}
