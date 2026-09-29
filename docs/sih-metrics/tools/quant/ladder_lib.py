"""Shared helpers for the IndicConformer quantisation ladder (desktop, non-destructive).

All inputs under .indicconformer-600m/ are read-only; every output goes to
.indicconformer-600m/quant-ladder/<step-name>/.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path

import numpy as np
import onnx
from onnx import helper, numpy_helper

IC = Path(r"D:\projects\SIH\iTantra\.indicconformer-600m")
QL = IC / "quant-ladder"
ASSETS = IC / "model" / "assets"
LANGS = ["hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn"]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 24), b""):
            h.update(block)
    return h.hexdigest()


def pointwise_conv_to_matmul(model: onnx.ModelProto) -> int:
    """Exact INT8-B rewrite, copied from quantization/quantize_ctc.py: every pointwise Conv
    (kernel 1, group 1, no pad/stride/dilation, initializer weight) becomes
    Transpose -> MatMul -> Add -> Transpose. Returns the number of rewritten Convs."""
    g = model.graph
    init = {i.name: i for i in g.initializer}
    new_nodes, drop_init, n_rw = [], set(), 0
    for n in g.node:
        a = {x.name: helper.get_attribute_value(x) for x in n.attribute}
        w = init.get(n.input[1]) if n.op_type == "Conv" and len(n.input) > 1 else None
        if (w is not None and len(w.dims) == 3 and w.dims[2] == 1 and a.get("group", 1) == 1
                and list(a.get("kernel_shape", [1])) == [1] and all(v == 0 for v in a.get("pads", [0, 0]))
                and list(a.get("strides", [1])) == [1] and list(a.get("dilations", [1])) == [1]):
            W = numpy_helper.to_array(w)[:, :, 0]
            wname = n.name + "_pw_matmul_w"
            g.initializer.append(numpy_helper.from_array(np.ascontiguousarray(W.T), wname))
            drop_init.add(w.name)
            tin, mm = n.name + "_pw_tin", n.name + "_pw_mm"
            new_nodes.append(helper.make_node("Transpose", [n.input[0]], [tin], perm=[0, 2, 1], name=n.name + "_pw_t_in"))
            new_nodes.append(helper.make_node("MatMul", [tin, wname], [mm], name=n.name + "_pw_matmul"))
            last = mm
            if len(n.input) > 2 and n.input[2]:
                new_nodes.append(helper.make_node("Add", [mm, n.input[2]], [mm + "_b"], name=n.name + "_pw_bias"))
                last = mm + "_b"
            new_nodes.append(helper.make_node("Transpose", [last], [n.output[0]], perm=[0, 2, 1], name=n.name + "_pw_t_out"))
            n_rw += 1
        else:
            new_nodes.append(n)
    del g.node[:]
    g.node.extend(new_nodes)
    keep = [i for i in g.initializer if i.name not in drop_init]
    del g.initializer[:]
    g.initializer.extend(keep)
    return n_rw


def _adapter_builder():
    import sys
    spec = importlib.util.spec_from_file_location("adapter_builder", IC / "multilingual-adapters" / "adapter_builder.py")
    mod = importlib.util.module_from_spec(spec)
    previous, sys.dont_write_bytecode = sys.dont_write_bytecode, True   # no __pycache__ inside multilingual-adapters/
    try:
        spec.loader.exec_module(mod)
    finally:
        sys.dont_write_bytecode = previous
    return mod


def build_package(encoder: Path, out: Path) -> dict:
    """Run the unmodified multilingual-adapters/adapter_builder.py with its SRC_ENC/OUT globals pointed at
    `encoder` and `out`, then record size + SHA-256 of every deployable file."""
    out.mkdir(parents=True, exist_ok=True)
    ab = _adapter_builder()
    ab.SRC_ENC, ab.OUT = str(encoder), str(out)
    ab.main()
    return package_manifest(out)


def deploy_files(pkg: Path) -> list[Path]:
    files = [pkg / "shared" / "encoder.weights.bin"]
    for lang in LANGS:
        files += [pkg / f"{lang}.onnx", pkg / "languages" / lang / "tokens.txt"]
    return files


def package_manifest(pkg: Path, record_dir: Path | None = None) -> dict:
    files = deploy_files(pkg)
    entries = [{"file": f.relative_to(pkg).as_posix(), "bytes": f.stat().st_size, "sha256": sha256(f)} for f in files]
    manifest = {"package": str(pkg), "total_bytes": sum(e["bytes"] for e in entries),
                "definition": "shared/encoder.weights.bin + 9 x <lang>.onnx + 9 x languages/<lang>/tokens.txt",
                "files": entries}
    record_dir = record_dir or pkg
    if QL not in [record_dir, *record_dir.parents]:
        raise RuntimeError(f"refusing to write outside quant-ladder: {record_dir}")
    record_dir.mkdir(parents=True, exist_ok=True)
    (record_dir / "package_manifest.json").write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    return manifest


def op_histogram(path: Path) -> dict:
    m = onnx.load(str(path), load_external_data=False)
    hist: dict[str, int] = {}
    for n in m.graph.node:
        key = (n.domain + "::" if n.domain else "") + n.op_type
        hist[key] = hist.get(key, 0) + 1
    return dict(sorted(hist.items(), key=lambda kv: -kv[1]))


def rss_note() -> str:
    try:
        import psutil
        vm = psutil.virtual_memory()
        return f"free RAM {vm.available / 1e9:.1f} GB of {vm.total / 1e9:.1f} GB"
    except Exception:  # pragma: no cover
        return "psutil unavailable"


if __name__ == "__main__":
    print(os.getcwd(), rss_note())


def quantize_wrapper_heads(pkg: Path) -> dict:
    """Q1 CTC head: in every <lang>.onnx wrapper replace ctc_head_conv(1x1 Conv 1024->257) + Transpose with
    Transpose -> DynamicQuantizeLinear -> MatMulInteger(int8 per-channel symmetric weight) -> Cast -> Mul(scale)
    -> Add(bias), i.e. the same dynamic scheme ORT quantize_dynamic gives the 265 encoder MatMuls in INT8-B.
    Done by hand because running the quantizer over a wrapper would inline the shared external weights.
    The shared weights file is not touched (load_external_data=False)."""
    from onnx import TensorProto
    report = {}
    for lang in LANGS:
        path = pkg / f"{lang}.onnx"
        m = onnx.load(str(path), load_external_data=False)
        g = m.graph
        init = {i.name: i for i in g.initializer}
        W = numpy_helper.to_array(init["ctc_head_W"])[:, :, 0].T.astype(np.float32)   # (1024, 257)
        b = numpy_helper.to_array(init["ctc_head_b"]).astype(np.float32)
        w_scale = (np.abs(W).max(axis=0) / 127.0).astype(np.float32)
        w_scale[w_scale == 0] = 1.0
        Wq = np.clip(np.rint(W / w_scale), -127, 127).astype(np.int8)
        keep = [i for i in g.initializer if i.name not in ("ctc_head_W",)]
        del g.initializer[:]
        g.initializer.extend(keep)
        g.initializer.extend([numpy_helper.from_array(Wq, "ctc_head_Wq"),
                              numpy_helper.from_array(w_scale, "ctc_head_W_scale"),
                              numpy_helper.from_array(np.zeros(W.shape[1], np.int8), "ctc_head_W_zp")])
        nodes = [n for n in g.node if n.name not in ("ctc_head_conv", "ctc_head_transpose")]
        mk = helper.make_node
        head = [mk("Transpose", ["outputs"], ["ctc_head_x"], perm=[0, 2, 1], name="ctc_head_t_in"),
                mk("DynamicQuantizeLinear", ["ctc_head_x"], ["ctc_head_xq", "ctc_head_xs", "ctc_head_xzp"], name="ctc_head_dql"),
                mk("MatMulInteger", ["ctc_head_xq", "ctc_head_Wq", "ctc_head_xzp", "ctc_head_W_zp"], ["ctc_head_i32"], name="ctc_head_mmi"),
                mk("Cast", ["ctc_head_i32"], ["ctc_head_f"], to=TensorProto.FLOAT, name="ctc_head_cast"),
                mk("Mul", ["ctc_head_xs", "ctc_head_W_scale"], ["ctc_head_scale"], name="ctc_head_scale_mul"),
                mk("Mul", ["ctc_head_f", "ctc_head_scale"], ["ctc_head_mm"], name="ctc_head_mul"),
                mk("Add", ["ctc_head_mm", "ctc_head_b"], ["ctc_head_logits"], name="ctc_head_bias")]
        idx = next(i for i, n in enumerate(nodes) if n.name == "ctc_head_logsoftmax")
        del g.node[:]
        g.node.extend(nodes[:idx] + head + nodes[idx:])
        onnx.save(m, str(path))
        report[lang] = path.stat().st_size
    return report
