package org.futo.inputmethod.latin

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.setSetting

val SubtypesSetting = SettingsKey(
    stringSetPreferencesKey("subtypes"),
    setOf()
)

suspend fun Context.saveSubtypes() {
    val inputMethodManager = getSystemService(android.inputmethodservice.InputMethodService.INPUT_METHOD_SERVICE) as InputMethodManager
    val inputMethodList = inputMethodManager.getEnabledInputMethodSubtypeList(
        RichInputMethodManager.getInstance().inputMethodInfoOfThisIme,
        true
    )

    val encodedSubtypes = inputMethodList.map {
        it.locale + ":" + (it.extraValue ?: "") + ":" + it.languageTag
    }.toSet()

    setSetting(SubtypesSetting, encodedSubtypes)
}