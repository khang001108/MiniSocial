#!/usr/bin/env python3
"""HTTP integration against a local fake Supabase. No real email/credentials needed."""
import os, subprocess, sys, json, time, urllib.request, urllib.error, socket, threading
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
UID='11111111-1111-1111-1111-111111111111'
OTP='123456'; JWT='fake-session-token-only-testing'; email='me@example.net'; current='old-password-okay'; NEW='new-password-12345'
received=[]
class FakeSupabase(BaseHTTPRequestHandler):
  def log_message(self, *a): pass
  def reply(self,code,body={}):
    blob=json.dumps(body).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(blob)));self.end_headers();self.wfile.write(blob)
  def do_GET(self):
    if self.path=='/auth/v1/user':
      return self.reply(200, {'id':UID,'email':email}) if self.headers.get('Authorization')=='Bearer '+JWT else self.reply(401,{'message':'Unauthorized'})
    self.reply(404)
  def do_POST(self):
    length=int(self.headers.get('Content-Length','0'));body=json.loads(self.rfile.read(length))
    received.append((self.path,body))
    if self.path=='/auth/v1/recover':return self.reply(200,{})
    if self.path=='/auth/v1/verify':
      return self.reply(200,{'access_token':JWT}) if body.get('email')==email and body.get('token')==OTP and body.get('type')=='recovery' else self.reply(403,{'message':'Invalid OTP'})
    if self.path=='/auth/v1/token?grant_type=password':
      return self.reply(200,{'user':{'id':UID}}) if body.get('email')==email and body.get('password')==current else self.reply(400,{'message':'Bad old password'})
    self.reply(404)
  def do_PUT(self):
    body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
    received.append((self.path,body))
    return self.reply(200,{'id':UID}) if self.headers.get('Authorization')=='Bearer '+JWT and body.get('password')==NEW else self.reply(403,{'message':'Bad token or password'})
def free_port():
  s=socket.socket();s.bind(('127.0.0.1',0));v=s.getsockname()[1];s.close();return v
def request(port,path,form=None,auth=None):
  from urllib.parse import urlencode
  url=f'http://127.0.0.1:{port}{path}';data=urlencode(form).encode() if form is not None else None
  req=urllib.request.Request(url,data=data,headers={'Authorization':'Bearer '+auth} if auth else {})
  try:
    with urllib.request.urlopen(req,timeout=5) as resp:return resp.status,resp.read().decode()
  except urllib.error.HTTPError as e:return e.code,e.read().decode()
def main():
  mock=ThreadingHTTPServer(('127.0.0.1',0),FakeSupabase)
  threading.Thread(target=mock.serve_forever,daemon=True).start();port=free_port()
  env=os.environ.copy();env.update(SUPABASE_URL=f'http://127.0.0.1:{mock.server_port}',SUPABASE_PUBLISHABLE_KEY='sb_publishable_TEST_LOCAL',SUPABASE_SECRET_KEY='sb_secret_TEST_LOCAL',PORT=str(port))
  process=subprocess.Popen(['java','-cp',str(ROOT/'server/build'), 'minisocial.Server'],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  try:
    for _ in range(70):
      try:
        if request(port,'/health')[0]==200:break
      except Exception:time.sleep(.1)
    else:raise AssertionError('server did not start')
    assert request(port,'/m/password/recover',{'email':'bad'})[0]==400
    assert request(port,'/m/password/recover',{'email':email})[0]==200
    # generic message must not include whether an email exists
    status,body=request(port,'/m/password/recover',{'email':email})
    assert status==429 and '60' in body, (status,body)
    assert request(port,'/m/password/reset',{'email':email,'otp':'wrong','password':NEW})[0]==400
    assert request(port,'/m/password/reset',{'email':email,'otp':'111111','password':NEW})[0]==400
    assert request(port,'/m/password/reset',{'email':email,'otp':OTP,'password':NEW})[0]==200
    assert request(port,'/m/password/change',{'current':current,'password':NEW})[0]==401
    assert request(port,'/m/password/change',{'current':'wrongpassword','password':NEW},JWT)[0]==401
    assert request(port,'/m/password/change',{'current':current,'password':NEW},JWT)[0]==200
    assert len([p for p,_ in received if p=='/auth/v1/user'])>=1
    assert len([p for p,_ in received if p=='/auth/v1/verify'])>=2
    assert len([p for p,_ in received if p=='/auth/v1/recover'])==1 # 60s throttle
    assert len([p for p,_ in received if p=='/auth/v1/user' ])>=1
    assert sum(p=='/auth/v1/user' for p,_ in received)>=1
    print('PASS mock Supabase: recover, throttle, bad OTP, good OTP, unauthenticated/wrong/current password change')
  finally:
    process.terminate();
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill()
    mock.shutdown()
if __name__=='__main__':main()
