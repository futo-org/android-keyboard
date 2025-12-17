package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SpacedColumn
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.credits.ThirdPartyItem
import org.futo.inputmethod.latin.uix.settings.pages.credits.ThirdPartyList
import org.futo.inputmethod.latin.uix.settings.pages.credits.codeContribs
import org.futo.inputmethod.latin.uix.settings.pages.credits.languageContribs
import org.futo.inputmethod.latin.uix.settings.pages.credits.layoutContribs
import org.futo.inputmethod.latin.uix.settings.pages.credits.text
import org.futo.inputmethod.latin.uix.settings.render
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.updates.openURI

@Composable
@Preview(showBackground = true)
fun ProjectInfoView(
    projectIndex: Int = 1,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val info = ThirdPartyList[projectIndex]
    ScrollableList {
        ScreenTitle(
            stringResource(R.string.credits_menu_project_information_title, info.name),
            showBack = true, navController
        )

        Text(
            info.description,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = Typography.Body.Regular,
            textAlign = TextAlign.Center
        )

        NavigationItem(
            title = stringResource(R.string.credits_menu_project_url_link),
            subtitle = info.projectUrl,
            style = NavigationItemStyle.ExternalLink,
            navigate = {
                context.openURI(info.projectUrl)
            }
        )

        Spacer(Modifier.height(16.dp))

        Text(
            info.copyright,
            modifier = Modifier.padding(8.dp),
            style = Typography.Small
        )
        Text(
            info.license.text(context),
            modifier = Modifier.padding(8.dp),
            style = Typography.Small
        )
    }
}

@Composable
fun <T> VerticalGrid(
    modifier: Modifier = Modifier,
    items: List<T>,
    columns: Int,
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable (item: T) -> Unit
) {
    val rows = items.chunked(columns)
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        rows.forEachIndexed { rowindex, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = horizontalArrangement) {
                rowItems.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        content(item)
                    }
                    if (index == rowItems.lastIndex && rowindex == rows.lastIndex) {
                        // Add a placeholder empty view
                        for (i in 0 until (columns - rowItems.size)) {
                            Spacer(
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreditCategorySection(
    icon: Int, title: String, names: List<String>, color: Color, columns: Int = 2,
    thirdPartyInformation: List<ThirdPartyItem>? = null, navController: NavHostController? = null
) {
    val context = LocalContext.current
    val compositingColor = MaterialTheme.colorScheme.background
    val bgGradient1 = compositingColor.copy(alpha = 0.6f).compositeOver(color)
    val bgGradient2 = compositingColor.copy(alpha = 0.77f).compositeOver(color)
    val foregroundColor = MaterialTheme.colorScheme.onBackground

    Surface(
        color = color,
        modifier = Modifier.widthIn(200.dp, 360.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(55.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.height(44.dp)) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(
                            Alignment.TopCenter
                        )
                    )
                    Text(
                        title,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        style = Typography.Body.Medium
                    )
                }
            }
            VerticalGrid(
                items = names.indices.toList(),
                columns = columns,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(bgGradient1, bgGradient2))
                    )
                    .padding(16.dp)
            ) {
                val name = names[it]
                val thirdPartyInfo = thirdPartyInformation?.get(it)
                if (thirdPartyInfo != null) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                navController!!.navigate("credits/thirdparty/" + it)
                            }
                    ) {
                        Text(
                            thirdPartyInfo.description,
                            color = foregroundColor, style = Typography.Body.Regular
                        )
                        Text(
                            thirdPartyInfo.copyright,
                            color = foregroundColor.copy(alpha = 0.7f),
                            style = Typography.Small
                        )
                    }
                } else {
                    Text(name, color = foregroundColor, style = Typography.Body.Regular)
                }
            }
        }
    }
}

val CreditsScreenLite = UserSettingsMenu(
    title = R.string.credits_menu_title,
    navPath = "credits", registerNavPath = false,
    settings = listOf(
        userSettingNavigationItem(
            title = (R.string.credits_menu_contribute_translations_button),
            style = NavigationItemStyle.Misc,
            navigate = {
                it.context.openURI("https://i18n-keyboard.futo.org/")
            }),
        userSettingNavigationItem(
            title = (R.string.credits_menu_contribute_keyboard_layouts_button),
            style = NavigationItemStyle.Misc,
            navigate = {
                it.context.openURI("https://github.com/futo-org/futo-keyboard-layouts")
            }),
        userSettingNavigationItem(
            title = (R.string.credits_menu_contribute_code_button),
            style = NavigationItemStyle.Misc,
            navigate = {
                it.context.openURI("https://github.com/futo-org/android-keyboard/")
            })

    )
)

@Preview(showBackground = true, heightDp = 1600)
@Composable
fun CreditsScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle(stringResource(R.string.credits_menu_title), showBack = true, navController)

        SpacedColumn(
            24.dp,
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.credits_menu_header_text),
                style = Typography.Body.RegularMl
            )

            CreditCategorySection(
                icon = R.drawable.file_text,
                title = stringResource(R.string.credits_menu_team_translators_title),
                names = languageContribs,
                color = Color(0xFF3157C6)
            )

            CreditCategorySection(
                icon = R.drawable.globe,
                title = stringResource(R.string.credits_menu_team_keyboard_layouts_title),
                names = layoutContribs,
                color = Color(0xff31c663)
            )

            CreditCategorySection(
                icon = R.drawable.code,
                title = stringResource(R.string.credits_menu_team_code_title),
                names = codeContribs,
                color = Color(0xffc69931)
            )

            CreditCategorySection(
                icon = R.drawable.cpu,
                title = stringResource(R.string.credits_menu_team_third_party_libraries_title2),
                columns = 1,
                names = ThirdPartyList.map { it.description },
                thirdPartyInformation = ThirdPartyList,
                navController = navController,
                color = Color(0xffb231c6)
            )

            ParagraphText(stringResource(R.string.credits_menu_nonaffiliation_notice))
        }

        CreditsScreenLite.render(showTitle = false)
        BottomSpacer()
    }
}