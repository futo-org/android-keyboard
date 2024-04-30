package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
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
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValueBlocking
import org.futo.inputmethod.latin.uix.theme.Typography
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
fun ParagraphText(it: String, modifier: Modifier = Modifier) {
    Text(it, modifier = modifier.padding(16.dp, 8.dp), style = Typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun PaymentText(verbose: Boolean) {
    val numDaysInstalled = useNumberOfDaysInstalled()

    // Doesn't make sense to say "You've been using for ... days" if it's less than seven days
    if(numDaysInstalled.intValue >= 7) {
        ParagraphText(stringResource(R.string.payment_text_1, numDaysInstalled.value))
    } else {
        ParagraphText(stringResource(R.string.payment_text_1_alt))
    }

    ParagraphText(stringResource(R.string.payment_text_2))

    if(verbose) {
        ParagraphText(stringResource(R.string.payment_text_3))
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
    showOnlyIfReminder: Boolean = false,
    inner: @Composable () -> Unit
) {
    val paymentUrl = useDataStoreValueBlocking(TMP_PAYMENT_URL)
    if(paymentUrl.isBlank()) return

    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStoreValueBlocking(FORCE_SHOW_NOTICE)
    val isAlreadyPaid = useDataStoreValueBlocking(IS_ALREADY_PAID)
    val pushReminderTime = useDataStoreValueBlocking(NOTICE_REMINDER_TIME)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime)

    val displayCondition = if(showOnlyIfReminder) {
        // Either the reminder time is not up, or we're not past the trial period
        (!isAlreadyPaid) && ((!reminderTimeIsUp) || (!forceShowNotice && numDaysInstalled.intValue < TRIAL_PERIOD_DAYS))
    } else {
        // The trial period time is over
        (forceShowNotice || (numDaysInstalled.intValue >= TRIAL_PERIOD_DAYS))
                // and the current time is past the reminder time
                && reminderTimeIsUp
                // and we have not already paid
                && (!isAlreadyPaid)
    }
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier
            .clickable { openMenu() }
            .fillMaxWidth()
            .padding(24.dp, 8.dp), shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp, 0.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.unpaid_title),
                modifier = Modifier.padding(8.dp),
                style = Typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            PaymentText(false)

            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(CenterHorizontally)
            ) {

                Box(modifier = Modifier.weight(1.0f)) {
                    Button(onClick = openMenu, modifier = Modifier.align(Center)) {
                        Text(stringResource(R.string.pay_now))
                    }
                }

                Box(modifier = Modifier.weight(1.0f)) {
                    Button(
                        onClick = openMenu, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ), modifier = Modifier.align(Center)
                    ) {
                        Text(stringResource(R.string.i_already_paid))
                    }
                }
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
        ParagraphText(stringResource(R.string.thank_you_for_purchasing_keyboard))
        if (isPending.value) {
            ParagraphText(stringResource(R.string.payment_pending_body))
        }
        ParagraphText(stringResource(R.string.purchase_will_help_body))

        ParagraphText(stringResource(R.string.payment_processing_note))

        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp)) {
            Button(
                onClick = {
                    hasSeenPaidNotice.setValue(true)
                    onExit()
                },
                modifier = Modifier
                    .align(Center)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.continue_))
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
@Preview(showBackground = true)
fun PaymentScreen(
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = { }
) {
    val isAlreadyPaid = useDataStore(IS_ALREADY_PAID)
    val pushReminderTime = useDataStore(NOTICE_REMINDER_TIME)
    val numDaysInstalled = useNumberOfDaysInstalled()
    val forceShowNotice = useDataStore(FORCE_SHOW_NOTICE)
    val currentTime = System.currentTimeMillis() / 1000L

    val reminderTimeIsUp = (currentTime >= pushReminderTime.value) && ((numDaysInstalled.intValue >= TRIAL_PERIOD_DAYS) || forceShowNotice.value)

    val onAlreadyPaid = {
        isAlreadyPaid.setValue(true)
        navController.navigateUp()
        navController.navigate("paid", NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    val counter = remember { mutableIntStateOf(0) }

    ScrollableList {
        ScreenTitle(stringResource(R.string.payment_title), showBack = true, navController = navController)
        PaymentText(true)

        val context = LocalContext.current
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(8.dp, 0.dp)) {
                Button(
                    onClick = {
                        val url = runBlocking { context.getSetting(TMP_PAYMENT_URL) }
                        if(url.isNotBlank()) {
                            context.openURI(url)
                        } else {
                            val toast = Toast.makeText(context, "Payment is unsupported on this build (still WIP)", Toast.LENGTH_SHORT)
                            toast.show()
                        }
                    }, modifier = Modifier
                        .weight(1.0f)
                        .padding(8.dp)
                ) {
                    Text(stringResource(R.string.pay))
                }


                Button(
                    onClick = {
                        counter.intValue += 1
                        if(counter.intValue == 2) {
                            onAlreadyPaid()
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ), modifier = Modifier
                        .weight(1.0f)
                        .padding(8.dp)
                ) {
                    Text(stringResource(
                        when(counter.intValue) {
                            0 -> R.string.i_already_paid
                            else -> R.string.i_already_paid_2
                        })
                    )
                }
            }

            if (reminderTimeIsUp) {
                val lastValidRemindValue = remember { mutableFloatStateOf(5.0f) }
                val remindDays = remember { mutableStateOf("5") }
                Row(
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .padding(16.dp, 0.dp)
                        .fillMaxWidth()
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pushNoticeReminderTime(context, lastValidRemindValue.floatValue)
                            }
                            onExit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.remind_me_in_x))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            BasicTextField(
                                value = remindDays.value,
                                onValueChange = {
                                    remindDays.value = it

                                    it.toFloatOrNull()?.let { lastValidRemindValue.floatValue = it }
                                },
                                modifier = Modifier
                                    .width(32.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(4.dp),
                                textStyle = Typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                            )
                        }
                        Text(stringResource(R.string.in_x_days))
                    }
                }
            }
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