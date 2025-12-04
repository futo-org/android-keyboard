package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SpacedColumn
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.updates.dismissedMigrateUpdateNotice
import org.futo.inputmethod.updates.openURI
import kotlin.math.absoluteValue


val IS_ALREADY_PAID = SettingsKey(booleanPreferencesKey("already_paid"), false)
val IS_PAYMENT_PENDING = SettingsKey(booleanPreferencesKey("payment_pending"), false)
val HAS_SEEN_PAID_NOTICE = SettingsKey(booleanPreferencesKey("seen_paid_notice"), false)
val FORCE_SHOW_NOTICE = SettingsKey(booleanPreferencesKey("force_show_notice"), false)
val NOTICE_REMINDER_TIME = SettingsKey(longPreferencesKey("notice_reminder_time"), 0L)
val EXT_LICENSE_KEY = SettingsKey(stringPreferencesKey("license_key"), "")

fun <T> Context.startAppActivity(activity: Class<T>, clearTop: Boolean = false) {
    val intent = Intent(this, activity)

    if(this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if(clearTop) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    startActivity(intent)
}

@Composable
fun useNumberOfDaysInstalled(): MutableIntState {
    if (LocalInspectionMode.current) {
        return remember { mutableIntStateOf(55) }
    }

    val dayCount = remember { mutableIntStateOf(-1) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

        val firstInstallTime = packageInfo.firstInstallTime

        val currentTime = System.currentTimeMillis()

        val diff = (currentTime - firstInstallTime) / (1000 * 60 * 60 * 24)
        dayCount.intValue = diff.toInt()
    }

    return dayCount
}

@Composable
fun ParagraphText(it: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha=0.9f)) {
    Text(it, modifier = modifier, style = Typography.SmallMl, color = color)
}

@Composable
fun IconText(icon: Painter, title: String, body: String) {
    Row {
        Icon(icon, contentDescription = null, modifier = Modifier
            .align(Alignment.Top)
            .padding(8.dp, 10.dp)
            .size(with(LocalDensity.current) { Typography.Heading.Medium.fontSize.toDp() }))
        Column(modifier = Modifier.padding(6.dp)) {
            Text(title, style = Typography.Body.Regular, color = LocalContentColor.current)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = Typography.SmallMl, color = LocalContentColor.current.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun PaymentBody(verbose: Boolean) {
    val numDaysInstalled = useNumberOfDaysInstalled()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Doesn't make sense to say "You've been using for ... days" if it's less than seven days
        if (numDaysInstalled.intValue >= 7) {
            ParagraphText(
                stringResource(R.string.payment_screen_sales_paragraph_1, numDaysInstalled.value)
            )
        } else {
            ParagraphText(
                stringResource(R.string.payment_screen_sales_paragraph_1_alt)
            )
        }

        if (!verbose) {
            ParagraphText(
                stringResource(R.string.payment_screen_sales_paragraph_2)
            )
        }
    }

}

@Composable
fun PaymentBulletPointList() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SpacedColumn(18.dp) {
            IconText(
                icon = painterResource(id = R.drawable.activity),
                title = stringResource(R.string.payment_screen_sales_point_development_title),
                body = stringResource(R.string.payment_screen_sales_point_development_body)
            )

            IconText(
                icon = painterResource(id = R.drawable.unlock),
                title = stringResource(R.string.payment_screen_sales_point_privacy_title),
                body = stringResource(R.string.payment_screen_sales_point_privacy_body)
            )
        }
    }
}

suspend fun pushNoticeReminderTime(context: Context, days: Float) {
    // If the user types in a crazy high number, the long can't store such a large value and it won't suppress the reminder
    // 21x the age of the universe ought to be enough for a payment notice reminder
    // Also take the absolute value in the case of a negative number
    val clampedDays = if (days.absoluteValue >= 1.06751991E14f) {
        1.06751991E14f
    } else {
        days.absoluteValue
    }

    context.setSetting(NOTICE_REMINDER_TIME,
        System.currentTimeMillis() / 1000L + (clampedDays * 60.0 * 60.0 * 24.0).toLong())
}

