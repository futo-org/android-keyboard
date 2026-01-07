package org.futo.inputmethod.latin.uix.actions

import android.os.Build
import android.os.Debug
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.general.GeneralIME
import org.futo.inputmethod.engine.general.JapaneseIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.LocalFoldingState
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.KeyboardSizeStateProvider

val DebugLabel = Typography.Small.copy(fontFamily = FontFamily.Monospace)
val DebugTitle = Typography.Body.Medium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

private fun getInputTypeAsString(inputType: Int): String {
    val types = mutableListOf<String>()

    // Classify the base type
    when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> {
            types.add("TEXT")

            // Add variations
            when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_NORMAL -> types.add("NORMAL")
                InputType.TYPE_TEXT_VARIATION_URI -> types.add("URI")
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> types.add("EMAIL_ADDRESS")
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> types.add("EMAIL_SUBJECT")
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> types.add("SHORT_MESSAGE")
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> types.add("LONG_MESSAGE")
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> types.add("PERSON_NAME")
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> types.add("POSTAL_ADDRESS")
                InputType.TYPE_TEXT_VARIATION_PASSWORD -> types.add("PASSWORD")
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> types.add("VISIBLE_PASSWORD")
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> types.add("WEB_EDIT_TEXT")
                InputType.TYPE_TEXT_VARIATION_FILTER -> types.add("FILTER")
                InputType.TYPE_TEXT_VARIATION_PHONETIC -> types.add("PHONETIC")
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> types.add("WEB_EMAIL_ADDRESS")
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> types.add("WEB_PASSWORD")

            }

            // Add flags
            if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
                types.add("FLAG_CAP_CHARACTERS")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0) {
                types.add("FLAG_CAP_WORDS")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0) {
                types.add("FLAG_CAP_SENTENCES")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0) {
                types.add("FLAG_AUTO_CORRECT")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) {
                types.add("FLAG_AUTO_COMPLETE")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
                types.add("FLAG_MULTI_LINE")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE != 0) {
                types.add("FLAG_IME_MULTI_LINE")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) {
                types.add("FLAG_NO_SUGGESTIONS")
            }
            if (inputType and InputType.TYPE_TEXT_FLAG_ENABLE_TEXT_CONVERSION_SUGGESTIONS != 0) {
                types.add("FLAG_ENABLE_TEXT_CONVERSION_SUGGESTIONS")
            }
        }
        InputType.TYPE_CLASS_NUMBER -> {
            types.add("NUMBER")

            // Add variations
            when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_NUMBER_VARIATION_NORMAL -> types.add("NORMAL")
                InputType.TYPE_NUMBER_VARIATION_PASSWORD -> types.add("PASSWORD")
            }

            // Add flags
            if (inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0) {
                types.add("FLAG_SIGNED")
            }
            if (inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) {
                types.add("FLAG_DECIMAL")
            }
        }
        InputType.TYPE_CLASS_PHONE -> {
            types.add("PHONE")
        }
        InputType.TYPE_CLASS_DATETIME -> {
            types.add("DATETIME")

            // Add variations
            when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_DATETIME_VARIATION_NORMAL -> types.add("NORMAL")
                InputType.TYPE_DATETIME_VARIATION_DATE -> types.add("DATE")
                InputType.TYPE_DATETIME_VARIATION_TIME -> types.add("TIME")
            }
        }
    }

    return types.joinToString(" | ")
}


private fun getImeOptionsString(imeOptions: Int): String {
    val options = mutableListOf<String>()

    // Add action
    when (imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_NONE -> options.add("IME_ACTION_NONE")
        EditorInfo.IME_ACTION_GO -> options.add("IME_ACTION_GO")
        EditorInfo.IME_ACTION_SEARCH -> options.add("IME_ACTION_SEARCH")
        EditorInfo.IME_ACTION_SEND -> options.add("IME_ACTION_SEND")
        EditorInfo.IME_ACTION_NEXT -> options.add("IME_ACTION_NEXT")
        EditorInfo.IME_ACTION_DONE -> options.add("IME_ACTION_DONE")
        EditorInfo.IME_ACTION_PREVIOUS -> options.add("IME_ACTION_PREVIOUS")
    }

    // Add flags
    if (imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0) {
        options.add("IME_FLAG_NO_FULLSCREEN")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS != 0) {
        options.add("IME_FLAG_NAVIGATE_PREVIOUS")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0) {
        options.add("IME_FLAG_NAVIGATE_NEXT")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0) {
        options.add("IME_FLAG_NO_EXTRACT_UI")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION != 0) {
        options.add("IME_FLAG_NO_ACCESSORY_ACTION")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
        options.add("IME_FLAG_NO_ENTER_ACTION")
    }
    if (imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0) {
        options.add("IME_FLAG_FORCE_ASCII")
    }
    if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) {
        options.add("IME_FLAG_NO_PERSONALIZED_LEARNING")
    }

    return options.joinToString(" | ")
}


