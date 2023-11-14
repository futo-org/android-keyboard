package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.xlm.HistoryLogForTraining
import org.futo.inputmethod.latin.xlm.TrainingState
import org.futo.inputmethod.latin.xlm.TrainingWorker
import org.futo.inputmethod.latin.xlm.TrainingWorkerStatus
import org.futo.inputmethod.latin.xlm.loadHistoryLogBackup
import java.util.concurrent.TimeUnit


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun TrainDevScreen(navController: NavHostController = rememberNavController()) {
    var trainingDataAmount by remember { mutableStateOf(0) }
    val trainingState = TrainingWorkerStatus.state.collectAsState(initial = TrainingState.None)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val data = mutableListOf<HistoryLogForTraining>()
        loadHistoryLogBackup(context, data)

        trainingDataAmount = data.size
    }

    ScrollableList {
        ScreenTitle("Training", showBack = true, navController)

        Text("There are $trainingDataAmount pending training examples.")

        Button(onClick = {
            val workRequest = OneTimeWorkRequestBuilder<TrainingWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS) // Run immediately
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }, enabled = !TrainingWorkerStatus.isTraining.value) {
            if(TrainingWorkerStatus.isTraining.value) {
                Text("Currently training, check status in logcat")
            } else {
                Text("Train model")
            }
        }

        when(trainingState.value) {
            TrainingState.Finished -> Text("Last train finished successfully!")
            TrainingState.ErrorInadequateData -> Text("Last training run failed due to lack of data")
            else -> { }
        }
    }
}