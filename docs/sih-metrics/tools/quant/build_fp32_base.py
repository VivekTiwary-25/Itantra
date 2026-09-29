#!/usr/bin/env python3
"""Step 0.4a: FP32 base = original model/assets/encoder.onnx (366 external FP32 tensors + positional
Constant) with the exact INT8-B pointwise Conv -> Transpose/MatMul/Add/Transpose rewrite, saved with
external data under quant-ladder/fp32-base/. No numeric change (the rewrite is exact)."""
import json
import time

import onnx

from ladder_lib import ASSETS, QL, op_histogram, pointwise_conv_to_matmul, sha256

OUT = QL / "fp32-base"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    t0 = time.time()
    m = onnx.load(str(ASSETS / "encoder.onnx"))
    n_rw = pointwise_conv_to_matmul(m)
    print("pointwise Conv -> MatMul rewrites:", n_rw, flush=True)
    onnx.save_model(m, str(OUT / "encoder_pw_fp32.onnx"), save_as_external_data=True, all_tensors_to_one_file=True,
                    location="encoder_pw_fp32.data", size_threshold=1024, convert_attribute=True)
    del m
    info = {"rewrites": n_rw, "seconds": round(time.time() - t0, 1),
            "files": {f: {"bytes": (OUT / f).stat().st_size, "sha256": sha256(OUT / f)}
                      for f in ("encoder_pw_fp32.onnx", "encoder_pw_fp32.data")},
            "ops": op_histogram(OUT / "encoder_pw_fp32.onnx")}
    (OUT / "build_info.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    print(json.dumps({k: info[k] for k in ("rewrites", "seconds", "files")}, indent=1))


if __name__ == "__main__":
    main()
