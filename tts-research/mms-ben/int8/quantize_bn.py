"""Dynamic INT8 quantization of the converted Bengali MMS VITS model (ONNX Runtime).
usage: quantize_bn.py <in.onnx> <out.onnx> <matmul|matmul_conv> [--per-channel]
Architecture/graph is unchanged; only weights are quantized. Metadata (sherpa) is preserved/re-applied."""
import sys, onnx
from onnxruntime.quantization import quantize_dynamic, QuantType
src, dst, mode = sys.argv[1:4]
ops = ["MatMul"] if mode == "matmul" else ["MatMul", "Conv"]
quantize_dynamic(src, dst, weight_type=QuantType.QUInt8, op_types_to_quantize=ops,
                 per_channel="--per-channel" in sys.argv)
meta = {p.key: p.value for p in onnx.load(src, load_external_data=False).metadata_props}
m = onnx.load(dst); have = {p.key for p in m.metadata_props}
for k, v in meta.items():
    if k not in have:
        p = m.metadata_props.add(); p.key = k; p.value = v
onnx.save(m, dst)
print("ok", dst, {p.key: p.value for p in onnx.load(dst).metadata_props})
