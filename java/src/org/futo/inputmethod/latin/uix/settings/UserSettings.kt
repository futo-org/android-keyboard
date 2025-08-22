package org.futo.inputmethod.latin.uix.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SettingsKey

data class UserSetting(
    @StringRes val name: Int,
    @StringRes val subtitle: Int? = null,
    @StringRes val searchTags: Int? = null,
    val searchTagList: List<Int>? = searchTags?.let { listOf(it) } ?: null,
    val visibilityCheck: (@Composable () -> Boolean)? = null,
    val appearInSearchIfVisibilityCheckFailed: Boolean = true,
    val appearsInSearch: Boolean = true,
    val component: @Composable () -> Unit,
)

data class UserSettingsMenu(
    @StringRes val title: Int,
    @StringRes val searchTags: Int? = null,
    val visibilityCheck: (@Composable () -> Boolean)? = null,
    val navPath: String,
    val registerNavPath: Boolean,
    val settings: List<UserSetting>
)


fun userSettingNavigationItem(
    @StringRes title: Int,
    style: NavigationItemStyle,
    navigateTo: String? = null,
    navigate: ((NavHostController) -> Unit)? = null,
    @DrawableRes icon: Int? = null,
    @StringRes subtitle: Int? = null
): UserSetting = UserSetting(
    name = title,
    subtitle = subtitle,
    component = {
        val navController = LocalNavController.current
        NavigationItem(
            title = stringResource(title),
            style = style,
            icon = icon?.let { painterResource(it) },
            subtitle = subtitle?.let { stringResource(it) },
            navigate = {
                if(navigateTo != null) navController!!.navigate(navigateTo)
                navigate?.invoke(navController!!)
            }
        )
    }
)

fun userSettingToggleSharedPrefs(
    @StringRes title: Int,
    key: String,
    default: @Composable () -> Boolean,
    @StringRes subtitle: Int? = null,
    @StringRes disabledSubtitle: Int? = null,
    disabled: @Composable () -> Boolean = {false},
    icon: (@Composable () -> Unit)? = null,
    submenu: String? = null,
): UserSetting = UserSetting(
    name = title,
    subtitle = subtitle,
    component = {
        val def = default.invoke()
        val navController = LocalNavController.current
        SettingToggleSharedPrefs(
            title = stringResource(title),
            key = key,
            default = def,
            subtitle = subtitle?.let { stringResource(it) },
            disabledSubtitle = disabledSubtitle?.let { stringResource(it) },
            disabled = disabled(),
            icon = icon,
            onSubmenuNavigate = submenu?.let {{
                navController!!.navigate(it)
            }},
        )
    }
)

fun userSettingToggleDataStore(
    @StringRes title: Int,
    setting: SettingsKey<Boolean>,
    @StringRes subtitle: Int? = null,
    @StringRes disabledSubtitle: Int? = null,
    disabled: @Composable () -> Boolean = {false},
    icon: (@Composable () -> Unit)? = null
): UserSetting = UserSetting(
    name = title,
    subtitle = subtitle,
    component = {
        SettingToggleDataStore(
            title = stringResource(title),
            setting = setting,
            subtitle = subtitle?.let { stringResource(it) },
            disabledSubtitle = disabledSubtitle?.let { stringResource(it) },
            disabled = disabled(),
            icon = icon
        )
    }
)

fun userSettingDecorationOnly(
    decoration: @Composable () -> Unit
): UserSetting = UserSetting(
    name = 0,
    component = decoration,
    appearsInSearch = false
)

@Composable
fun UserSettingsMenu.render(showBack: Boolean = true, showTitle: Boolean = true) {
    val navController = LocalNavController.current

    if(showTitle) {
        ScreenTitle(stringResource(title), showBack = showBack, navController)
    }
    settings.forEach {
        if(it.visibilityCheck?.invoke() != false) {
            it.component()
        }
    }
}

@Composable
fun UserSettingsMenuScreen(menu: UserSettingsMenu) {
    ScrollableList {
        menu.render()
        BottomSpacer()
    }
}