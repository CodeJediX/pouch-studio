"""Rebuild the metric pouch GLB from the supplied reference images.
Requires Python 3.11+, numpy and Pillow. Run from any working directory.
"""
from pathlib import Path
import io, json, struct
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
REF = ROOT / 'references'
INCH = .0254
W, H, D = 16*INCH, 12*INCH, 1.5*INCH
R = .014
TW, TH = 2048, 1536
PPIN = TW/16

# Sample the photographed textile, removing broad baked-in lighting before tiling.
sample = ImageOps.grayscale(Image.open(REF/'fabric-and-seam.png').crop((390,530,646,786)))
a = np.asarray(sample, dtype=float)
low = np.asarray(sample.filter(ImageFilter.GaussianBlur(3)), dtype=float)
fiber = np.clip(30+(a-low)*.45, 17, 44).astype(np.uint8)
tile = np.tile(fiber, (6,8)).astype(float)
yy,xx = np.indices((TH,TW))
weave = 1.4*np.sin(xx*np.pi)+1.6*np.cos((xx+yy)*np.pi/2)
base = np.clip(tile+weave, 15, 46).astype(np.uint8)
cloth = np.stack([base*.94,base*.97,base],axis=-1).astype(np.uint8)
colors = [(0,184,235),(255,133,14),(15,28,150)]

def panel_texture(front):
    arr = cloth.copy()
    # One inch TOTAL, split equally into cyan / orange / royal blue.
    start = round((16-.7-1)*PPIN)
    for k,color in enumerate(colors):
        left,right = start+round(k*PPIN/3),start+round((k+1)*PPIN/3)
        arr[:,left:right] = np.clip(np.array(color)[None,None,:]*(.95+(base[:,left:right,None].astype(float)-30)/190),0,255)
    im = Image.fromarray(arr)
    if front:
        # Extract only the supplied crest, excluding red measurement annotations.
        crest = np.array(Image.open(REF/'measurements.png').convert('RGB').crop((70,1115,430,1415)))
        cy,cx = np.indices(crest.shape[:2])
        red = (crest[:,:,0].astype(float)>crest[:,:,1]*4.)&(crest[:,:,0].astype(float)>crest[:,:,2]*2.)&(crest[:,:,0]>20)
        annotation = red & ((cx<43)|(cy<12)|((cx>265)&(cy<45)))
        alpha = np.clip((crest.max(axis=2).astype(float)-12)*4,0,255).astype(np.uint8)
        alpha[annotation] = 0
        logo = Image.fromarray(np.dstack([crest,alpha])).resize((round(3*PPIN),round(2.3*PPIN)),Image.Resampling.LANCZOS)
        im.paste(logo,(round(.65*PPIN),round((12-.65-2.3)*PPIN)),logo)
        text = Image.new('RGBA',(1100,190))
        draw = ImageDraw.Draw(text)
        font_path = Path('C:/Windows/Fonts/arial.ttf') if Path('C:/Windows/Fonts/arial.ttf').exists() else Path('/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf')
        font = ImageFont.truetype(str(font_path),62)
        lines = ['General Sir John Kotelawala','Defence University']
        for row,line in enumerate(lines): draw.text((0,row*83),line,font=font,fill=(237,239,242,255),stroke_width=0)
        box = text.getbbox(); text = text.crop(box)
        text = text.resize((round(4.5*PPIN),round(text.height*4.5*PPIN/text.width)),Image.Resampling.LANCZOS)
        im.paste(text,(round(3.95*PPIN),round((12-.92)*PPIN)-text.height),text)
    return im

# Tangent-space micro-normal map, reused over each face.
f = np.tile(fiber,(6,8)).astype(float)/255
ny,nx = np.gradient(f)
normal = np.stack([-nx*1.4,ny*1.4,np.ones_like(f)],-1)
normal /= np.linalg.norm(normal,axis=-1,keepdims=True)
normal_image = Image.fromarray(np.uint8(np.clip((normal*.5+.5)*255,0,255)))

j = {'asset':{'version':'2.0','generator':'Pouch Studio reference reconstruction','extras':{
    'dimensions_inches':[16,12,1.5],'depth_is_approximate':True,
    'stripe_total_width_inches':1,'crest_inches':[3,2.3],'text_width_inches':4.5,
    'reference':'Revised character sheet controls design; photographs control textile and construction details.'}},
    'scene':0,'scenes':[{'nodes':[]}],'nodes':[],'meshes':[],'materials':[],
    'buffers':[],'bufferViews':[],'accessors':[],'images':[],'textures':[],
    'samplers':[{'magFilter':9729,'minFilter':9987,'wrapS':33071,'wrapT':33071}]}
binary = bytearray()
def buffer(data,target=None):
    while len(binary)%4: binary.append(0)
    view={'buffer':0,'byteOffset':len(binary),'byteLength':len(data)}
    if target: view['target']=target
    j['bufferViews'].append(view); binary.extend(data)
    return len(j['bufferViews'])-1

