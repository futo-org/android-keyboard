package org.futo.voiceinput.shared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LifecycleCoroutineScope
import com.konovalov.vad.Vad
import com.konovalov.vad.config.FrameSize
import com.konovalov.vad.config.Mode
import com.konovalov.vad.config.Model
import com.konovalov.vad.config.SampleRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.shared.ml.RunState
import org.futo.voiceinput.shared.ml.WhisperModelWrapper
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class MagnitudeState {
    NOT_TALKED_YET,
    MIC_MAY_BE_BLOCKED,
    TALKING
}

abstract class AudioRecognizer {
    private var isRecording = false
    private var recorder: AudioRecord? = null

    private var model: WhisperModelWrapper? = null

    private val floatSamples: FloatBuffer = FloatBuffer.allocate(16000 * 30)
    private var recorderJob: Job? = null
    private var modelJob: Job? = null
    private var loadModelJob: Job? = null


    protected abstract val context: Context
    protected abstract val lifecycleScope: LifecycleCoroutineScope

    protected abstract fun cancelled()
    protected abstract fun finished(result: String)
    protected abstract fun languageDetected(result: String)
    protected abstract fun partialResult(result: String)
    protected abstract fun decodingStatus(status: RunState)

    protected abstract fun loading()
    protected abstract fun needPermission()
    protected abstract fun permissionRejected()

    protected abstract fun recordingStarted()
    protected abstract fun updateMagnitude(magnitude: Float, state: MagnitudeState)

    protected abstract fun processing()

    protected abstract fun tryRestoreCachedModel(): WhisperModelWrapper?
    protected abstract fun cacheModel(model: WhisperModelWrapper)

    fun finishRecognizerIfRecording() {
        if(isRecording) {
            finishRecognizer()
        }
    }

    protected fun finishRecognizer() {
        println("Finish called")
        onFinishRecording()
    }

    protected fun cancelRecognizer() {
        println("Cancelling recognition")
        reset()

        cancelled()
    }

    fun reset() {
        recorder?.stop()
        recorderJob?.cancel()

        recorder?.release()
        recorder = null

        modelJob?.cancel()
        isRecording = false
    }

    protected fun openPermissionSettings() {
        val packageName = context.packageName
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(myAppSettings)

        cancelRecognizer()
    }

