import os
import sys
import argparse
import urllib.request
import zipfile
import torch
import numpy as np
import onnxruntime as ort

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../../')))
from manga_translator.ocr.model_48px_ctc import OCR

MODEL_URL = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/ocr-ctc.zip'

def download_and_extract(url, zip_path, extract_dir):
    if not os.path.exists(zip_path):
        print(f"Downloading {url} to {zip_path}...")
        urllib.request.urlretrieve(url, zip_path)
        print("Download complete.")
    
    if not os.path.exists(os.path.join(extract_dir, 'ocr-ctc.ckpt')):
        print(f"Extracting {zip_path} to {extract_dir}...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(extract_dir)
        print("Extraction complete.")

def load_model(ckpt_path, dict_path):
    with open(dict_path, 'r', encoding='utf-8') as fp:
        dictionary = [s[:-1] for s in fp.readlines()]
    
    model = OCR(dictionary, 768)
    sd = torch.load(ckpt_path, map_location='cpu')
    sd = sd['model'] if 'model' in sd else sd
    if 'encoders.layers.0.pe.pe' in sd: del sd['encoders.layers.0.pe.pe']
    if 'encoders.layers.1.pe.pe' in sd: del sd['encoders.layers.1.pe.pe']
    if 'encoders.layers.2.pe.pe' in sd: del sd['encoders.layers.2.pe.pe']
    
    model.load_state_dict(sd, strict=False)
    model.eval()
    return model, dictionary

def export_ocr(model, onnx_path):
    print(f"Exporting to ONNX at {onnx_path}...")
    dummy_input = torch.randn(1, 3, 48, 128)
    
    # We define dynamic axes for batch size and width
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['pred_char_logits', 'pred_color_values'],
        dynamic_axes={
            'input': {0: 'batch_size', 3: 'width'},
            'pred_char_logits': {0: 'batch_size', 1: 'seq_len'},
            'pred_color_values': {0: 'batch_size', 1: 'seq_len'}
        }
    )
    print("Export complete.")

def verify_onnx(model, onnx_path):
    print("Verifying PyTorch vs ONNX output...")
    # Test on a different width to ensure dynamic axes work
    dummy_input = torch.randn(1, 3, 48, 256)
    
    with torch.no_grad():
        pt_logits, pt_colors = model(dummy_input)
    
    ort_session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
    ort_inputs = {ort_session.get_inputs()[0].name: dummy_input.numpy()}
    ort_outs = ort_session.run(None, ort_inputs)

    ort_logits, ort_colors = ort_outs[0], ort_outs[1]
    
    try:
        np.testing.assert_allclose(pt_logits.numpy(), ort_logits, rtol=1e-03, atol=1e-05)
        np.testing.assert_allclose(pt_colors.numpy(), ort_colors, rtol=1e-03, atol=1e-05)
        print("Output matches!")
    except AssertionError as e:
        print(f"Output mismatch: {e}")

def main():
    parser = argparse.ArgumentParser(description="Export CTC OCR model to ONNX")
    parser.add_argument('--zip_path', type=str, default='ocr-ctc.zip', help='Path to download zip')
    parser.add_argument('--extract_dir', type=str, default='.', help='Directory to extract files')
    parser.add_argument('--onnx_path', type=str, default='ocr_48px_ctc.onnx', help='Path to output ONNX model')
    args = parser.parse_args()

    try:
        download_and_extract(MODEL_URL, args.zip_path, args.extract_dir)
        
        ckpt_path = os.path.join(args.extract_dir, 'ocr-ctc.ckpt')
        dict_path = os.path.join(args.extract_dir, 'alphabet-all-v5.txt')
        
        print("Loading PyTorch model...")
        model, _ = load_model(ckpt_path, dict_path)
        
        export_ocr(model, args.onnx_path)
        verify_onnx(model, args.onnx_path)
        
        pt_size = os.path.getsize(ckpt_path) / (1024 * 1024)
        onnx_size = os.path.getsize(args.onnx_path) / (1024 * 1024)
        print(f"Model sizes: PyTorch={pt_size:.2f}MB, ONNX={onnx_size:.2f}MB")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    main()
