import os
import sys

# Ensure UTF-8 output on Windows console
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
import math
import shutil
import urllib.request
import zipfile
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import Optional, List, Tuple
import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

# Directory setup
MODELS_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(MODELS_DIR, 'exported')
ASSETS_DIR = os.path.abspath(os.path.join(MODELS_DIR, '../app/src/main/assets'))
ASSETS_MODELS_DIR = os.path.join(ASSETS_DIR, 'models')

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)
os.makedirs(ASSETS_MODELS_DIR, exist_ok=True)

# URLs
URL_CTD_ONNX = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/comictextdetector.pt.onnx'
URL_OCR_ZIP = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/ocr-ctc.zip'
URL_AOT_CKPT = 'https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/inpainting.ckpt'

def download(url, dest):
    if not os.path.exists(dest):
        print(f"[DOWNLOAD] {url} -> {dest}")
        urllib.request.urlretrieve(url, dest)
        print(f"[DOWNLOAD] Finished ({os.path.getsize(dest) / (1024*1024):.2f} MB)")
    else:
        print(f"[EXISTS] {dest} ({os.path.getsize(dest) / (1024*1024):.2f} MB)")

# -------------------------------------------------------------
# 1. CTD Detector
# -------------------------------------------------------------
def export_ctd():
    print("\n" + "="*50)
    print("1. ComicTextDetector (CTD)")
    print("="*50)
    ctd_onnx = os.path.join(OUTPUT_DIR, 'ctd_detector.onnx')
    download(URL_CTD_ONNX, ctd_onnx)
    print(f"CTD ONNX Model ready: {os.path.getsize(ctd_onnx) / (1024*1024):.2f} MB")
    return ctd_onnx

# -------------------------------------------------------------
# 2. 48px CTC OCR Architecture (Exact from model_48px_ctc.py)
# -------------------------------------------------------------
class PositionalEncoding(nn.Module):
    def __init__(self, d_model, dropout=0.1, max_len=5000):
        super(PositionalEncoding, self).__init__()
        self.dropout = nn.Dropout(p=dropout)
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0)
        self.register_buffer('pe', pe)

    def forward(self, x, offset = 0):
        x = x + self.pe[:, offset: offset + x.size(1), :]
        return x

class CustomTransformerEncoderLayer(nn.Module):
    def __init__(self, d_model, nhead, dim_feedforward=2048, dropout=0.1, activation="gelu",
                 layer_norm_eps=1e-5, batch_first=False, norm_first=False,
                 device=None, dtype=None) -> None:
        factory_kwargs = {'device': device, 'dtype': dtype}
        super(CustomTransformerEncoderLayer, self).__init__()
        self.self_attn = nn.MultiheadAttention(d_model, nhead, dropout=dropout, batch_first=batch_first,
                                            **factory_kwargs)
        self.linear1 = nn.Linear(d_model, dim_feedforward, **factory_kwargs)
        self.dropout = nn.Dropout(dropout)
        self.linear2 = nn.Linear(dim_feedforward, d_model, **factory_kwargs)
        self.norm_first = norm_first
        self.norm1 = nn.LayerNorm(d_model, eps=layer_norm_eps, **factory_kwargs)
        self.norm2 = nn.LayerNorm(d_model, eps=layer_norm_eps, **factory_kwargs)
        self.dropout1 = nn.Dropout(dropout)
        self.dropout2 = nn.Dropout(dropout)
        self.pe = PositionalEncoding(d_model, max_len = 768)
        self.activation = F.gelu

    def forward(self, src: torch.Tensor, src_mask: Optional[torch.Tensor] = None, src_key_padding_mask: Optional[torch.Tensor] = None, is_causal = None) -> torch.Tensor:
        x = src
        if self.norm_first:
            x = x + self._sa_block(self.norm1(x), src_mask, src_key_padding_mask)
            x = x + self._ff_block(self.norm2(x))
        else:
            x = self.norm1(x + self._sa_block(x, src_mask, src_key_padding_mask))
            x = self.norm2(x + self._ff_block(x))
        return x

    def _sa_block(self, x: torch.Tensor, attn_mask: Optional[torch.Tensor], key_padding_mask: Optional[torch.Tensor]) -> torch.Tensor:
        x = self.self_attn(self.pe(x), self.pe(x), x, attn_mask=attn_mask, key_padding_mask=key_padding_mask, need_weights=False)[0]
        return self.dropout1(x)

    def _ff_block(self, x: torch.Tensor) -> torch.Tensor:
        x = self.linear2(self.dropout(self.activation(self.linear1(x))))
        return self.dropout2(x)