def texture(im):
    stream=io.BytesIO(); im.save(stream,format='PNG')
    j['images'].append({'bufferView':buffer(stream.getvalue()),'mimeType':'image/png'})
    j['textures'].append({'source':len(j['images'])-1,'sampler':0})
    return len(j['textures'])-1
front_tex,back_tex,norm_tex = texture(panel_texture(True)),texture(panel_texture(False)),texture(normal_image)
for name,tex in [('Front woven artwork',front_tex),('Back woven tricolor',back_tex)]:
    j['materials'].append({'name':name,'pbrMetallicRoughness':{'baseColorTexture':{'index':tex},'metallicFactor':0,'roughnessFactor':.86},'normalTexture':{'index':norm_tex,'scale':.65}})
for name,color,rough,metal in [('Black textile',[.012,.014,.018,1],.9,0),('Bound black edges',[.008,.009,.012,1],.85,0),('Graphite stitching',[.03,.033,.038,1],.93,0),('Silver zipper hardware',[.62,.66,.7,1],.24,1),('Dark zipper coil',[.035,.038,.043,1],.6,.15)]:
    j['materials'].append({'name':name,'pbrMetallicRoughness':{'baseColorFactor':color,'roughnessFactor':rough,'metallicFactor':metal}})

def accessor(a,kind):
    a=np.asarray(a,dtype='<u4' if kind=='SCALAR' else '<f4')
    ac={'bufferView':buffer(a.tobytes(),34963 if kind=='SCALAR' else 34962),'componentType':5125 if kind=='SCALAR' else 5126,'count':len(a),'type':kind}
    if kind=='VEC3': ac.update(min=a.min(0).tolist(),max=a.max(0).tolist())
    j['accessors'].append(ac); return len(j['accessors'])-1

def mesh(name,verts,faces,material,uv=None):
    v=np.array(verts,dtype=float); faces=np.array(faces,dtype=np.uint32).reshape(-1,3)
    normals=np.zeros_like(v)
    fn=np.cross(v[faces[:,1]]-v[faces[:,0]],v[faces[:,2]]-v[faces[:,0]])
    for k in range(3): np.add.at(normals,faces[:,k],fn)
    normals/=np.maximum(np.linalg.norm(normals,axis=1,keepdims=True),1e-12)
    attr={'POSITION':accessor(v,'VEC3'),'NORMAL':accessor(normals,'VEC3')}
    if uv is not None: attr['TEXCOORD_0']=accessor(uv,'VEC2')
    j['meshes'].append({'name':name,'primitives':[{'attributes':attr,'indices':accessor(faces.flatten(),'SCALAR'),'material':material}]})
    j['nodes'].append({'name':name,'mesh':len(j['meshes'])-1}); j['scenes'][0]['nodes'].append(len(j['nodes'])-1)

def depth(x,y):
    u,v=x/(W/2),y/(H/2)
    edge=max(0,1-u*u)*max(0,1-v*v)
    cushion=.0045+(D/2-.0045)*edge**.48
    folds=.0013*np.sin(36*u+9*v)*np.exp(-((abs(u)-.86)/.17)**2)*max(0,1-v*v)
    folds+=.0008*np.sin(28*u-7*v)*np.exp(-((abs(v)-.82)/.18)**2)*max(0,1-u*u)
    return cushion+folds

# Dense rounded rectangular panels, with shallow textile puckering near the binding.
N,M=128,96
for side in [1,-1]:
    verts=[]; uv=[]; faces=[]
    for row in range(M+1):
        y=-H/2+H*row/M
        inset=R-np.sqrt(max(0,R*R-max(0,abs(y)-(H/2-R))**2))
        half=W/2-inset
        for col in range(N+1):
            x=-half+2*half*col/N
            verts.append([x,y,side*depth(x,y)])
            uv.append([.5+side*x/W,.5-y/H])
    for row in range(M):
        for col in range(N):
            a=row*(N+1)+col; b=a+1; c=a+N+1; d=c+1
            faces.extend([[a,b,d],[a,d,c]] if side==1 else [[a,d,b],[a,c,d]])
    mesh('Front padded woven panel' if side==1 else 'Back padded woven panel',verts,faces,0 if side==1 else 1,uv)

def tube(name,points,radius,material,segments=8,closed=False):
    pts=np.array(points); verts=[]; faces=[]
    for i,p in enumerate(pts):
        tangent=pts[(i+1)%len(pts)]-pts[i-1] if closed else pts[min(i+1,len(pts)-1)]-pts[max(0,i-1)]
        tangent/=np.linalg.norm(tangent)
        ref=np.array([0.,0.,1.]) if abs(tangent[2])<.9 else np.array([0.,1.,0.])
        a=np.cross(tangent,ref); a/=np.linalg.norm(a); b=np.cross(tangent,a)
        for t in np.arange(segments)*2*np.pi/segments: verts.append(p+radius*(np.cos(t)*a+np.sin(t)*b))
    for i in range(len(pts) if closed else len(pts)-1):
        for k in range(segments):
            a=i*segments+k; b=i*segments+(k+1)%segments; c=((i+1)%len(pts))*segments+k; d=((i+1)%len(pts))*segments+(k+1)%segments
            faces.extend([[a,b,d],[a,d,c]])
    mesh(name,verts,faces,material)

