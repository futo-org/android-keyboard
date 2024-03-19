package org.futo.inputmethod.latin.uix.settings.pages.modelmanager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.xlm.ModelPaths
import java.io.File


@Preview
@Composable
fun ModelDeleteConfirmScreen(path: File = File("/example"), navController: NavHostController = rememberNavController()) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = "Error")
        },
        title = {
            Text(text = "Delete model \"${path.nameWithoutExtension}\"")
        },
        text = {
            Text(text = "Are you sure you want to delete this model? You will not be able to recover it. If this model was finetuned, everything it learned will be lost.")
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    path.delete()
                    runBlocking {
                        ModelPaths.signalReloadModels()
                    }
                    navController.navigateUp()
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.delete_dict))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun PrivateModelExportConfirmation(path: File = File("/example"), navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = "Error")
        },
        title = {
            Text(text = "PRIVACY WARNING - \"${path.nameWithoutExtension}\"")
        },
        text = {
            Text(text = "This model has been tainted with your personal data through finetuning. If you share the exported file, others may be able to reconstruct things you've typed.\n\nExporting is intended for transferring between devices or backup. We do not recommend sharing the exported file with other people.")
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                    triggerModelExport(context, path)
                }
            ) {
                Text("I understand")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