const val TRIAL_PERIOD_DAYS = 30

@Composable
fun UnpaidNoticeCondition(
    force: Boolean = LocalInspectionMode.current,
    inner: @Composable () -> Unit
) {
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStoreValue(FORCE_SHOW_NOTICE)
    val isAlreadyPaid = useDataStoreValue(IS_ALREADY_PAID)
    val pushReminderTime = useDataStoreValue(NOTICE_REMINDER_TIME)
    val currentTime = System.currentTimeMillis() / 1000L

    val isDisplayingMigrationNotice = !useDataStoreValue(dismissedMigrateUpdateNotice)

    val reminderTimeIsUp = (currentTime >= pushReminderTime)

    val displayCondition =
        // The trial period time is over
        (forceShowNotice || (numDaysInstalled.intValue >= TRIAL_PERIOD_DAYS))
                // and the current time is past the reminder time
                && reminderTimeIsUp
                // and we have not already paid
                && (!isAlreadyPaid)
                // and not overridden by migration notice
                && !isDisplayingMigrationNotice

    if (force || displayCondition) {
        inner()
    }
}

@Composable
@Preview
fun UnpaidNotice(openMenu: () -> Unit = { }) {
    PaymentSurface(isPrimary = true, onClick = openMenu) {
        PaymentSurfaceHeading(stringResource(R.string.payment_unpaid_version_title))

        PaymentBody(false)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
        ) {
            TextButton(
                onClick = openMenu,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.payment_screen_more_options), color = MaterialTheme.colorScheme.primary)
            }

            Button(onClick = openMenu, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.payment_screen_pay_via_futopay, BuildConfig.PAYMENT_PRICE))
            }
        }
    }
}


@Composable
@Preview
fun ConditionalUnpaidNoticeWithNav(navController: NavController = rememberNavController()) {
    UnpaidNoticeCondition {
        UnpaidNotice(openMenu = {
            navController.navigate("payment")
        })
    }
}

@Composable
@Preview
fun PaymentThankYouScreen(onExit: () -> Unit = { }) {
    val hasSeenPaidNotice = useDataStore(HAS_SEEN_PAID_NOTICE)
    val isPending = useDataStore(IS_PAYMENT_PENDING)

    ScrollableList {

        ScreenTitle(
            if (isPending.value) {
                stringResource(R.string.payment_screen_aftersales_title_pending)
            } else {
                stringResource(R.string.payment_screen_aftersales_title)
            },
            showBack = false
        )

        PaymentSurface(isPrimary = true) {
            SpacedColumn(24.dp) {
                PaymentSurfaceHeading(stringResource(R.string.payment_screen_aftersales_paragraph_1))

                if (isPending.value) {
                    ParagraphText(stringResource(R.string.payment_screen_aftersales_paragraph_2_pending))
                }
                ParagraphText(stringResource(R.string.payment_screen_aftersales_paragraph_2))

                ParagraphText(stringResource(R.string.payment_screen_aftersales_paragraph_3))

                Button(
                    onClick = {
                        hasSeenPaidNotice.setValue(true)
                        onExit()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.payment_screen_continue_button))
                }
            }
        }
    }
}

