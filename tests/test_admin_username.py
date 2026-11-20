#!/usr/bin/env python3
"""Local mock integration test. No network calls to real Supabase."""
import json, os, subprocess, threading, time, urllib.request, urllib.error, urllib.parse, uuid, socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BASE=Path(__file__).resolve().parents[1]
profiles={}
limits={"id":1,"total_limit_bytes":536870912,"used_bytes":0,"registration_enabled":True}
passwords={}

def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]

class Mock(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def send(self,code,body):
  raw=json.dumps(body).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
 def do_GET(self):
  p=urllib.parse.urlparse(self.path);q=urllib.parse.parse_qs(p.query)
  if p.path=='/rest/v1/minisocial_limits':self.send(200,[limits]);return
  if p.path=='/rest/v1/profiles':
   users=list(profiles.values());handle=q.get('handle',[''])[0]
   if handle.startswith('eq.'):users=[u for u in users if u['handle']==handle[3:]]
   elif handle.startswith('ilike.'):users=[u for u in users if handle[6:-1] in u['handle']]
   uid=q.get('id',[''])[0]
   if uid.startswith('eq.'):users=[u for u in users if u['id']==uid[3:]]
   offset=int(q.get('offset',['0'])[0]);self.send(200,users[offset:offset+int(q.get('limit',['100'])[0])]);return
  if p.path=='/rest/v1/media_files':self.send(200,[]);return
  if p.path=='/auth/v1/user':
   user=next(iter(profiles.values()),None);self.send(200,{'id':user['id']} if user else {});return
  self.send(404,{'message':'missing'})
 def do_POST(self):
  n=int(self.headers.get('content-length','0'));data=json.loads(self.rfile.read(n) or '{}')
  p=urllib.parse.urlparse(self.path)
  if p.path=='/auth/v1/admin/users':
   h=data['user_metadata']['handle'];email=data['email']
   if any(u['handle']==h for u in profiles.values()):self.send(422,{'message':'already exists'});return
   uid=str(uuid.uuid4());profiles[uid]={'id':uid,'handle':h,'display_name':h,'auth_email':email,'is_suspended':False,
      'sessions_revoked_before':0,'storage_limit_bytes':52428800,'storage_used_bytes':0,'created_at':'2026-11-21T00:00:00Z'}
   passwords[uid]=data['password'];self.send(201,{'id':uid});return
  if p.path=='/auth/v1/token':
   user=next((u for u in profiles.values() if u['auth_email']==data['email'] and passwords[u['id']]==data['password']),None)
   if not user:self.send(401,{'message':'invalid credentials'});return
   self.send(200,{'access_token':'a.eyJpYXQiOjE3OTUyMzAwMDB9.signature-test-token','user':{'id':user['id']}});return
  self.send(404,{'message':'missing'})
 def do_PATCH(self):
  n=int(self.headers.get('content-length','0'));data=json.loads(self.rfile.read(n) or '{}')
  p=urllib.parse.urlparse(self.path);q=urllib.parse.parse_qs(p.query)
  if p.path=='/rest/v1/profiles':
   uid=q.get('id',[''])[0][3:];profiles[uid].update(data);self.send(200,[profiles[uid]]);return
  if p.path=='/rest/v1/minisocial_limits':limits.update(data);self.send(200,[limits]);return
  self.send(404,{})
 def do_PUT(self):
  n=int(self.headers.get('content-length','0'));data=json.loads(self.rfile.read(n) or '{}')
  uid=self.path.rsplit('/',1)[-1]
  if uid in profiles:passwords[uid]=data['password'];self.send(200,{'id':uid});return
  self.send(404,{})
 def do_DELETE(self):
  uid=self.path.rsplit('/',1)[-1]
  if uid in profiles:del profiles[uid];self.send(200,{});return
  self.send(404,{})

mockport,backendport=port(),port()
server=ThreadingHTTPServer(('127.0.0.1',mockport),Mock)
thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
classes=BASE/'server/build/classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*map(str,(BASE/'server/src/minisocial').glob('*.java'))],check=True,stdout=subprocess.DEVNULL)
env=dict(os.environ,PORT=str(backendport),SUPABASE_URL='http://127.0.0.1:'+str(mockport),SUPABASE_PUBLISHABLE_KEY='sb_publishable_test',SUPABASE_SECRET_KEY='sb_secret_test',MINISOCIAL_ADMIN_USER='admin',MINISOCIAL_ADMIN_PASSWORD='sample-secret-password-is-long',MINISOCIAL_WEB_DIR=str(BASE/'web'))
proc=subprocess.Popen(['java','-cp',str(classes),'minisocial.Server'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
def req(path,data=None,headers=None):
 body=None if data is None else urllib.parse.urlencode(data).encode()
 request=urllib.request.Request('http://127.0.0.1:'+str(backendport)+path,data=body,headers=headers or {})
 try:
  with urllib.request.urlopen(request,timeout=4) as r:return r.status,dict(r.headers),r.read().decode()
 except urllib.error.HTTPError as e:return e.code,dict(e.headers),e.read().decode()
try:
 for _ in range(50):
  try:
   if req('/health')[0]==200:break
  except Exception:time.sleep(.1)
 else:raise AssertionError('backend failed to start')
 assert req('/admin')[0]==200
 assert req('/admin/assets/admin.js')[0]==200
 assert req('/m/register',{'handle':'khazg','password':'12345678'})[0]==200
 assert len(profiles)==1
 assert req('/m/register',{'handle':'khazg','password':'12345678'})[0]==409
 assert req('/m/login',{'handle':'khazg','password':'12345678'})[0]==200
 assert req('/admin/api/data')[0]==401
 assert req('/admin/api/login',{'username':'admin','password':'bad'})[0]==401
 stat,head,body=req('/admin/api/login',{'username':'admin','password':env['MINISOCIAL_ADMIN_PASSWORD']})
 assert stat==200,(stat,body)
 set_cookie=next(v for k,v in head.items() if k.lower()=='set-cookie')
 cookie=set_cookie.split(';')[0]
 assert 'HttpOnly' in set_cookie and 'Secure' in set_cookie
 stat,_,body=req('/admin/api/session',headers={'Cookie':cookie})
 assert stat==200,(stat,body)
 csrf=json.loads(body)['csrf']
 data_headers={'Cookie':cookie,'X-MiniSocial-CSRF':csrf}
 assert req('/admin/api/data',headers={'Cookie':cookie})[0]==200
 assert req('/admin/api/suspend',{'id':next(iter(profiles)),'suspended':'true'},headers={'Cookie':cookie})[0]==403
 uid=next(iter(profiles))
 assert req('/admin/api/suspend',{'id':uid,'suspended':'true'},headers=data_headers)[0]==200
 assert req('/m/login',{'handle':'khazg','password':'12345678'})[0]==401
 assert req('/admin/api/suspend',{'id':uid,'suspended':'false'},headers=data_headers)[0]==200
 assert req('/admin/api/account-quota',{'id':uid,'bytes':'1000000'},headers=data_headers)[0]==200
 assert profiles[uid]['storage_limit_bytes']==1000000
 assert req('/admin/api/global-quota',{'bytes':'2097152'},headers=data_headers)[0]==200
 assert limits['total_limit_bytes']==2097152
 assert req('/admin/api/registration',{'enabled':'false'},headers=data_headers)[0]==200
 assert req('/m/register',{'handle':'another','password':'12345678'})[0]==403
 assert req('/admin/api/reset-password',{'id':uid,'password':'newpassword123'},headers=data_headers)[0]==200
 assert passwords[uid]=='newpassword123'
 assert req('/admin/api/delete',{'id':uid,'confirm':'DELETE'},headers=data_headers)[0]==200
 assert not profiles
 print('PASS mock Supabase: username registration/login, duplicate handle, admin login/cookie/CSRF, suspend, quotas, reset, delete')
finally:
 proc.terminate()
 try:proc.communicate(timeout=5)
 except:proc.kill()
 server.shutdown()
