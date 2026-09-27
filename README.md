# VerdictEdge 

> **Air-Gapped, On-Device Legal Risk & Statutory Analyzer for Android**

VerdictEdge is a fully offline, privacy-first legal intelligence mobile platform. Powered by Google MediaPipe, on-device Gemma 2B LLM inference, Google ML Kit OCR, and local Neural Text-to-Speech (Sherpa-ONNX), VerdictEdge scans, parses, and identifies high-risk clauses, financial exposures, statutory voidability, and custom dealbreakers without sending a single byte to external servers.

---


## 📽️ Demo & Visuals

### App Demo

<p align="center">
  <img src="docs/demo.gif" width="320" alt="VerdictEdge Demo Walkthrough" />
</p>

---

### App Screenshots

| Input & Quick Scan | Comprehensive Analysis | Legal Warnings & Checklist |
| :---: | :---: | :---: |
| <img src="docs/screenshot-input.png" width="260" alt="Input Screen"/> | <img src="docs/screenshot-analysis.png" width="260" alt="Analysis Screen"/> | <img src="docs/screenshot-warnings.png" width="260" alt="Warnings Screen"/> |

| Offline Scan History | Export PDF Report | Home Screen Widget |
| :---: | :---: | :---: |
| <img src="docs/screenshot-history.png" width="260" alt="History Screen"/> | <img src="docs/screenshot-pdf.png" width="260" alt="Export PDF Screen"/> | <img src="docs/screenshot-widget.png" width="260" alt="Widget Screen"/> |

---

## ⚡ Core Features

* **100% On-Device & Air-Gapped:** Zero cloud dependencies. All OCR text extraction, contract evaluation, risk scoring, and audio summary synthesis happen locally.
* **Gemma 2B + Rule Engine Analysis:** Combines on-device Gemma 2B via MediaPipe LLM Inference API with precise rule-based parsing to evaluate liabilities, indemnities, unilateral terminations, and IP assignment clauses.
* **Statutory Voidability Detection (Indian Law):** Built-in legal rules flag illegal contract clauses under the **Indian Contract Act, 1872** (e.g., Section 27 Restraint of Trade, Section 28 Restraint of Legal Proceedings).
* **Multi-Format & Multi-Doc OCR:** Scan single physical contract pages using the high-resolution camera or parse multi-page PDFs using Google ML Kit.
* **Custom Dealbreaker Rules:** Input custom user constraints (e.g., *"60 days notice period"*, *"non-compete"*) to instantly evaluate compliance across multi-page agreements.
* **Neural & System Multilingual TTS:** Instant audio summaries rendered in **English**, **Hindi (हिंदी)**, and **Kannada (ಕನ್ನಡ)**. Supports both Sherpa-ONNX Neural TTS models and Android System TTS with currency and symbol normalization.
* **Pre-Signing Checklist & Counter-Offers:** Generates proposed counter-clause drafts to restore balance in lopsided contracts, paired with an interactive pre-signing resolution checklist.
* **PDF Report Generation:** Export full, structured PDF reports directly to local storage for offline archival or sharing.
* **Home Screen Quick Scan Widget:** Android App Widget for immediate one-tap camera scanning or document uploading directly from the home screen.

---

## 🏗️ System Architecture

```
                                  +------------------------------------+
                                  |           VerdictEdge App          |
                                  |   (Quick Scan / Document Upload)   |
                                  +-----------------+------------------+
                                                    |
                                                    v
+---------------------------------------------------------------------------------------------------+
|                                        MainActivity (Compose UI)                                  |
+---------------------------------------------------------------------------------------------------+
          |                                         |                                     |
          v                                         v                                     v
+-------------------+                   +-----------------------+               +-------------------+
|    OcrManager     |                   |    ContractEngine     |               |   VoiceManager    |
| (Google ML Kit)   |                   | (Gemma 2B / MediaPipe)|               |  (Sherpa-ONNX /   |
+---------+---------+                   +-----------+-----------+               |   System TTS)     |
          |                                         |                           +---------+---------+
          v                                         v                                     v
+-------------------+                   +-----------------------+               +-------------------+
| Dynamic Image/PDF |                   |  Risk, Statutory &    |               | Multilingual      |
| Text Extraction   |                   |  Ambiguity Engine     |               | Audio Playback    |
+---------+---------+                   +-----------+-----------+               +---------+---------+
          |                                         |                                     |
          +--------------------+--------------------+-------------------------------------+
                               |
                               v
               +-------------------------------+
               |     Analysis Result Model     |
               |  (Risk, Voidability, PDF)     |
               +---------------+---------------+
                               |
                               v
               +-------------------------------+
               |  Local Encrypted History Store|
               |       (SharedPreferences)     |
               +---------------+---------------+
```

