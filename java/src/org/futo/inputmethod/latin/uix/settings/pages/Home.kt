package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.TextEditPopupActivity
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStoreValueBlocking
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.updates.ConditionalMigrateUpdateNotice
import org.futo.inputmethod.updates.ConditionalUpdate

@Preview(showBackground = true)
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isDeveloper = useDataStoreValueBlocking(IS_DEVELOPER)
    val isPaid = useDataStoreValueBlocking(IS_ALREADY_PAID)
    

    Column {
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            ScreenTitle("FUTO Keyboard Settings")

            ConditionalMigrateUpdateNotice()
            ConditionalUpdate(navController)
            ConditionalUnpaidNoticeWithNav(navController)

            NavigationItem(
                title = "Languages & Models",
                style = NavigationItemStyle.HomePrimary,
                navigate = { navController.navigate("languages") },
                icon = painterResource(id = R.drawable.globe)
            )

            NavigationItem(
                title = "Predictive Text",
                style = NavigationItemStyle.HomeSecondary,
                navigate = { navController.navigate("predictiveText") },
                icon = painterResource(id = R.drawable.shift)
            )

            NavigationItem(
                title = "Typing Preferences",
                style = NavigationItemStyle.HomeSecondary,
                navigate = { navController.navigate("typing") },
                icon = painterResource(id = R.drawable.delete)
            )

            NavigationItem(
                title = "Voice Input",
                style = NavigationItemStyle.HomeSecondary,
                subtitle = if(useDataStoreValueBlocking(USE_SYSTEM_VOICE_INPUT)) { "Built-in voice input is disabled!" } else { null },
                navigate = { navController.navigate("voiceInput") },
                icon = painterResource(id = R.drawable.mic_fill)
            )

            NavigationItem(
                title = "Theme",
                style = NavigationItemStyle.HomeTertiary,
                navigate = { navController.navigate("themes") },
                icon = painterResource(id = R.drawable.eye)
            )

            if(!isPaid) {
                NavigationItem(
                    title = stringResource(R.string.payment),
                    style = NavigationItemStyle.HomePrimary,
                    navigate = { navController.navigate("payment") },
                    icon = painterResource(R.drawable.dollar_sign)
                )
            }

            NavigationItem(
                title = "Help & Feedback",
                style = NavigationItemStyle.HomePrimary,
                navigate = { navController.navigate("help") },
                icon = painterResource(id = R.drawable.help_circle)
            )

            if(isDeveloper || LocalInspectionMode.current) {
                NavigationItem(
                    title = "Developer Settings",
                    style = NavigationItemStyle.HomeSecondary,
                    navigate = { navController.navigate("developer") },
                    icon = painterResource(id = R.drawable.code)
                )
            }

            NavigationItem(
                title = "Credits",
                style = NavigationItemStyle.MiscNoArrow,
                navigate = { navController.navigate("credits") },
            )


            Spacer(modifier = Modifier.height(16.dp))

            if(isPaid || LocalInspectionMode.current) {
                Text(
                    stringResource(R.string.thank_you_for_using_paid),
                    style = Typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = Typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        TextButton(onClick = {
            val intent = Intent()
            intent.setClass(context, TextEditPopupActivity::class.java)
            intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.try_typing), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth())
        }
    }
}