def conv3x3(in_planes, out_planes, stride=1, groups=1, dilation=1):
    return nn.Conv2d(in_planes, out_planes, kernel_size=3, stride=stride, padding=dilation, groups=groups, bias=False, dilation=dilation)

def conv1x1(in_planes, out_planes, stride=1):
    return nn.Conv2d(in_planes, out_planes, kernel_size=1, stride=stride, bias=False)

class BasicBlock(nn.Module):
    expansion = 1
    def __init__(self, inplanes, planes, stride=1, downsample=None):
        super(BasicBlock, self).__init__()
        self.bn1 = nn.BatchNorm2d(inplanes)
        self.conv1 = self._conv3x3(inplanes, planes)
        self.bn2 = nn.BatchNorm2d(planes)
        self.conv2 = self._conv3x3(planes, planes)
        self.downsample = downsample
        self.stride = stride

    def _conv3x3(self, in_planes, out_planes, stride=1):
        return nn.Conv2d(in_planes, out_planes, kernel_size=3, stride=stride, padding=1, bias=False)

    def forward(self, x):
        residual = x
        out = self.bn1(x)
        out = F.relu(out)
        out = self.conv1(out)
        out = self.bn2(out)
        out = F.relu(out)
        out = self.conv2(out)
        if self.downsample is not None:
            residual = self.downsample(residual)
        return out + residual

class ResNet(nn.Module):
    def __init__(self, input_channel, output_channel, block, layers):
        super(ResNet, self).__init__()
        self.output_channel_block = [int(output_channel / 4), int(output_channel / 2), output_channel, output_channel]
        self.inplanes = int(output_channel / 8)
        self.conv0_1 = nn.Conv2d(input_channel, int(output_channel / 8), kernel_size=3, stride=1, padding=1, bias=False)
        self.bn0_1 = nn.BatchNorm2d(int(output_channel / 8))
        self.conv0_2 = nn.Conv2d(int(output_channel / 8), self.inplanes, kernel_size=3, stride=1, padding=1, bias=False)

        self.maxpool1 = nn.AvgPool2d(kernel_size=2, stride=2, padding=0)
        self.layer1 = self._make_layer(block, self.output_channel_block[0], layers[0])
        self.bn1 = nn.BatchNorm2d(self.output_channel_block[0])
        self.conv1 = nn.Conv2d(self.output_channel_block[0], self.output_channel_block[0], kernel_size=3, stride=1, padding=1, bias=False)

        self.maxpool2 = nn.AvgPool2d(kernel_size=2, stride=2, padding=0)
        self.layer2 = self._make_layer(block, self.output_channel_block[1], layers[1], stride=1)
        self.bn2 = nn.BatchNorm2d(self.output_channel_block[1])
        self.conv2 = nn.Conv2d(self.output_channel_block[1], self.output_channel_block[1], kernel_size=3, stride=1, padding=1, bias=False)

        self.maxpool3 = nn.AvgPool2d(kernel_size=2, stride=(2, 1), padding=(0, 1))
        self.layer3 = self._make_layer(block, self.output_channel_block[2], layers[2], stride=1)
        self.bn3 = nn.BatchNorm2d(self.output_channel_block[2])
        self.conv3 = nn.Conv2d(self.output_channel_block[2], self.output_channel_block[2], kernel_size=3, stride=1, padding=1, bias=False)

        self.layer4 = self._make_layer(block, self.output_channel_block[3], layers[3], stride=1)
        self.bn4_1 = nn.BatchNorm2d(self.output_channel_block[3])
        self.conv4_1 = nn.Conv2d(self.output_channel_block[3], self.output_channel_block[3], kernel_size=3, stride=(2, 1), padding=(1, 1), bias=False)
        self.bn4_2 = nn.BatchNorm2d(self.output_channel_block[3])
        self.conv4_2 = nn.Conv2d(self.output_channel_block[3], self.output_channel_block[3], kernel_size=3, stride=1, padding=0, bias=False)
        self.bn4_3 = nn.BatchNorm2d(self.output_channel_block[3])

    def _make_layer(self, block, planes, blocks, stride=1):
        downsample = None
        if stride != 1 or self.inplanes != planes * block.expansion:
            downsample = nn.Sequential(
                nn.BatchNorm2d(self.inplanes),
                nn.Conv2d(self.inplanes, planes * block.expansion, kernel_size=1, stride=stride, bias=False),
            )
        layers = []
        layers.append(block(self.inplanes, planes, stride, downsample))
        self.inplanes = planes * block.expansion
        for i in range(1, blocks):
            layers.append(block(self.inplanes, planes))
        return nn.Sequential(*layers)

    def forward(self, x):
        x = self.conv0_1(x)
        x = self.bn0_1(x)
        x = F.relu(x)
        x = self.conv0_2(x)
        x = self.maxpool1(x)
        x = self.layer1(x)
        x = self.bn1(x)
        x = F.relu(x)
        x = self.conv1(x)
        x = self.maxpool2(x)
        x = self.layer2(x)
        x = self.bn2(x)
        x = F.relu(x)
        x = self.conv2(x)
        x = self.maxpool3(x)
        x = self.layer3(x)
        x = self.bn3(x)
        x = F.relu(x)
        x = self.conv3(x)
        x = self.layer4(x)
        x = self.bn4_1(x)
        x = F.relu(x)
        x = self.conv4_1(x)
        x = self.bn4_2(x)
        x = F.relu(x)
        x = self.conv4_2(x)
        x = self.bn4_3(x)
        return x

