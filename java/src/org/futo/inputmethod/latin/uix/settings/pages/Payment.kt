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
import org.futo.inputmethod.latin.payment.PaymentActivity
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
fun ParagraphText(it: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
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
            Text(title, style = Typography.Body.Regular, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = Typography.SmallMl, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
                stringResource(R.string.payment_text_1, numDaysInstalled.value),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            ParagraphText(
                stringResource(R.string.payment_text_1_alt),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        if (!verbose) {
            ParagraphText(
                stringResource(R.string.payment_text_2),
                color = MaterialTheme.colorScheme.onSecondaryContainer
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
                title = "Sustainable Development",
                body = "FUTO's mission is for open-source software and non-malicious software business practices to become a sustainable income source for projects and their developers. For this reason, we are in favor of users actually paying for software."
            )

            IconText(
                icon = painterResource(id = R.drawable.unlock),
                title = "Commitment to Privacy",
                body = "This app will never serve you ads or sell your data. We are not in the business of doing that."
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
    val paymentUrl = useDataStoreValue(TMP_PAYMENT_URL)
    if(paymentUrl.isBlank()) return

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
                // and you can pay to begin with
                && BuildConfig.PAYMENT_URL.isNotBlank()

    if (force || displayCondition) {
        inner()
    }
}

@Composable
@Preview
fun ConditionalUnpaidNoticeInVoiceInputWindow(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current

    UnpaidNoticeCondition {
        TextButton(onClick = {
            context.startAppActivity(PaymentActivity::class.java)
            if (onClose != null) onClose()
        }) {
            Text(stringResource(R.string.unpaid_indicator), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
@Preview
fun UnpaidNotice(openMenu: () -> Unit = { }) {
    PaymentSurface(isPrimary = true, onClick = openMenu) {
        PaymentSurfaceHeading(stringResource(R.string.unpaid_title))

        PaymentBody(false)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {  }
        ) {
            TextButton(
                onClick = openMenu,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.more_options), color = MaterialTheme.colorScheme.primary)
            }

            Button(onClick = openMenu, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pay, BuildConfig.PAYMENT_PRICE))
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
                stringResource(R.string.payment_pending)
            } else {
                stringResource(R.string.thank_you)
            },
            showBack = false
        )

        PaymentSurface(isPrimary = true) {
            SpacedColumn(24.dp) {
                PaymentSurfaceHeading(stringResource(R.string.thank_you_for_purchasing_keyboard))

                if (isPending.value) {
                    ParagraphText(stringResource(R.string.payment_pending_body))
                }
                ParagraphText(stringResource(R.string.purchase_will_help_body))

                ParagraphText(stringResource(R.string.payment_processing_note))

                Button(
                    onClick = {
                        hasSeenPaidNotice.setValue(true)
                        onExit()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.continue_))
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
        ScreenTitle(stringResource(R.string.payment_error), showBack = false)

        @Suppress("KotlinConstantConditions")
        (ParagraphText(
        when (BuildConfig.FLAVOR) {
            "fDroid" -> stringResource(R.string.payment_failed_body_ex)
            "dev" -> stringResource(R.string.payment_failed_body_ex)
            "standalone" -> stringResource(R.string.payment_failed_body_ex)
            else -> stringResource(R.string.payment_failed_body)
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
                Text(stringResource(R.string.continue_))
            }
        }
    }
}

@Composable
fun PaymentSurfaceHeading(title: String) {
    Text(
        title,
        style = Typography.Body.MediumMl,
        color = MaterialTheme.colorScheme.onPrimaryContainer
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

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Center) {
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

    val counter = remember { mutableIntStateOf(0) }

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_title), showBack = true, navController = navController)

        Spacer(Modifier.height(51.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Center) {
            Icon(painterResource(id = R.drawable.keyboard_icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
        }

        val context = LocalContext.current

        SpacedColumn(32.dp) {
            PaymentSurface(isPrimary = true) {
                TextColumn {
                    TitleAndBodyColumn {
                        PaymentSurfaceHeading(title = "Pay for FUTO Keyboard")
                        PaymentBody(true)
                    }
                    PaymentBulletPointList()
                }

                Button(
                    onClick = {
                        val url = runBlocking { context.getSetting(TMP_PAYMENT_URL) }
                        if (url.isNotBlank()) {
                            context.openURI(url)
                        } else {
                            val toast = Toast.makeText(
                                context,
                                "Payment is unsupported on this build",
                                Toast.LENGTH_SHORT
                            )
                            toast.show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    if (BuildConfig.IS_PLAYSTORE_BUILD) {
                        Text(stringResource(R.string.pay_via_google, BuildConfig.PAYMENT_PRICE), style = Typography.Body.Medium)
                    } else {
                        Text(stringResource(R.string.pay, BuildConfig.PAYMENT_PRICE), style = Typography.Body.Medium)
                    }
                }
            }

            PaymentSurface(isPrimary = false) {
                TitleAndBodyColumn {
                    PaymentSurfaceHeading(title = "Already paid?")

                    ParagraphText(it = "If you already paid for FUTO Keyboard or FUTO Voice Input, tap below.")
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
                            when (counter.intValue) {
                                0 -> R.string.i_already_paid
                                else -> R.string.i_already_paid_2
                            }
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        style = Typography.Body.Medium
                    )
                }
            }

            if (reminderTimeIsUp) {
                PaymentSurface(isPrimary = false) {
                    TitleAndBodyColumn {
                        PaymentSurfaceHeading(title = "Not ready to pay yet?")

                        ParagraphText("This will hide the reminder in the settings screen for the period of days entered.")

                        ParagraphText("When would you like to be reminded?")
                    }

                    ActionsColumn {
                        val remindOptions = remember {
                            listOf(
                                7 to "in 7 days",
                                30 to "in 1 month",
                                182 to "in 6 months",
                                36500 to "next century",
                            )
                        }
                        val selection = remember { mutableStateOf(remindOptions.first()) }

                        DropDownPicker(
                            "",
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
                                    stringResource(R.string.confirm_reminder),
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

        ScreenTitle("Other Options")
        NavigationItem(title = "Help", subtitle = "Need help? Visit our website", style = NavigationItemStyle.ExternalLink, navigate = {
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
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        icon = {
        },
        title = {
            Text("Confirm your choice", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onPrimaryContainer)
        },
        text = {
            Text("Are you sure you want to confirm you already paid? Once confirmed, you won't be asked about paying again.",
                style = Typography.SmallMl,
                color = MaterialTheme.colorScheme.onSecondaryContainer
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
                Text("I already paid", color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                navController.navigateUp()
            }) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        }
    )
}