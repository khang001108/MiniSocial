package minisocial;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** MiniSocial's compact URL-encoded line protocol for legacy Java ME devices. */
public final class Server {
  private static final String UTF8="UTF-8";
  private final Supabase db=new Supabase();
  private final AdminConsole admin=new AdminConsole(db);
  private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
  // Bound recovery abuse; throttles are additive to Supabase's own limits.
  private final ConcurrentHashMap<String,Long> recoveryRequests=new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String,Attempt> recoveryAttempts=new ConcurrentHashMap<>();
  private static final class Attempt {long until;int count;Attempt(long now){until=now+600000;count=0;}}
  private static final class Session {final String id; final long until;Session(String id,long until){this.id=id;this.until=until;}}
  private static final class Fault extends RuntimeException{final int status;Fault(int s,String m){super(m);status=s;}}
  public static void main(String[] args)throws Exception{new Server().start();}
  private void start()throws Exception{
    int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
    HttpServer http=HttpServer.create(new InetSocketAddress("0.0.0.0",port),64);
    http.createContext("/",this::route);http.setExecutor(Executors.newFixedThreadPool(16));http.start();
    System.out.println("MiniSocial backend started on :"+port+" (secrets not logged)");
  }
  private void route(HttpExchange x)throws IOException {
    try{
      String path=x.getRequestURI().getPath();
      if(path.equals("/health")){send(x,200,"{\"ok\":true,\"service\":\"MiniSocial\"}","application/json; charset=UTF-8");return;}
      if(path.equals("/")||path.equals("/index.html")){serveStatic(x,"index.html",true);return;}
      if(path.equals("/download/MiniSocial-Responsive.jar")){serveStatic(x,"MiniSocial-Responsive.jar",false);return;}
      if(path.equals("/download/MiniSocial-Responsive.jad")){serveStatic(x,"MiniSocial-Responsive.jad",false);return;}
      if(path.equals("/admin")||path.startsWith("/admin/")){admin.handle(x);return;}
      if(!path.startsWith("/m/"))throw new Fault(404,"Not found");
      boolean post=x.getRequestMethod().equals("POST");boolean get=x.getRequestMethod().equals("GET");
      if(!post&&!get)throw new Fault(405,"Method not allowed");
      Map<String,String> q=parse(x.getRequestURI().getRawQuery());
      Map<String,String> f=post&&!path.equals("/m/upload")?parse(new String(readLimit(x.getRequestBody(),32768),StandardCharsets.UTF_8)):Collections.emptyMap();
      if(path.equals("/m/register")&&post){registration(x,f);return;}
      if(path.equals("/m/login")&&post){login(x,f);return;}
      if((path.equals("/m/password/recover")||path.equals("/m/password/reset"))&&post)
        throw new Fault(410,"Khoi phuc mat khau: lien he quan tri vien MiniSocial");
      String me=authenticated(x);
      switch(path){
        case "/m/account/delete": require(post);deleteAccount(x,f,me);break;
        case "/m/storage": require(get);storage(x,me);break;
        case "/m/storage/clear": require(post);clearMedia(x,f,me);break;
        case "/m/history": require(get);history(x,q,me);break;
        case "/m/password/change": require(post);changePassword(x,f,me);break;
        case "/m/feed": require(get); feed(x,null);break;
        case "/m/profile":if(get)profile(x,q,me);else profileUpdate(x,f,me);break;
        case "/m/onboarding":if(get)onboardingStatus(x,me);else onboardingSave(x,f,me);break;
        case "/m/profileposts":require(get);feed(x,id(q.getOrDefault("user",me)));break;
        case "/m/post":require(post);post(x,f,me);break;
        case "/m/like":require(post);like(x,f,me);break;
        case "/m/comments":if(get)comments(x,q);else comment(x,f,me);break;
        case "/m/friends":require(get);friends(x,me);break;
        case "/m/requests":require(get);requests(x,me);break;
        case "/m/search":require(get);search(x,q,me);break;
        case "/m/friend":require(post);invite(x,f,me);break;
        case "/m/accept":require(post);accept(x,f,me);break;
        case "/m/conversations":require(get);conversations(x,me);break;
        case "/m/chat":require(get);chat(x,q,me);break;
        case "/m/message":require(post);message(x,f,me);break;
        case "/m/upload":require(post);upload(x,q,me);break;
        case "/m/photo":require(get);photo(x,q,me);break;
        default:throw new Fault(404,"Unknown endpoint");
      }
    }catch(Fault e){send(x,e.status,"ERR|"+enc(e.getMessage()),"text/plain; charset=UTF-8");}
     catch(Supabase.Error e){
       String message=e.getMessage().toLowerCase(Locale.ROOT);
       if(message.contains("storage limit exceeded"))send(x,507,"ERR|"+enc("Da dat gioi han luu tru MiniSocial"),"text/plain; charset=UTF-8");
       else {System.err.println("Supabase request error: HTTP "+e.status);
         send(x,e.status==401?401:(e.status>=400&&e.status<500?400:502),"ERR|"+enc("Yeu cau that bai, kiem tra tai khoan hoac cau hinh Supabase"),"text/plain; charset=UTF-8");}
     }
     catch(Exception e){System.err.println("Request failed: "+e.getClass().getSimpleName());send(x,500,"ERR|Server+error","text/plain; charset=UTF-8");}
     finally{x.close();}
  }
  /** Serve only explicitly allowlisted download paths; never expose project files or secrets. */
  private static void serveStatic(HttpExchange x,String file,boolean page)throws IOException{
    String method=x.getRequestMethod();
    if(!method.equals("GET")&&!method.equals("HEAD"))throw new Fault(405,"GET or HEAD required");
    String root=System.getenv().getOrDefault(page?"MINISOCIAL_WEB_DIR":"MINISOCIAL_DOWNLOAD_DIR",page?"/app/web":"/app/download");
    Path src=Path.of(root,file);
    if(!Files.isRegularFile(src))throw new Fault(404,"File not found");
    byte[] content=Files.readAllBytes(src);
    Headers h=x.getResponseHeaders();
    String mime=page?"text/html; charset=UTF-8":file.endsWith(".jad")?"text/vnd.sun.j2me.app-descriptor; charset=UTF-8":"application/java-archive";
    h.set("Content-Type",mime);
    h.set("X-Content-Type-Options","nosniff");
    h.set("Cache-Control",page?"no-cache":"public, max-age=3600");
    if(page){
      h.set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
      h.set("X-Frame-Options","DENY");
    }else{
      h.set("Content-Disposition","attachment; filename=\""+file+"\"");
    }
    if(method.equals("HEAD")){
      h.set("Content-Length",Integer.toString(content.length));
      x.sendResponseHeaders(200,-1);
      return;
    }
    x.sendResponseHeaders(200,content.length);
    try(OutputStream os=x.getResponseBody()){os.write(content);}
  }
  private static void require(boolean valid){if(!valid)throw new Fault(405,"Wrong HTTP method");}
  private String authenticated(HttpExchange x){
    String h=x.getRequestHeaders().getFirst("Authorization");if(h==null||!h.startsWith("Bearer "))throw new Fault(401,"Please log in");
    String token=h.substring(7);if(token.length()<20||token.length()>4096)throw new Fault(401,"Bad token");
    Session cached=sessions.get(token);long now=System.currentTimeMillis();
    String user=(cached!=null&&now<cached.until)?cached.id:db.userId(token);
    Map<String,Object> profile=accountStatus(user);
    if(Boolean.TRUE.equals(profile.get("is_suspended")))throw new Fault(403,"Tai khoan da bi khoa, lien he quan tri");
    long revoked=number(profile.get("sessions_revoked_before"));
    if(revoked>0&&tokenIssuedAt(token)<=revoked)throw new Fault(401,"Phien dang nhap het han, hay dang nhap lai");
    if(cached==null||now>=cached.until){
      if(sessions.size()>1000)sessions.entrySet().removeIf(e->e.getValue().until<now);
      if(sessions.size()<1000)sessions.put(token,new Session(user,now+30000));
    }
    return user;
  }
  private Map<String,Object> accountStatus(String user){
    Map<String,Object> p=Json.map(first(db.db("GET","profiles?select=is_suspended,sessions_revoked_before&id=eq."+user+"&limit=1",null)));
    if(p.isEmpty())throw new Fault(401,"Profile not found");
    return p;
  }
  private static long number(Object v){if(v instanceof Number)return ((Number)v).longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
  private static long tokenIssuedAt(String jwt){
    try{
      String[] parts=jwt.split("\\.");
      if(parts.length!=3)throw new Exception();
      String text=new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8);
      long iat=number(Json.map(Json.parse(text)).get("iat"));
      if(iat<=0)throw new Exception();return iat;
    }catch(Exception e){throw new Fault(401,"Please log in again");}
  }
  private static String id(String v){if(v==null||!v.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw new Fault(400,"Invalid ID");return v;}
  private static String body(Map<String,String> m,String k,int max,boolean required){String v=m.getOrDefault(k,"").trim();if(v.length()>max||(required&&v.isEmpty()))throw new Fault(400,"Invalid "+k+" (max "+max+")");return v;}
  private static Object first(Object o){List<Object>l=Json.list(o);return l.isEmpty()?null:l.get(0);}
  private static String val(Map<String,Object>m,String k){return Json.string(m.get(k));}
  private static String enc(String v){try{return URLEncoder.encode(v==null?"":v,UTF8);}catch(Exception e){throw new RuntimeException(e);}}
  private static String line(Object...fields){StringBuilder b=new StringBuilder();for(int i=0;i<fields.length;i++){if(i>0)b.append('|');b.append(enc(Json.string(fields[i])));}return b.toString();}
  private static void rows(HttpExchange x,List<String> rows)throws IOException{StringBuilder b=new StringBuilder("OK\n");for(String r:rows)b.append(r).append('\n');send(x,200,b.toString(),"text/plain; charset=UTF-8");}
  private static void ok(HttpExchange x,Object...fields)throws IOException{send(x,200,"OK|"+line(fields),"text/plain; charset=UTF-8");}
  private void registration(HttpExchange x,Map<String,String> f)throws IOException{
    String handle=body(f,"handle",24,true).toLowerCase(Locale.ROOT),pass=f.getOrDefault("password","");
    if(!handle.matches("[a-z0-9_]{3,24}")||pass.length()<8||pass.length()>128)
      throw new Fault(400,"Ten tai khoan 3-24 ky tu a-z0-9_, mat khau it nhat 8 ky tu");
    checkRecoveryAttempts("reg-ip:"+address(x),20);
    Map<String,Object> settings=Json.map(first(db.db("GET","minisocial_limits?select=registration_enabled&id=eq.1",null)));
    if(settings.isEmpty())throw new Fault(503,"Chua cap nhat SQL MiniSocial 1.7");
    if(!Boolean.TRUE.equals(settings.get("registration_enabled")))throw new Fault(403,"Dang ky dang dong, lien he quan tri");
    if(first(db.db("GET","profiles?select=id&handle=eq."+handle+"&limit=1",null))!=null)
      throw new Fault(409,"Ten tai khoan da duoc su dung");
    try{db.registerHandle(handle,pass);}
    catch(Supabase.Error e){
      if(e.status>=400&&e.status<500)throw new Fault(409,"Ten tai khoan da ton tai hoac khong hop le");
      throw e;
    }
    ok(x,"Tao tai khoan thanh cong, khong can email/OTP");
  }
  private void login(HttpExchange x,Map<String,String> f)throws IOException{
    String name=f.getOrDefault("handle",f.getOrDefault("email","")).trim().toLowerCase(Locale.ROOT);
    String password=f.getOrDefault("password","");
    if(password.isEmpty()||password.length()>128)throw new Fault(401,"Sai tai khoan hoac mat khau");
    String email=name;
    if(!name.contains("@")){
      if(!name.matches("[a-z0-9_]{3,24}"))throw new Fault(401,"Sai tai khoan hoac mat khau");
      Map<String,Object> record=Json.map(first(db.db("GET","profiles?select=id,auth_email,is_suspended&handle=eq."+name+"&limit=1",null)));
      if(record.isEmpty()||Boolean.TRUE.equals(record.get("is_suspended")))throw new Fault(401,"Sai tai khoan hoac mat khau");
      email=val(record,"auth_email");
    }else if(name.length()>140)throw new Fault(401,"Sai tai khoan hoac mat khau");
    if(email.length()==0)throw new Fault(401,"Sai tai khoan hoac mat khau");
    Map<String,Object> auth;
    try{auth=Json.map(db.login(email,password));}
    catch(Supabase.Error e){if(e.status>=400&&e.status<500)throw new Fault(401,"Sai tai khoan hoac mat khau");throw e;}
    String jwt=val(auth,"access_token"),user=val(Json.map(auth.get("user")),"id");
    if(jwt.length()<20)throw new Fault(401,"Sai tai khoan hoac mat khau");
    Map<String,Object> profile=accountStatus(user);
    if(Boolean.TRUE.equals(profile.get("is_suspended")))throw new Fault(403,"Tai khoan da bi khoa");
    ok(x,user,jwt);
  }
  private static boolean validEmail(String email){
    return email.length()>5&&email.length()<141&&email.indexOf('@')>0&&email.indexOf('.')>email.indexOf('@')+1&&email.indexOf(' ')<0;
  }
  private static String password(Map<String,String>form,String key){
    String v=form.getOrDefault(key,"");
    if(v.length()<8||v.length()>128)throw new Fault(400,"Password requires at least 8 characters");
    return v;
  }
  private static String address(HttpExchange x){
    return x.getRemoteAddress().getAddress().getHostAddress();
  }
  private void sendRecovery(HttpExchange x,Map<String,String> f)throws IOException{
    String email=body(f,"email",140,true).toLowerCase(Locale.ROOT);
    if(!validEmail(email))throw new Fault(400,"Invalid email");
    long now=System.currentTimeMillis();
    if(recoveryRequests.size()>4000)recoveryRequests.entrySet().removeIf(e->e.getValue()+600000<now);
    // Per-email 60s cooldown, plus a broad per-source limit. Do not block every
    // person on a reverse proxy just because another person sent one email.
    String mail="mail:"+email;
    Long last=recoveryRequests.get(mail);
    if(last!=null&&now-last<60000)throw new Fault(429,"Wait 60 seconds before another OTP");
    checkRecoveryAttempts("send-ip:"+address(x),50);
    recoveryRequests.put(mail,now);
    try{db.sendRecovery(email);}
    catch(Supabase.Error ex){
      if(ex.status==429)throw new Fault(429,"Email rate limit; try later");
      // Do not disclose whether an email belongs to a MiniSocial account.
      if(ex.status>=500)throw new Fault(503,"Recovery email service temporarily unavailable");
      // Auth may return a generic error for non-existent users. Avoid enumeration.
    }
    ok(x,"If this email is registered, a recovery code has been sent");
  }
  private void checkRecoveryAttempts(String key,int maxTries){
    long now=System.currentTimeMillis();
    if(recoveryAttempts.size()>4000)recoveryAttempts.entrySet().removeIf(e->e.getValue().until<now);
    Attempt a=recoveryAttempts.computeIfAbsent(key,k->new Attempt(now));
    synchronized(a){if(a.until<now){a.until=now+600000;a.count=0;}if(++a.count>maxTries)throw new Fault(429,"Too many tries; wait 10 minutes");}
  }
  private void resetPassword(HttpExchange x,Map<String,String>f)throws IOException{
    String email=body(f,"email",140,true).trim().toLowerCase(Locale.ROOT);
    String code=body(f,"otp",12,true),next=password(f,"password");
    if(!validEmail(email)||!code.matches("[0-9]{6}"))throw new Fault(400,"Email or 6-digit code invalid");
    checkRecoveryAttempts("verify-ip:"+address(x),100);checkRecoveryAttempts("verify-mail:"+email,6);
    try{db.resetPassword(email,code,next);}catch(Supabase.Error e){
      if(e.status>=500)throw new Fault(503,"Authentication service unavailable");
      if(e.status==429)throw new Fault(429,"Please try later");
      throw new Fault(400,"Invalid/expired OTP or password policy error");
    }
    recoveryAttempts.remove("verify-mail:"+email);
    ok(x,"Password reset successfully");
  }
  private void changePassword(HttpExchange x,Map<String,String> f,String me)throws IOException{
    String current=f.getOrDefault("current", ""),next=password(f,"password");
    if(current.length()==0||current.length()>128)throw new Fault(400,"Invalid current password");
    if(current.equals(next))throw new Fault(400,"New password must be different");
    String bearer=x.getRequestHeaders().getFirst("Authorization");
    if(bearer==null||!bearer.startsWith("Bearer "))throw new Fault(401,"Please log in");
    db.changePassword(bearer.substring(7),current,next,me);
    db.db("PATCH","profiles?id=eq."+me,Map.of("sessions_revoked_before",System.currentTimeMillis()/1000L));
    sessions.remove(bearer.substring(7));
    ok(x,"Password changed");
  }
  private void feed(HttpExchange x,String author)throws IOException{
    String path="feed_items?select=id,author_id,display_name,body,created_at,likes_count,comments_count,media_id,device_model,avatar_media_id&order=created_at.desc&limit=15";
    if(author!=null)path+="&author_id=eq."+author;
    List<String> lines=new ArrayList<>();for(Object o:Json.list(db.db("GET",path,null))){Map<String,Object>m=Json.map(o);lines.add(line(val(m,"id"),val(m,"author_id"),val(m,"display_name"),val(m,"created_at"),val(m,"likes_count"),val(m,"comments_count"),val(m,"body"),val(m,"media_id"),val(m,"device_model"),val(m,"avatar_media_id")));}rows(x,lines);
  }
  /** Own liked/posted history, using the existing feed wire format. */
  private void history(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String type=q.getOrDefault("type","liked");
    String path;
    if(type.equals("posted")){
      path="feed_items?select=id,author_id,display_name,body,created_at,likes_count,comments_count,media_id,device_model,avatar_media_id&author_id=eq."+me+"&order=created_at.desc&limit=30";
    }else if(type.equals("liked")){
      List<Object> likes=Json.list(db.db("GET","post_likes?select=post_id&user_id=eq."+me+"&limit=30",null));
      if(likes.isEmpty()){rows(x,Collections.emptyList());return;}
      StringJoiner ids=new StringJoiner(",");
      for(Object o:likes){String post=val(Json.map(o),"post_id");ids.add(id(post));}
      path="feed_items?select=id,author_id,display_name,body,created_at,likes_count,comments_count,media_id,device_model,avatar_media_id&id=in.("+ids+")&order=created_at.desc&limit=30";
    }else throw new Fault(400,"Unsupported history type");
    List<String> entries=new ArrayList<>();
    for(Object o:Json.list(db.db("GET",path,null))){Map<String,Object>m=Json.map(o);
      entries.add(line(val(m,"id"),val(m,"author_id"),val(m,"display_name"),val(m,"created_at"),val(m,"likes_count"),val(m,"comments_count"),val(m,"body"),val(m,"media_id"),val(m,"device_model"),val(m,"avatar_media_id")));
    }
    rows(x,entries);
  }
  private void storage(HttpExchange x,String me)throws IOException{
    Map<String,Object>p=Json.map(first(db.db("GET","profiles?select=storage_used_bytes,storage_limit_bytes&id=eq."+me+"&limit=1",null)));
    if(p.isEmpty())throw new Fault(404,"Storage profile not found");
    ok(x,Long.toString(number(p.get("storage_used_bytes"))),Long.toString(number(p.get("storage_limit_bytes"))));
  }
  /** Explicitly clear only images owned by caller; keep text, posts, messages and account. */
  private void clearMedia(HttpExchange x,Map<String,String>f,String me)throws IOException{
    if(!"CLEAR".equals(f.get("confirm")))throw new Fault(400,"Confirmation required");
    int deleted=0;
    for(int pass=0;pass<50;pass++){
      List<Object> media=Json.list(db.db("GET","media_files?select=id,storage_path&owner_id=eq."+me+"&limit=100",null));
      if(media.isEmpty()){ok(x,Integer.toString(deleted));return;}
      for(Object o:media){Map<String,Object>item=Json.map(o);
        String mediaId=id(val(item,"id")),path=val(item,"storage_path");
        db.db("DELETE","media_files?id=eq."+mediaId+"&owner_id=eq."+me,null);
        if(!path.isEmpty())db.deleteUpload(path);deleted++;
      }
    }
    throw new Fault(503,"Too many images; continue deletion with another request");
  }
  /** Password verification and exact typed confirmation before permanent user deletion. */
  private void deleteAccount(HttpExchange x,Map<String,String>f,String me)throws IOException{
    if(!"DELETE".equals(f.get("confirm")))throw new Fault(400,"Confirmation required");
    String current=password(f,"password");
    checkRecoveryAttempts("account-delete:"+address(x)+":"+me,5);
    try{db.verifyPassword(me,current);}catch(Supabase.Error e){
      if(e.status==400||e.status==401||e.status==422)throw new Fault(401,"Incorrect account password");throw e;
    }
    List<String> mediaPaths=new ArrayList<>();
    for(Object o:Json.list(db.db("GET","media_files?select=storage_path&owner_id=eq."+me+"&limit=10000",null)))
      mediaPaths.add(val(Json.map(o),"storage_path"));
    db.adminDeleteUser(me);sessions.entrySet().removeIf(e->e.getValue().id.equals(me));
    for(String path:mediaPaths)if(!path.isEmpty())db.deleteUpload(path);
    ok(x,"Account deleted");
  }
  /** Device label is a user-supplied display hint, NEVER trusted identity or authentication. */
  private static String deviceModel(HttpExchange x){
    String raw=x.getRequestHeaders().getFirst("X-MiniSocial-Device");
    if(raw==null)return "Thiet bi khong ro";
    raw=raw.trim();
    if(raw.length()<2||raw.length()>64||!raw.matches("[a-zA-Z0-9 ._()/-]{2,64}"))return "Thiet bi khong ro";
    return raw;
  }
  private void onboardingStatus(HttpExchange x,String me)throws IOException{
    Map<String,Object>m=Json.map(first(db.db("GET","profiles?select=id,display_name,handle,avatar_media_id,onboarding_complete&id=eq."+me+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Profile missing");
    ok(x,Boolean.TRUE.equals(m.get("onboarding_complete"))?"1":"0",val(m,"display_name"),val(m,"handle"),val(m,"avatar_media_id"));
  }
  private void onboardingSave(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String name=body(f,"name",50,true),avatar=f.getOrDefault("avatar","");
    if(name.length()<2)throw new Fault(400,"Ten hien thi can 2-50 ky tu");
    Map<String,Object>p=new LinkedHashMap<>();p.put("display_name",name);p.put("onboarding_complete",true);
    if(!avatar.isEmpty()){id(avatar);validateMedia(avatar,me,"avatar",null);p.put("avatar_media_id",avatar);}
    db.db("PATCH","profiles?id=eq."+me,p);ok(x,"Profile ready");
  }
  private void profile(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String who=id(q.getOrDefault("user",me));Map<String,Object>m=Json.map(first(db.db("GET","profiles?select=id,display_name,handle,bio,created_at,avatar_media_id&id=eq."+who+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Profile not found");ok(x,val(m,"id"),val(m,"display_name"),val(m,"handle"),val(m,"bio"),val(m,"created_at"),val(m,"avatar_media_id"));
  }
  private void profileUpdate(HttpExchange x,Map<String,String>f,String me)throws IOException{
    Map<String,Object> changes=new LinkedHashMap<>();
    if(f.containsKey("name")){
      String name=body(f,"name",50,true);if(name.length()<2)throw new Fault(400,"Display name too short");
      changes.put("display_name",name);
    }
    if(f.containsKey("bio"))changes.put("bio",body(f,"bio",240,false));
    if(changes.isEmpty())throw new Fault(400,"No profile fields specified");
    db.db("PATCH","profiles?id=eq."+me,changes);ok(x,"Updated");
  }
  private void post(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String content=body(f,"body",500,false),media=f.getOrDefault("media","");if(content.isEmpty()&&media.isEmpty())throw new Fault(400,"Empty post");
    Map<String,Object>p=new LinkedHashMap<>();p.put("author_id",me);p.put("body",content);p.put("device_model",deviceModel(x));
    if(!media.isEmpty()){id(media);validateMedia(media,me,"post",null);p.put("media_id",media);}
    Object o=first(db.db("POST","posts",p));ok(x,val(Json.map(o),"id"));
  }
  private void like(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String pid=id(f.get("post"));if(first(db.db("GET","posts?select=id&id=eq."+pid+"&limit=1",null))==null)throw new Fault(404,"Post missing");
    String path="post_likes?post_id=eq."+pid+"&user_id=eq."+me;
    if(first(db.db("GET",path+"&select=post_id&limit=1",null))!=null){db.db("DELETE",path,null);ok(x,"0");}
    else {db.db("POST","post_likes",Map.of("post_id",pid,"user_id",me));ok(x,"1");}
  }
  private void comments(HttpExchange x,Map<String,String>q)throws IOException{
    String post=id(q.get("post"));List<String>out=new ArrayList<>();
    for(Object o:Json.list(db.db("GET","comment_items?select=id,display_name,body,created_at,device_model&post_id=eq."+post+"&order=created_at.asc&limit=40",null))){Map<String,Object>m=Json.map(o);out.add(line(val(m,"id"),val(m,"display_name"),val(m,"body"),val(m,"created_at"),val(m,"device_model")));}rows(x,out);
  }
  private void comment(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String post=id(f.get("post")),msg=body(f,"body",300,true);
    Object o=first(db.db("POST","comments",Map.of("post_id",post,"author_id",me,"body",msg,"device_model",deviceModel(x))));ok(x,val(Json.map(o),"id"));
  }
  private void friends(HttpExchange x,String me)throws IOException{
    List<String>out=new ArrayList<>();for(Object o:Json.list(db.db("GET","friend_list?select=friend_id,display_name,handle&user_id=eq."+me+"&order=display_name.asc&limit=100",null))){Map<String,Object>m=Json.map(o);out.add(line(val(m,"friend_id"),val(m,"display_name"),val(m,"handle")));}rows(x,out);
  }
  private void requests(HttpExchange x,String me)throws IOException{
    List<String>out=new ArrayList<>();for(Object o:Json.list(db.db("GET","incoming_requests?select=requester_id,display_name,handle&addressee_id=eq."+me+"&limit=30",null))){Map<String,Object>m=Json.map(o);out.add(line(val(m,"requester_id"),val(m,"display_name"),val(m,"handle")));}rows(x,out);
  }
  private void search(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String handle=q.getOrDefault("handle","").trim().toLowerCase(Locale.ROOT);if(!handle.matches("[a-z0-9_]{3,24}"))throw new Fault(400,"Invalid handle");
    Map<String,Object>m=Json.map(first(db.db("GET","profiles?select=id,display_name,handle&id=not.eq."+me+"&handle=eq."+handle+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Handle not found");ok(x,val(m,"id"),val(m,"display_name"),val(m,"handle"));
  }
  private void invite(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String handle=body(f,"handle",24,true).toLowerCase(Locale.ROOT);if(!handle.matches("[a-z0-9_]{3,24}"))throw new Fault(400,"Invalid handle");
    Map<String,Object>u=Json.map(first(db.db("GET","profiles?select=id&handle=eq."+handle+"&limit=1",null)));
    String target=val(u,"id");if(target.isEmpty()||target.equals(me))throw new Fault(400,"User not found");
    Object found=first(db.db("GET","friendships?select=requester_id&or=(and(requester_id.eq."+me+",addressee_id.eq."+target+"),and(requester_id.eq."+target+",addressee_id.eq."+me+"))&limit=1",null));
    if(found!=null)throw new Fault(409,"Friend request already exists");
    db.db("POST","friendships",Map.of("requester_id",me,"addressee_id",target,"status","pending"));ok(x,"Request sent");
  }
  private void accept(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String requester=id(f.get("user"));Object found=first(db.db("GET","friendships?select=requester_id&requester_id=eq."+requester+"&addressee_id=eq."+me+"&status=eq.pending&limit=1",null));
    if(found==null)throw new Fault(404,"Request not found");db.db("PATCH","friendships?requester_id=eq."+requester+"&addressee_id=eq."+me,Map.of("status","accepted"));ok(x,"Accepted");
  }
  private boolean friendsWith(String a,String b){return first(db.db("GET","friend_list?select=friend_id&user_id=eq."+a+"&friend_id=eq."+b+"&limit=1",null))!=null;}
  /** Sidebar index, derived from existing friend_list + messages; no schema migration.
   * Never expose conversations with users who are not accepted friends.
   * Unread/read status is deliberately omitted because the database has no read receipts.
   */
  private void conversations(HttpExchange x,String me)throws IOException{
    List<Object> known=Json.list(db.db("GET","friend_list?select=friend_id,display_name,handle&user_id=eq."+me+"&limit=100",null));
    List<Object> recent=Collections.emptyList();
    if(!known.isEmpty()){
      String path="messages?select=sender_id,recipient_id,body,media_id,created_at&or=(sender_id.eq."+me+",recipient_id.eq."+me+")&order=created_at.desc&limit=300";
      recent=Json.list(db.db("GET",path,null));
    }
    rows(x,conversationRows(me,known,recent));
  }
  /** Pure index builder, unit-testable without Supabase credentials. */
  static List<String> conversationRows(String me,List<Object> known,List<Object> recent){
    Map<String,Map<String,Object>> latest=new HashMap<>();
    for(Object obj:recent){
      Map<String,Object> m=Json.map(obj);
      String from=val(m,"sender_id"),to=val(m,"recipient_id"),other=from.equals(me)?to:from;
      if(other.length()>0&&!latest.containsKey(other))latest.put(other,m);
    }
    List<Map<String,Object>> sorted=new ArrayList<>();
    for(Object item:known)sorted.add(Json.map(item));
    sorted.sort((a,b)->{
      Map<String,Object> ma=latest.get(val(a,"friend_id")),mb=latest.get(val(b,"friend_id"));
      String ta=ma==null?"":val(ma,"created_at"),tb=mb==null?"":val(mb,"created_at");
      int recentTime=tb.compareTo(ta);
      return recentTime!=0?recentTime:val(a,"display_name").compareToIgnoreCase(val(b,"display_name"));
    });
    List<String> result=new ArrayList<>();
    for(Map<String,Object> f:sorted){
      String friend=val(f,"friend_id");Map<String,Object> msg=latest.get(friend);
      String preview=msg==null?"":val(msg,"body");
      if(msg!=null&&!val(msg,"media_id").isEmpty()&&preview.isEmpty())preview="[Ảnh]";
      result.add(line(friend,val(f,"display_name"),val(f,"handle"),preview,msg==null?"":val(msg,"created_at")));
    }
    return result;
  }
  private void chat(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String peer=id(q.get("user"));if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");
    String path="messages?select=id,sender_id,recipient_id,body,media_id,created_at,device_model&or=(and(sender_id.eq."+me+",recipient_id.eq."+peer+"),and(sender_id.eq."+peer+",recipient_id.eq."+me+"))&order=created_at.desc&limit=40";
    List<Object>all=Json.list(db.db("GET",path,null));List<String>out=new ArrayList<>();for(int i=all.size()-1;i>=0;i--){Map<String,Object>m=Json.map(all.get(i));out.add(line(val(m,"id"),val(m,"sender_id"),val(m,"body"),val(m,"media_id"),val(m,"created_at"),val(m,"device_model")));}rows(x,out);
  }
  private void message(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String peer=id(f.get("user")),content=body(f,"body",500,false),media=f.getOrDefault("media","");
    if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");if(content.isEmpty()&&media.isEmpty())throw new Fault(400,"Empty message");
    Map<String,Object>msg=new LinkedHashMap<>();msg.put("sender_id",me);msg.put("recipient_id",peer);msg.put("body",content);msg.put("device_model",deviceModel(x));
    if(!media.isEmpty()){id(media);validateMedia(media,me,"chat",peer);msg.put("media_id",media);}
    db.db("POST","messages",msg);ok(x,"Sent");
  }
  private Map<String,Object> validateMedia(String media,String me,String scope,String peer){
    Map<String,Object>m=Json.map(first(db.db("GET","media_files?select=id,owner_id,receiver_id,scope,storage_path,mime_type&id=eq."+media+"&limit=1",null)));
    if(m.isEmpty()||!me.equals(val(m,"owner_id"))||!scope.equals(val(m,"scope"))||(peer!=null&&!peer.equals(val(m,"receiver_id"))))throw new Fault(403,"Media not authorized");return m;
  }
  private void upload(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String scope=q.getOrDefault("scope","");if(!scope.equals("post")&&!scope.equals("chat")&&!scope.equals("avatar"))throw new Fault(400,"Scope must be post, chat or avatar");
    String peer=null;if(scope.equals("chat")){peer=id(q.get("user"));if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");}
    String mime=x.getRequestHeaders().getFirst("Content-Type");if(mime==null)throw new Fault(400,"Missing image type");mime=mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if(!mime.equals("image/jpeg")&&!mime.equals("image/png"))throw new Fault(415,"Only JPEG/PNG");
    byte[] raw=readLimit(x.getRequestBody(),180*1024);if(raw.length<8)throw new Fault(400,"Image too small");
    boolean jpg=(raw[0]&255)==255&&(raw[1]&255)==216&&(raw[2]&255)==255;
    boolean png=(raw[0]&255)==137&&raw[1]==80&&raw[2]==78&&raw[3]==71;
    if((mime.equals("image/jpeg")&&!jpg)||(mime.equals("image/png")&&!png))throw new Fault(415,"Invalid image signature");
    String media=UUID.randomUUID().toString(),path=me+"/"+media+(jpg?".jpg":".png");
    db.upload(path,raw,mime);
    try {Map<String,Object>r=new LinkedHashMap<>();r.put("id",media);r.put("owner_id",me);r.put("scope",scope);r.put("storage_path",path);r.put("mime_type",mime);r.put("size_bytes",raw.length);r.put("device_model",deviceModel(x));if(peer!=null)r.put("receiver_id",peer);db.db("POST","media_files",r);}
    catch(Exception e){db.deleteUpload(path);throw e;}
    ok(x,media);
  }
  private void photo(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String media=id(q.get("id"));Map<String,Object>m=Json.map(first(db.db("GET","media_files?select=id,owner_id,receiver_id,scope,storage_path,mime_type&id=eq."+media+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Image not found");
    boolean allowed=me.equals(val(m,"owner_id"))||me.equals(val(m,"receiver_id"));
    if("post".equals(val(m,"scope"))){allowed=allowed||first(db.db("GET","posts?select=id&media_id=eq."+media+"&limit=1",null))!=null;}
    if("avatar".equals(val(m,"scope")))allowed=allowed||first(db.db("GET","profiles?select=id&avatar_media_id=eq."+media+"&limit=1",null))!=null;
    if(!allowed)throw new Fault(403,"Private image");
    byte[]raw=db.download(val(m,"storage_path"));x.getResponseHeaders().set("Cache-Control","private, max-age=60");send(x,200,raw,val(m,"mime_type"));
  }
  private static void send(HttpExchange x,int status,String payload,String type)throws IOException{send(x,status,payload.getBytes(StandardCharsets.UTF_8),type);}
  private static void send(HttpExchange x,int status,byte[] bytes,String type)throws IOException{
    x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("Cache-Control","no-store");
    x.sendResponseHeaders(status,bytes.length);try(OutputStream os=x.getResponseBody()){os.write(bytes);}
  }
  private static byte[]readLimit(InputStream in,int max)throws IOException{byte[] b=in.readNBytes(max+1);if(b.length>max)throw new Fault(413,"Payload too large");return b;}
  private static Map<String,String>parse(String s){Map<String,String>m=new LinkedHashMap<>();if(s==null||s.isEmpty())return m;for(String part:s.split("&")){String[]kv=part.split("=",2);try{m.put(URLDecoder.decode(kv[0],UTF8),URLDecoder.decode(kv.length>1?kv[1]:"",UTF8));}catch(Exception e){throw new Fault(400,"Malformed form data");}}return m;}
}