class ResNet_FeatureExtractor(nn.Module):
    def __init__(self, input_channel, output_channel=128):
        super(ResNet_FeatureExtractor, self).__init__()
        self.ConvNet = ResNet(input_channel, output_channel, BasicBlock, [4, 6, 8, 6, 3])

    def forward(self, input):
        return self.ConvNet(input)

class OCR(nn.Module):
    def __init__(self, dictionary, max_len=768):
        super(OCR, self).__init__()
        self.max_len = max_len
        self.dictionary = dictionary
        self.dict_size = len(dictionary)
        self.backbone = ResNet_FeatureExtractor(3, 320)
        enc = CustomTransformerEncoderLayer(320, 8, 320 * 4, dropout=0.05, batch_first=True, norm_first=True)
        self.encoders = nn.TransformerEncoder(enc, 3)
        self.char_pred_norm = nn.Sequential(nn.LayerNorm(320), nn.Dropout(0.1), nn.GELU())
        self.char_pred = nn.Linear(320, self.dict_size)
        self.color_pred1 = nn.Sequential(nn.Linear(320, 6))

    def forward(self, img: torch.FloatTensor):
        feats = self.backbone(img).squeeze(2)
        feats = self.encoders(feats.permute(0, 2, 1))
        pred_char_logits = self.char_pred(self.char_pred_norm(feats))
        pred_color_values = self.color_pred1(feats)
        return pred_char_logits, pred_color_values

