package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import icu.astronot233.rime.Rime
import icu.astronot233.rime.RimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.engine.general.ChineseIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow

@Composable
private fun RimeDashboardScreen(
    rime: Rime,
    coroScope: CoroutineScope
) {
    val schemata = remember { RimeApi.getSchemata() }

    Row(modifier = Modifier.fillMaxSize()) {
        RimeSchemaMenu(schemata) { schema -> coroScope.launch { rime.selectSchema(schema) } }
        RimeStatusMenu()
    }
}

val RimeDashboard = Action(
    icon = R.drawable.cpu, // TODO: draw RIME icon
    name = R.string.action_rime_dashboard,
    simplePressImpl = null,
    persistentState = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ -> object : ActionWindow() {
        @Composable override fun windowName(): String = stringResource(R.string.action_rime_dashboard)

        @Composable override fun WindowContents(keyboardShown: Boolean) {
            val ime = manager.getCurrentIME()
            if (ime !is ChineseIME)
                return
            val (rime, coroScope) = ime.requestTakeOver(this)
            if (rime == null || coroScope == null)
                return
            RimeDashboardScreen(rime, coroScope)
        }
    } }
)
