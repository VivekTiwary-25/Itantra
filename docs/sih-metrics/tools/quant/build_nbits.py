#!/usr/bin/env python3
"""Q2 / Q3: 4-bit block-wise weights (com.microsoft MatMulNBits) built from the FP32 base (pointwise Convs already
rewritten to MatMul, as in INT8-B).

Stage A  ORT quantize_dynamic (INT8-B settings: per-channel symmetric int8, reduce_range=False) on the MatMuls that
         must stay INT8 (Q3: pre-encode + first/last N conformer blocks) and, when Q1 was accepted, on the Q1 Convs.
Stage B  MatMulNBitsQuantizer on every remaining weight MatMul: bits=4, asymmetric (uint4 + zero point),
         block size along K = --block, accuracy_level=4 (int8 activations inside the kernel, like INT8-B's dynamic
         activations; this is the arm64 fast path).
CTC head: INT8 in every wrapper when --q1 (Q1 accepted), otherwise FP32 like INT8-B."""
import argparse
import json
import re
import time

import onnx
import onnxruntime
from onnx import helper
from onnxruntime.quantization import QuantType, quantize_dynamic
from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer

from ladder_lib import QL, build_package, op_histogram, package_manifest, quantize_wrapper_heads, sha256


def layer_of(name: str):
    m = re.search(r"/layers\.(\d+)/", name) or re.search(r"layers\.(\d+)\.", name)
    return int(m.group(1)) if m else None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--step", required=True)
    parser.add_argument("--block", type=int, default=32)
    parser.add_argument("--int8-edge", type=int, default=0, help="keep this many blocks at each end INT8 (Q3)")
    parser.add_argument("--q1", choices=["none", "full", "gentle"], default="none", help="accepted Q1 variant to carry")
    args = parser.parse_args()
    step = QL / args.step
    step.mkdir(parents=True, exist_ok=True)
    base = QL / "fp32-base" / "encoder_pw_fp32.onnx"
    g = onnx.load(str(base), load_external_data=False).graph
    inits = {i.name for i in g.initializer}
    matmuls = [n.name for n in g.node if n.op_type == "MatMul" and n.input[1] in inits]
    layers = sorted({layer_of(n) for n in matmuls if layer_of(n) is not None})
    edge = set(layers[:args.int8_edge] + layers[-args.int8_edge:]) if args.int8_edge else set()
    int8_mm = [n for n in matmuls if args.int8_edge and (layer_of(n) is None or layer_of(n) in edge)]
    nbits_mm = [n for n in matmuls if n not in int8_mm]
    convs = []
    if args.q1 != "none":
        for n in g.node:
            if n.op_type == "Conv":
                k = next(helper.get_attribute_value(a) for a in n.attribute if a.name == "kernel_shape")
                if args.q1 == "full" or all(v == 1 for v in k):
                    convs.append(n.name)
    print(f"{len(matmuls)} weight MatMuls in {len(layers)} blocks; INT8: {len(int8_mm)} (edge blocks {sorted(edge)}); "
          f"4-bit: {len(nbits_mm)}; INT8 Convs: {len(convs)}", flush=True)
    del g
    t0 = time.time()
    stage_a = base
    if int8_mm or convs:
        stage_a = step / "stage_a.onnx"
        if not stage_a.exists():
            quantize_dynamic(str(base), str(stage_a), op_types_to_quantize=["MatMul", "Conv"], nodes_to_quantize=int8_mm + convs,
                             per_channel=True, reduce_range=False, weight_type=QuantType.QInt8, use_external_data_format=True)
        print("stage A", round(time.time() - t0, 1), "s", flush=True)
    enc = step / "encoder.onnx"
    if not enc.exists():
        model = onnx.load(str(stage_a))
        q = MatMulNBitsQuantizer(model, bits=4, block_size=args.block, is_symmetric=False, accuracy_level=4,
                                 nodes_to_include=nbits_mm, op_types_to_quantize=("MatMul",))
        q.process()
        q.model.save_model_to_file(str(enc), use_external_data_format=True)
        del q, model
    print("stage B", round(time.time() - t0, 1), "s", flush=True)
    ops = op_histogram(enc)
    print({k: v for k, v in ops.items() if "MatMul" in k or "Conv" in k}, flush=True)
    build_package(enc, step / "package")
    heads = quantize_wrapper_heads(step / "package") if args.q1 != "none" else None
    manifest = package_manifest(step / "package")
    info = {"onnxruntime_quantizer": onnxruntime.__version__, "args": vars(args), "int8_matmuls": int8_mm,
            "nbits_matmul_count": len(nbits_mm), "int8_convs": convs, "wrapper_bytes": heads,
            "encoder_graph": {"bytes": enc.stat().st_size, "sha256": sha256(enc)}, "encoder_ops": ops,
            "package_total_bytes": manifest["total_bytes"]}
    (step / "build_info.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    print("package_total_bytes", manifest["total_bytes"])


if __name__ == "__main__":
    main()
