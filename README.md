# ⚖️ VerdictEdge (Android)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green?style=for-the-badge&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin%201.9%20%2B%20Jetpack%20Compose-purple?style=for-the-badge&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/On--Device%20LLM-SmolLM2--360M%20%2F%20Gemma%202B-orange?style=for-the-badge" alt="On-Device LLM" />
  <img src="https://img.shields.io/badge/Jurisdiction-Indian%20Contract%20Act%201872-darkgreen?style=for-the-badge" alt="ICA 1872" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Air--Gapped%20Offline-success?style=for-the-badge" alt="Air Gapped" />
  <img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge" alt="Build" />
</p>

> **Private, 100% on-device legal contract risk analyzer for Android. Powered by on-device LLM inference (SmolLM2-360M / Gemma 2B), Google ML Kit OCR, and local Neural TTS (Sherpa-ONNX). Scans, parses, and identifies high-risk clauses, financial exposures, statutory voidability, and cross-clause conflicts without sending a single byte to external servers.**

---

### 📌 GitHub Repository Details (About Box & Metadata)
- **Tagline / About:** `Air-gapped, on-device legal risk and statutory analyzer for Android. Powered by on-device LLM inference & Indian Contract Act 1872 statutory rule engine.`
- **Topics / Tags:** `android`, `jetpack-compose`, `legal-ai`, `on-device-llm`, `smollm2`, `gemma-2b`, `ocr`, `contract-analysis`, `air-gapped`, `privacy-first`

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

## ⚡ Core Features & Technical Blueprint

* **100% On-Device & Air-Gapped:** Zero cloud dependencies. All OCR text extraction, contract evaluation, risk scoring, and audio summary synthesis happen locally.
* **Dual-Core Legal Architecture:**
  * **On-Device Neural LLM:** Compact on-device intelligence (`SmolLM2-360M` / `Gemma 2B`) for executive risk synthesis, reciprocal fairness analysis, and strategic leverage.
  * **Statutory Rule Engine:** Instant deterministic scanning under the **Indian Contract Act, 1872** (Section 27 Restraint of Trade, Section 28 Restraint of Legal Proceedings).
* **Evidence-First Canonical Findings:** Every identified issue links directly to verbatim text extracted from the document with exact character offsets.
* **Structured Financial & Dates Ledger:** Extracts currencies (₹, $, €, £), payment terms (Net 30/60), cure periods, and auto-renewal terms into an organized financial overview.
* **Cross-Clause Relationship Graph & Cap Puncturing:** Detects when an uncapped indemnity or IP clause punctures an explicit limitation of liability cap elsewhere in the agreement.
* **Family-Aware Missing-Clause Engine:** Classifies the contract family (NDA, Employment, MSA/Vendor, Consulting) and flags critical missing protections.
* **Multi-Format & Multi-Doc OCR:** Scan physical contracts using the camera or parse multi-page PDFs using Google ML Kit.
* **Neural & System Multilingual TTS:** Instant audio summaries rendered in **English**, **Hindi (हिंदी)**, and **Kannada (ಕನ್ನಡ)** using Sherpa-ONNX or Android System TTS.
* **Pre-Signing Checklist & Counter-Offers:** Interactive pre-signing checklist with auto-generated balanced counter-proposals.
* **Home Screen Quick Scan Widget:** Android App Widget for immediate one-tap camera scanning or document uploading.
* **Offline PDF Report Export:** Generates clean, formatted PDF reports saved directly to device storage.

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
|            (InputScreen, AnalysisScreen, WarningsScreen, LedgerView, HistoryDrawer)               |
+---------------------------------------------------------------------------------------------------+
          |                                         |                                     |
          v                                         v                                     v
+-------------------+                   +-----------------------+               +-------------------+
|    OcrManager     |                   |    ContractEngine     |               |   VoiceManager    |
| (Google ML Kit)   |                   | (On-Device LLM +      |               |  (Sherpa-ONNX /   |
+---------+---------+                   |  Statutory & Rules)   |               |   System TTS)     |
          |                             +-----------+-----------+               +---------+---------+
          v                                         |                                     |
+-------------------+                   +-----------+-----------+                         v
| Dynamic Image/PDF |                   | Financial Ledger      |               +-------------------+
| Text Extraction   |                   | Cross-Clause Graph    |               | Multilingual      |
+---------+---------+                   | Missing Clause Engine |               | Audio Playback    |
          |                             | Counter-Offer Drafter |               +---------+---------+
          |                             +-----------+-----------+                         |
          |                                         |                                     |
          +--------------------+--------------------+-------------------------------------+
                               |
                               v
                +-------------------------------+
                |     Analysis Result Model     |
                | (Findings, Ledger, Graph, PDF)|
                +---------------+---------------+
                                |
                                v
                +-------------------------------+
                |  Local Encrypted History Store|
                |       (SharedPreferences)     |
                +-------------------------------+
