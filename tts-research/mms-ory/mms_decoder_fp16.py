# Derives the Bengali MMS model shipped in app assets (decoder in fp16) from the fp32 vits-mms.py export:
#   python mms_decoder_fp16.py model.onnx model.fp16dec.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh
#   fp32 in  f8f7bf4f0b703f706e0531ff2b364bf9a3ebb8bed69262258f543e0212094e71 (114,065,046 B)
#   fp16 out 8a819bba1b0c424842b71f89d36987e278dde7e4e564e94af0b90a782a2fb90e (85,415,964 B)
# Tested with onnx 1.17.0 / Python 3.10 (D:/iTantra-tts-models/.venv-mms). On Git Bash set MSYS_NO_PATHCONV=1
# or "/dec/" is rewritten into a Windows path and nothing is converted ("region nodes: 0").
#
# Run a region of the graph in fp16: nodes whose name starts with PREFIX and whose op_type is in ALLOW.
# Float initializers/constants used by the region become fp16; Casts are inserted only where a float
# tensor crosses the region boundary. Everything outside the region stays fp32; graph I/O unchanged.
import onnx, sys, numpy as np
from onnx import helper, numpy_helper, TensorProto, shape_inference
src, dst, prefix, allow = sys.argv[1], sys.argv[2], sys.argv[3], set(sys.argv[4].split(","))
also_region = set(sys.argv[5].split(",")) if len(sys.argv) > 5 else set()   # extra node names forced into the region
m = onnx.load(src)
m = shape_inference.infer_shapes(m)
g = m.graph
ftype = {}
for vi in list(g.value_info) + list(g.input) + list(g.output):
    ftype[vi.name] = vi.type.tensor_type.elem_type
inits = {i.name: i for i in g.initializer}
for i in g.initializer: ftype[i.name] = i.data_type
region = [n for n in g.node if (n.name.startswith(prefix) and n.op_type in allow) or n.name in also_region]
rnames = {n.name for n in region}
produced_in_region = {o for n in region for o in n.output}
consumers_outside = set(i for n in g.node if n.name not in rnames for i in n.input) | {o.name for o in g.output}
cast_in, new_nodes, n16 = {}, [], {}
def fp16_name(t):
    return t + "__fp16"
out_nodes = []
for n in g.node:
    if n.name not in rnames:
        out_nodes.append(n); continue
    if n.op_type == "Constant":
        a = numpy_helper.to_array(n.attribute[0].t)
        if a.dtype == np.float32:
            n.attribute[0].t.CopyFrom(numpy_helper.from_array(a.astype(np.float16), n.attribute[0].t.name))
            ftype[n.output[0]] = TensorProto.FLOAT16
    for k, t in enumerate(n.input):
        if not t: continue
        if t in produced_in_region:
            if ftype.get(t) in (TensorProto.FLOAT, TensorProto.FLOAT16) and t in n16: n.input[k] = n16[t]
            continue
        if ftype.get(t) != TensorProto.FLOAT: continue
        if t in inits:
            nm = t + "__fp16init"
            if nm not in inits:
                nt = numpy_helper.from_array(numpy_helper.to_array(inits[t]).astype(np.float16), nm); g.initializer.append(nt); inits[nm] = nt
            n.input[k] = nm
        else:
            if t not in cast_in:
                cast_in[t] = fp16_name(t)
                out_nodes.append(helper.make_node("Cast", [t], [cast_in[t]], to=TensorProto.FLOAT16, name="cast16_" + t))
            n.input[k] = cast_in[t]
    tail = []
    for k, o in enumerate(n.output):
        if n.op_type != "Constant" and ftype.get(o, TensorProto.FLOAT) != TensorProto.FLOAT: continue
        if n.op_type == "Constant":
            n16[o] = o; continue
        n16[o] = fp16_name(o); n.output[k] = n16[o]
        if o in consumers_outside:
            tail.append(helper.make_node("Cast", [n16[o]], [o], to=TensorProto.FLOAT, name="cast32_" + o))
    out_nodes.append(n); out_nodes.extend(tail)
del g.node[:]; g.node.extend(out_nodes)
del g.value_info[:]
used = {i for n in g.node for i in n.input}
keep = [i for i in g.initializer if i.name in used]
del g.initializer[:]; g.initializer.extend(keep)
onnx.checker.check_model(m)
onnx.save(m, dst)
print("region nodes:", len(region), "boundary casts in:", len(cast_in), "->", dst)
