# Mixed precision: run selected Conv nodes in fp16 (weights/bias stored fp16), Cast activations in/out.
# Everything else (ConvTranspose, flow, encoder, duration predictor, elementwise) stays fp32.
import onnx, sys, numpy as np
from onnx import helper, numpy_helper, TensorProto
src, dst, scope = sys.argv[1], sys.argv[2], sys.argv[3]   # scope: "dec" = decoder Convs only, "all" = every Conv
m = onnx.load(src); g = m.graph
inits = {i.name: i for i in g.initializer}
new_nodes, n_conv = [], 0
for n in g.node:
    ok = n.op_type == "Conv" and n.input[1] in inits and (scope == "all" or n.name.startswith("/dec/"))
    if not ok: new_nodes.append(n); continue
    n_conv += 1
    for k in range(1, len(n.input)):
        t = inits[n.input[k]]
        if t.data_type == TensorProto.FLOAT:
            a = numpy_helper.to_array(t).astype(np.float16)
            nt = numpy_helper.from_array(a, n.input[k] + "_fp16"); g.initializer.append(nt); inits[nt.name] = nt
            n.input[k] = nt.name
    x16 = n.input[0] + "_to16_" + str(n_conv)
    new_nodes.append(helper.make_node("Cast", [n.input[0]], [x16], to=TensorProto.FLOAT16, name=n.name + "_cast_in"))
    y32 = n.output[0]; y16 = y32 + "_fp16"
    n.input[0] = x16; n.output[0] = y16
    new_nodes.append(n)
    new_nodes.append(helper.make_node("Cast", [y16], [y32], to=TensorProto.FLOAT, name=n.name + "_cast_out"))
del g.node[:]; g.node.extend(new_nodes)
# drop now-unused fp32 initializers
used = {i for n in g.node for i in n.input}
keep = [i for i in g.initializer if i.name in used]
del g.initializer[:]; g.initializer.extend(keep)
onnx.checker.check_model(m)
onnx.save(m, dst)
print("converted", n_conv, "Conv nodes ->", dst)
