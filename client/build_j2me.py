#!/usr/bin/env python3
"""Experimental CLDC1.1 bytecode converter: Java 8 StackMapTable -> CLDC StackMap.
Assumes javac --release 8, and source restricted to CLDC APIs.
Unlike a full preverifier, this does NOT do independent dataflow verification.
Device compatibility MUST be tested; for releases prefer Oracle WTK preverify.
"""
import struct, zipfile, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'build'
SRC=ROOT/'src'
STUB=ROOT/'lib-stubs/midp2-compile-only.jar'

def u1(n):return bytes([n])
def u2(n):return struct.pack('>H',n)
def u4(n):return struct.pack('>I',n)
class Reader:
 def __init__(self,b):self.b=b;self.i=0
 def read(self,n):v=self.b[self.i:self.i+n];assert len(v)==n;self.i+=n;return v
 def U1(self):return self.read(1)[0]
 def U2(self):return struct.unpack('>H',self.read(2))[0]
 def U4(self):return struct.unpack('>I',self.read(4))[0]

def desc_locals(desc,access,this_class,is_init,cp,clsname):
 out=[]
 if not access&8:out.append(b'\x06' if is_init else b'\x07'+u2(this_class))
 p=1
 while desc[p]!=')':
  ch=desc[p]
  if ch=='[':
   begin=p
   while desc[p]=='[':p+=1
   if desc[p]=='L':p=desc.index(';',p)+1
   else:p+=1
   cls=desc[begin:p]
   idx=clsname(cls);out.append(b'\x07'+u2(idx))
  elif ch=='L':
   end=desc.index(';',p);idx=clsname(desc[p+1:end]);out.append(b'\x07'+u2(idx));p=end+1
  else:
   out.append(bytes([{'F':2,'D':3,'J':4}.get(ch,1)]));p+=1
 return out

def rewrite(raw):
 r=Reader(raw);assert r.U4()==0xCAFEBABE;minor=r.U2();major=r.U2();assert major==52,(minor,major)
 n=r.U2();cp=[None]*n;rawcp=[None]*n;i=1
 while i<n:
  st=r.i;tag=r.U1()
  if tag==1:
   ln=r.U2();s=r.read(ln).decode('utf-8');cp[i]=s
  elif tag in (7,8,16):cp[i]=r.U2()
  elif tag in (9,10,11,12,18):cp[i]=(r.U2(),r.U2())
  elif tag in (3,4):cp[i]=r.read(4)
  elif tag in (5,6):cp[i]=r.read(8);i+=1
  elif tag==15:cp[i]=(r.U1(),r.U2())
  else:raise Exception('Unknown const pool tag '+str(tag))
  rawcp[i if tag not in (5,6) else i-1]=raw[st:r.i];i+=1
 cpbytes=[]
 for j in range(1,n):
  b=rawcp[j]
  if b is None:continue
  if isinstance(cp[j],str) and 'java/lang/StringBuilder' in cp[j]:
   # CLDC has StringBuffer but no StringBuilder; update type names AND method descriptors.
   repl=cp[j].replace('java/lang/StringBuilder','java/lang/StringBuffer').encode('utf-8')
   b=b'\x01'+u2(len(repl))+repl;cp[j]=repl.decode('utf-8')
  cpbytes.append(b)
 ext_names=[]
 def utf8(s):
  if s in cp:return cp.index(s)
  cp.append(s);ext_names.append(b'\x01'+u2(len(s.encode()))+s.encode());return len(cp)-1
 def classname(s):
  if s in ['java/lang/Object'] or s.startswith('[') or '/' in s:
   for k in range(1,len(cp)):
    if isinstance(cp[k],int) and cp[k]>0 and cp[k]<len(cp) and cp[cp[k]]==s:return k
   u=utf8(s);cp.append(u);ext_names.append(b'\x07'+u2(u));return len(cp)-1
  raise Exception(s)
 stack_name=utf8('StackMap');headstart=r.i
 access=r.U2();this_class=r.U2();sup=r.U2();
 b=u2(access)+u2(this_class)+u2(sup)
 interfaces=r.U2();b+=u2(interfaces)+r.read(2*interfaces)
 def copy_member(r,method=False):
  access=r.U2();name=r.U2();desc=r.U2();count=r.U2();attrs=[]
  for _ in range(count):
   attname=r.U2();length=r.U4();body=r.read(length)
   if method and cp[attname]=='Code':body=code(body,access,cp[name]=='<init>',cp[desc]);
   attrs.append(u2(attname)+u4(len(body))+body)
  return u2(access)+u2(name)+u2(desc)+u2(len(attrs))+b''.join(attrs)
 def code(b,access,is_init,desc):
  x=Reader(b);p=x.read(2+2);sz=x.U4();p+=u4(sz)+x.read(sz);ext=x.U2();p+=u2(ext)+x.read(8*ext)
  a=x.U2();attrs=[]
  for j in range(a):
   nm=x.U2();l=x.U4();rawattr=x.read(l)
   if cp[nm]=='StackMapTable':
    rr=Reader(rawattr);cnt=rr.U2();locals_=desc_locals(desc,access,this_class,is_init,cp,classname);prev=-1;out=[]
    for k in range(cnt):
     ft=rr.U1();stack=[]
     def vt():
      tag=rr.U1();return u1(tag)+rr.read(2) if tag in (7,8) else u1(tag)
     if ft<=63:delta=ft
     elif ft<=127:delta=ft-64;stack=[vt()]
     elif ft==247:delta=rr.U2();stack=[vt()]
     elif 248<=ft<=250:delta=rr.U2();locals_=locals_[:-(251-ft)]
     elif ft==251:delta=rr.U2()
     elif 252<=ft<=254:delta=rr.U2();locals_=locals_+[vt() for _ in range(ft-251)]
     elif ft==255:
      delta=rr.U2();lc=rr.U2();locals_=[vt() for _ in range(lc)];sc=rr.U2();stack=[vt() for _ in range(sc)]
     else:raise Exception('Unknown frame '+str(ft))
     offset=prev+delta+1;prev=offset
     out.append(u2(offset)+u2(len(locals_))+b''.join(locals_)+u2(len(stack))+b''.join(stack))
    assert rr.i==len(rawattr)
    converted=u2(len(out))+b''.join(out)
    attrs.append(u2(stack_name)+u4(len(converted))+converted)
   else:attrs.append(u2(nm)+u4(l)+rawattr)
  assert x.i==len(b)
  return p+u2(len(attrs))+b''.join(attrs)
 fields=r.U2();b+=u2(fields)
 for _ in range(fields):b+=copy_member(r)
 methods=r.U2();b+=u2(methods)
 for _ in range(methods):b+=copy_member(r,True)
 attrs=r.U2();b+=u2(attrs)
 for _ in range(attrs):
  nm=r.U2();sz=r.U4();b+=u2(nm)+u4(sz)+r.read(sz)
 assert r.i==len(raw)
 return u4(0xCAFEBABE)+u2(3)+u2(45)+u2(len(cp))+b''.join(cpbytes)+b''.join(ext_names)+b

