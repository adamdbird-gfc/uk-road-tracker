"""Original Roadprints line-vector masters and Android exports, art version 2.
No photographs, traced silhouettes, fonts or copied sculpture reliefs.
"""
import math
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
GOLD='#F7C450'
# Each master has a 48-unit viewport; all paths are original simplified symbols.
def circle(x,y,r):return f'M {x-r},{y} a {r},{r} 0 1,0 {2*r},0 a {r},{r} 0 1,0 {-2*r},0'
def line(x,y,X,Y):return f'M {x:g},{y:g} L {X:g},{Y:g}'
def radial(n,r1,r2):
 return ' '.join(line(24+math.sin(i*2*math.pi/n)*r1,24-math.cos(i*2*math.pi/n)*r1,24+math.sin(i*2*math.pi/n)*r2,24-math.cos(i*2*math.pi/n)*r2) for i in range(n))
TREE='M24,39 L24,12 M24,24 L15,16 L11,16 M24,20 L31,13 L37,13 M24,31 L34,24 L39,24 M24,29 L15,25 L10,25 M17,39 L31,39 M16,12 C10,5 6,15 15,17 M32,10 C39,4 43,13 36,16'
ANCHOR=circle(24,11,3)+' M24,14 L24,37 M15,21 L33,21 M11,28 C11,42 37,42 37,28 M11,28 L9,32 M11,28 L15,30 M37,28 L39,32 M37,28 L33,30'
COCKEREL='M13,37 L19,37 L21,29 C12,28 11,22 10,18 L18,23 C22,24 27,23 28,18 L28,12 L32,10 L35,14 L39,16 L34,18 C36,28 31,31 26,32 L26,38 L32,38 M30,10 L29,7 L32,8 L34,6 L35,11 M11,18 L9,12 L15,18 M32,15 L32.3,15'
TRACK='M12,37 C35,29 13,18 34,10 M19,39 C42,30 21,19 41,12 M14,33 L21,34 M16,28 L24,29 M17,22 L25,23 M19,17 L27,18 M25,12 L31,14'
BRIDGE='M7,29 L41,29 M7,35 L41,35 M11,29 L11,20 M37,29 L37,20 M11,21 C18,8 30,8 37,21 M16,18 L16,29 M24,14 L24,29 M32,18 L32,29 M7,38 L7,29 M41,38 L41,29'
COMPASS=circle(24,24,14)+' M24,8 L24,4 M40,24 L44,24 M24,40 L24,44 M8,24 L4,24 M29,16 L26,27 L17,32 L21,21 Z'
SEASONS='M24,8 L24,40 M8,24 L40,24 '+circle(15,15,4)+' M15,7 L15,9 M7,15 L9,15 M20,9 L22,7 M10,20 L8,22 M30,17 C29,10 40,8 39,10 C39,18 34,21 30,17 M31,17 L37,12 M12,33 C12,26 19,28 19,33 C19,40 12,40 12,33 M13,31 L18,37 M30,33 L39,33 M34.5,28 L34.5,38 M31,29 L38,37 M31,37 L38,29'
CONTINENTS=circle(24,24,18)+' M9,15 L14,10 L20,12 L21,17 L17,20 L16,25 L12,24 L11,19 Z M18,25 L23,27 L24,31 L21,39 L18,35 L18,30 L16,27 Z M26,11 L33,12 L39,17 L38,22 L33,21 L32,26 L29,27 L27,22 L24,20 L25,16 L29,16 Z M28,26 L32,28 L31,34 L28,36 L26,31 Z M35,32 L39,31 L41,35 L37,37 L34,35 Z'
AGES=''
for x,y,r in [(10,25,2),(22,19,3),(35,12,3)]:
 AGES+=circle(x,y,r)+f' M{x},{y+r} L{x},{39-r} M{x-4},{y+8} L{x},{y+5} L{x+4},{y+8} M{x-3},40 L{x},{39-r} L{x+3},40 '
AGES+='M40,25 L43,40 M6,43 L42,43'
ZODIAC=circle(24,24,17)+' '+radial(12,14,17)+' '+circle(24,24,6)
for i in range(12):
 x=24+math.sin(i*math.pi/6)*11;y=24-math.cos(i*math.pi/6)*11
 ZODIAC+=f' M{x-1:g},{y:g} L{x+1:g},{y:g} M{x:g},{y-1:g} L{x:g},{y+1:g}'
