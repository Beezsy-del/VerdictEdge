package com.example.clausehawk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class VoiceEngineType {
    NEURAL_SHERPA,
    SYSTEM_OG
}

interface VoiceEngine {
    fun speak(text: String)
    fun stop()
    fun release()
    val isReady: Boolean
}

/**
 * Original Android TextToSpeech engine (OG Voice).
 * Preserves 100% of the baseline behavior.
 */
class SystemVoiceEngine(
    context: Context,
    private val onReadyCallback: (() -> Unit)? = null
) : VoiceEngine, TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var currentLocale: Locale = Locale.US
    override var isReady: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLocaleAndVoice(currentLocale)
            isReady = true
            onReadyCallback?.invoke()
            Log.d("VoiceEngine", "SystemVoiceEngine (OG) initialized successfully")
        } else {
            Log.e("VoiceEngine", "SystemVoiceEngine initialization failed with code: $status")
        }
    }

    fun setLanguage(locale: Locale) {
        currentLocale = locale
        if (isReady) {
            applyLocaleAndVoice(locale)
        }
    }

    private fun applyLocaleAndVoice(locale: Locale) {
        try {
            val res = tts.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("VoiceEngine", "Locale $locale not fully supported by TTS; fallback to default")
                return
            }

            val candidateVoices = tts.voices?.filter { voice ->
                voice.locale.language == locale.language && !voice.isNetworkConnectionRequired
            } ?: emptyList()

            // Pick highest quality and most natural voice model available
            val bestVoice = if (locale.language == "hi") {
                // For Hindi, Google TTS voices 'hie' and 'hid' offer warmer, more natural neural prosody
                candidateVoices.firstOrNull { it.name.contains("hie", ignoreCase = true) }
                    ?: candidateVoices.firstOrNull { it.name.contains("hid", ignoreCase = true) }
                    ?: candidateVoices.maxByOrNull { it.quality }
            } else if (locale.language == "kn") {
                candidateVoices.firstOrNull { it.name.contains("knd", ignoreCase = true) }
                    ?: candidateVoices.firstOrNull { it.name.contains("knc", ignoreCase = true) }
                    ?: candidateVoices.maxByOrNull { it.quality }
            } else {
                // For English, match country and prioritize natural neural voices over arbitrary regional voices
                val countryMatches = candidateVoices.filter { it.locale.country.equals(locale.country, ignoreCase = true) }
                val targetPool = if (countryMatches.isNotEmpty()) countryMatches else candidateVoices
                targetPool.firstOrNull { it.name.contains("sfg", ignoreCase = true) }
                    ?: targetPool.firstOrNull { it.name.contains("iol", ignoreCase = true) }
                    ?: targetPool.firstOrNull { it.name.contains("en-us", ignoreCase = true) }
                    ?: targetPool.maxByOrNull { it.quality }
            }

            if (bestVoice != null) {
                tts.voice = bestVoice
                Log.d("VoiceEngine", "Selected system voice: ${bestVoice.name} (quality=${bestVoice.quality})")
            }

            // Conversational expressiveness and cadence tuning
            if (locale.language == "hi") {
                // Grounded pitch and relaxed pacing for articulating complex Hindi legal vocabulary
                tts.setPitch(1.0f)
                tts.setSpeechRate(0.88f)
            } else if (locale.language == "kn") {
                tts.setPitch(1.0f)
                tts.setSpeechRate(0.88f)
            } else {
                // Grounded conversational tone and clear cadence for English
                tts.setPitch(1.0f)
                tts.setSpeechRate(0.95f)
            }
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error setting language on SystemVoiceEngine", e)
        }
    }

    fun speak(text: String, locale: Locale) {
        setLanguage(locale)
        speak(text)
    }

    override fun speak(text: String) {
        if (isReady) {
            val clean = when (currentLocale.language) {
                "hi" -> text
                    .replace("₹", " रुपये ")
                    .replace(Regex("(?i)\\bINR\\b"), " रुपये ")
                    .replace("%", " प्रतिशत ")
                    .replace(" / ", ", ")
                    .replace("•", " ")
                    .replace("—", ", ")
                    .replace("–", ", ")
                    .replace(";", "। ")
                "kn" -> text
                    .replace("₹", " ರೂಪಾಯಿ ")
                    .replace(Regex("(?i)\\bINR\\b"), " ರೂಪಾಯಿ ")
                    .replace("%", " ಪ್ರತಿಶತ ")
                    .replace(" / ", ", ")
                    .replace("•", " ")
                    .replace("—", ", ")
                    .replace("–", ", ")
                    .replace(";", ", ")
                else -> text
                    .replace(Regex("(?i)(?:₹|INR)\\s*([0-9][0-9,]*)"), "$1 Rupees")
                    .replace("₹", " Rupees ")
                    .replace(Regex("(?i)\\bINR\\b"), " Rupees ")
                    .replace("%", " percent ")
                    .replace(" / ", ", ")
                    .replace("•", ", ")
                    .replace("—", ", ")
                    .replace("–", ", ")
                    .replace(";", ", ")
                    .replace("HIGH RISK", "High Risk")
                    .replace("CRITICAL RISK", "Critical Risk")
                    .replace("MODERATE RISK", "Moderate Risk")
                    .replace("LOW RISK", "Low Risk")
            }
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "VerdictEdgeSystemTTS")
        }
    }

    override fun stop() {
        if (isReady) {
            tts.stop()
        }
    }

    override fun release() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error shutting down SystemVoiceEngine", e)
        }
    }
}

