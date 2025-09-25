package org.futo.inputmethod.latin.uix.settings.pages.pdict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.PersonalWord
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.UserDictionaryIO
import org.futo.inputmethod.latin.uix.getImportedUserDictFilesForLocale
import org.futo.inputmethod.latin.uix.removeImportedUserDictFile
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage
import java.util.Locale

@Suppress("HardCodedStringLiteral")
enum class PosTypes(val id: MozcPos, val text: String) {
    NO_POS(MozcPos.NO_POS, "品詞なし"),
    NOUN(MozcPos.NOUN, "名詞"),
    ABBREVIATION(MozcPos.ABBREVIATION, "短縮よみ"),
    SUGGESTION_ONLY(MozcPos.SUGGESTION_ONLY, "サジェストのみ"),
    PROPER_NOUN(MozcPos.PROPER_NOUN, "固有名詞"),
    PERSONAL_NAME(MozcPos.PERSONAL_NAME, "人名"),
    FAMILY_NAME(MozcPos.FAMILY_NAME, "姓"),
    FIRST_NAME(MozcPos.FIRST_NAME, "名"),
    ORGANIZATION_NAME(MozcPos.ORGANIZATION_NAME, "組織"),
    PLACE_NAME(MozcPos.PLACE_NAME, "地名"),
    SA_IRREGULAR_CONJUGATION_NOUN(MozcPos.SA_IRREGULAR_CONJUGATION_NOUN, "名詞サ変"),
    ADJECTIVE_VERBAL_NOUN(MozcPos.ADJECTIVE_VERBAL_NOUN, "名詞形動"),
    NUMBER(MozcPos.NUMBER, "数"),
    ALPHABET(MozcPos.ALPHABET, "アルファベット"),
    SYMBOL(MozcPos.SYMBOL, "記号"),
    EMOTICON(MozcPos.EMOTICON, "顔文字"),
    ADVERB(MozcPos.ADVERB, "副詞"),
    PRENOUN_ADJECTIVAL(MozcPos.PRENOUN_ADJECTIVAL, "連体詞"),
    CONJUNCTION(MozcPos.CONJUNCTION, "接続詞"),
    INTERJECTION(MozcPos.INTERJECTION, "感動詞"),
    PREFIX(MozcPos.PREFIX, "接頭語"),
    COUNTER_SUFFIX(MozcPos.COUNTER_SUFFIX, "助数詞"),
    GENERIC_SUFFIX(MozcPos.GENERIC_SUFFIX, "接尾一般"),
    PERSON_NAME_SUFFIX(MozcPos.PERSON_NAME_SUFFIX, "接尾人名"),
    PLACE_NAME_SUFFIX(MozcPos.PLACE_NAME_SUFFIX, "接尾地名"),
    WA_GROUP1_VERB(MozcPos.WA_GROUP1_VERB, "動詞ワ行五段"),
    KA_GROUP1_VERB(MozcPos.KA_GROUP1_VERB, "動詞カ行五段"),
    SA_GROUP1_VERB(MozcPos.SA_GROUP1_VERB, "動詞サ行五段"),
    TA_GROUP1_VERB(MozcPos.TA_GROUP1_VERB, "動詞タ行五段"),
    NA_GROUP1_VERB(MozcPos.NA_GROUP1_VERB, "動詞ナ行五段"),
    MA_GROUP1_VERB(MozcPos.MA_GROUP1_VERB, "動詞マ行五段"),
    RA_GROUP1_VERB(MozcPos.RA_GROUP1_VERB, "動詞ラ行五段"),
    GA_GROUP1_VERB(MozcPos.GA_GROUP1_VERB, "動詞ガ行五段"),
    BA_GROUP1_VERB(MozcPos.BA_GROUP1_VERB, "動詞バ行五段"),
    HA_GROUP1_VERB(MozcPos.HA_GROUP1_VERB, "動詞ハ行四段"),
    GROUP2_VERB(MozcPos.GROUP2_VERB, "動詞一段"),
    KURU_GROUP3_VERB(MozcPos.KURU_GROUP3_VERB, "動詞カ変"),
    SURU_GROUP3_VERB(MozcPos.SURU_GROUP3_VERB, "動詞サ変"),
    ZURU_GROUP3_VERB(MozcPos.ZURU_GROUP3_VERB, "動詞ザ変"),
    RU_GROUP3_VERB(MozcPos.RU_GROUP3_VERB, "動詞ラ変"),
    ADJECTIVE(MozcPos.ADJECTIVE, "形容詞"),
    SENTENCE_ENDING_PARTICLE(MozcPos.SENTENCE_ENDING_PARTICLE, "終助詞"),
    PUNCTUATION(MozcPos.PUNCTUATION, "句読点"),
    FREE_STANDING_WORD(MozcPos.FREE_STANDING_WORD, "独立語"),
    SUPPRESSION_WORD(MozcPos.SUPPRESSION_WORD, "抑制単語"),
}
typealias MozcPos = ProtoUserDictionaryStorage.UserDictionary.PosType