def export_ocr():
    print("\n" + "="*50)
    print("2. 48px CTC OCR Model")
    print("="*50)
    zip_path = os.path.join(OUTPUT_DIR, 'ocr-ctc.zip')
    download(URL_OCR_ZIP, zip_path)

    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(OUTPUT_DIR)

    ckpt_path = os.path.join(OUTPUT_DIR, 'ocr-ctc.ckpt')
    dict_path = os.path.join(OUTPUT_DIR, 'alphabet-all-v5.txt')

    with open(dict_path, 'r', encoding='utf-8') as fp:
        dictionary = [s.strip('\r\n') for s in fp.readlines()]

    print(f"Loaded OCR dictionary: {len(dictionary)} tokens")
    model = OCR(dictionary, 768)
    sd = torch.load(ckpt_path, map_location='cpu')
    sd = sd['model'] if 'model' in sd else sd
    res = model.load_state_dict(sd, strict=False)
    print(f"✓ Model load result: missing={res.missing_keys}, unexpected={res.unexpected_keys}")
    model.eval()

    onnx_path = os.path.join(OUTPUT_DIR, 'ocr_ctc_48px.onnx')
    dummy_input = torch.randn(1, 3, 48, 128)

    print(f"Exporting OCR to ONNX: {onnx_path}...")
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
        input_names=['input'],
        output_names=['pred_char_logits', 'pred_color_values'],
        dynamic_axes={
            'input': {0: 'batch_size', 3: 'width'},
            'pred_char_logits': {0: 'batch_size', 1: 'seq_len'},
            'pred_color_values': {0: 'batch_size', 1: 'seq_len'}
        }
    )
    print(f"OCR ONNX Export Complete: {os.path.getsize(onnx_path)/(1024*1024):.2f} MB")
    return onnx_path

# -------------------------------------------------------------
# 3. AOT-GAN Inpainting Architecture (Exact from inpainting_aot.py)
# -------------------------------------------------------------
def relu_nf(x):
    return F.relu(x) * 1.7139588594436646

class LambdaLayer(nn.Module):
    def __init__(self, f):
        super(LambdaLayer, self).__init__()
        self.f = f

    def forward(self, x):
        return self.f(x)

class ScaledWSConv2d(nn.Conv2d):
    def __init__(self, in_channels, out_channels, kernel_size, stride=1, padding=0, dilation=1, groups=1, bias=True, gain=True, eps=1e-4):
        super(ScaledWSConv2d, self).__init__(in_channels, out_channels, kernel_size, stride=stride, padding=padding, dilation=dilation, groups=groups, bias=bias)
        if gain:
            self.gain = nn.Parameter(torch.ones(self.out_channels, 1, 1, 1))
        else:
            self.gain = None
        self.eps = eps

    def get_weight(self):
        fan_in = np.prod(self.weight.shape[1:])
        var, mean = torch.var_mean(self.weight, dim=(1, 2, 3), keepdims=True)
        scale = torch.rsqrt(torch.max(var * fan_in, torch.tensor(self.eps).to(var.device))) * self.gain.view_as(var).to(var.device)
        shift = mean * scale
        return self.weight * scale - shift

    def forward(self, x):
        return F.conv2d(x, self.get_weight(), self.bias, self.stride, self.padding, self.dilation, self.groups)

class ScaledWSTransposeConv2d(nn.ConvTranspose2d):
    def __init__(self, in_channels, out_channels, kernel_size, stride=1, padding=0, output_padding=0, groups=1, bias=True, dilation=1, padding_mode='zeros', gain=True, eps=1e-4):
        super(ScaledWSTransposeConv2d, self).__init__(in_channels, out_channels, kernel_size, stride=stride, padding=padding, output_padding=output_padding, groups=groups, bias=bias, dilation=dilation, padding_mode=padding_mode)
        if gain:
            self.gain = nn.Parameter(torch.ones(self.in_channels, 1, 1, 1))
        else:
            self.gain = None
        self.eps = eps

    def get_weight(self):
        fan_in = np.prod(self.weight.shape[1:])
        var, mean = torch.var_mean(self.weight, dim=(1, 2, 3), keepdims=True)
        scale = torch.rsqrt(torch.max(var * fan_in, torch.tensor(self.eps).to(var.device))) * self.gain.view_as(var).to(var.device)
        shift = mean * scale
        return self.weight * scale - shift

    def forward(self, x, output_size: Optional[List[int]] = None):
        output_padding = self._output_padding(x, output_size, self.stride, self.padding, self.kernel_size, self.dilation)
        return F.conv_transpose2d(x, self.get_weight(), self.bias, self.stride, self.padding, output_padding, self.groups, self.dilation)