/**
 * Next-gen offline neural voice engine powered by Sherpa-ONNX + Piper VITS model.
 * 100% offline, human-like cadence, running locally via ONNX Runtime.
 */
class SherpaOnnxVoiceEngine(
    private val context: Context,
    private val fallbackEngine: VoiceEngine? = null
) : VoiceEngine {

    private var tts: OfflineTts? = null
    private var currentTrack: AudioTrack? = null
    private var activeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override var isReady: Boolean = false
        private set

    init {
        scope.launch {
            initEngine()
        }
    }

    private fun initEngine() {
        try {
            val relativeDataDir = "vits-piper-en_US-amy-medium-int8/espeak-ng-data"
            val targetDir = File(context.filesDir, relativeDataDir)
            if (!targetDir.exists() || targetDir.list().isNullOrEmpty()) {
                Log.d("VoiceEngine", "Copying espeak-ng-data assets to internal storage...")
                copyAssets(context, relativeDataDir)
            }

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = "vits-piper-en_US-amy-medium-int8/en_US-amy-medium.onnx",
                tokens = "vits-piper-en_US-amy-medium-int8/tokens.txt",
                dataDir = targetDir.absolutePath,
                noiseScale = 0.72f,
                noiseScaleW = 0.85f,
                lengthScale = 1.02f
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false
            )
            val config = OfflineTtsConfig(
                model = modelConfig
            )

            tts = OfflineTts(context.assets, config)
            isReady = true
            Log.d("VoiceEngine", "SherpaOnnxVoiceEngine initialized successfully (sampleRate=${tts?.sampleRate()})")
        } catch (e: Throwable) {
            Log.e("VoiceEngine", "SherpaOnnxVoiceEngine failed to load. Falling back to system voice.", e)
            isReady = false
        }
    }

    private fun copyAssets(context: Context, path: String) {
        try {
            val assets = context.assets.list(path) ?: return
            if (assets.isEmpty()) {
                copyFile(context, path)
            } else {
                val dir = File(context.filesDir, path)
                dir.mkdirs()
                for (asset in assets) {
                    val p = if (path.isEmpty()) "" else "$path/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: Exception) {
            Log.e("VoiceEngine", "Failed to copy asset $path", ex)
        }
    }

    private fun copyFile(context: Context, filename: String) {
        val target = File(context.filesDir, filename)
        if (target.exists() && target.length() > 0) return
        target.parentFile?.mkdirs()
        try {
            context.assets.open(filename).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Failed to copy file $filename", e)
        }
    }

    override fun speak(text: String) {
        val engine = tts
        if (!isReady || engine == null) {
            Log.w("VoiceEngine", "SherpaOnnx not ready yet; using fallback engine")
            fallbackEngine?.speak(text)
            return
        }

        activeJob?.cancel()
        stopPlayback()

        activeJob = scope.launch {
            try {
                // Preprocess text for better cadence
                val cleanText = preprocessForSpeech(text)
                val audio: GeneratedAudio = engine.generate(cleanText, sid = 0, speed = 1.0f)
                val samples = audio.samples
                val sampleRate = audio.sampleRate

                if (samples.isNotEmpty()) {
                    playAudio(samples, sampleRate)
                }
            } catch (e: Exception) {
                Log.e("VoiceEngine", "Error generating Sherpa-ONNX speech", e)
                withContext(Dispatchers.Main) {
                    fallbackEngine?.speak(text)
                }
            }
        }
    }

    private fun preprocessForSpeech(text: String): String {
        return text
            // Currency amounts (e.g. ₹50,000 or INR 50000 -> 50,000 Rupees)
            .replace(Regex("(?i)(?:₹|INR)\\s*([0-9][0-9,]*)"), "$1 Rupees")
            .replace("₹", " Rupees ")
            .replace(Regex("(?i)\\bINR\\b"), " Rupees ")
            .replace("%", " percent ")
            // Slashes & bullet symbols
            .replace(" / ", ", ")
            .replace("•", ", ")
            .replace("—", ", ")
            .replace("–", ", ")
            .replace(";", ", ")
            // Abbreviations
            .replace(Regex("(?i)\\bsec\\.\\s*(\\d+)"), "Section $1")
            .replace(Regex("(?i)\\bcl\\.\\s*(\\d+)"), "Clause $1")
            .replace(Regex("(?i)\\bvs\\.?\\b"), "versus")
            .replace(Regex("(?i)\\be\\.g\\.,?"), "for example,")
            .replace(Regex("(?i)\\bi\\.e\\.,?"), "that is,")
            .replace(Regex("(?i)\\bapprox\\.?\\b"), "approximately")
            .replace(Regex("(?i)\\bwarning:\\s*"), "Warning! ")
            .replace(Regex("(?i)\\balert:\\s*"), "Alert! ")
            .replace(Regex("(?i)\\bcaution:\\s*"), "Caution! ")
            // Quotes and excessive ellipses
            .replace(Regex("[“”\"]"), "")
            .replace(Regex("\\.{3,}"), ", ")
            // Capitalized risk levels for natural prosody
            .replace("HIGH RISK", "High Risk")
            .replace("CRITICAL RISK", "Critical Risk")
            .replace("MODERATE RISK", "Moderate Risk")
            .replace("LOW RISK", "Low Risk")
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        stopPlayback()

        // Convert FloatArray [-1.0, 1.0] to 16-bit PCM ShortArray
        val shortSamples = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }

        // Apply 5ms soft ramp-in and ramp-out to eliminate digital audio clicks
        val rampLength = (sampleRate * 0.005f).toInt().coerceAtMost(shortSamples.size / 4)
        for (i in 0 until rampLength) {
            val factor = i.toFloat() / rampLength
            shortSamples[i] = (shortSamples[i] * factor).toInt().toShort()
            val endIdx = shortSamples.size - 1 - i
            shortSamples[endIdx] = (shortSamples[endIdx] * factor).toInt().toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(shortSamples.size * 2)

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        currentTrack = track
        track.play()
        track.write(shortSamples, 0, shortSamples.size)
    }

    private fun stopPlayback() {
        try {
            currentTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error stopping AudioTrack", e)
        } finally {
            currentTrack = null
        }
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        stopPlayback()
    }

    override fun release() {
        stop()
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error releasing OfflineTts", e)
        }
        tts = null
        isReady = false
    }
}