data class JapanesePersonalWord(
    val furigana: String,
    val output: String,
    val pos: PosTypes
)

fun JapanesePersonalWord.encode(locale: Locale? = Locale.JAPANESE) = PersonalWord(
    word = output,
    shortcut = "${furigana}\t${PosTypes.entries.indexOf(pos)}",
    locale = locale?.toString(),
    appId = 0,
    frequency = 0
)

fun decodeJapanesePersonalWord(word: PersonalWord): JapanesePersonalWord? {
    val segments = (word.shortcut ?: return null).split('\t', limit = 2)
    if(segments.size != 2) return null

    return JapanesePersonalWord(
        output = word.word,
        furigana = segments[0],
        pos = PosTypes.entries[segments[1].toIntOrNull() ?: return null]
    )
}

@Composable
@Preview
fun JapaneseWordPopupDialog(selectedWord: JapanesePersonalWord? = null, locale: Locale? = Locale.JAPANESE) {
    val navController = LocalNavController.current
    val furigana = remember { mutableStateOf((selectedWord?.furigana ?: "")) }
    val word = remember { mutableStateOf((selectedWord?.output ?: "")) }
    val pos = remember { mutableStateOf(selectedWord?.pos ?: PosTypes.NO_POS) }
    AlertDialog(
        icon = { },
        title = {
            Text(
                if (selectedWord == null) {
                    stringResource(R.string.user_dict_settings_add_dialog_title)
                } else {
                    stringResource(R.string.user_dict_settings_edit_dialog_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("よみ:")
                SettingsTextEdit(furigana, placeholder = "...")

                Spacer(Modifier.Companion.height(12.dp))

                Text("単語:")
                SettingsTextEdit(word, placeholder = "...")

                Spacer(Modifier.Companion.height(12.dp))

                Text("品詞:")
                DropDownPicker(
                    options = PosTypes.entries.toList(),
                    selection = pos.value,
                    onSet = { pos.value = it },
                    getDisplayName = { it.text },
                    scrollableOptions = true)
            }
        },
        onDismissRequest = {
            navController!!.navigateUp()
        },
        confirmButton = {
            val context = LocalContext.current
            Row {
                if (selectedWord != null) {
                    TextButton(
                        onClick = {
                            val udictIo = UserDictionaryIO(context)
                            udictIo.remove(listOf(selectedWord.encode(locale)))
                            GlobalIMEMessage.tryEmit(IMEMessage.ReloadPersonalDict)
                            navController!!.navigateUp()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.user_dict_settings_delete))
                    }
                }
                Spacer(Modifier.Companion.weight(1.0f))

                TextButton(
                    onClick = {
                        navController!!.navigateUp()
                    }
                ) {
                    Text(stringResource(R.string.action_emoji_clear_recent_emojis_cancel))
                }

                TextButton(onClick = {
                    val udictIo = UserDictionaryIO(context)
                    if (selectedWord != null) {
                        // Edit existing word by deleting it then re-inserting the new one
                        udictIo.remove(listOf(selectedWord.encode(locale)))
                    }

                    val wordToAdd = JapanesePersonalWord(
                        furigana = furigana.value,
                        output = word.value,
                        pos = pos.value
                    ).encode(locale)

                    udictIo.put(
                        listOf(
                            wordToAdd
                        ), clear = false
                    )

                    GlobalIMEMessage.tryEmit(IMEMessage.ReloadPersonalDict)
                    navController!!.navigateUp()
                }, enabled = word.value.isNotBlank()) {
                    Text(stringResource(R.string.user_dict_settings_add_dialog_confirm))
                }
            }
        },
    )
}

@Composable
fun ConfirmDeleteExtraDictFileDialog(name: String) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    AlertDialog(
        icon = { },
        title = {
            Text(stringResource(R.string.personal_dictionary_delete_additional_file))
        },
        text = {
            Text(
                stringResource(
                    R.string.personal_dictionary_delete_additional_file_text,
                    name.split(' ', limit = 2)[0]
                )
            )
        },
        onDismissRequest = {
            navController!!.navigateUp()
        },
        dismissButton = {
            TextButton(onClick = {
                navController!!.navigateUp()
            }) {
                Text(stringResource(R.string.action_emoji_clear_recent_emojis_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val fileToRemove = getImportedUserDictFilesForLocale(context, null).find {
                    it.first.name == name
                }!!

                lifecycle.lifecycleScope.launch(Dispatchers.IO) {
                    removeImportedUserDictFile(context, fileToRemove)
                    withContext(Dispatchers.Main) { navController!!.navigateUp() }
                }
            }) {
                Text(stringResource(R.string.user_dict_settings_delete))
            }
        },
    )
}

fun localeSupportsFileImport(locale: Locale?) =
    locale?.language == "ja"