import os
import sys
import argparse
import urllib.request
import torch
import torch.nn as nn
import numpy as np
import onnxruntime as ort

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../../')))
from manga_translator.inpainting.inpainting_aot import AOTGenerator

MODEL_URL = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/inpainting.ckpt'

def download_file(url, path):
    if not os.path.exists(path):
        print(f"Downloading {url} to {path}...")
        urllib.request.urlretrieve(url, path)
        print("Download complete.")
    else:
        print(f"File {path} already exists.")

class AOTWrapper(nn.Module):
    def __init__(self, aot_model):
        super().__init__()
        self.aot = aot_model

    def forward(self, x):
        # x is (1, 4, H, W) -> [mask, r, g, b]
        mask = x[:, 0:1, :, :]
        img = x[:, 1:4, :, :]
        return self.aot(img, mask)

def export_aot(ckpt_path, onnx_path):
    print(f"Loading PyTorch model from {ckpt_path}...")
    model = AOTGenerator()
    sd = torch.load(ckpt_path, map_location='cpu')
    model.load_state_dict(sd['model'] if 'model' in sd else sd)
    model.eval()

    wrapper = AOTWrapper(model)
    wrapper.eval()

    # (1, 4, 512, 512)
    dummy_input = torch.randn(1, 4, 512, 512)

    print(f"Exporting to ONNX at {onnx_path}...")
    torch.onnx.export(
        wrapper,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['output'],
        dynamic_axes={
            'input': {0: 'batch_size', 2: 'height', 3: 'width'},
            'output': {0: 'batch_size', 2: 'height', 3: 'width'}
        }
    )
    print("Export complete.")

def verify_onnx(ckpt_path, onnx_path):
    print("Verifying PyTorch vs ONNX output...")
    model = AOTGenerator()
    sd = torch.load(ckpt_path, map_location='cpu')
    model.load_state_dict(sd['model'] if 'model' in sd else sd)
    model.eval()
    
    wrapper = AOTWrapper(model)
    wrapper.eval()

    # Different resolution to test dynamic axes
    dummy_input = torch.randn(1, 4, 256, 256)
    
    with torch.no_grad():
        pt_out = wrapper(dummy_input)
    
    ort_session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
    ort_inputs = {ort_session.get_inputs()[0].name: dummy_input.numpy()}
    ort_out = ort_session.run(None, ort_inputs)[0]

    try:
        np.testing.assert_allclose(pt_out.numpy(), ort_out, rtol=1e-03, atol=1e-05)
        print("Output matches!")
    except AssertionError as e:
        print(f"Output mismatch: {e}")

def main():
    parser = argparse.ArgumentParser(description="Export AOT Inpainting model to ONNX")
    parser.add_argument('--ckpt_path', type=str, default='inpainting.ckpt', help='Path to PyTorch checkpoint')
    parser.add_argument('--onnx_path', type=str, default='aot_generator.onnx', help='Path to output ONNX model')
    args = parser.parse_args()

    try:
        download_file(MODEL_URL, args.ckpt_path)
        
        export_aot(args.ckpt_path, args.onnx_path)
        verify_onnx(args.ckpt_path, args.onnx_path)
        
        pt_size = os.path.getsize(args.ckpt_path) / (1024 * 1024)
        onnx_size = os.path.getsize(args.onnx_path) / (1024 * 1024)
        print(f"Model sizes: PyTorch={pt_size:.2f}MB, ONNX={onnx_size:.2f}MB")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    main()