CENTURIES=radial(20,19,21)+' M17,11 L31,11 M17,37 L31,37 M18,11 C18,23 30,25 30,37 M30,11 C30,23 18,25 18,37 M20,17 L28,17 M20,33 L28,33 M24,23 L24,26'
MYSTERY='M24,6 L39,15 L39,33 L24,42 L9,33 L9,15 Z M24,13 L33,19 L33,29 L24,35 L15,29 L15,19 Z M24,13 L24,6 M33,19 L39,15 M33,29 L39,33 M24,35 L24,42 M15,29 L9,33 M15,19 L9,15 '+circle(24,23,3)+' M24,26 L24,30'
COLLECTIVE='M19,13 L29,13 M19,35 L29,35 M20,13 C20,23 28,25 28,35 M28,13 C28,23 20,25 20,35 '
for i in range(6):
 x=24+math.sin(i*math.pi/3)*18;y=24-math.cos(i*math.pi/3)*18
 COLLECTIVE+=circle(round(x,3),round(y,3),2.5)+' '
POSTS={
 'mills':'M21,43 L21,20 L9,12 L8,8 L15,8 L24,14 L33,8 L40,8 L39,12 L27,20 L27,43 M18,43 L30,43 M24,23 L24,37',
 'rowe':'M22,43 L22,7 L26,7 L26,43 M22,12 L11,12 L7,16 L11,19 L22,19 M26,23 L37,23 L41,27 L37,30 L26,30 M18,43 L30,43 M24,8 L24,4',
 'mccoll':'M21,43 L21,24 C13,22 11,16 9,11 L17,15 L21,13 L21,7 L25,10 L29,6 L31,12 L38,15 L32,19 L29,25 L27,43 M18,43 L30,43 M21,31 L27,31 M21,35 L27,35',
 'dudgeon':'M18,43 L18,13 C18,7 30,7 30,13 L30,43 M18,18 L30,18 M18,25 L30,25 M22,19 L22,24 M27,19 L27,24 M21,29 L27,32 L21,35 L27,38 M15,43 L33,43',
 'unknown':'M19,43 L19,12 L29,12 L29,43 M16,43 L32,43 M22,20 C22,16 29,17 26,22 L24,24 M24,28 L24,29'
}
def assets():
 result={}
 for name,shape in zip(('mills','rowe','mccoll','dudgeon','grimshaw','harrow'),(TREE,ANCHOR,COCKEREL,TRACK,BRIDGE,COMPASS)):
  result['milepost_medallion_'+name]=circle(24,24,22)+' '+shape
 result['milepost_collective']=COLLECTIVE
 for k,v in POSTS.items():result['milepost_design_'+k]=v
 for k,v in zip(('seasons','continents','ages','zodiac','centuries','mystery','harrow'),(SEASONS,CONTINENTS,AGES,ZODIAC,CENTURIES,MYSTERY,COMPASS)):
  result['milepost_theme_'+k]=v
 result['milepost_mystery']=circle(24,24,21)+' M19,18 C19,11 31,11 31,18 C31,23 24,23 24,29 M24,34 L24,35'
 result['milepost_ring_inner']=circle(24,24,17)+' '+radial(12,16,17)
 result['milepost_ring_outer']=circle(24,24,22)+' '+radial(24,21,22)
 result['milepost_card_layer']='M5,3 L43,3 Q45,3 45,5 L45,43 Q45,45 43,45 L5,45 Q3,45 3,43 L3,5 Q3,3 5,3 Z'
 return result

def generate():
 draw=ROOT/'android-prototype/app/src/main/res/drawable'; masters=ROOT/'docs/millennium-mileposts/artwork-v2';masters.mkdir(exist_ok=True)
 manifest=[]
 for name,path in assets().items():
  svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><path d="{path}" fill="none" stroke="{GOLD}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>\n'
  xml=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48"><path android:pathData="{path}" android:fillColor="@android:color/transparent" android:strokeColor="{GOLD}" android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round"/></vector>\n'
  (masters/(name+'.svg')).write_text(svg);(draw/(name+'.xml')).write_text(xml)
  manifest.append(dict(name=name,svg_sha256=hashlib.sha256(svg.encode()).hexdigest(),android_sha256=hashlib.sha256(xml.encode()).hexdigest()))
 (masters/'manifest.json').write_text(json.dumps(dict(artwork_version=2,creator='Original Roadprints project artwork',rights='Project-authored line geometry; no third-party images incorporated',assets=manifest),indent=2)+'\n')
 print('Generated',len(manifest),'vector masters and Android drawables')
if __name__=='__main__':generate()
