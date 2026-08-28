# Manga Translator Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2026%2B-green.svg?style=flat&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-Mobile-orange.svg?style=flat&logo=onnx)](https://onnxruntime.ai)
[![License](https://img.shields.io/badge/License-GPL%20v3-red.svg?style=flat)](LICENSE)

Native Android application based on [zyddnys/manga-image-translator](https://github.com/zyddnys/manga-image-translator), providing on-device AI manga translation, interactive text bubble correction, manual typesetting adjustment, and chapter batch processing powered by ONNX Runtime Mobile, OpenCV, and Jetpack Compose (Material Design 3).

Optimized specifically for translating raw **Japanese Manga (`JA / JPN`)** into **English, and other target languages** directly on mobile devices with comic-grade typesetting and neural inpainting.

> [!WARNING]
> ### Disclaimer
> * **Hardware Requirements:** Running deep learning models directly on-device requires sufficient RAM and CPU processing. Performance depends on device specifications.
> * **Source Language Specialization:** The on-device OCR model and dictionary are specialized for Japanese Manga typography (Kanji, Hiragana, Katakana, Romaji, and common comic symbols). Translating non-Japanese comics is not supported for now.
> * **Edge Cases:** Highly distorted handwritten script, dense sound effects (SFX), or degraded low-resolution scans may require manual adjustment via the built-in interactive editors.

---

## Showcase

### Before & After Translation

<div align="center">
  <table>
    <tr>
      <th align="center" width="50%">Raw</th>
      <th align="center" width="50%">Translated & Typeset</th>
    </tr>
    <tr>
      <td align="center">
        <!-- Place your raw manga sample image at docs/assets/before_translate.png -->
        <img src="docs/assets/before_translate.png" alt="Original Raw Japanese Manga" width="100%" />
      </td>
      <td align="center">
        <!-- Place your translated manga output image at docs/assets/after_translate.png -->
        <img src="docs/assets/after_translate.png" alt="Translated Manga Page Output" width="100%" />
      </td>
    </tr>
  </table>
</div>

### Interactive Features & Workflow

<div align="center">
  <table>
    <tr>
      <th align="center" width="33.3%">Review Text Bubbles</th>
      <th align="center" width="33.3%">Edit Text & Bubbles</th>
      <th align="center" width="33.3%">Batch Chapter Translation</th>
    </tr>
    <tr>
      <td align="center">
        <!-- Place your Review Text Bubbles screenshot at docs/assets/review_text_bubbles.png -->
        <img src="docs/assets/review_text_bubbles.png" alt="Review Text Bubbles Editor" width="100%" />
      </td>
      <td align="center">
        <!-- Place your Edit Text & Bubbles screenshot at docs/assets/edit_text_bubbles.png -->
        <img src="docs/assets/edit_text_bubbles.png" alt="Edit Text & Bubbles Editor" width="100%" />
      </td>
      <td align="center">
        <!-- Place your Batch Translation screenshot at docs/assets/batch_translate.png -->
        <img src="docs/assets/batch_translate.png" alt="Batch Chapter Translation" width="100%" />
      </td>
    </tr>
    <tr>
      <td align="center"><sub>Adjust detection boundaries, add missed speech bubbles, or discard SFX boxes with pinch-zoom support.</sub></td>
      <td align="center"><sub>Interactive text editing, font size stepper, alignment, bold/italic, and comic font selector (Wild Words / Badaboom BB).</sub></td>
      <td align="center"><sub>Multi-page queue processing with automatic sorting, live stage indicators, and chapter album organization.</sub></td>
    </tr>
  </table>
</div>

---

## Key Features

### 1. On-Device AI Pipeline
* **Comic Text Detection (CTD):** Deep learning segmentation to identify dialogue bubbles, rectangular narrative boxes, and panel textlines across complex layouts.
* **Dual OCR Engine Support:**
  * **48px CTC OCR (Lightweight & Fast):** CRNN-based Japanese text recognition optimized for mobile performance.
  * **Manga-OCR (Full-Precision ViT + RoBERTa):** Vision Transformer encoder paired with an autoregressive RoBERTa decoder, delivering maximum accuracy on stylized Japanese manga typography, handwriting, and complex Kanji/Kana layouts.
* **AOT-GAN Inpainting:** Neural image inpainting that removes original Japanese text strokes while preserving background artwork and screentones.

### 2. Interactive Bubble Review & Text Editing
* **Review Text Bubbles (`Review & Translate`):**
  * Interactive canvas with smooth pinch-to-zoom, pan, and double-tap gestures.
  * Add custom bounding boxes over missed dialogue bubbles.
  * Move and resize detection boundaries using corner drag handles.
  * Remove unwanted bounding boxes or ignore stylized sound effects (SFX).
* **Edit Text & Bubbles (`Customize Comic Text`):**
  * Real-time text block inspector directly over the inpainted image.
  * Modify translated dialogue text manually.
  * Select comic font family (**Wild Words** for standard dialogue, **Badaboom BB** for shouting/action).
  * Adjust font size with increment/decrement steppers.
  * Adjust text styling (Bold, Italic) and text alignment (Left, Center, Right).
  * Reposition and resize text rendered areas with live canvas updates.
  * Save modifications directly back to the database and update stored image files.

### 3. Batch Chapter Translation
* Select multiple manga pages from device storage or gallery.
* Automatic filename sorting for correct chapter page ordering.
* Sequential pipeline processing with live stage progress indicators.
* Page-level text bubble review and individual text editing for completed pages.
* Chapter album grouping in the gallery database with multi-page reader support.

### 4. Translation Engines
* Cloud API integration: **Claude**, **GLM**, **DeepL**, **Google Gemini**, **OpenAI**, **DeepSeek**, **Groq**, **OpenRouter**, and **Naver Papago**.
* Encrypted local storage for provider API keys.
* Fast re-translate capability to switch engines without re-running OCR and inpainting.

### 5. Gallery & Reader
* Chapter album grouping and single-page history cards.
* Instant toggle between original Japanese scan and translated output.
* Multi-select management with batch deletion.
* Native image sharing via Android system share sheet.

### 6. Comprehensive Localization
* Full multi-language user interface support:
  * English (Default)
  * Indonesian (Bahasa Indonesia)
  * Japanese (日本語)
  * Simplified Chinese (简体中文)

---

## Architecture

The application is built using Clean Architecture principles and MVVM pattern:

```text
┌───────────────────────────────────────────────────────────┐
│                    Presentation Layer                     │
│  Jetpack Compose • Material 3 • Navigation • ViewModels   │
│  Interactive Canvas Editors (Detection & Render Editors)  │
└─────────────────────────────┬─────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                       Domain Layer                        │
│   UseCases • Repository Interfaces • Pipeline Models      │
└─────────────────────────────┬─────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                        Data Layer                         │
│  ONNX Runtime • OpenCV • Room DB • DataStore • Retrofit   │
└───────────────────────────────────────────────────────────┘
```

* **UI Framework:** Jetpack Compose with Material Design 3
* **Dependency Injection:** Hilt
* **AI Runtime:** ONNX Runtime Mobile
* **Computer Vision:** OpenCV Android SDK
* **Image Loading:** Coil Compose
* **Local Storage:** Room Database (v3) & Jetpack DataStore Preferences
* **Asynchronous Operations:** Kotlin Coroutines & Flow

---

## Translation Pipeline Workflow

```mermaid
graph TD
    A[Manga Image] --> B[CTD Text Detection]
    B --> C{Review Mode Enabled?}
    C -- Yes --> D[Interactive Detection Editor]
    C -- No --> E[Spatial Clustering]
    D --> E
    E --> F[48px CTC OCR]
    F --> G[Textline Merger]
    G --> H[Translation Engine]
    H --> I[AOT Inpainting]
    I --> J[Canvas Typography Renderer]
    J --> K{Manual Edit Requested?}
    K -- Yes --> L[Interactive Typeset Editor]
    K -- No --> M[Final Translated Manga]
    L --> M
```

1. **Detection:** CTD produces text probability maps to isolate speech bubble coordinates.
2. **Review (Optional):** User inspects, adds, resizes, or removes detection boxes before OCR.
3. **OCR:** 48px CTC model extracts Japanese characters from cropped text regions.
4. **Textline Merging:** Merges disjointed vertical/horizontal textlines belonging to the same bubble.
5. **Translation:** Translates text via the configured translation engine.
6. **Inpainting:** AOT-GAN neural network cleans Japanese text strokes from the original image.
7. **Rendering & Typesetting:** Calculates line wrapping, applies white outlines, and draws text with CC Wild Words typeface.
8. **Typeset Editor (Optional):** User can fine-tune text alignment, font size, position, and wording.

---

## Build & Setup Instructions

### Prerequisites
* Android Studio Ladybug (2024.2.1+) or newer.
* JDK 17 or JDK 21.
* Android SDK: Compile SDK 35, Min SDK 26 (Android 8.0+).

### 1. Clone Repository
```bash
git clone https://github.com/Yuu18id/manga-image-translator-android.git
cd manga-image-translator-android
```

### 2. Model Assets Placement
Place required ONNX models, vocabularies, and dictionary files inside `app/src/main/assets/models/` or your device's external storage (`/sdcard/Android/data/com.yuu18id.mangatranslator/files/models/`):

```text
app/src/main/assets/
├── fonts/
│   ├── cc-wild-words-roman.ttf   # Standard dialogue font
│   └── BADABB__.TTF              # Shouting / action comic font
└── models/
    ├── alphabet-all-v5.txt       # CTC OCR dictionary
    ├── manga_ocr_vocab.txt       # Manga-OCR Japanese WordPiece vocabulary
    ├── ctd_detector.onnx         # Comic Text Detector
    ├── ocr_ctc_48px.onnx         # 48px CTC OCR (default)
    ├── aot_inpainter.onnx        # AOT-GAN Inpainting
    ├── manga_ocr_encoder.onnx    # Manga-OCR Vision Transformer (optional)
    └── manga_ocr_decoder.onnx    # Manga-OCR RoBERTa Autoregressive Decoder (optional)
```

Pre-converted models can be downloaded from the repository Releases section or exported from base PyTorch checkpoints using the conversion scripts in `models/`.

### 3. Build APK
```bash
# Verify compilation
./gradlew compileDebugKotlin

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Generated APK output directory:
* Debug: `app/build/outputs/apk/debug/app-debug.apk`
* Release: `app/build/outputs/apk/release/app-release.apk`

---

## Settings & Configuration

The application **Settings** screen provides configuration for:
* **OCR Engine:** Choose between **48px CTC OCR** (Fast & Lightweight) and **Manga-OCR** (Full-Precision Vision Transformer ViT + RoBERTa for complex fonts).
* **Translation Engine:** Select default provider (DeepL, Gemini, OpenAI, Groq, DeepSeek, OpenRouter, Papago).
* **API Keys:** Securely input and store provider keys.
* **Target Language:** Select default output language (English, etc.).
* **Storage Management:** View and clear translation cache and historical rendered image files.

---

## Acknowledgements

* **Base Project:** [zyddnys/manga-image-translator](https://github.com/zyddnys/manga-image-translator) for the original research, pipeline architecture, and training pipelines.
* **Manga-OCR:** [kha-white/manga-ocr](https://github.com/kha-white/manga-ocr) for the state-of-the-art Japanese manga Vision Transformer OCR model.
* **Comic Text Detector:** [dmMaze/ComicTextDetector](https://github.com/dmMaze/ComicTextDetector) for the deep learning text detection model.
* **Neural Inpainting:** [AOT-GAN](https://github.com/researchmm/AOT-GAN-for-Inpainting) for background reconstruction.
* **Typography:** *CC Wild Words Roman* comic typeface.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](../LICENSE) file for full details.
