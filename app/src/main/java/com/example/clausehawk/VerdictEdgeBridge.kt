package com.example.clausehawk

import android.util.Log

/**
 * JNI Bridge connecting the Android Studio Kotlin app to the high-performance
 * Rust core engine (verdict_edge_rs) and Microsoft Phi-3.5 Mini legal reasoning pipeline.
 */
object VerdictEdgeBridge {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("verdict_edge_rs")
            isNativeLoaded = true
            Log.d("VerdictEdgeBridge", "Successfully loaded native verdict_edge_rs Rust library")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.w("VerdictEdgeBridge", "Native library verdict_edge_rs not found in jniLibs, using Kotlin fallback: ${e.message}")
        }
    }

    /**
     * Check if the native Rust library is loaded
     */
    fun isLoaded(): Boolean = isNativeLoaded

    /**
     * Perform contract risk & statutory voidability analysis in native Rust
     * Returns serialized JSON matching AnalysisResult
     */
    external fun analyzeContract(
        contractText: String,
        dealbreakers: Array<String>,
        langCode: String
    ): String

    /**
     * Normalize legal numbers, currency, and punctuation for speech in native Rust
     */
    external fun normalizeSpeech(
        rawText: String,
        langCode: String
    ): String

    /**
     * Generate balanced counter-clauses using Microsoft Phi-3.5 Mini in native Rust
     */
    external fun generateCounterClause(
        originalClause: String,
        legalIssue: String
    ): String
}
