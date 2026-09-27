package com.example.clausehawk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
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
    fun speak(text: String, locale: Locale)
    fun stop()
    fun release()
    val isReady: Boolean
}

/**
 * System TextToSpeech engine supporting multi-lingual voice selection, pitch/rate tuning,
 * and currency/legal text normalization for English, Hindi, and Kannada.
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
            Log.d("VoiceEngine", "SystemVoiceEngine initialized successfully")
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
                Log.w("VoiceEngine", "Locale $locale not supported by TTS; using fallback")
                return
            }

            val candidateVoices = tts.voices?.filter { voice ->
                voice.locale.language == locale.language && !voice.isNetworkConnectionRequired
            } ?: emptyList()

            val bestVoice = if (locale.language == "hi") {
                candidateVoices.firstOrNull { it.name.contains("hie", ignoreCase = true) }
                    ?: candidateVoices.firstOrNull { it.name.contains("hid", ignoreCase = true) }
                    ?: candidateVoices.maxByOrNull { it.quality }
            } else if (locale.language == "kn") {
                candidateVoices.firstOrNull { it.name.contains("knd", ignoreCase = true) }
                    ?: candidateVoices.firstOrNull { it.name.contains("knc", ignoreCase = true) }
                    ?: candidateVoices.maxByOrNull { it.quality }
            } else {
                val countryMatches = candidateVoices.filter { it.locale.country.equals(locale.country, ignoreCase = true) }
                val targetPool = if (countryMatches.isNotEmpty()) countryMatches else candidateVoices
                targetPool.firstOrNull { it.name.contains("sfg", ignoreCase = true) }
                    ?: targetPool.firstOrNull { it.name.contains("iol", ignoreCase = true) }
                    ?: targetPool.firstOrNull { it.name.contains("en-us", ignoreCase = true) }
                    ?: targetPool.maxByOrNull { it.quality }
            }

            if (bestVoice != null) {
                tts.voice = bestVoice
            }

            if (locale.language == "hi" || locale.language == "kn") {
                tts.setPitch(1.0f)
                tts.setSpeechRate(0.88f)
            } else {
                tts.setPitch(1.0f)
                tts.setSpeechRate(0.95f)
            }
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error setting language on SystemVoiceEngine", e)
        }
    }

    override fun speak(text: String, locale: Locale) {
        setLanguage(locale)
        speak(text)
    }

    fun speak(text: String) {
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
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ClauseHawkSystemTTS")
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
 * Reflective Sherpa-ONNX Voice Engine.
 * Dynamically binds to Sherpa-ONNX classes if present, otherwise gracefully defaults
 * to SystemVoiceEngine without compilation errors.
 */
class SherpaOnnxVoiceEngine(
    private val context: Context,
    private val fallbackEngine: SystemVoiceEngine
) : VoiceEngine {

    private var sherpaTtsInstance: Any? = null
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
                copyAssets(context, relativeDataDir)
            }

            val vitsConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig")
            val modelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig")
            val configClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            val ttsClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")

            val vitsConfig = vitsConfigClass.getConstructor(
                String::class.java, String::class.java, String::class.java,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
            ).newInstance(
                "vits-piper-en_US-amy-medium-int8/en_US-amy-medium.onnx",
                "vits-piper-en_US-amy-medium-int8/tokens.txt",
                targetDir.absolutePath,
                0.72f, 0.85f, 1.02f
            )

            val modelConfig = modelConfigClass.getConstructor(
                vitsConfigClass, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
            ).newInstance(vitsConfig, 2, false)

            val config = configClass.getConstructor(modelConfigClass).newInstance(modelConfig)

            sherpaTtsInstance = ttsClass.getConstructor(
                android.content.res.AssetManager::class.java, configClass
            ).newInstance(context.assets, config)

            isReady = true
            Log.d("VoiceEngine", "SherpaOnnxVoiceEngine loaded successfully via reflection")
        } catch (e: Throwable) {
            Log.w("VoiceEngine", "SherpaOnnx library missing or failed to initialize; defaulting to SystemVoiceEngine.")
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
        try {
            val target = File(context.filesDir, filename)
            if (target.exists() && target.length() > 0) return
            target.parentFile?.mkdirs()
            context.assets.open(filename).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Error copying asset file $filename", e)
        }
    }

    override fun speak(text: String, locale: Locale) {
        if (!isReady || sherpaTtsInstance == null || locale.language != "en") {
            fallbackEngine.speak(text, locale)
            return
        }
        stop()
        activeJob = scope.launch {
            try {
                val generateMethod = sherpaTtsInstance!!::class.java.getMethod(
                    "generate", String::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType
                )
                val audioResult = generateMethod.invoke(sherpaTtsInstance, text, 0, 1.0f)
                if (audioResult != null) {
                    val samplesField = audioResult::class.java.getField("samples")
                    val sampleRateField = audioResult::class.java.getField("sampleRate")
                    val samples = samplesField.get(audioResult) as FloatArray
                    val sampleRate = sampleRateField.getInt(audioResult)

                    if (samples.isNotEmpty()) {
                        playPcm(samples, sampleRate)
                    } else {
                        withContext(Dispatchers.Main) {
                            fallbackEngine.speak(text, locale)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        fallbackEngine.speak(text, locale)
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceEngine", "Sherpa ONNX speak error", e)
                withContext(Dispatchers.Main) {
                    fallbackEngine.speak(text, locale)
                }
            }
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val shortSamples = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, shortSamples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = track
        track.write(shortSamples, 0, shortSamples.size)
        track.play()
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        try {
            currentTrack?.stop()
            currentTrack?.release()
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error stopping AudioTrack", e)
        }
        currentTrack = null
    }

    override fun release() {
        stop()
        try {
            if (sherpaTtsInstance != null) {
                sherpaTtsInstance!!::class.java.getMethod("release").invoke(sherpaTtsInstance)
            }
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Error releasing Sherpa TTS", e)
        }
        sherpaTtsInstance = null
    }
}

/**
 * Main Controller class consumed directly by MainActivity.
 */
class VoiceManager(context: Context) {
    private val systemEngine = SystemVoiceEngine(context)
    private val sherpaEngine = SherpaOnnxVoiceEngine(context, systemEngine)
    private var selectedEngineType: VoiceEngineType = VoiceEngineType.NEURAL_SHERPA

    fun setEngineType(type: VoiceEngineType) {
        this.selectedEngineType = type
    }

    fun speak(text: String, language: Language) {
        val locale = when (language) {
            Language.ENGLISH -> Locale.US
            Language.HINDI -> Locale("hi", "IN")
            Language.KANNADA -> Locale("kn", "IN")
        }

        stop()

        if (selectedEngineType == VoiceEngineType.NEURAL_SHERPA && sherpaEngine.isReady && language == Language.ENGLISH) {
            sherpaEngine.speak(text, locale)
        } else {
            systemEngine.speak(text, locale)
        }
    }

    fun speak(text: String) {
        speak(text, Language.ENGLISH)
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