@Composable
@Preview
fun PaymentFailedScreen(onExit: () -> Unit = { }) {
    val hasSeenPaidNotice = useDataStore(HAS_SEEN_PAID_NOTICE.key, default = true)

    val context = LocalContext.current

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_screen_payment_failed_title), showBack = false)

        @Suppress("KotlinConstantConditions")
        (ParagraphText(
        when (BuildConfig.FLAVOR) {
            "fDroid" -> stringResource(R.string.payment_screen_payment_failed_body_unconfident)
            "dev" -> stringResource(R.string.payment_screen_payment_failed_body_unconfident)
            "standalone" -> stringResource(R.string.payment_screen_payment_failed_body_unconfident)
            else -> stringResource(R.string.payment_screen_payment_failed_body)
        }
    ))
        NavigationItem(title = "Email keyboard@futo.org", style = NavigationItemStyle.Mail, navigate = {
            context.openURI("mailto:keyboard@futo.org")
        })
        Box(modifier = Modifier.fillMaxWidth()) {
            val coroutineScope = rememberCoroutineScope()
            Button(
                onClick = {
                    // It would be rude to immediately annoy the user again about paying, so delay the notice forever
                    coroutineScope.launch {
                        pushNoticeReminderTime(context, Float.MAX_VALUE)
                    }

                    hasSeenPaidNotice.setValue(false)
                    onExit()
                },
                modifier = Modifier.align(Center)
            ) {
                Text(stringResource(R.string.payment_screen_continue_button))
            }
        }
    }
}

@Composable
fun PaymentSurfaceHeading(title: String) {
    Text(
        title,
        style = Typography.Body.MediumMl,
        color = LocalContentColor.current
    )
}

