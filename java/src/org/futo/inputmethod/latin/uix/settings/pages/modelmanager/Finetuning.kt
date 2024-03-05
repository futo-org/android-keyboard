package org.futo.inputmethod.latin.uix.settings.pages.modelmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.latin.xlm.TrainingState
import org.futo.inputmethod.latin.xlm.TrainingStateWithModel
import org.futo.inputmethod.latin.xlm.TrainingWorkerStatus
import org.futo.inputmethod.latin.xlm.scheduleTrainingWorkerImmediately
import java.io.File
import kotlin.math.roundToInt

@Composable
fun FinetuningStateDisplay(navController: NavHostController, trainingState: TrainingStateWithModel, progress: Float, loss: Float) {
    val context = LocalContext.current
    val modelPath = if(LocalInspectionMode.current) { "" } else {
        remember {
            File(
                ModelPaths.getModelDirectory(context = context),
                trainingState.model!!
            ).absolutePath
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text("TRAINING IN PROGRESS", style = Typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())


    Column(modifier = Modifier.padding(16.dp, 16.dp)) {
        Text("Progress: ${(progress * 100.0f).roundToInt()}%")
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Loss: $loss")
    }

    ModelNavigationItem(
        navController = navController,
        name = trainingState.model!!,
        path = modelPath,
        isPrimary = true
    )
}

@Preview(showBackground = true)
@Composable
fun FinetuningStatePreview() {
    ScrollableList {
        ScreenTitle("Finetuning", showBack = true)
        FinetuningStateDisplay(
            navController = rememberNavController(),
            trainingState = TrainingStateWithModel(
                TrainingState.Training,
                "example model"
            ), progress = 0.43f, loss = 9.81f
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun FinetuneModelScreen(file: File? = null, navController: NavHostController = rememberNavController()) {
    val model = remember { file?.let { ModelInfoLoader(name = it.nameWithoutExtension, path = it).loadDetails() } }

    val context = LocalContext.current
    val models = if(!LocalInspectionMode.current) {
        remember { runBlocking { ModelPaths.getModelOptions(context) }.values.mapNotNull { it.loadDetails() } }
    } else {
        PreviewModels
    }

    val trainingState = TrainingWorkerStatus.state.collectAsState(initial = TrainingStateWithModel(
        TrainingState.None, null)
    )
    val currentModel = remember { mutableStateOf(model) }

    val progress = TrainingWorkerStatus.progress.collectAsState(initial = 0.0f)
    val loss = TrainingWorkerStatus.loss.collectAsState(initial = Float.MAX_VALUE)

    val customData = remember { mutableStateOf("") }

    ScrollableList {
        ScreenTitle("Finetuning", showBack = true, navController)

        if(trainingState.value.state == TrainingState.Training && TrainingWorkerStatus.isTraining.value) {
            Text("Currently busy finetuning ${trainingState.value.model}")
            Text("Progress ${(progress.value * 100.0f).roundToInt()}%")
            Text("Loss ${loss.value}")
        } else {
            if(trainingState.value.state != TrainingState.None && trainingState.value.model == currentModel.value?.toLoader()?.path?.nameWithoutExtension) {
                when(trainingState.value.state) {
                    TrainingState.None -> {} // unreachable
                    TrainingState.Training -> {} // unreachable
                    TrainingState.ErrorInadequateData -> {
                        Text("Last training run failed due to lack of data")
                    }
                    TrainingState.Finished -> {
                        Text("Last training run succeeded with final loss ${loss.value}")
                    }
                    TrainingState.FatalError -> {
                        Text("Fatal error")
                    }
                }
            }

            ModelPicker("Model", models, currentModel.value) { currentModel.value = it }

            //TextField(value = customData.value, onValueChange = { customData.value = it }, placeholder = {
            //    Text("Custom training data. Leave blank for none", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
            //})

            Button(onClick = {
                println("PATH ${currentModel.value?.toLoader()?.path?.absolutePath}, ${currentModel.value?.toLoader()?.path?.exists()}")
                scheduleTrainingWorkerImmediately(
                    context,
                    model = currentModel.value?.toLoader(),
                    trainingData = if(customData.value.isEmpty()) { null } else { customData.value }
                )
            }) {
                Text("Start Training")
            }
        }
    }
}