class GatedWSConvPadded(nn.Module):
    def __init__(self, in_ch, out_ch, ks, stride = 1, dilation = 1):
        super(GatedWSConvPadded, self).__init__()
        self.padding = nn.ReflectionPad2d(((ks - 1) * dilation) // 2)
        self.conv = ScaledWSConv2d(in_ch, out_ch, kernel_size = ks, stride = stride, dilation = dilation)
        self.conv_gate = ScaledWSConv2d(in_ch, out_ch, kernel_size = ks, stride = stride, dilation = dilation)

    def forward(self, x):
        x = self.padding(x)
        signal = self.conv(x)
        gate = torch.sigmoid(self.conv_gate(x))
        return signal * gate * 1.8

class GatedWSTransposeConvPadded(nn.Module):
    def __init__(self, in_ch, out_ch, ks, stride = 1):
        super(GatedWSTransposeConvPadded, self).__init__()
        self.conv = ScaledWSTransposeConv2d(in_ch, out_ch, kernel_size = ks, stride = stride, padding = (ks - 1) // 2)
        self.conv_gate = ScaledWSTransposeConv2d(in_ch, out_ch, kernel_size = ks, stride = stride, padding = (ks - 1) // 2)

    def forward(self, x):
        signal = self.conv(x)
        gate = torch.sigmoid(self.conv_gate(x))
        return signal * gate * 1.8

def my_layer_norm(feat):
    mean = feat.mean((2, 3), keepdim=True)
    std = feat.std((2, 3), keepdim=True) + 1e-9
    feat = 2 * (feat - mean) / std - 1
    feat = 5 * feat
    return feat

class AOTBlock(nn.Module):
    def __init__(self, dim, rates = [2, 4, 8, 16]):
        super(AOTBlock, self).__init__()
        self.rates = rates
        for i, rate in enumerate(rates):
            self.__setattr__(
                'block{}'.format(str(i).zfill(2)), 
                nn.Sequential(
                    nn.ReflectionPad2d(rate),
                    nn.Conv2d(dim, dim//4, 3, padding=0, dilation=rate),
                    nn.ReLU(True)))
        self.fuse = nn.Sequential(
            nn.ReflectionPad2d(1),
            nn.Conv2d(dim, dim, 3, padding=0, dilation=1))
        self.gate = nn.Sequential(
            nn.ReflectionPad2d(1),
            nn.Conv2d(dim, dim, 3, padding=0, dilation=1))

    def forward(self, x):
        out = [self.__getattr__(f'block{str(i).zfill(2)}')(x) for i in range(len(self.rates))]
        out = torch.cat(out, 1)
        out = self.fuse(out)
        mask = my_layer_norm(self.gate(x))
        mask = torch.sigmoid(mask)
        return x * (1 - mask) + out * mask

class AOTGenerator(nn.Module):
    def __init__(self, in_ch = 4, out_ch = 3, ch = 32, alpha = 0.0):
        super(AOTGenerator, self).__init__()
        self.head = nn.Sequential(
            GatedWSConvPadded(in_ch, ch, 3, stride = 1),
            LambdaLayer(relu_nf),
            GatedWSConvPadded(ch, ch * 2, 4, stride = 2),
            LambdaLayer(relu_nf),
            GatedWSConvPadded(ch * 2, ch * 4, 4, stride = 2),
        )
        self.body_conv = nn.Sequential(*[AOTBlock(ch * 4) for _ in range(10)])
        self.tail = nn.Sequential(
            GatedWSConvPadded(ch * 4, ch * 4, 3, 1),
            LambdaLayer(relu_nf),
            GatedWSConvPadded(ch * 4, ch * 4, 3, 1),
            LambdaLayer(relu_nf),
            GatedWSTransposeConvPadded(ch * 4, ch * 2, 4, 2),
            LambdaLayer(relu_nf),
            GatedWSTransposeConvPadded(ch * 2, ch, 4, 2),
            LambdaLayer(relu_nf),
            GatedWSConvPadded(ch, out_ch, 3, stride = 1),
        )

    def forward(self, img, mask):
        x = torch.cat([mask, img], dim = 1)
        x = self.head(x)
        conv = self.body_conv(x)
        x = self.tail(conv)
        return torch.clip(x, -1, 1)

class AOTWrapper(nn.Module):
    def __init__(self, aot_model):
        super().__init__()
        self.aot = aot_model

    def forward(self, x):
        # x is (1, 4, H, W) where channel 0 is mask, channels 1..3 are RGB
        mask = x[:, 0:1, :, :]
        img = x[:, 1:4, :, :]
        return self.aot(img, mask)

def export_aot():
    print("\n" + "="*50)
    print("3. AOT-GAN Inpainter Model")
    print("="*50)
    ckpt_path = os.path.join(OUTPUT_DIR, 'inpainting.ckpt')
    download(URL_AOT_CKPT, ckpt_path)

    model = AOTGenerator()
    sd = torch.load(ckpt_path, map_location='cpu')
    sd = sd['model'] if 'model' in sd else sd
    model.load_state_dict(sd, strict=False)
    model.eval()

    wrapper = AOTWrapper(model)
    wrapper.eval()

    onnx_path = os.path.join(OUTPUT_DIR, 'aot_inpainter.onnx')
    dummy_input = torch.randn(1, 4, 512, 512)

    print(f"Exporting AOT-GAN to ONNX: {onnx_path}...")
    torch.onnx.export(
        wrapper,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
        input_names=['input'],
        output_names=['output'],
        dynamic_axes={
            'input': {0: 'batch_size', 2: 'height', 3: 'width'},
            'output': {0: 'batch_size', 2: 'height', 3: 'width'}
        }
    )
    print(f"AOT-GAN ONNX Export Complete: {os.path.getsize(onnx_path)/(1024*1024):.2f} MB")
    return onnx_path

# -------------------------------------------------------------
# 4. Quantization & Deployment
# -------------------------------------------------------------
def quantize_all(models):
    print("\n" + "="*50)
    print("4. INT8 Dynamic Quantization")
    print("="*50)
    quantized_paths = []
    for model_path in models:
        base, ext = os.path.splitext(model_path)
        int8_path = f"{base}_int8{ext}"
        print(f"Quantizing {os.path.basename(model_path)} -> {os.path.basename(int8_path)}...")
        try:
            quantize_dynamic(
                model_input=model_path,
                model_output=int8_path,
                weight_type=QuantType.QUInt8
            )
            orig_sz = os.path.getsize(model_path) / (1024 * 1024)
            int8_sz = os.path.getsize(int8_path) / (1024 * 1024)
            print(f"-> Original: {orig_sz:.2f} MB | INT8: {int8_sz:.2f} MB ({int8_sz/orig_sz*100:.1f}%)")
            quantized_paths.append(int8_path)
        except Exception as e:
            print(f"Quantization warning for {model_path}: {e}")
    return quantized_paths

def copy_to_assets(all_models):
    print("\n" + "="*50)
    print("5. Deploying Models & Alphabet to Android Assets")
    print("="*50)
    dict_src = os.path.join(OUTPUT_DIR, 'alphabet-all-v5.txt')
    if os.path.exists(dict_src):
        dict_dst = os.path.join(ASSETS_DIR, 'alphabet-all-v5.txt')
        shutil.copyfile(dict_src, dict_dst)
        print(f"Deployed alphabet: {dict_dst}")

    for model_path in all_models:
        dst = os.path.join(ASSETS_MODELS_DIR, os.path.basename(model_path))
        shutil.copyfile(model_path, dst)
        print(f"Deployed: {os.path.basename(model_path)} -> {dst} ({os.path.getsize(dst)/(1024*1024):.2f} MB)")

def main():
    print("🚀 Manga Translator Model Download & Export Suite Starting...")
    ctd_path = export_ctd()
    ocr_path = export_ocr()
    aot_path = export_aot()

    fp32_models = [ctd_path, ocr_path, aot_path]
    int8_models = quantize_all(fp32_models)

    all_models = fp32_models + int8_models
    copy_to_assets(all_models)

    print("\n" + "="*50)
    print("🎉 ALL MODELS DOWNLOADED, EXPORTED, QUANTIZED & DEPLOYED TO ANDROID ASSETS SUCCESSFULLY!")
    print("="*50)

if __name__ == '__main__':
    main()
