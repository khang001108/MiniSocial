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
  private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
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
      if(!path.startsWith("/m/"))throw new Fault(404,"Not found");
      boolean post=x.getRequestMethod().equals("POST");boolean get=x.getRequestMethod().equals("GET");
      if(!post&&!get)throw new Fault(405,"Method not allowed");
      Map<String,String> q=parse(x.getRequestURI().getRawQuery());
      Map<String,String> f=post&&!path.equals("/m/upload")?parse(new String(readLimit(x.getRequestBody(),32768),StandardCharsets.UTF_8)):Collections.emptyMap();
      if(path.equals("/m/register")&&post){registration(x,f);return;}
      if(path.equals("/m/login")&&post){login(x,f);return;}
      String me=authenticated(x);
      switch(path){
        case "/m/feed": require(get); feed(x,null);break;
        case "/m/profile":if(get)profile(x,q,me);else profileUpdate(x,f,me);break;
        case "/m/profileposts":require(get);feed(x,id(q.getOrDefault("user",me)));break;
        case "/m/post":require(post);post(x,f,me);break;
        case "/m/like":require(post);like(x,f,me);break;
        case "/m/comments":if(get)comments(x,q);else comment(x,f,me);break;
        case "/m/friends":require(get);friends(x,me);break;
        case "/m/requests":require(get);requests(x,me);break;
        case "/m/search":require(get);search(x,q,me);break;
        case "/m/friend":require(post);invite(x,f,me);break;
        case "/m/accept":require(post);accept(x,f,me);break;
        case "/m/chat":require(get);chat(x,q,me);break;
        case "/m/message":require(post);message(x,f,me);break;
        case "/m/upload":require(post);upload(x,q,me);break;
        case "/m/photo":require(get);photo(x,q,me);break;
        default:throw new Fault(404,"Unknown endpoint");
      }
    }catch(Fault e){send(x,e.status,"ERR|"+enc(e.getMessage()),"text/plain; charset=UTF-8");}
     catch(Supabase.Error e){int status=e.status==401?401:(e.status>=400&&e.status<500?400:502);send(x,status,"ERR|"+enc(e.getMessage()),"text/plain; charset=UTF-8");}
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
    Session s=sessions.get(token);long now=System.currentTimeMillis();if(s!=null&&now<s.until)return s.id;
    String id=db.userId(token);
    if(sessions.size()>1000)sessions.entrySet().removeIf(e->e.getValue().until<now);
    if(sessions.size()<1000)sessions.put(token,new Session(id,now+30000));return id;
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
    String email=body(f,"email",140,true),pass=body(f,"password",128,true),name=body(f,"name",50,true),handle=body(f,"handle",24,true).toLowerCase(Locale.ROOT);
    if(pass.length()<8||!email.contains("@")||!handle.matches("[a-z0-9_]{3,24}"))throw new Fault(400,"Enter valid email, 8+ character password and handle a-z0-9_");
    db.signup(email,pass,name,handle);ok(x,"Account created. Confirm email if Supabase requires it.");
  }
  private void login(HttpExchange x,Map<String,String> f)throws IOException{
    Object obj=db.login(body(f,"email",140,true),body(f,"password",128,true));Map<String,Object> a=Json.map(obj);String token=val(a,"access_token");if(token.length()<20)throw new Fault(401,"Login failed");
    String user=val(Json.map(a.get("user")),"id");ok(x,user,token);
  }
  private void feed(HttpExchange x,String author)throws IOException{
    String path="feed_items?select=id,author_id,display_name,body,created_at,likes_count,comments_count,media_id&order=created_at.desc&limit=15";
    if(author!=null)path+="&author_id=eq."+author;
    List<String> lines=new ArrayList<>();for(Object o:Json.list(db.db("GET",path,null))){Map<String,Object>m=Json.map(o);lines.add(line(val(m,"id"),val(m,"author_id"),val(m,"display_name"),val(m,"created_at"),val(m,"likes_count"),val(m,"comments_count"),val(m,"body"),val(m,"media_id")));}rows(x,lines);
  }
  private void profile(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String who=id(q.getOrDefault("user",me));Map<String,Object>m=Json.map(first(db.db("GET","profiles?select=id,display_name,handle,bio,created_at&id=eq."+who+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Profile not found");ok(x,val(m,"id"),val(m,"display_name"),val(m,"handle"),val(m,"bio"),val(m,"created_at"));
  }
  private void profileUpdate(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String name=body(f,"name",50,true),bio=body(f,"bio",240,false);
    db.db("PATCH","profiles?id=eq."+me,Map.of("display_name",name,"bio",bio));ok(x,"Updated");
  }
  private void post(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String content=body(f,"body",500,false),media=f.getOrDefault("media","");if(content.isEmpty()&&media.isEmpty())throw new Fault(400,"Empty post");
    Map<String,Object>p=new LinkedHashMap<>();p.put("author_id",me);p.put("body",content);
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
    for(Object o:Json.list(db.db("GET","comment_items?select=id,display_name,body,created_at&post_id=eq."+post+"&order=created_at.asc&limit=40",null))){Map<String,Object>m=Json.map(o);out.add(line(val(m,"id"),val(m,"display_name"),val(m,"body"),val(m,"created_at")));}rows(x,out);
  }
  private void comment(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String post=id(f.get("post")),msg=body(f,"body",300,true);
    Object o=first(db.db("POST","comments",Map.of("post_id",post,"author_id",me,"body",msg)));ok(x,val(Json.map(o),"id"));
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
  private void chat(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String peer=id(q.get("user"));if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");
    String path="messages?select=id,sender_id,recipient_id,body,media_id,created_at&or=(and(sender_id.eq."+me+",recipient_id.eq."+peer+"),and(sender_id.eq."+peer+",recipient_id.eq."+me+"))&order=created_at.desc&limit=40";
    List<Object>all=Json.list(db.db("GET",path,null));List<String>out=new ArrayList<>();for(int i=all.size()-1;i>=0;i--){Map<String,Object>m=Json.map(all.get(i));out.add(line(val(m,"id"),val(m,"sender_id"),val(m,"body"),val(m,"media_id"),val(m,"created_at")));}rows(x,out);
  }
  private void message(HttpExchange x,Map<String,String>f,String me)throws IOException{
    String peer=id(f.get("user")),content=body(f,"body",500,false),media=f.getOrDefault("media","");
    if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");if(content.isEmpty()&&media.isEmpty())throw new Fault(400,"Empty message");
    Map<String,Object>msg=new LinkedHashMap<>();msg.put("sender_id",me);msg.put("recipient_id",peer);msg.put("body",content);
    if(!media.isEmpty()){id(media);validateMedia(media,me,"chat",peer);msg.put("media_id",media);}
    db.db("POST","messages",msg);ok(x,"Sent");
  }
  private Map<String,Object> validateMedia(String media,String me,String scope,String peer){
    Map<String,Object>m=Json.map(first(db.db("GET","media_files?select=id,owner_id,receiver_id,scope,storage_path,mime_type&id=eq."+media+"&limit=1",null)));
    if(m.isEmpty()||!me.equals(val(m,"owner_id"))||!scope.equals(val(m,"scope"))||(peer!=null&&!peer.equals(val(m,"receiver_id"))))throw new Fault(403,"Media not authorized");return m;
  }
  private void upload(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String scope=q.getOrDefault("scope","");if(!scope.equals("post")&&!scope.equals("chat"))throw new Fault(400,"Scope must be post or chat");
    String peer=null;if(scope.equals("chat")){peer=id(q.get("user"));if(!friendsWith(me,peer))throw new Fault(403,"Add friend first");}
    String mime=x.getRequestHeaders().getFirst("Content-Type");if(mime==null)throw new Fault(400,"Missing image type");mime=mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if(!mime.equals("image/jpeg")&&!mime.equals("image/png"))throw new Fault(415,"Only JPEG/PNG");
    byte[] raw=readLimit(x.getRequestBody(),180*1024);if(raw.length<8)throw new Fault(400,"Image too small");
    boolean jpg=(raw[0]&255)==255&&(raw[1]&255)==216&&(raw[2]&255)==255;
    boolean png=(raw[0]&255)==137&&raw[1]==80&&raw[2]==78&&raw[3]==71;
    if((mime.equals("image/jpeg")&&!jpg)||(mime.equals("image/png")&&!png))throw new Fault(415,"Invalid image signature");
    String media=UUID.randomUUID().toString(),path=me+"/"+media+(jpg?".jpg":".png");
    db.upload(path,raw,mime);
    try {Map<String,Object>r=new LinkedHashMap<>();r.put("id",media);r.put("owner_id",me);r.put("scope",scope);r.put("storage_path",path);r.put("mime_type",mime);if(peer!=null)r.put("receiver_id",peer);db.db("POST","media_files",r);}
    catch(Exception e){db.deleteUpload(path);throw e;}
    ok(x,media);
  }
  private void photo(HttpExchange x,Map<String,String>q,String me)throws IOException{
    String media=id(q.get("id"));Map<String,Object>m=Json.map(first(db.db("GET","media_files?select=id,owner_id,receiver_id,scope,storage_path,mime_type&id=eq."+media+"&limit=1",null)));
    if(m.isEmpty())throw new Fault(404,"Image not found");
    boolean allowed=me.equals(val(m,"owner_id"))||me.equals(val(m,"receiver_id"));
    if("post".equals(val(m,"scope"))){allowed=allowed||first(db.db("GET","posts?select=id&media_id=eq."+media+"&limit=1",null))!=null;}
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
