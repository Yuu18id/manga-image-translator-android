import os
import sys
import argparse
import urllib.request
import torch
import numpy as np
import onnx
import onnxruntime as ort

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../../')))
from manga_translator.detection.ctd_utils.basemodel import TextDetBase

MODEL_PT_URL = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/comictextdetector.pt'
MODEL_ONNX_URL = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/comictextdetector.pt.onnx'

def download_file(url, path):
    if not os.path.exists(path):
        print(f"Downloading {url} to {path}...")
        urllib.request.urlretrieve(url, path)
        print("Download complete.")
    else:
        print(f"File {path} already exists.")

def export_ctd(pt_path, onnx_path, opset_version=17):
    print(f"Loading PyTorch model from {pt_path}...")
    device = 'cpu'
    model = TextDetBase(pt_path, device=device, act='leaky')
    model.eval()

    dummy_input = torch.randn(1, 3, 1024, 1024)

    print(f"Exporting to ONNX at {onnx_path}...")
    torch.onnx.export(
        model, 
        dummy_input, 
        onnx_path,
        export_params=True,
        opset_version=opset_version,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['blks', 'mask', 'lines'],
        dynamic_axes={
            'input': {0: 'batch_size'},
            'blks': {0: 'batch_size'},
            'mask': {0: 'batch_size'},
            'lines': {0: 'batch_size'}
        }
    )
    print("Export complete.")

def verify_onnx(pt_path, onnx_path):
    print("Verifying PyTorch vs ONNX output...")
    # Run PyTorch
    device = 'cpu'
    model = TextDetBase(pt_path, device=device, act='leaky')
    model.eval()
    
    dummy_input = torch.randn(1, 3, 1024, 1024)
    with torch.no_grad():
        pt_outs = model(dummy_input)
    
    # Run ONNX
    ort_session = ort.InferenceSession(onnx_path, providers=['CPUExecutionProvider'])
    ort_inputs = {ort_session.get_inputs()[0].name: dummy_input.numpy()}
    ort_outs = ort_session.run(None, ort_inputs)

    # Compare
    for i, name in enumerate(['blks', 'mask', 'lines']):
        pt_out = pt_outs[i].numpy()
        ort_out = ort_outs[i]
        try:
            np.testing.assert_allclose(pt_out, ort_out, rtol=1e-03, atol=1e-05)
            print(f"Output '{name}' matches!")
        except AssertionError as e:
            print(f"Output '{name}' mismatch: {e}")

def main():
    parser = argparse.ArgumentParser(description="Export CTD model to ONNX")
    parser.add_argument('--pt_path', type=str, default='comictextdetector.pt', help='Path to PyTorch model')
    parser.add_argument('--onnx_path', type=str, default='comictextdetector.onnx', help='Path to output ONNX model')
    parser.add_argument('--opset', type=int, default=17, help='ONNX opset version')
    parser.add_argument('--download_only', action='store_true', help='Only download models')
    args = parser.parse_args()

    try:
        download_file(MODEL_PT_URL, args.pt_path)
        download_file(MODEL_ONNX_URL, args.onnx_path + ".original")
        
        if not args.download_only:
            export_ctd(args.pt_path, args.onnx_path, args.opset)
            verify_onnx(args.pt_path, args.onnx_path)
            
            pt_size = os.path.getsize(args.pt_path) / (1024 * 1024)
            onnx_size = os.path.getsize(args.onnx_path) / (1024 * 1024)
            print(f"Model sizes: PyTorch={pt_size:.2f}MB, ONNX={onnx_size:.2f}MB")
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    main()