---

## 🛠️ Technology Stack

| Layer | Technology / Library |
| :--- | :--- |
| **Language** | Kotlin 1.9+ |
| **UI Framework** | Jetpack Compose, Material3, Material Icons |
| **On-Device LLM** | Google MediaPipe LLM Inference API (`tasks-genai`), Gemma 2B |
| **Vision / OCR** | Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`) |
| **Neural TTS Engine** | Sherpa-ONNX (`vits-piper`), Android `TextToSpeech` |
| **Document Processing** | Android native `PdfDocument`, FileProvider API |
| **Concurrency & Async** | Kotlin Coroutines, StateFlow / Compose State |
| **System Integration** | Android AppWidget Provider, System Intent Receivers |

---

## ⚖️ Legal Intelligence & Statutory Scanners

VerdictEdge automatically detects and extracts:

1. **Statutory Voidability Warnings:**
   * **Section 27 (Indian Contract Act):** Restraint of trade, occupation, or business.
   * **Section 28 (Indian Contract Act):** Unlawful restraint of legal proceedings or jurisdiction limitations.
2. **Vulnerabilities & Lopsided Provisions:**
   * **Unbalanced Indemnity & Unlimited Liabilities**
   * **Unilateral Termination & One-sided Modifications**
   * **Irrevocable IP Assignment & Perpetuity**
   * **Hidden Auto-Renewals & Excessive Termination Fees**
3. **Financial Exposure Analysis:**
   * Calculates financial caps, penalty amounts, interest rates, and fee obligations.
4. **Ambiguity & Discretion Detector:**
   * Flags subjective phrases like *"at sole discretion"*, *"reasonable efforts"*, *"from time to time"*, and *"as deemed fit"*.

---

## 📁 Repository Structure

```
VerdictEdge/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/example/VerdictEdge/
│           │   ├── MainActivity.kt        # Main Compose Application Shell & PDF Exporter
│           │   ├── ContractEngine.kt      # Core Risk, Statutory Analysis & Gemma Pipeline
│           │   ├── OcrManager.kt          # ML Kit Image/PDF OCR Processor
│           │   ├── VoiceManager.kt        # Sherpa-ONNX & System TTS Engine
│           │   └── QuickScanWidget.kt     # AppWidget Provider for Home Screen Quick Scan
│           └── res/
│               ├── layout/
│               │   └── widget_quick_scan.xml # Home Screen Widget Layout
│               └── xml/
│                   └── quick_scan_widget_info.xml
├── docs/                                  # Assets directory for README media
│   ├── demo.mp4                           # App Walkthrough Video
│   ├── screenshot-input.png               # Input & Scanning Screen
│   ├── screenshot-analysis.png            # Contract Analysis Output
│   ├── screenshot-warnings.png            # Statutory Warning Details
│   └── screenshot-history.png             # Local History Drawer
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

* Android Studio Jellyfish | 2023.3.1 or newer
* Android SDK 26 (Android 8.0) or higher (Target SDK: 34)
* Physical Android device recommended for camera OCR & Sherpa-ONNX speech synthesis

### Installation

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/ssaiskanda0-spec/VerdictEdge](https://github.com/ssaiskanda0-spec/VerdictEdge)
   cd VerdictEdge
   ```

2. **Model Setup (Gemma 2B & Sherpa-ONNX):**
   * Download the `gemma-2b-it-gpu-int4.bin` model file from Google AI Edge / Kaggle.
   * Place the model file in the app assets folder:
     ```
     app/src/main/assets/gemma-2b-it-gpu-int4.bin
     ```
   * (Optional) Place Sherpa-ONNX VITS voice models (`vits-piper-en_US-amy-medium-int8`) in `assets/sherpa_models/`.

3. **Build & Run:**
   * Open the project in Android Studio.
   * Sync Gradle files.
   * Run the `app` configuration on your Android device.

---

## 📲 Home Screen Widget Setup

VerdictEdge includes a quick launcher widget for instant contract scanning:

1. Long-press on your device's home screen.
2. Select **Widgets** and scroll down to **VerdictEdge**.
3. Drag the **Quick Scan** widget to your home screen.
4. Tap **📷 Camera** to launch directly into single-page OCR or **📂 Upload** to parse multi-page PDFs.

---

## ⚠️ Disclaimer

VerdictEdge / VerdictEdge is an automated artificial intelligence tool designed for informational and educational assistance only. It does not constitute formal legal advice. For binding contract evaluations, always consult a qualified legal practitioner.
