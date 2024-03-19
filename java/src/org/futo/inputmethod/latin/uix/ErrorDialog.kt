package org.futo.inputmethod.latin.uix

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R

@Composable
fun ErrorDialog(title: String, body: String, navController: NavHostController = rememberNavController()) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = "Error")
        },
        title = {
            Text(text = title)
        },
        text = {
            Text(text = body)
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = { },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

@Composable
fun InfoDialog(title: String, body: String, navController: NavHostController = rememberNavController()) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Info, contentDescription = "Info")
        },
        title = {
            Text(text = title)
        },
        text = {
            Text(text = body)
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = { },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}