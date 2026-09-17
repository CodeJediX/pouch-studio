import * as THREE from 'three';
import { OrbitControls } from './vendor/OrbitControls.js';
import { GLTFLoader } from './vendor/GLTFLoader.js';
const host=document.querySelector('#canvas-host'),loading=document.querySelector('#loading');
let renderer;
try{renderer=new THREE.WebGLRenderer({antialias:true,alpha:true});}catch(e){loading.textContent='3D graphics are unavailable in this browser. Try a browser with WebGL enabled.';throw e;}
renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.35;host.appendChild(renderer.domElement);
const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(36,1,.001,20);
const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.enablePan=false;controls.minDistance=.24;controls.maxDistance=1.4;controls.autoRotateSpeed=1.2;
scene.add(new THREE.HemisphereLight(0xffffff,0x586273,2));
function light(c,i,p){const l=new THREE.DirectionalLight(c,i);l.position.set(...p);scene.add(l)}
light(0xffffff,3,[1,2,3]);light(0xcbdfff,1.5,[-2,0,2]);light(0xffffff,2,[1,1,-2]);
// Broad studio reflections make the metallic zipper legible without external assets.
const env=new THREE.Scene();env.background=new THREE.Color(0x9099a4);
for(const [p,s,intensity] of [[[0,3,1],[4,1,3],4],[[-3,1,1],[1,3,4],2],[[3,0,-1],[1,4,3],3]]){const o=new THREE.Mesh(new THREE.BoxGeometry(...s),new THREE.MeshBasicMaterial({color:new THREE.Color(intensity,intensity,intensity)}));o.position.set(...p);env.add(o)}
const pmrem=new THREE.PMREMGenerator(renderer);scene.environment=pmrem.fromScene(env,.08).texture;pmrem.dispose();
const views={default:{p:[.31,.14,.70],t:[0,0,0],label:'Three-quarter view'},front:{p:[0,0,.76],t:[0,0,0],label:'Front view'},back:{p:[0,0,-.76],t:[0,0,0],label:'Back view'},side:{p:[.70,.02,.03],t:[0,0,0],label:'Side profile'},detail:{p:[-.10,-.08,.28],t:[-.13,-.105,.01],label:'Crest detail'}};
function size(){const w=host.clientWidth,h=host.clientHeight;renderer.setSize(w,h);camera.aspect=w/h;camera.updateProjectionMatrix();}
function setView(key){const v=views[key],scale=key==='detail'?1:Math.max(1,.9/camera.aspect);camera.position.set(...v.p).multiplyScalar(scale);controls.target.set(...v.t);controls.update();document.querySelector('#view-label').textContent=v.label;document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===key));}
new ResizeObserver(size).observe(host);size();setView('default');
new GLTFLoader().load('./Laptop_Pouch.glb',g=>{scene.add(g.scene);g.scene.traverse(o=>{if(o.isMesh){o.material.envMapIntensity=.7;}});loading.hidden=true;},undefined,()=>{loading.textContent='The model could not load. Please refresh the page.';});
document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{stop();setView(b.dataset.view)});
const rotate=document.querySelector('#rotate');function stop(){controls.autoRotate=false;rotate.setAttribute('aria-pressed','false');rotate.firstElementChild.textContent='Start 360° rotation';}
rotate.onclick=()=>{controls.autoRotate=!controls.autoRotate;rotate.setAttribute('aria-pressed',String(controls.autoRotate));rotate.firstElementChild.textContent=controls.autoRotate?'Pause rotation':'Start 360° rotation';};
document.querySelector('#reset').onclick=()=>{stop();setView('default')};
controls.addEventListener('start',()=>{stop();document.querySelector('#view-label').textContent='Custom view';document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));});
document.querySelector('#fullscreen').onclick=async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await document.querySelector('.viewer').requestFullscreen();}catch{document.querySelector('#fullscreen').textContent='Fullscreen unavailable';}};
host.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','+','-','='].includes(e.key))return;e.preventDefault();stop();const offset=camera.position.clone().sub(controls.target),s=new THREE.Spherical().setFromVector3(offset);if(e.key==='ArrowLeft')s.theta-=.12;if(e.key==='ArrowRight')s.theta+=.12;if(e.key==='ArrowUp')s.phi=Math.max(.1,s.phi-.1);if(e.key==='ArrowDown')s.phi=Math.min(Math.PI-.1,s.phi+.1);if(['+','='].includes(e.key))s.radius=Math.max(.24,s.radius*.9);if(e.key==='-')s.radius=Math.min(1.4,s.radius*1.1);camera.position.copy(controls.target).add(new THREE.Vector3().setFromSpherical(s));controls.update();});
function frame(){requestAnimationFrame(frame);controls.update();renderer.render(scene,camera);}frame();
