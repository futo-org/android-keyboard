package org.futo.voiceinput.shared

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import org.futo.voiceinput.shared.ui.theme.Typography
import java.io.File

@Composable
fun Screen(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxSize()) {
        Text(title, style = Typography.titleLarge)


        Column(modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()) {
            content()
        }
    }
}

class ValueFromSettings<T>(val key: Preferences.Key<T>, val default: T) {
    private var _value = default

    val value: T
        get() { return _value }

    suspend fun load(context: Context, onResult: ((T) -> Unit)? = null) {
        val valueFlow: Flow<T> = context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        valueFlow.collect {
            _value = it

            if(onResult != null) {
                onResult(it)
            }
        }
    }

    suspend fun get(context: Context): T {
        val valueFlow: Flow<T> =
            context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        return valueFlow.first()
    }
}

enum class Status {
    Unknown,
    False,
    True;

    companion object {
        fun from(found: Boolean): Status {
            return if (found) { True } else { False }
        }
    }
}

data class ModelData(
    val name: String,

    val is_builtin_asset: Boolean,
    val encoder_xatn_file: String,
    val decoder_file: String,

    val vocab_file: String,
    val vocab_raw_asset: Int? = null
)

fun Array<DoubleArray>.transpose(): Array<DoubleArray> {
    return Array(this[0].size) { i ->
        DoubleArray(this.size) { j ->
            this[j][i]
        }
    }
}

fun Array<DoubleArray>.shape(): IntArray {
    return arrayOf(size, this[0].size).toIntArray()
}

fun DoubleArray.toFloatArray(): FloatArray {
    return this.map { it.toFloat() }.toFloatArray()
}

fun FloatArray.toDoubleArray(): DoubleArray {
    return this.map { it.toDouble() }.toDoubleArray()
}

fun Context.startModelDownloadActivity(models: List<ModelData>) {
    // TODO
}

val ENGLISH_MODELS = listOf(
    // TODO: The names are not localized
    ModelData(
        name = "English-39 (default)",

        is_builtin_asset = true,
        encoder_xatn_file = "tiny-en-encoder-xatn.tflite",
        decoder_file = "tiny-en-decoder.tflite",

        vocab_file = "tinyenvocab.json",
        vocab_raw_asset = R.raw.tinyenvocab
    ),
    ModelData(
        name = "English-74 (slower, more accurate)",

        is_builtin_asset = false,
        encoder_xatn_file = "base.en-encoder-xatn.tflite",
        decoder_file = "base.en-decoder.tflite",

        vocab_file = "base.en-vocab.json",
    )
)

val MULTILINGUAL_MODELS = listOf(
    ModelData(
        name = "Multilingual-39 (less accurate)",

        is_builtin_asset = false,
        encoder_xatn_file = "tiny-multi-encoder-xatn.tflite",
        decoder_file = "tiny-multi-decoder.tflite",

        vocab_file = "tiny-multi-vocab.json",
    ),
    ModelData(
        name = "Multilingual-74 (default)",

        is_builtin_asset = false,
        encoder_xatn_file = "base-encoder-xatn.tflite",
        decoder_file = "base-decoder.tflite",

        vocab_file = "base-vocab.json",
    ),
    ModelData(
        name = "Multilingual-244 (slow)",

        is_builtin_asset = false,
        encoder_xatn_file = "small-encoder-xatn.tflite",
        decoder_file = "small-decoder.tflite",

        vocab_file = "small-vocab.json",
    ),
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settingsVoice")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val VERBOSE_PROGRESS = booleanPreferencesKey("verbose_progress")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")
val DISALLOW_SYMBOLS = booleanPreferencesKey("disallow_symbols")

val ENGLISH_MODEL_INDEX = intPreferencesKey("english_model_index")
val ENGLISH_MODEL_INDEX_DEFAULT = 0

val MULTILINGUAL_MODEL_INDEX = intPreferencesKey("multilingual_model_index")
val MULTILINGUAL_MODEL_INDEX_DEFAULT = 1

val LANGUAGE_TOGGLES = stringSetPreferencesKey("enabled_languages")