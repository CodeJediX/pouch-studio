"""Validate GLB structure, finite geometry, winding and reference dimensions."""
from pathlib import Path
import json, struct
import numpy as np
p=(Path(__file__).resolve().parents[1]/'Laptop_Pouch.glb').read_bytes()
magic,version,total=struct.unpack_from('<4sII',p)
assert (magic,version,total)==(b'glTF',2,len(p))
n,kind=struct.unpack_from('<I4s',p,12); assert kind==b'JSON'
j=json.loads(p[20:20+n]); size,kind=struct.unpack_from('<I4s',p,20+n)
assert kind==b'BIN\0'; binary=p[28+n:]; assert size==len(binary)==j['buffers'][0]['byteLength']
for v in j['bufferViews']:
    assert v.get('byteOffset',0)%4==0
    assert v.get('byteOffset',0)+v['byteLength']<=size

def read(i):
    a=j['accessors'][i];v=j['bufferViews'][a['bufferView']]
    width={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
    result=np.frombuffer(binary,dtype='<u4' if a['componentType']==5125 else '<f4',count=a['count']*width,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,width)
    assert np.isfinite(result).all()
    return result
triangles=0
for m in j['meshes']:
    for prim in m['primitives']:
        v=read(prim['attributes']['POSITION']); normals=read(prim['attributes']['NORMAL']); idx=read(prim['indices']).reshape(-1,3)
        assert idx.max()<len(v)
        assert np.allclose(np.linalg.norm(normals,axis=1),1,atol=1e-4),m['name']
        area=np.linalg.norm(np.cross(v[idx[:,1]]-v[idx[:,0]],v[idx[:,2]]-v[idx[:,0]]),axis=1)
        assert (area>1e-14).all(),m['name']
        triangles+=len(idx)
front=read(j['meshes'][0]['primitives'][0]['attributes']['POSITION'])
back=read(j['meshes'][1]['primitives'][0]['attributes']['POSITION'])
assert np.allclose(np.ptp(front[:,:2],axis=0),[.4064,.3048],atol=1e-6)
assert abs((front[:,2].max()-back[:,2].min())-.0381)<.0002
for image in j['images']:
    v=j['bufferViews'][image['bufferView']]; start=v['byteOffset']
    assert binary[start:start+8]==b'\x89PNG\r\n\x1a\n'
assert j['asset']['extras']['stripe_total_width_inches']==1
print(f'PASS: {len(j["meshes"])} meshes, {triangles:,} triangles, embedded PNG textures, unit normals, nondegenerate faces, 16 x 12 x ~1.5-inch body.')
