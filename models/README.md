# Manga Image Translator - ONNX Models Export

This directory contains scripts to export the original PyTorch models from `manga-image-translator` to ONNX format, optimizing them for mobile deployment on Android.

## Prerequisites

1.  Make sure you are in the `models` directory:
    ```bash
    cd d:\BAYU\PROJECT\manga-image-translator\android-app\models\
    ```
2.  Install the required dependencies:
    ```bash
    pip install torch torchvision onnx onnxruntime onnxruntime-tools numpy opencv-python
    ```

## Model Checkpoints

The export scripts will **automatically download** the necessary base PyTorch model checkpoints into the current directory when executed. You don't need to manually download them.

## Exporting Models

Run the following scripts one by one to export each model.

### 1. Comic Text Detector (CTD)
Exports the ComicTextDetector object detection model.
```bash
python export_ctd_onnx.py
```
- **Downloads:** `comictextdetector.pt` and `comictextdetector.pt.onnx`
- **Output:** `comictextdetector.onnx`
- **Expected Size:** ~113 MB

### 2. CTC OCR Model
Exports the 48px CTC OCR recognition model.
```bash
python export_ocr_ctc_onnx.py
```
- **Downloads:** `ocr-ctc.zip` (extracts `ocr-ctc.ckpt` and `alphabet-all-v5.txt`)
- **Output:** `ocr_48px_ctc.onnx`
- **Expected Size:** ~85 MB

### 3. AOT-GAN Inpainting
Exports the AOT-GAN model used for redrawing missing backgrounds.
```bash
python export_aot_onnx.py
```
- **Downloads:** `inpainting.ckpt`
- **Output:** `aot_generator.onnx`
- **Expected Size:** ~150 MB

## Quantization (Optional but Recommended for Android)

Mobile apps benefit greatly from smaller model sizes. We provide a script to apply INT8 dynamic quantization to the exported ONNX models.
```bash
python quantize_models.py
```
- **Input:** `comictextdetector.onnx`, `ocr_48px_ctc.onnx`, `aot_generator.onnx`
- **Output:** `comictextdetector_int8.onnx`, `ocr_48px_ctc_int8.onnx`, `aot_generator_int8.onnx`
- **Size Reduction:** Typically ~75% reduction (e.g., AOT goes from 150MB to ~40MB).

## Android App Deployment

1.  Take the `.onnx` files (preferably the `_int8.onnx` quantized versions).
2.  Copy them to your Android project's `app/src/main/assets/` directory.
3.  Also copy `alphabet-all-v5.txt` to the assets directory, as it's required for OCR decoding.
4.  Use the ONNX Runtime Android library in your app to load and infer these models!