val MemoryDebugAction = Action(
    icon = R.drawable.code,
    name = R.string.action_debug_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        val latinIme = manager.getLatinIMEForDebug()
        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_debug_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val state: MutableState<Map<String, String>> = remember { mutableStateOf(mapOf()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(250)

                        val newInfo = Debug.MemoryInfo()
                        Debug.getMemoryInfo(newInfo)
                        state.value = newInfo.memoryStats
                    }
                }
                
                val foldingState = LocalFoldingState.current

                ScrollableList {
                    Text("Editor Info", style = DebugTitle)
                    latinIme.currentInputEditorInfo?.let { info ->
                        Text("packageName       = ${info.packageName}",       style = DebugLabel)
                        Text("inputType         = ${info.inputType} (${getInputTypeAsString(info.inputType)})", style = DebugLabel)
                        Text("imeOptions        = ${info.imeOptions} (${getImeOptionsString(info.imeOptions)})", style = DebugLabel)
                        Text("privateImeOptions = ${info.privateImeOptions}", style = DebugLabel)
                        Text("actionId          = ${info.actionId}",          style = DebugLabel)
                        Text("actionLabel       = ${info.actionLabel}",       style = DebugLabel)
                        Text("extras            = ${info.extras}",            style = DebugLabel)
                        Text("fieldId           = ${info.fieldId}",           style = DebugLabel)
                        Text("fieldName         = ${info.fieldName}",         style = DebugLabel)
                        Text("hintLocales       = ${info.hintLocales}",       style = DebugLabel)
                        Text("hintText          = ${info.hintText}",          style = DebugLabel)
                        Text("initialCapsMode   = ${info.initialCapsMode}",   style = DebugLabel)
                        Text("initialSelEnd     = ${info.initialSelEnd}",     style = DebugLabel)
                        Text("initialSelStart   = ${info.initialSelStart}",   style = DebugLabel)
                        Text("label             = ${info.label}",             style = DebugLabel)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            Text("contentMimeTypes  = ${info.contentMimeTypes?.joinToString(",")}", style = DebugLabel)
                        }

                        info
                    } ?: run {
                        Text("editor info is null", style = DebugLabel)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Keyboard State", style = DebugTitle)
                    val ime = remember { latinIme.imeManager.getActiveIME(Settings.getInstance().current) }
                    when(ime) {
                        is GeneralIME -> {
                            ime.debugInfo().forEach {
                                Text(it, style = DebugLabel)
                            }
                        }

                        is JapaneseIME -> {
                            Text("JapaneseIME [no debug info yet]", style = DebugLabel)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Screen State Info", style = DebugTitle)
                    Text("size mode     = ${(manager.getContext() as KeyboardSizeStateProvider).currentSizeState}", style = DebugLabel)
                    Text("Fold State", style = DebugTitle)
                    Text("state         = ${foldingState.feature?.state}",          style = DebugLabel)
                    Text("orientation   = ${foldingState.feature?.orientation}",    style = DebugLabel)
                    Text("isSeparating  = ${foldingState.feature?.isSeparating}",   style = DebugLabel)
                    Text("occlusionType = ${foldingState.feature?.occlusionType}",  style = DebugLabel)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("IME as GeneralIME = ${manager.getIMEInterface(GeneralIME::class.java)}")
                    Text("IME as JapaneseIME = ${manager.getIMEInterface(JapaneseIME::class.java)}")

                    Spacer(modifier = Modifier.height(8.dp))


                    Text("Memory Use", style = DebugTitle)
                    state.value.forEach {
                        val value = it.value.toInt().toFloat() / 1000.0f
                        Text("${it.key}: ${String.format("%.2f", value)}MB", style = DebugLabel)
                    }

                    Button(onClick = {
                        val testTexts = listOf(
                            "One type of sentence.",
                            "One other type of sentence?",
                            "[Three kinds now! Okay, this text should appear identically 5 times]"
                        )


                        val txn = manager.createInputTransaction()
                        testTexts.forEach { txn.updatePartial(it) }
                        txn.commit(testTexts.last() + " ")


                        val innerDelays = listOf(16L, 20L, 24L, 200L)
                        manager.getLifecycleScope().launch {
                            for(innerDelay in innerDelays) {
                                delay(1000L)
                                run {
                                    val txn = manager.createInputTransaction()
                                    testTexts.forEach {
                                        delay(innerDelay)
                                        withContext(Dispatchers.Main) { txn.updatePartial(it) }
                                    }
                                    withContext(Dispatchers.Main) { txn.commit(testTexts.last() + " ") }
                                }
                            }
                        }

                    }) {
                        Text("Test action input transaction")
                    }
                }
            }
        }
    }
)