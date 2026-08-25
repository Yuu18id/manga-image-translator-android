import os
import argparse
import onnxruntime
from onnxruntime.quantization import quantize_dynamic, QuantType

def quantize_model(input_model, output_model):
    if not os.path.exists(input_model):
        print(f"Error: Input model {input_model} does not exist. Please run the export script first.")
        return

    print(f"Quantizing {input_model} to {output_model}...")
    
    try:
        quantize_dynamic(
            model_input=input_model,
            model_output=output_model,
            weight_type=QuantType.QInt8
        )
        print("Quantization successful.")
        
        orig_size = os.path.getsize(input_model) / (1024 * 1024)
        quant_size = os.path.getsize(output_model) / (1024 * 1024)
        
        print(f"Original size: {orig_size:.2f} MB")
        print(f"Quantized size: {quant_size:.2f} MB")
        print(f"Reduction: {(1 - quant_size/orig_size)*100:.1f}%\n")
        
    except Exception as e:
        print(f"Quantization failed for {input_model}: {e}\n")

def main():
    parser = argparse.ArgumentParser(description="Quantize ONNX models to INT8")
    parser.add_argument('--models', nargs='+', default=[
        ('comictextdetector.onnx', 'comictextdetector_int8.onnx'),
        ('ocr_48px_ctc.onnx', 'ocr_48px_ctc_int8.onnx'),
        ('aot_generator.onnx', 'aot_generator_int8.onnx')
    ], help='List of tuples containing input and output model paths')
    
    args = parser.parse_args()
    
    for input_model, output_model in args.models:
        if type(input_model) == tuple:
            in_path, out_path = input_model
        else:
            # If arguments are passed from command line, they will be strings.
            # Assuming pairs are not supported from cmd yet, we use defaults.
            pass
            
    # Process defaults
    default_models = [
        ('comictextdetector.onnx', 'comictextdetector_int8.onnx'),
        ('ocr_48px_ctc.onnx', 'ocr_48px_ctc_int8.onnx'),
        ('aot_generator.onnx', 'aot_generator_int8.onnx')
    ]
    
    for in_path, out_path in default_models:
        quantize_model(in_path, out_path)

if __name__ == '__main__':
    main()
