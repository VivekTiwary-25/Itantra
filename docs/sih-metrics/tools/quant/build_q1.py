#!/usr/bin/env python3
"""Q1: quantise what INT8-B still keeps at full precision, dynamic INT8.

Full    (q1-convs):          INT8-B recipe + the 29 remaining encoder Convs (5 pre-encode + 24 depthwise) via
                             quantize_dynamic ('Conv' -> ConvInteger; ORT's dynamic Conv quantizer is per-tensor)
                             + the per-language CTC head (1x1 Conv 1024->257) as per-channel MatMulInteger.
Gentler (q1g-pointwise):     only pointwise (1x1) Convs: pre_encode conv.3 / conv.6 + the CTC head;
                             depthwise and 3x3 pre-encode Convs stay FP32.
Built from the FP32 base; the MatMul part is identical to the INT8-B recipe (same quantizer call)."""
import argparse
import json
import time

import onnx
import onnxruntime
from onnx import helper
from onnxruntime.quantization import QuantType, quantize_dynamic

from ladder_lib import QL, build_package, op_histogram, package_manifest, quantize_wrapper_heads, sha256


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gentle", action="store_true")
    args = parser.parse_args()
    step = QL / ("q1g-pointwise" if args.gentle else "q1-convs")
    step.mkdir(parents=True, exist_ok=True)
    base = QL / "fp32-base" / "encoder_pw_fp32.onnx"
    g = onnx.load(str(base), load_external_data=False).graph
    inits = {i.name for i in g.initializer}
    matmuls = [n.name for n in g.node if n.op_type == "MatMul" and n.input[1] in inits]
    convs = []
    for n in g.node:
        if n.op_type != "Conv":
            continue
        k = next(helper.get_attribute_value(a) for a in n.attribute if a.name == "kernel_shape")
        if not args.gentle or all(v == 1 for v in k):
            convs.append(n.name)
    print(len(matmuls), "weight MatMuls;", len(convs), "Convs:", convs if len(convs) < 6 else f"{convs[:3]}...", flush=True)
    enc = step / "encoder.onnx"
    t0 = time.time()
    if not enc.exists():
        quantize_dynamic(str(base), str(enc), op_types_to_quantize=["MatMul", "Conv"], nodes_to_quantize=matmuls + convs,
                         per_channel=True, reduce_range=False, weight_type=QuantType.QInt8, use_external_data_format=False)
    print("quantized", round(time.time() - t0, 1), "s", flush=True)
    build_package(enc, step / "package")
    heads = quantize_wrapper_heads(step / "package")
    manifest = package_manifest(step / "package")
    info = {"onnxruntime_quantizer": onnxruntime.__version__, "gentle": args.gentle, "quantized_convs": convs,
            "encoder": {"bytes": enc.stat().st_size, "sha256": sha256(enc)}, "wrapper_bytes": heads,
            "ops": op_histogram(step / "package" / "hi.onnx"), "package_total_bytes": manifest["total_bytes"]}
    (step / "build_info.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    print(json.dumps({k: info[k] for k in ("encoder", "package_total_bytes")}, indent=1))


if __name__ == "__main__":
    main()