    private val languages = ValueFromSettings(LANGUAGE_TOGGLES, setOf("en"))
    private val useMultilingualModel = ValueFromSettings(ENABLE_MULTILINGUAL, false)
    private val suppressNonSpeech = ValueFromSettings(DISALLOW_SYMBOLS, true)
    private val englishModelIndex = ValueFromSettings(ENGLISH_MODEL_INDEX, ENGLISH_MODEL_INDEX_DEFAULT)
    private val multilingualModelIndex = ValueFromSettings(MULTILINGUAL_MODEL_INDEX, MULTILINGUAL_MODEL_INDEX_DEFAULT)
    private suspend fun tryLoadModelOrCancel(primaryModel: ModelData, secondaryModel: ModelData?) {
        yield()
        model = tryRestoreCachedModel()

        val suppressNonSpeech = suppressNonSpeech.get(context)
        val languages = if(secondaryModel != null) languages.get(context) else null

        val modelNeedsReloading = model == null || model!!.let {
            it.primaryModel != primaryModel
                    || it.fallbackEnglishModel != secondaryModel
                    || it.suppressNonSpeech != suppressNonSpeech
                    || it.languages != languages
        }

        if(!modelNeedsReloading) {
            println("Skipped loading model due to cache")
            return
        }

        try {
            yield()
            model = WhisperModelWrapper(
                context,
                primaryModel,
                secondaryModel,
                suppressNonSpeech,
                languages
            )

            yield()
            cacheModel(model!!)
        } catch (e: IOException) {
            yield()
            context.startModelDownloadActivity(
                listOf(primaryModel).let {
                    if(secondaryModel != null) it + secondaryModel
                    else it
                }
            )

            yield()
            cancelRecognizer()
        }
    }
    private fun loadModel() {
        if(model == null) {
            loadModelJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    if(useMultilingualModel.get(context)) {
                        tryLoadModelOrCancel(
                            MULTILINGUAL_MODELS[multilingualModelIndex.get(context)],
                            ENGLISH_MODELS[englishModelIndex.get(context)]
                        )
                    } else {
                        tryLoadModelOrCancel(
                            ENGLISH_MODELS[englishModelIndex.get(context)],
                            null
                        )
                    }
                }
            }
        }
    }

    fun create() {
        loading()

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needPermission()
        }else{
            startRecording()
        }
    }

    fun permissionResultGranted() {
        startRecording()
    }

    fun permissionResultRejected() {
        permissionRejected()
    }

    private fun startRecording(){
        if(isRecording) {
            throw IllegalStateException("Start recording when already recording")
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                16000 * 2 * 5
            )


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recorder!!.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
            }

            recorder!!.startRecording()

            isRecording = true

            val canMicBeBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(SensorPrivacyManager::class.java) as SensorPrivacyManager).supportsSensorToggle(
                    SensorPrivacyManager.Sensors.MICROPHONE
                )
            } else {
                false
            }

            recorderJob = lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    var hasTalked = false
                    var anyNoiseAtAll = false
                    var isMicBlocked = false

                    Vad.builder()
                        .setModel(Model.WEB_RTC_GMM)
                        .setMode(Mode.VERY_AGGRESSIVE)
                        .setFrameSize(FrameSize.FRAME_SIZE_480)
                        .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                        .setSpeechDurationMs(150)
                        .setSilenceDurationMs(300)
                        .build().use { vad ->
                            val vadSampleBuffer = ShortBuffer.allocate(480)
                            var numConsecutiveNonSpeech = 0
                            var numConsecutiveSpeech = 0

                            val samples = FloatArray(1600)

                            yield()
                            while (isRecording && recorder != null && recorder!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                yield()
                                val nRead =
                                    recorder!!.read(samples, 0, 1600, AudioRecord.READ_BLOCKING)

                                if (nRead <= 0) break
                                if (!isRecording || recorder!!.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                                if (floatSamples.remaining() < 1600) {
                                    withContext(Dispatchers.Main) { finishRecognizer() }
                                    break
                                }

                                // Run VAD
                                var remainingSamples = nRead
                                var offset = 0
                                while (remainingSamples > 0) {
                                    if (!vadSampleBuffer.hasRemaining()) {
                                        val isSpeech = vad.isSpeech(vadSampleBuffer.array())
                                        vadSampleBuffer.clear()
                                        vadSampleBuffer.rewind()

                                        if (!isSpeech) {
                                            numConsecutiveNonSpeech++
                                            numConsecutiveSpeech = 0
                                        } else {
                                            numConsecutiveNonSpeech = 0
                                            numConsecutiveSpeech++
                                        }
                                    }

                                    val samplesToRead =
                                        min(min(remainingSamples, 480), vadSampleBuffer.remaining())
                                    for (i in 0 until samplesToRead) {
                                        vadSampleBuffer.put(
                                            (samples[offset] * 32768.0).toInt().toShort()
                                        )
                                        offset += 1
                                        remainingSamples -= 1
                                    }
                                }

                                floatSamples.put(samples.sliceArray(0 until nRead))

                                // Don't set hasTalked if the start sound may still be playing, otherwise on some
                                // devices the rms just explodes and `hasTalked` is always true
                                val startSoundPassed = (floatSamples.position() > 16000 * 0.6)
                                if (!startSoundPassed) {
                                    numConsecutiveSpeech = 0
                                    numConsecutiveNonSpeech = 0
                                }

                                val rms =
                                    sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()

                                if (startSoundPassed && ((rms > 0.01) || (numConsecutiveSpeech > 8))) hasTalked =
                                    true

                                if (rms > 0.0001) {
                                    anyNoiseAtAll = true
                                    isMicBlocked = false
                                }

                                // Check if mic is blocked
                                if (!anyNoiseAtAll && canMicBeBlocked && (floatSamples.position() > 2 * 16000)) {
                                    isMicBlocked = true
                                }

                                // End if VAD hasn't detected speech in a while
                                if (hasTalked && (numConsecutiveNonSpeech > 66)) {
                                    withContext(Dispatchers.Main) { finishRecognizer() }
                                    break
                                }

                                val magnitude = (1.0f - 0.1f.pow(24.0f * rms))

                                val state = if (hasTalked) {
                                    MagnitudeState.TALKING
                                } else if (isMicBlocked) {
                                    MagnitudeState.MIC_MAY_BE_BLOCKED
                                } else {
                                    MagnitudeState.NOT_TALKED_YET
                                }

                                yield()
                                withContext(Dispatchers.Main) {
                                    updateMagnitude(magnitude, state)
                                }

                                // Skip ahead as much as possible, in case we are behind (taking more than
                                // 100ms to process 100ms)
                                while (true) {
                                    yield()
                                    val nRead2 = recorder!!.read(
                                        samples,
                                        0,
                                        1600,
                                        AudioRecord.READ_NON_BLOCKING
                                    )
                                    if (nRead2 > 0) {
                                        if (floatSamples.remaining() < nRead2) {
                                            withContext(Dispatchers.Main) { finishRecognizer() }
                                            break
                                        }
                                        floatSamples.put(samples.sliceArray(0 until nRead2))
                                    } else {
                                        break
                                    }
                                }
                            }
                        }
                }
            }

            // We can only load model now, because the model loading may fail and need to cancel
            // everything we just did.
            // TODO: We could check if the model exists before doing all this work
            loadModel()

            recordingStarted()
        } catch(e: SecurityException){
            // It's possible we may have lost permission, so let's just ask for permission again
            needPermission()
        }
    }

    private suspend fun runModel(){
        if(loadModelJob != null && loadModelJob!!.isActive) {
            println("Model was not finished loading...")
            loadModelJob!!.join()
        }else if(model == null) {
            println("Model was null by the time runModel was called...")
            loadModel()
            loadModelJob!!.join()
        }

        val model = model!!
        val floatArray = floatSamples.array().sliceArray(0 until floatSamples.position())

        val onStatusUpdate = { state: RunState ->
            decodingStatus(state)
        }

        yield()
        val text = model.run(floatArray, onStatusUpdate) {
            lifecycleScope.launch {
                withContext(Dispatchers.Main) {
                    yield()
                    partialResult(it)
                }
            }
        }

        yield()
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                yield()
                finished(text)
            }
        }
    }

    private fun onFinishRecording() {
        recorderJob?.cancel()

        if(!isRecording) {
            throw IllegalStateException("Should not call onFinishRecording when not recording")
        }

        isRecording = false
        recorder?.stop()

        processing()

        modelJob = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runModel()
            }
        }
    }
}