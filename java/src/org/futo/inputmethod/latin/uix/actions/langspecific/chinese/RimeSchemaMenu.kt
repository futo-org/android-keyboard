package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

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
import icu.astronot233.rime.RimeApi
import icu.astronot233.rime.RimeSchema

@Composable
fun RimeSchemaMenu(
    schemata: List<RimeSchema>,
    onSchemaSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(RimeApi.getCurrentSchema()) }
    Box(modifier = Modifier
        .wrapContentSize(Alignment.TopStart)
        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                .fillMaxWidth(0.9f)
                .heightIn(max = 320.dp),
        ) {
            if (schemata.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(text = "¯\\_(ツ)_/¯", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false }
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
                            )
                            Text(
                                text = schema.schemaId,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    onClick = {
                        onSchemaSelected(schema.schemaId)
                        current = schema
                        expanded = false
                    },
                    trailingIcon = {
//                            if (schema.schemaId == current.schemaId)
//                                Icon
                    }
                )
            }
        }
    }
}
