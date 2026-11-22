package jp.miyavi.android.clawdroid.feature.chat.composer

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
) {
    var listening by mutableStateOf(false)
        internal set

    fun toggle() = if (listening) stopImpl() else startImpl()
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
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                state.listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf(String::isNotBlank)?.let(currentOnResult)
            }
            override fun onError(error: Int) {
                state.listening = false
                if (error != SpeechRecognizer.ERROR_CLIENT) currentOnError()
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
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
        )
    }

    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }
    return state
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