def perimeter(z):
    p=[]
    for cx,cy,start in [(W/2-R,H/2-R,0),(-W/2+R,H/2-R,90),(-W/2+R,-H/2+R,180),(W/2-R,-H/2+R,270)]:
        for a in np.linspace(start,start+90,25):
            t=np.deg2rad(a); p.append([cx+R*np.cos(t),cy+R*np.sin(t),z])
    return p
p=perimeter(0)
verts=[[x,y,z] for z in [-.0045,.0045] for x,y,_ in p]; faces=[]
for i in range(len(p)):
    k=(i+1)%len(p); faces.extend([[i,k,k+len(p)],[i,k+len(p),i+len(p)]])
mesh('Slim perimeter gusset',verts,faces,2)
for s in [-1,1]:
    tube('Bound perimeter',perimeter(s*.0045),.0008,3,closed=True)
# Front panel construction seam is present on the revised sheet; back remains clean.
seam_y=.066
xs=np.linspace(-W/2+.001,W/2-.001,180)
tube('Front horizontal panel seam',[[x,seam_y,depth(x,seam_y)+.00035] for x in xs],.00043,3)
for i,x in enumerate(np.arange(-W/2+.005,W/2-.004,.003)):
    tube('Front seam stitch %03d'%i,[[q,seam_y-.0013,depth(q,seam_y-.0013)+.00035] for q in [x,x+.0014]],.00013,4,segments=5)
# Full-width curved zipper, two interlocking dark coil rows and textile tape.
zip_x=np.linspace(-W/2+.006,W/2-.006,200)
def zip_y(x): return H/2+.0007-.003*(abs(x)/(W/2))**14
for z in [-.0025,.0025]: tube('Woven zipper tape',[[x,zip_y(x),z] for x in zip_x],.0017,2)
for i,x in enumerate(np.arange(-W/2+.008,W/2-.008,.0021)):
    for s in [-1,1]:
        q=x+(.001 if s==1 else 0)
        tube('Interlocking zipper coil %d %d'%(i,s),[[q,zip_y(q)+.001,-.0002*s],[q+.0004,zip_y(q)+.0014,.0012*s]],.00042,6,segments=6)

def box(name,center,size,material):
    c=np.array(center); h=np.array(size)/2
    verts=[c+h*np.array(p) for p in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    faces=[[0,2,1],[0,3,2],[4,5,6],[4,6,7],[0,1,5],[0,5,4],[3,7,6],[3,6,2],[0,4,7],[0,7,3],[1,2,6],[1,6,5]]
    mesh(name,verts,faces,material)
# Sheet places the silver slider at the upper right with a suspended woven pull.
x=W/2-.009; y=H/2+.001
box('Silver zipper slider',[x,y,0],[.011,.0035,.0065],5)
loop=[[x+.003+.004*np.cos(t),y+.003+.004*np.sin(t),.001] for t in np.linspace(0,2*np.pi,40,endpoint=False)]
tube('Silver pull attachment ring',loop,.00075,5,closed=True)
handle=[[x+.005,y+.003,0],[x+.013,y-.001,.002],[x+.019,y-.009,.004],[x+.014,y-.013,.004],[x+.007,y-.003,.002]]
tube('Open silver zipper pull',handle,.00085,5,closed=True)
# Diagonal fabric tab built as a thick ribbon.
center=np.array([x+.022,y-.022,.004]); axis=np.array([.5,-.866,0]); across=np.array([.866,.5,0])
v=[]
for z in [-.00065,.00065]:
    for a,b in [(-1,-1),(1,-1),(1,1),(-1,1)]: v.append(center+axis*a*.013+across*b*.0038+[0,0,z])
mesh('Black woven zipper pull extension',v,[[0,2,1],[0,3,2],[4,5,6],[4,6,7],[0,1,5],[0,5,4],[3,7,6],[3,6,2],[0,4,7],[0,7,3],[1,2,6],[1,6,5]],2)
box('Left zipper end fabric tab',[-W/2-.003,H/2-.011,0],[.008,.022,.0025],2)

while len(binary)%4: binary.append(0)
j['buffers']=[{'byteLength':len(binary)}]
data=json.dumps(j,separators=(',',':')).encode(); data+=b' '*((-len(data))%4)
output=struct.pack('<4sII',b'glTF',2,12+8+len(data)+8+len(binary))+struct.pack('<I4s',len(data),b'JSON')+data+struct.pack('<I4s',len(binary),b'BIN\0')+binary
(ROOT/'Laptop_Pouch.glb').write_bytes(output)
print(f'Built {len(output):,} bytes, {len(j["meshes"])} meshes; body {W:.4f} x {H:.4f} x {D:.4f} m')