def main():
 (BUILD/'classes').mkdir(parents=True,exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(STUB),'-d',str(BUILD/'classes'),str(SRC/'minisocial/MiniSocialMidlet.java')],check=True)
 pv=BUILD/'cldc-classes';pv.mkdir(exist_ok=True)
 for f in (BUILD/'classes').rglob('*.class'):
  out=pv/f.relative_to(BUILD/'classes');out.parent.mkdir(parents=True,exist_ok=True)
  out.write_bytes(rewrite(f.read_bytes()))
 manifest='''Manifest-Version: 1.0\nMIDlet-1: MiniSocial,,minisocial.MiniSocialMidlet\nMIDlet-Name: MiniSocial\nMIDlet-Version: 1.3.0\nMIDlet-Vendor: Xubi\nMicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\nMIDlet-Permissions-Opt: javax.microedition.io.Connector.http,javax.microedition.io.Connector.https,javax.microedition.io.Connector.file.read\nMiniSocial-Api-Url: https://minisocial.xubi.org\n\n'''
 dist=ROOT/'dist';dist.mkdir(exist_ok=True)
 jar=dist/'MiniSocial-Responsive.jar';
 with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  z.writestr('META-INF/MANIFEST.MF',manifest)
  for f in pv.rglob('*.class'):z.write(f,f.relative_to(pv).as_posix())
 jad='''MIDlet-Name: MiniSocial\nMIDlet-1: MiniSocial,,minisocial.MiniSocialMidlet\nMIDlet-Version: 1.3.0\nMIDlet-Vendor: Xubi\nMicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\nMIDlet-Permissions-Opt: javax.microedition.io.Connector.http,javax.microedition.io.Connector.https,javax.microedition.io.Connector.file.read\nMiniSocial-Api-Url: https://minisocial.xubi.org\nMIDlet-Jar-URL: MiniSocial-Responsive.jar\nMIDlet-Jar-Size: %d\n'''%jar.stat().st_size
 (dist/'MiniSocial-Responsive.jad').write_text(jad)
 print('CLDC JAR:',jar,'size',jar.stat().st_size,'bytes; classes',len(list(pv.rglob('*.class'))))
if __name__=='__main__':main()
