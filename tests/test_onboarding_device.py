#!/usr/bin/env python3
"""Local integration: signup, onboarding, avatar upload and device attribution. No live Supabase."""
import json, os, socket, subprocess, threading, time, urllib.request, urllib.error
from urllib.parse import parse_qs, urlsplit
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
UID='11111111-1111-1111-1111-111111111111'
JWT='a-valid-fake-session-token-for-the-local-test'
profile={'id':UID,'handle':'newuser','display_name':'newuser','bio':'','onboarding_complete':False,'avatar_media_id':None,'auth_email':'newuser@noemail.minisocial.invalid','is_suspended':False,'sessions_revoked_before':0,'created_at':'2026-11-20'}
media={};posts=[];registered=[];requests=[]
class Fake(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def reply(self,status,obj=None):
  content=json.dumps(obj if obj is not None else {}).encode()
  self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(content)));self.end_headers();self.wfile.write(content)
 def data(self):
  n=int(self.headers.get('Content-Length','0'));return self.rfile.read(n)
 def do_GET(self):
  path=urlsplit(self.path).path;q=parse_qs(urlsplit(self.path).query)
  if path=='/auth/v1/user':return self.reply(200,{'id':UID,'email':'test@example.com'}) if self.headers.get('Authorization')=='Bearer '+JWT else self.reply(401,{'message':'Unauthorized'})
  if path=='/rest/v1/minisocial_limits':return self.reply(200,[{'registration_enabled':True}])
  if path=='/rest/v1/profiles':
   if 'handle' in q:
    handle=q['handle'][0].removeprefix('eq.')
    return self.reply(200,[{'id':UID}] if handle=='taken' else ([profile] if handle=='newuser' and registered else []))
   return self.reply(200,[profile])
  if path=='/rest/v1/media_files':
   key=q.get('id',[''])[0].removeprefix('eq.');return self.reply(200,[media[key]] if key in media else [])
  if path=='/rest/v1/feed_items':return self.reply(200,[dict(id=p['id'],author_id=UID,display_name=profile['display_name'],body=p['body'],created_at='2026-11-20T23:00:00',likes_count=0,comments_count=0,media_id=p.get('media_id'),device_model=p['device_model']) for p in posts])
  if path=='/rest/v1/posts':return self.reply(200,[{'id':p['id']} for p in posts])
  return self.reply(404,{'message':f'Unhandled GET {path}'})
 def do_POST(self):
  path=urlsplit(self.path).path
  if path.startswith('/storage/v1/object/minisocial-private/'):
   self.data();return self.reply(200,{'Key':path})
  data=json.loads(self.data())
  if path=='/auth/v1/admin/users':registered.append(data);return self.reply(200,{'user':{'id':UID}})
  if path=='/auth/v1/token':return self.reply(200,{'access_token':JWT,'user':{'id':UID}})
  if path=='/rest/v1/media_files':media[data['id']]=data;return self.reply(201,[data])
  if path=='/rest/v1/posts':
   data['id']='22222222-2222-2222-2222-222222222222';posts.append(data);return self.reply(201,[data])
  return self.reply(404,{'message':f'Unhandled POST {path}'})
 def do_PATCH(self):
  path=urlsplit(self.path).path
  if path=='/rest/v1/minisocial_limits':return self.reply(200,[{'registration_enabled':True}])
  if path=='/rest/v1/profiles':profile.update(json.loads(self.data()));return self.reply(200,[profile])
  return self.reply(404,{'message':'Unknown PATCH'})
def free_port():
 s=socket.socket();s.bind(('127.0.0.1',0));port=s.getsockname()[1];s.close();return port
def call(port,path,form=None,token=None,device=None,mime=None):
 from urllib.parse import urlencode
 headers={}
 if token:headers['Authorization']='Bearer '+token
 if device:headers['X-MiniSocial-Device']=device
 if isinstance(form,dict):payload=urlencode(form).encode()
 else:payload=form
 if mime:headers['Content-Type']=mime
 r=urllib.request.Request(f'http://127.0.0.1:{port}{path}',data=payload,headers=headers)
 try:
  with urllib.request.urlopen(r,timeout=5) as f:return f.status,f.read().decode()
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
def main():
 fake=ThreadingHTTPServer(('127.0.0.1',0),Fake)
 threading.Thread(target=fake.serve_forever,daemon=True).start()
 port=free_port();env=os.environ.copy();env.update(SUPABASE_URL=f'http://127.0.0.1:{fake.server_port}',SUPABASE_PUBLISHABLE_KEY='sb_publishable_TEST_LOCAL',SUPABASE_SECRET_KEY='sb_secret_TEST_LOCAL',PORT=str(port))
 proc=subprocess.Popen(['java','-cp',str(ROOT/'server/build/minisocial-server.jar'),'minisocial.Server'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
 try:
  for _ in range(80):
   try:
    if call(port,'/health')[0]==200:break
   except Exception:time.sleep(.1)
  else:raise AssertionError('server failed to start')
  status,msg=call(port,'/m/register',{'handle':'taken','password':'12345678'})
  assert status==409 and 'Ten+tai+khoan' in msg,(status,msg)
  status,msg=call(port,'/m/register',{'handle':'newuser','password':'12345678'})
  assert status==200,(status,msg)
  assert registered and registered[0]['user_metadata']=={'handle':'newuser'} and registered[0]['email'].startswith('newuser@'),registered
  status,msg=call(port,'/m/login',{'handle':'newuser','password':'12345678'});assert status==200 and JWT in msg,(status,msg)
  status,msg=call(port,'/m/onboarding',token=JWT);assert status==200 and '|0|' in msg,(status,msg)
  png=b'\x89PNG\r\n\x1a\n' + b'\x00'*10
  status,msg=call(port,'/m/upload?scope=avatar',png,token=JWT,device='Nokia Asha 302',mime='image/png')
  assert status==200,(status,msg)
  avatar=msg.split('|')[-1].strip();assert avatar in media
  status,msg=call(port,'/m/onboarding',{'name':'khazg','avatar':avatar},token=JWT)
  assert status==200 and profile['onboarding_complete'] is True and profile['avatar_media_id']==avatar,(status,msg,profile)
  status,msg=call(port,'/m/onboarding',token=JWT);assert '|1|' in msg and 'khazg' in msg,(status,msg)
  status,msg=call(port,'/m/post',{'body':'Vua dang anh tu Nokia'},token=JWT,device='Nokia Asha 302');assert status==200,(status,msg)
  status,msg=call(port,'/m/feed',token=JWT);assert status==200 and 'Nokia+Asha+302' in msg,(status,msg)
  print('PASS signup unique handle, metadata, login, onboarding, avatar upload, device label in feed')
 finally:
  proc.terminate();proc.wait(timeout=5);fake.shutdown()
if __name__=='__main__':main()
