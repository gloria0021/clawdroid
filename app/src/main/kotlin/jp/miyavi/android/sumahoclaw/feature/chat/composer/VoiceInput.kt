package jp.miyavi.android.sumahoclaw.feature.chat.composer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Android 組み込みの音声認識（FR-029、research R13）。結果は入力欄に入れるだけで送らない。
 * 端末に認識サービスが無ければ [available] は false（マイクを出さない）。
 */
@Stable
class VoiceInputState internal constructor(
    val available: Boolean,
    private val startImpl: () -> Unit,
    private val stopImpl: () -> Unit,
    private val cancelImpl: () -> Unit,
) {
    var listening by mutableStateOf(false)
        internal set

    /** 聞き取り中の途中の結果（確定前。確定したら入力欄に入る）。 */
    var partial by mutableStateOf("")
        internal set

    /** 声の大きさの履歴（聞き取り中の波形に使う）。 */
    val levels = VoiceLevels()

    /** 結果が入力欄に入ったあとに続けて行うこと（「終えて送信」）。 */
    internal var afterResult: (() -> Unit)? = null

    fun toggle() = if (listening) stopImpl() else startImpl()

    /** 聞き取りを終え、結果を入力欄に入れる。 */
    fun finish() = stopImpl()

    /** 聞き取りを終え、結果を入力欄に入れてから [action]（送信）。 */
    fun finishAndThen(action: () -> Unit) {
        afterResult = action
        stopImpl()
    }

    /** 聞き取りをやめる（結果は捨てる）。 */
    fun cancel() {
        afterResult = null
        cancelImpl()
    }
}

/**
 * いまの声の大きさ（0〜1）。認識サービスから不規則な間隔で届く値をそのまま置くだけで、
 * 波形はこれを毎フレーム読んでなめらかにする（状態にしないので、届いても再構成は起きない）。
 */
class VoiceLevels {
    @Volatile var target: Float = 0f
        private set

    internal fun push(level: Float) {
        target = level.coerceIn(0f, 1f)
    }

    internal fun clear() {
        target = 0f
    }
}

@Composable
fun rememberVoiceInput(
    onResult: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    onError: () -> Unit,
): VoiceInputState {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    lateinit var state: VoiceInputState

    fun startListening() {
        val r = recognizer ?: return
        state.listening = true
        state.partial = ""
        state.levels.clear()
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                state.listening = false
                state.partial = ""
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf(String::isNotBlank)?.let(currentOnResult)
                state.afterResult?.invoke()
                state.afterResult = null
            }
            override fun onError(error: Int) {
                state.listening = false
                state.partial = ""
                state.afterResult = null
                if (error != SpeechRecognizer.ERROR_CLIENT) currentOnError()
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                state.levels.push((rmsdB - RMS_FLOOR_DB) / (RMS_CEILING_DB - RMS_FLOOR_DB))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { state.partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                // 聞き取りながら途中の結果を見せる
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            },
        )
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else onPermissionDenied()
    }

    state = remember {
        VoiceInputState(
            available = available,
            startImpl = {
                if (hasMicPermission(context)) startListening() else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            stopImpl = { recognizer?.stopListening() },
            cancelImpl = {
                recognizer?.cancel()
                state.listening = false
                state.partial = ""
            },
        )
    }

    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }
    return state
}

/** 認識サービスが返す声の大きさ（dB）のおおよその幅。これを 0〜1 に当てる。 */
private const val RMS_FLOOR_DB = -2f
private const val RMS_CEILING_DB = 10f

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