/**
 * Unified Voice Controller for VerdictEdge.
 * Manages dual-engine switching:
 * - NEURAL_SHERPA: Next-gen human offline narrator
 * - SYSTEM_OG: Original Android TextToSpeech
 */
class VoiceManager(context: Context) {

    private val systemEngine = SystemVoiceEngine(context)
    private val sherpaEngine = SherpaOnnxVoiceEngine(context, fallbackEngine = systemEngine)

    var currentEngineType: VoiceEngineType = VoiceEngineType.NEURAL_SHERPA
        private set

    fun setEngineType(type: VoiceEngineType) {
        stop()
        currentEngineType = type
        Log.d("VoiceManager", "Switched voice engine to: $type")
    }

    fun speak(text: String, language: Language = Language.ENGLISH) {
        when (language) {
            Language.ENGLISH -> {
                when (currentEngineType) {
                    VoiceEngineType.NEURAL_SHERPA -> {
                        if (sherpaEngine.isReady) {
                            sherpaEngine.speak(text)
                        } else {
                            systemEngine.speak(text, Locale.US)
                        }
                    }
                    VoiceEngineType.SYSTEM_OG -> {
                        systemEngine.speak(text, Locale.US)
                    }
                }
            }
            Language.HINDI -> {
                stop()
                systemEngine.speak(text, Locale("hi", "IN"))
            }
            Language.KANNADA -> {
                stop()
                systemEngine.speak(text, Locale("kn", "IN"))
            }
        }
    }

    fun stop() {
        sherpaEngine.stop()
        systemEngine.stop()
    }

    fun release() {
        sherpaEngine.release()
        systemEngine.release()
    }
}
