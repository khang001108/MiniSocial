package minisocial;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URLDecoder;

/** Server-side MiniSocial administrator console. Never expose Supabase credentials. */
final class AdminConsole {
  private final Supabase db;
  private final String adminUser=System.getenv().getOrDefault("MINISOCIAL_ADMIN_USER","admin");
  private final String adminPass=System.getenv().getOrDefault("MINISOCIAL_ADMIN_PASSWORD","");
  private final ConcurrentHashMap<String,LoginSession> sessions=new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
  private final SecureRandom rng=new SecureRandom();
  private static final class LoginSession {String csrf;long expiry;LoginSession(String c,long t){csrf=c;expiry=t;}}
  private static final class Attempt {long until;int count;Attempt(long t){until=t+600000;}}
  private static final class Reject extends RuntimeException {int status;Reject(int s,String m){super(m);status=s;}}
  AdminConsole(Supabase db){this.db=db;}
  boolean enabled(){return adminPass.length()>=16;}

  void handle(HttpExchange x) throws IOException {
    try{
      Headers h=x.getResponseHeaders();
      h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");
      h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");
      h.set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; script-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
      if(!enabled())throw new Reject(503,"Admin chưa được cấu hình: đặt MINISOCIAL_ADMIN_PASSWORD >=16 ký tự trong Xubi");
      String path=x.getRequestURI().getPath(),method=x.getRequestMethod();
      if(path.equals("/admin")||path.equals("/admin/")){
        if(!method.equals("GET"))throw new Reject(405,"GET required");
        byte[] data=Files.readAllBytes(Path.of(System.getenv().getOrDefault("MINISOCIAL_WEB_DIR","/app/web"),"admin.html"));
        send(x,200,data,"text/html; charset=UTF-8");return;
      }
      if(path.equals("/admin/assets/admin.js")){
        if(!method.equals("GET"))throw new Reject(405,"GET required");
        byte[] data=Files.readAllBytes(Path.of(System.getenv().getOrDefault("MINISOCIAL_WEB_DIR","/app/web"),"admin.js"));
        send(x,200,data,"text/javascript; charset=UTF-8");return;
      }
      if(!path.startsWith("/admin/api/"))throw new Reject(404,"Not found");
      if(method.equals("POST"))checkOrigin(x);
      if(path.equals("/admin/api/login")){
        if(!method.equals("POST"))throw new Reject(405,"POST required");login(x,form(x));return;
      }
      LoginSession session=authorize(x);
      if(path.equals("/admin/api/session")&&method.equals("GET")){
        respond(x,200,Map.of("authenticated",true,"csrf",session.csrf));return;
      }
      if(path.equals("/admin/api/logout")){
        if(!method.equals("POST"))throw new Reject(405,"POST required");checkCsrf(x,session);
        sessions.remove(cookie(x));x.getResponseHeaders().set("Set-Cookie","ms_admin=; Max-Age=0; Path=/admin; HttpOnly; Secure; SameSite=Strict");respond(x,200,Map.of("ok",true));return;
      }
      if(path.equals("/admin/api/data")&&method.equals("GET")){data(x);return;}
      if(!method.equals("POST"))throw new Reject(405,"POST required");
      checkCsrf(x,session);
      Map<String,String> f=form(x);
      switch(path){
        case "/admin/api/suspend":{
          String user=uuid(f.get("id"));boolean suspended=flag(f.get("suspended"));
          db.db("PATCH","profiles?id=eq."+user,Map.of("is_suspended",suspended));
          respond(x,200,Map.of("ok",true));break;
        }
        case "/admin/api/reset-password":{
          String user=uuid(f.get("id")),pwd=f.getOrDefault("password","");
          if(pwd.length()<8||pwd.length()>128)throw new Reject(400,"Mật khẩu phải dài 8-128 ký tự");
          db.adminResetPassword(user,pwd);
          db.db("PATCH","profiles?id=eq."+user,Map.of("sessions_revoked_before",System.currentTimeMillis()/1000L));
          respond(x,200,Map.of("ok",true));break;
        }
        case "/admin/api/delete":{
          String user=uuid(f.get("id"));
          if(!"DELETE".equals(f.get("confirm")))throw new Reject(400,"Chưa xác nhận xóa vĩnh viễn");
          List<String> paths=new ArrayList<>();
          for(Object o:Json.list(db.db("GET","media_files?select=storage_path&owner_id=eq."+user+"&limit=10000",null)))
            paths.add(Json.string(Json.map(o).get("storage_path")));
          db.adminDeleteUser(user);
          for(String p:paths)if(!p.isEmpty())db.deleteUpload(p);
          respond(x,200,Map.of("ok",true,"deleted_media",paths.size()));break;
        }
        case "/admin/api/account-quota":{
          String user=uuid(f.get("id"));long limit=number(f.get("bytes"),0L,1000000000000L);
          db.db("PATCH","profiles?id=eq."+user,Map.of("storage_limit_bytes",limit));
          respond(x,200,Map.of("ok",true));break;
        }
        case "/admin/api/global-quota":{
          long limit=number(f.get("bytes"),1048576L,1000000000000L);
          db.db("PATCH","minisocial_limits?id=eq.1",Map.of("total_limit_bytes",limit));
          respond(x,200,Map.of("ok",true));break;
        }
        case "/admin/api/registration":{
          db.db("PATCH","minisocial_limits?id=eq.1",Map.of("registration_enabled",flag(f.get("enabled"))));
          respond(x,200,Map.of("ok",true));break;
        }
        default:throw new Reject(404,"Unknown admin route");
      }
    }catch(Reject e){respond(x,e.status,Map.of("error",e.getMessage()));}
     catch(Supabase.Error e){int status=e.status>=400&&e.status<500?400:502;
       // Avoid showing raw SQL, user emails or internal Supabase response to admin UI.
       System.err.println("Admin datastore error "+e.status);
       respond(x,status,Map.of("error","Supabase từ chối thao tác. Kiểm tra SQL migration và log server."));}
     catch(Exception e){System.err.println("Admin request failure: "+e.getClass().getSimpleName());respond(x,500,Map.of("error","Lỗi máy chủ"));}
     finally{x.close();}
  }
  private void data(HttpExchange x)throws IOException{
    Map<String,String> q=parse(x.getRequestURI().getRawQuery());
    String search=q.getOrDefault("q","").trim().toLowerCase(Locale.ROOT);
    if(!search.matches("[a-z0-9_]{0,24}"))throw new Reject(400,"Từ khóa tìm kiếm không hợp lệ");
    long offset=number(q.getOrDefault("offset","0"),0,100000);
    String path="profiles?select=id,handle,display_name,created_at,is_suspended,storage_limit_bytes,storage_used_bytes"+
      (search.isEmpty()?"":"&handle=ilike.*"+search+"*")+"&order=created_at.desc&limit=50&offset="+offset;
    Object users=db.db("GET",path,null);
    Object limits=db.db("GET","minisocial_limits?select=total_limit_bytes,used_bytes,registration_enabled&id=eq.1",null);
    respond(x,200,Map.of("users",users,"limits",Json.list(limits).isEmpty()?Map.of():Json.list(limits).get(0),"offset",offset));
  }
  private void login(HttpExchange x,Map<String,String>f)throws IOException{
    String ip=x.getRemoteAddress().getAddress().getHostAddress();long now=System.currentTimeMillis();
    if(attempts.size()>4000)attempts.entrySet().removeIf(e->e.getValue().until<now);
    Attempt a=attempts.computeIfAbsent(ip,k->new Attempt(now));
    synchronized(a){if(a.until<now){a.until=now+600000;a.count=0;}if(++a.count>12)throw new Reject(429,"Quá nhiều lần thử, hãy đợi 10 phút");}
    byte[] provided=f.getOrDefault("password","").getBytes(StandardCharsets.UTF_8);
    if(!adminUser.equals(f.get("username"))||!MessageDigest.isEqual(adminPass.getBytes(StandardCharsets.UTF_8),provided)){
      throw new Reject(401,"Sai tài khoản hoặc mật khẩu admin");
    }
    attempts.remove(ip);
    if(sessions.size()>500)sessions.entrySet().removeIf(e->e.getValue().expiry<now);
    String token=random(),csrf=random();sessions.put(token,new LoginSession(csrf,now+7200000));
    x.getResponseHeaders().set("Set-Cookie","ms_admin="+token+"; Max-Age=7200; Path=/admin; HttpOnly; Secure; SameSite=Strict");
    respond(x,200,Map.of("ok",true));
  }
  private LoginSession authorize(HttpExchange x){
    LoginSession s=sessions.get(cookie(x));
    if(s==null||s.expiry<System.currentTimeMillis())throw new Reject(401,"Vui lòng đăng nhập quản trị");
    return s;
  }
  private static void checkCsrf(HttpExchange x,LoginSession s){
    String header=x.getRequestHeaders().getFirst("X-MiniSocial-CSRF");
    if(header==null||!MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),s.csrf.getBytes(StandardCharsets.UTF_8)))
      throw new Reject(403,"Phiên bảo mật CSRF không hợp lệ");
  }
  private static void checkOrigin(HttpExchange x){
    String origin=x.getRequestHeaders().getFirst("Origin");
    if(origin!=null){String host=x.getRequestHeaders().getFirst("Host");
      if(host==null||!origin.equals("https://"+host))throw new Reject(403,"Nguồn yêu cầu không hợp lệ");}
  }
  private static String cookie(HttpExchange x){
    String value=x.getRequestHeaders().getFirst("Cookie");if(value==null)return "";
    for(String c:value.split(";")){c=c.trim();if(c.startsWith("ms_admin="))return c.substring(9);}
    return "";
  }
  private String random(){byte[] b=new byte[32];rng.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private static String uuid(String s){
    if(s==null||!s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new Reject(400,"ID không hợp lệ");return s;
  }
  private static boolean flag(String v){
    if(!"true".equals(v)&&!"false".equals(v))throw new Reject(400,"Giá trị phải là true/false");
    return "true".equals(v);
  }
  private static long number(String v,long min,long max){
    try{long n=Long.parseLong(v);if(n<min||n>max)throw new NumberFormatException();return n;}
    catch(Exception e){throw new Reject(400,"Giới hạn ngoài phạm vi cho phép");}
  }
  private static Map<String,String> form(HttpExchange x)throws IOException{
    byte[] raw=x.getRequestBody().readNBytes(8193);if(raw.length>8192)throw new Reject(413,"Nội dung quá dài");
    return parse(new String(raw,StandardCharsets.UTF_8));
  }
  private static Map<String,String> parse(String s){
    Map<String,String> m=new LinkedHashMap<>();if(s==null||s.isEmpty())return m;
    for(String item:s.split("&")){String[]p=item.split("=",2);
      try{m.put(URLDecoder.decode(p[0],"UTF-8"),URLDecoder.decode(p.length>1?p[1]:"","UTF-8"));}
      catch(Exception e){throw new Reject(400,"Dữ liệu không hợp lệ");}}
    return m;
  }
  private static void respond(HttpExchange x,int code,Object o)throws IOException{send(x,code,Json.stringify(o).getBytes(StandardCharsets.UTF_8),"application/json; charset=UTF-8");}
  private static void send(HttpExchange x,int code,byte[] b,String mime)throws IOException{
    x.getResponseHeaders().set("Content-Type",mime);
    x.sendResponseHeaders(code,b.length);try(OutputStream out=x.getResponseBody()){out.write(b);}
  }
}
