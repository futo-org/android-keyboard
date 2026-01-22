package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icu.astronot233.rime.Rime
import icu.astronot233.rime.RimeApi
import icu.astronot233.rime.RimeSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.IconWithColor

@Composable
internal fun RimeSchemaMenu(rime: Rime, coroScope: CoroutineScope, timestamp: Long) {
    val schemata = RimeApi.getSchemata()
    val timestamp = timestamp
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(RimeApi.getCurrentSchema()) }
    Box(modifier = Modifier
        .fillMaxWidth(0.7f)
        .wrapContentSize(Alignment.TopStart)
        .padding(vertical = 10.dp)
    ) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (current.schemaId.isEmpty()) {
                        EmptySchema()
                        return@Column
                    }
                    Text(
                        text = current.schemaName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = current.schemaId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .heightIn(max = 320.dp),
        ) {
            if (schemata.isEmpty()) {
                DropdownMenuItem(
                    text = { EmptySchema() },
                    onClick = {}
                )
                return@DropdownMenu
            }
            schemata.forEach { schema ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = schema.schemaName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = schema.schemaId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        current = schema
                        Log.d("RimeSchema", "schema: [${current.schemaId}] (${current.schemaName})")
                        coroScope.launch { rime.selectSchema(current.schemaId) }
                    },
                    trailingIcon = {
                        if (schema.schemaId == current.schemaId) {
                            IconWithColor(R.drawable.check_circle, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptySchema() {
    Text(text = "¯\\_(ツ)_/¯", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Text(text = "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