```

---

## 🛠️ Technology Stack

| Layer | Technology / Library |
| :--- | :--- |
| **Language** | Kotlin 1.9+ |
| **UI Framework** | Jetpack Compose, Material3, Material Icons |
| **On-Device LLM** | SmolLM2-360M / Gemma 2B via MediaPipe LLM Inference API |
| **Vision / OCR** | Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`) |
| **Neural TTS Engine** | Sherpa-ONNX (`vits-piper`), Android `TextToSpeech` |
| **Document Processing** | Android native `PdfDocument`, FileProvider API |
| **Concurrency & Async** | Kotlin Coroutines, StateFlow / Compose State |
| **System Integration** | Android AppWidget Provider, System Intent Receivers |

---

## ⚖️ Legal Intelligence & Statutory Scanners

VerdictEdge automatically detects and extracts:

1. **Statutory Voidability Warnings:**
   * **Section 27 (Indian Contract Act):** Restraint of trade, occupation, or post-employment non-competes (*Niranjan Shankar Golikari*, *Percept D'Mark*).
   * **Section 28 (Indian Contract Act):** Unlawful restraint of legal proceedings or jurisdiction limitations.
2. **Vulnerabilities & Lopsided Provisions:**
   * **Unbalanced Indemnity & Unlimited Liabilities**
   * **Unilateral Termination & One-sided Modifications**
   * **Irrevocable IP Assignment & Perpetuity**
   * **Hidden Auto-Renewals & Excessive Termination Fees**
3. **Structured Financial Ledger:**
   * Financial caps, penalty amounts, interest rates, payment milestones, and cure periods.
4. **Cross-Clause Conflicts & Punctured Caps:**
   * Detects carve-outs where indemnities negate limitation of liability protections.
5. **Ambiguity & Discretion Detector:**
   * Flags subjective phrases like *"at sole discretion"*, *"reasonable efforts"*, *"from time to time"*, and *"as deemed fit"*.

---

## 📁 Repository Structure

```
VerdictEdge/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/example/clausehawk/
│           │   ├── MainActivity.kt        # Main Compose Application Shell & PDF Exporter
│           │   ├── ContractEngine.kt      # Core Risk, Statutory Analysis & Blueprint Logic
│           │   ├── ContractModels.kt      # Canonical Findings, Ledger, and Graph Models
│           │   ├── OcrManager.kt          # ML Kit Image/PDF OCR Processor
│           │   ├── VoiceManager.kt        # Sherpa-ONNX & System TTS Engine
│           │   └── QuickScanWidget.kt     # AppWidget Provider for Home Screen Quick Scan
│           └── res/
│               ├── layout/
│               │   └── widget_quick_scan.xml # Home Screen Widget Layout
│               └── xml/
│                   └── quick_scan_widget_info.xml
├── docs/                                  # Assets directory for README media
│   ├── demo.gif                           # App Walkthrough GIF
│   ├── screenshot-input.png               # Input & Scanning Screen
│   ├── screenshot-analysis.png            # Contract Analysis Output
│   ├── screenshot-warnings.png            # Statutory Warning Details
│   ├── screenshot-history.png             # Local History Drawer
│   ├── screenshot-pdf.png                 # Export PDF Screen
│   └── screenshot-widget.png              # Home Screen Widget
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

* Android Studio Jellyfish | 2023.3.1 or newer
* Android SDK 26 (Android 8.0) or higher (Target SDK: 34)
* Physical Android device or Android Emulator (API 34 / Pixel 9)

### Installation

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Beezsy-del/VerdictEdge.git
   cd VerdictEdge
   ```

2. **Build with Gradle:**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install to Connected Device / Emulator:**
   ```bash
   ./gradlew installDebug
   ```

---

## 📲 Home Screen Widget Setup

VerdictEdge includes a quick launcher widget for instant contract scanning:

1. Long-press on your device's home screen.
2. Select **Widgets** and scroll down to **VerdictEdge**.
3. Drag the **Quick Scan** widget to your home screen.
4. Tap **📷 Camera** to launch directly into single-page OCR or **📂 Upload** to parse multi-page PDFs.

---

## ⚠️ Disclaimer

VerdictEdge is an automated artificial intelligence tool designed for informational and educational assistance only. It does not constitute formal legal advice. For binding contract evaluations, always consult a qualified legal practitioner.

---

## 📄 License
VerdictEdge is released under the **MIT License**.
