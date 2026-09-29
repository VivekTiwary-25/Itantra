#!/usr/bin/env python3
"""Step 0.4b: re-apply the INT8-B recipe to the rebuilt FP32 base and package it with adapter_builder.py.
Recipe (quantization/quantize_ctc.py variant B): ORT quantize_dynamic, op_types ['MatMul'], per-channel,
symmetric INT8 weights, reduce_range=False, no calibration, on the pointwise-rewritten FP32 encoder."""
import json
import time

import onnxruntime
from onnxruntime.quantization import QuantType, quantize_dynamic

from ladder_lib import QL, build_package, op_histogram, sha256

STEP = QL / "int8b-rebuild"


def main() -> None:
    STEP.mkdir(parents=True, exist_ok=True)
    enc = STEP / "encoder_int8b.onnx"
    t0 = time.time()
    if not enc.exists():
        quantize_dynamic(str(QL / "fp32-base" / "encoder_pw_fp32.onnx"), str(enc), op_types_to_quantize=["MatMul"],
                         per_channel=True, reduce_range=False, weight_type=QuantType.QInt8, use_external_data_format=False)
    print("quantized", round(time.time() - t0, 1), "s", flush=True)
    manifest = build_package(enc, STEP / "package")
    info = {"onnxruntime_quantizer": onnxruntime.__version__, "encoder": {"bytes": enc.stat().st_size, "sha256": sha256(enc)},
            "ops": op_histogram(enc), "package_total_bytes": manifest["total_bytes"]}
    (STEP / "build_info.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    print(json.dumps({k: info[k] for k in ("encoder", "package_total_bytes")}, indent=1))


if __name__ == "__main__":
    main()