@Composable
fun PaymentSurface(isPrimary: Boolean, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp), contentAlignment = Center) {
        Surface(
            color = containerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .let {
                    if (onClick != null) {
                        it.clickable { onClick() }
                    } else {
                        it
                    }
                }
                .widthIn(Dp.Unspecified, 400.dp)

        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    Modifier.padding(top = 19.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun TextColumn(content: @Composable ColumnScope.() -> Unit) =
    SpacedColumn(25.dp, content = content)

@Composable
private fun TitleAndBodyColumn(content: @Composable ColumnScope.() -> Unit) =
    SpacedColumn(12.dp, content = content)

@Composable
private fun ActionsColumn(content: @Composable ColumnScope.() -> Unit) =
    SpacedColumn(32.dp, content = content)


@Composable
@Preview(showBackground = true, heightDp = 2048)
fun PaymentScreen(
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = { }
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    if(isAlreadyPaid.value) {
        PaymentThankYouScreen(onExit)
        return
    }
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME)
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value) && ((numDaysInstalled.intValue >= TRIAL_PERIOD_DAYS) || forceShowNotice.value)

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_screen_short_title), showBack = true, navController = navController)

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Center) {
            Icon(painterResource(id = R.drawable.keyboard_icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(8.dp))

        val context = LocalContext.current

        SpacedColumn(32.dp) {
            PaymentSurface(isPrimary = true) {
                TextColumn {
                    TitleAndBodyColumn {
                        PaymentSurfaceHeading(title = stringResource(R.string.payment_screen_title))
                        PaymentBody(true)
                    }
                    PaymentBulletPointList()
                }


                if(BuildConfig.IS_PLAYSTORE_BUILD) {
                    Button(
                        onClick = { context.openURI(BuildConfig.GOOGLEPAY_URL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {

                        Text(
                            stringResource(
                                R.string.payment_screen_pay_via_google,
                                BuildConfig.GOOGLEPAY_PRICE
                            ), style = Typography.Body.Medium
                        )
                    }

                    Button(
                        onClick = { context.openURI(BuildConfig.FUTOPAY_URL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {

                        Text(
                            stringResource(
                                R.string.payment_screen_pay_via_futopay2,
                                BuildConfig.FUTOPAY_PRICE
                            ), style = Typography.Body.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = { context.openURI(BuildConfig.FUTOPAY_URL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {

                        Text(
                            stringResource(
                                R.string.payment_screen_pay_via_futopay,
                                BuildConfig.FUTOPAY_PRICE
                            ), style = Typography.Body.Medium
                        )
                    }
                }
            }

            PaymentSurface(isPrimary = false) {
                TitleAndBodyColumn {
                    PaymentSurfaceHeading(title = stringResource(R.string.payment_screen_already_paid_title))

                    ParagraphText(it = stringResource(R.string.payment_screen_already_paid_body))
                }

                OutlinedButton(
                    onClick = {
                        navController.navigate("alreadyPaid")
                    }, colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.outlineVariant
                    ), modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.payment_screen_already_paid_button
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        style = Typography.Body.Medium
                    )
                }
            }

            if (reminderTimeIsUp) {
                PaymentSurface(isPrimary = false) {
                    TitleAndBodyColumn {
                        PaymentSurfaceHeading(title = stringResource(R.string.payment_screen_set_reminder_title))

                        ParagraphText(stringResource(R.string.payment_screen_set_reminder_body))

                        ParagraphText(stringResource(R.string.payment_screen_set_reminder_setter))
                    }

                    ActionsColumn {
                        val context = LocalContext.current
                        val remindOptions = remember(context) {
                            listOf(
                                7 to context.getString(R.string.payment_screen_set_reminder_setter_7_days),
                                30 to context.getString(R.string.payment_screen_set_reminder_setter_1_month),
                                182 to context.getString(R.string.payment_screen_set_reminder_setter_6_months),
                                36500 to context.getString(R.string.payment_screen_set_reminder_setter_next_century),
                            )
                        }
                        val selection = remember(remindOptions) { mutableStateOf(remindOptions.first()) }

                        DropDownPicker(
                            remindOptions,
                            selection.value,
                            onSet = { selection.value = it },
                            getDisplayName = { it.second }
                        )

                        val coroutineScope = rememberCoroutineScope()
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pushNoticeReminderTime(
                                            context,
                                            selection.value.first.toFloat()
                                        )
                                    }
                                    onExit()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.payment_screen_set_reminder_setter_confirm),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = Typography.Body.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(51.dp))

        ScreenTitle(stringResource(R.string.payment_screen_other_options))
        NavigationItem(title = stringResource(R.string.payment_screen_need_help_title), subtitle = stringResource(R.string.payment_screen_need_help_subtitle), style = NavigationItemStyle.ExternalLink, navigate = {
            context.openURI("https://keyboard.futo.org/")
        })
    }
}

@Composable
@Preview(heightDp = 2048)
private fun PaymentScreenDark() {
    val context = LocalContext.current
    UixThemeWrapper(colorScheme = DynamicDarkTheme.obtainColors(context)) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PaymentScreen()
        }
    }
}


@Composable
fun PaymentScreenSwitch(
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = { },
    startDestination: String = "payment"
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val hasSeenNotice = useDataStore(HAS_SEEN_PAID_NOTICE)
    val paymentDest = if (!isAlreadyPaid.value && hasSeenNotice.value) {
        "error"
    } else if (isAlreadyPaid.value && !hasSeenNotice.value) {
        "paid"
    } else {
        "payment"
    }

    LaunchedEffect(paymentDest) {
        if (paymentDest != "payment") {
            navController.navigate(
                paymentDest,
                NavOptions.Builder().setLaunchSingleTop(true).build()
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("payment") {
            PaymentScreen(navController, onExit)
        }

        composable("paid") {
            PaymentThankYouScreen(onExit)
        }

        composable("error") {
            PaymentFailedScreen(onExit)
        }
    }
}

@Composable
fun AlreadyPaidDialog(navController: NavHostController) {
    val context = LocalContext.current
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        icon = {
        },
        title = {
            Text(stringResource(R.string.payment_screen_already_paid_confirmation), style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onPrimaryContainer)
        },
        text = {
            Text(
                stringResource(R.string.payment_screen_already_paid_confirmation_body),
                style = Typography.SmallMl,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            OutlinedButton(onClick = {
                context.setSettingBlocking(IS_ALREADY_PAID.key, true)
                navController.navigateUp()
                navController.navigateUp()
                navController.navigate("paid", NavOptions.Builder().setLaunchSingleTop(true).build())
            }) {
                Text(stringResource(R.string.payment_screen_already_paid_confirmation_confirm), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                navController.navigateUp()
            }) {
                Text(stringResource(R.string.payment_screen_already_paid_confirmation_cancel), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        }
    )
}