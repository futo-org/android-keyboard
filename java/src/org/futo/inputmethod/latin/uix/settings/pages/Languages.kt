package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.BinaryDictionaryGetter
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.icon
import org.futo.inputmethod.latin.uix.kindTitle
import org.futo.inputmethod.latin.uix.namePreferenceKeyFor
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.pages.modelmanager.openModelImporter
import org.futo.inputmethod.latin.uix.settings.useDataStoreValueBlocking
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.youAreImporting
import org.futo.inputmethod.latin.utils.Dictionaries
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.openURI
import java.util.Locale

data class LanguageItem (
    val languageName: String,
    val options: LanguageOptions,
    val layouts: List<String>
)

val TextHeadingMediumMlStyle = Typography.titleLarge.copy(
    fontSize = 20.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight(500)
)

val TextSmallStyle = Typography.titleSmall.copy(fontWeight = FontWeight.W400)
val TextBodyRegularMlStyle = Typography.titleMedium.copy(fontWeight = FontWeight.W400)

@Composable
fun LanguageConfigurable(
    kind: FileKind,
    selection: String
) {
    Row(modifier = Modifier
        .padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp)
        .defaultMinSize(0.dp, 50.dp)) {
        Column(modifier = Modifier
            .weight(1.0f)
            .align(Alignment.CenterVertically)) {
            Row(modifier = Modifier.alpha(0.75f)) {
                Icon(
                    painterResource(kind.icon()),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    kind.kindTitle(),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = TextSmallStyle
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                selection,
                style = TextBodyRegularMlStyle
            )

        }
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(44.dp)
                .padding(10.dp)
        )
    }
}

@Composable
fun LayoutConfigurable(
    name: String,
    active: Boolean
) {
    Row(modifier = Modifier
        .padding(start = 16.dp, end = 6.dp)
        .height(44.dp)) {
        Text(
            name,
            style = TextBodyRegularMlStyle,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        Spacer(modifier = Modifier.weight(1.0f))

        Switch(checked = active, onCheckedChange = {}, modifier = Modifier.align(Alignment.CenterVertically))
        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = { /*TODO*/ }, modifier = Modifier.align(Alignment.CenterVertically)) {
            Icon(painterResource(id = R.drawable.trash), contentDescription = null)
        }
    }
}

@Composable
fun ActionableItem(
    icon: Painter,
    text: String,
    color: Color
) {
    TextButton(onClick = { /*TODO*/ }, colors = ButtonColors(
        containerColor = Color.Transparent,
        contentColor = color,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = color.copy(alpha = 0.75f)
    ), modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)) {
        Row {
            Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, modifier = Modifier.align(Alignment.CenterVertically), style = Typography.labelLarge)
        }
    }
}

