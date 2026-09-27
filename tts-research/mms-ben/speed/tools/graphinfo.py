import onnx, sys, collections
from onnx import numpy_helper
for p in sys.argv[1:]:
    m = onnx.load(p, load_external_data=True)
    g = m.graph
    ops = collections.Counter(n.op_type for n in g.node)
    inits = {i.name: i for i in g.initializer}
    params = sum(numpy_helper.to_array(i).size for i in g.initializer)
    print("==", p.split('/')[-1], "opset", [ (o.domain,o.version) for o in m.opset_import], "params %.2fM"%(params/1e6))
    print(" inputs", [(i.name,[d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim]) for i in g.input])
    print(" meta", {p.key:p.value[:60] for p in m.metadata_props})
    print(" ops", dict(ops.most_common(25)))
    ks = collections.Counter()
    for n in g.node:
        if n.op_type in ("Conv","ConvTranspose"):
            w = inits.get(n.input[1])
            if w is not None:
                s = tuple(w.dims); ks[(n.op_type,)+s]+=1
    big = sorted(ks.items(), key=lambda kv: -kv[0][1]*kv[0][2]*kv[0][3]*kv[1])[:12]
    for k,v in big: print("   ",k,"x",v)