@Composable
fun LanguageSurface(item: LanguageItem, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(start = 32.dp, end = 32.dp)
            .widthIn(296.dp, 400.dp)
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(size = 16.dp)
            )
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
            Text(item.languageName, modifier = Modifier.padding(start = 16.dp, end = 16.dp), style = TextHeadingMediumMlStyle)
            Spacer(modifier = Modifier.height(32.dp))

            LanguageConfigurable(kind = FileKind.VoiceInput, selection = item.options.voiceInputModel ?: "(None)")
            LanguageConfigurable(kind = FileKind.Dictionary, selection = item.options.dictionary ?: "(None)")
            LanguageConfigurable(kind = FileKind.Transformer, selection = item.options.transformerModel ?: "(None)")

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Keyboard Layouts",
                modifier = Modifier
                    .alpha(0.75f)
                    .padding(start = 16.dp, end = 16.dp),
                style = TextSmallStyle
            )

            Spacer(modifier = Modifier.height(9.dp))

            item.layouts.forEach {
                LayoutConfigurable(name = it, active = true)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(21.dp))

            ActionableItem(
                icon = painterResource(id = R.drawable.plus_circle),
                text = "Add Keyboard Layout",
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            ActionableItem(
                icon = painterResource(id = R.drawable.trash),
                text = "Remove Language",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Preview
@Composable
fun LanguageSurfacePreview() {
    UixThemeWrapper(colorScheme = DynamicDarkTheme.obtainColors(LocalContext.current)) {
        LanguageSurface(item = LanguageItem(
            languageName = "Language Name",
            options = LanguageOptions(
                "Model Name",
                "Model Name",
                "Model Name"
            ),
            layouts = listOf(
                "Keyboard Name",
                "Keyboard Name"
            )
        ))
    }
}

data class LanguageOptions(
    val voiceInputModel: String?,
    val dictionary: String?,
    val transformerModel: String?
)

@Composable
fun ConfirmResourceActionDialog(
    onDismissRequest: () -> Unit,

    onExplore: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,

    resourceKind: FileKind,
    isCurrentlySet: Boolean,
    locale: Locale
) {
    val hasBuiltInFallback = if(resourceKind == FileKind.VoiceInput) {
        ResourceHelper.BuiltInVoiceInputFallbacks[locale.language] != null
    } else if(resourceKind == FileKind.Dictionary) {
        Dictionaries.getDictionaryId(locale) != 0
    } else {
        true
    }

    AlertDialog(
        icon = {
            Icon(painterResource(id = resourceKind.icon()), contentDescription = "Action")
        },
        title = {
            Text(text = "${locale.displayLanguage} - ${resourceKind.kindTitle()}")
        },
        text = {
            if(isCurrentlySet && hasBuiltInFallback) {
                Text(text = "Would you like to revert ${resourceKind.youAreImporting()} to default for ${locale.displayLanguage}, or replace it with another file?")
            } else if(isCurrentlySet) {
                Text(text = "Would you like to remove the ${resourceKind.youAreImporting()} for ${locale.displayLanguage}, or replace it with another file?\n\nNo built-in ${resourceKind.youAreImporting()} exists for this language, so if you remove it, it will stop working until you import a different one!")
            } else {
                Text(text = "No ${resourceKind.youAreImporting()} override is set for ${locale.displayLanguage}. You can explore downloads online, or import an existing file.")
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(onClick = {
                onImport()
            }) {
                Text(
                    if(isCurrentlySet) {
                        "Replace"
                    } else {
                        "Import"
                    }
                )
            }
        },
        dismissButton = {
            if(isCurrentlySet) {
                TextButton(onClick = { onDelete() }) {
                    if(hasBuiltInFallback) {
                        Text("Revert to Default")
                    } else {
                        Text("Remove")
                    }

                }
            } else {
                TextButton(onClick = { onExplore() }) {
                    Text("Explore")
                }
            }
        }
    )
}

data class DeleteInfo(
    val locale: Locale,
    val kind: FileKind
)

@Preview(showBackground = true)
@Composable
fun LanguagesScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val deleteDialogInfo: MutableState<DeleteInfo?> = remember { mutableStateOf(null) }

    val inputMethods = useDataStoreValueBlocking(SubtypesSetting)
    val inputMethodList = remember(inputMethods) {
        Subtypes.layoutsMappedByLanguage(inputMethods)
    }

    val inputMethodKeys = remember(inputMethodList) { inputMethodList.keys.toList().sorted() }

    if(deleteDialogInfo.value != null) {
        val info = deleteDialogInfo.value!!
        ConfirmResourceActionDialog(
            onDismissRequest = { deleteDialogInfo.value = null },
            onExplore = {
                context.openURI(info.kind.getAddonUrlForLocale(info.locale), true)
                deleteDialogInfo.value = null
            },
            onDelete = {
                ResourceHelper.deleteResourceForLanguage(context, info.kind, info.locale)
                deleteDialogInfo.value = null
            },
            onImport = {
                openModelImporter(context)
                deleteDialogInfo.value = null
            },

            resourceKind = info.kind,
            locale = info.locale,
            isCurrentlySet = runBlocking { ResourceHelper.findFileForKind(context, info.locale, info.kind)?.exists() == true }
        )
    }
    LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            ScreenTitle("Languages & Models", showBack = true, navController)
        }

        item {
            NavigationItem(
                title = "Add language",
                style = NavigationItemStyle.Misc,
                navigate = {
                    navController.navigate("addLanguage")
                },
            )
        }

        items(inputMethodKeys) { localeString ->
            val subtypes = inputMethodList[localeString]!!

            val locale = Subtypes.getLocale(localeString)

            val name = Subtypes.getName(subtypes.first())

            val voiceInputModelName = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)?.name?.let { stringResource(it) }
            val dictionaryName = runBlocking { ResourceHelper.findKeyForLocaleAndKind(context, locale, FileKind.Dictionary) }?.let {
                runBlocking { context.getSetting(FileKind.Dictionary.namePreferenceKeyFor(it), "Dictionary") } + " (Imported)"
            } ?: if(BinaryDictionaryGetter.getDictionaryFiles(locale, context, false, false).isNotEmpty()) {
                    "Built-in Dictionary"
            } else {
                    null
            }

            val transformerName = runBlocking { ModelPaths.getModelOptions(context) }.get(locale.language)?.let {
                it.loadDetails()?.name
            }

            val options = LanguageOptions(
                voiceInputModel = voiceInputModelName,
                dictionary = dictionaryName,
                transformerModel = transformerName
            )

            /*
            Spacer(modifier = Modifier.height(12.dp))

            LanguageSurface(
                LanguageItem(
                    languageName = name,
                    options = options,
                    layouts = subtypes.map {
                        Subtypes.getLayoutName(
                            context,
                            it.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: "default"
                        )
                    }
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))
            */

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(0.dp, 16.dp)) {
                    Text(name, style = Typography.titleLarge)
                    if(subtypes.size == 1) {
                        val layout = Subtypes.getLayoutName(context,
                            subtypes.first().getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: ""
                        )

                        Text(layout,
                            style = Typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                if(subtypes.size == 1) {
                    IconButton(modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterVertically), onClick = {
                        Subtypes.removeLanguage(context, subtypes.first())
                    }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remove language",
                            modifier = Modifier
                        )
                    }
                }
            }

            NavigationItem(
                title = options.voiceInputModel ?: "None",
                style = options.voiceInputModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    deleteDialogInfo.value = DeleteInfo(locale, FileKind.VoiceInput)
                },
                icon = painterResource(FileKind.VoiceInput.icon())
            )
            NavigationItem(
                title = options.dictionary ?: "None",
                style = options.dictionary?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    deleteDialogInfo.value = DeleteInfo(locale, FileKind.Dictionary)
                },
                icon = painterResource(FileKind.Dictionary.icon())
            )
            NavigationItem(
                title = options.transformerModel ?: "None",
                style = options.transformerModel?.let { NavigationItemStyle.HomeTertiary } ?: NavigationItemStyle.MiscNoArrow,
                navigate = {
                    navController.navigate("models")
                },
                icon = painterResource(FileKind.Transformer.icon())
            )
            if(subtypes.size > 1) {
                subtypes.forEach {
                    val layout = Subtypes.getLayoutName(
                        context,
                        it.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: "default"
                    )
                    SettingItem(title = "Layout $layout") {
                        IconButton(modifier = Modifier.fillMaxHeight(), onClick = {
                            Subtypes.removeLanguage(context, it)
                        }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Remove layout $layout",
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            ScreenTitle("Other options")
            NavigationItem(
                title = "Import resource file",
                style = NavigationItemStyle.Misc,
                navigate = {
                    openModelImporter(context)
                },
            )
            NavigationItem(
                title = "Explore voice input models",
                style = NavigationItemStyle.Misc,
                navigate = {
                    context.openURI(
                        FileKind.VoiceInput.getAddonUrlForLocale(null),
                        true
                    )
                },
            )
            NavigationItem(
                title = "Explore dictionaries",
                style = NavigationItemStyle.Misc,
                navigate = {
                    context.openURI(
                        FileKind.Dictionary.getAddonUrlForLocale(null),
                        true
                    )
                },
            )
            NavigationItem(
                title = "Explore transformer models",
                style = NavigationItemStyle.Misc,
                navigate = {
                    context.openURI(
                        FileKind.Transformer.getAddonUrlForLocale(null),
                        true
                    )
                },
            )
        }
    }
}