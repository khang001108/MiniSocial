package minisocial;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

final class Supabase {
  final String url, publishable, secret;
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7)).build();
  Supabase(){url=env("SUPABASE_URL");publishable=envEither("SUPABASE_PUBLISHABLE_KEY","SUPABASE_ANON_KEY");secret=envEither("SUPABASE_SECRET_KEY","SUPABASE_SERVICE_ROLE_KEY");}
  private static String env(String k){String v=System.getenv(k);if(v==null||v.trim().isEmpty())throw new IllegalStateException("Missing env: "+k);return v.replaceAll("/$","");}
  private static String envEither(String preferred,String legacy){String v=System.getenv(preferred);return v!=null&&!v.trim().isEmpty()?v:env(legacy);}
  static class Error extends RuntimeException {final int status;Error(int status,String message){super(message);this.status=status;}}
  private HttpResponse<byte[]> call(String method,String path,String key,String jwt,byte[] body,String mime,boolean prefer){
    try {
      HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url+path)).timeout(Duration.ofSeconds(16))
      .header("apikey",key).header("Accept","application/json");
      // New sb_publishable_ / sb_secret_ opaque keys MUST NOT be used as Bearer JWTs.
      if(jwt!=null&&!jwt.isEmpty()&&!jwt.startsWith("sb_"))b.header("Authorization","Bearer "+jwt);
      if(mime!=null)b.header("Content-Type",mime);
      if(prefer)b.header("Prefer","return=representation");
      b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
      HttpResponse<byte[]> r=client.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
      if(r.statusCode()<200||r.statusCode()>=300){String raw=new String(r.body(),java.nio.charset.StandardCharsets.UTF_8);String detail=Json.string(Json.map(safeJson(raw)).getOrDefault("msg",Json.map(safeJson(raw)).getOrDefault("message",raw)));throw new Error(r.statusCode(),detail.length()>130?detail.substring(0,130):detail);}
      return r;
    } catch(Error ex){throw ex;}catch(Exception ex){throw new Error(502,"Supabase is unreachable: "+ex.getClass().getSimpleName());}
  }
  private static Object safeJson(String s){try{return Json.parse(s);}catch(Exception e){return null;}}
  private static byte[] bytes(Object o){return Json.stringify(o).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
  Object db(String method,String path,Object body){HttpResponse<byte[]>r=call(method,"/rest/v1/"+path,secret,secret,body==null?null:bytes(body),body==null?null:"application/json",!method.equals("GET"));String s=new String(r.body(),java.nio.charset.StandardCharsets.UTF_8);return s.isEmpty()?Collections.emptyList():Json.parse(s);}
  Object signup(String email,String pass,String handle){Map<String,Object> payload=Map.of("email",email,"password",pass,"data",Map.of("handle",handle));return auth("/auth/v1/signup",payload);}
  Object login(String email,String pass){return auth("/auth/v1/token?grant_type=password",Map.of("email",email,"password",pass));}
  /** Create confirmed internal account. The virtual email is NEVER shown or emailed. */
  Object registerHandle(String handle,String password){
    String virtualEmail=handle+"@noemail.minisocial.invalid";
    Map<String,Object> payload=new LinkedHashMap<>();
    payload.put("email",virtualEmail);payload.put("password",password);payload.put("email_confirm",true);
    payload.put("user_metadata",Map.of("handle",handle));
    HttpResponse<byte[]> r=call("POST","/auth/v1/admin/users",secret,secret,bytes(payload),"application/json",false);
    return Json.parse(new String(r.body(),java.nio.charset.StandardCharsets.UTF_8));
  }
  void adminResetPassword(String id,String next){
    call("PUT","/auth/v1/admin/users/"+id,secret,secret,bytes(Map.of("password",next)),"application/json",false);
  }
  void adminDeleteUser(String id){
    call("DELETE","/auth/v1/admin/users/"+id,secret,secret,null,null,false);
  }
  private Object auth(String path,Object payload){HttpResponse<byte[]>r=call("POST",path,publishable,publishable,bytes(payload),"application/json",false);return Json.parse(new String(r.body(),java.nio.charset.StandardCharsets.UTF_8));}
  String userId(String jwt){HttpResponse<byte[]>r=call("GET","/auth/v1/user",publishable,jwt,null,null,false);String id=Json.string(Json.map(Json.parse(new String(r.body(),java.nio.charset.StandardCharsets.UTF_8))).get("id"));if(!id.matches("[0-9a-fA-F-]{36}"))throw new Error(401,"Invalid session");return id;}
  // Recovery codes are generated/verified by Supabase Auth. They are never stored here.
  void sendRecovery(String email){call("POST","/auth/v1/recover",publishable,publishable,bytes(Map.of("email",email)),"application/json",false);}
  void resetPassword(String email,String otp,String next){
    Map<String,Object> session=Json.map(auth("/auth/v1/verify",Map.of("type","recovery","email",email,"token",otp)));
    String access=Json.string(session.get("access_token"));
    if(access.length()<20||access.startsWith("sb_"))throw new Error(401,"Invalid OTP or expired code");
    updatePassword(access,next);
  }
  private void updatePassword(String jwt,String next){
    call("PUT","/auth/v1/user",publishable,jwt,bytes(Map.of("password",next)),"application/json",false);
  }
  void changePassword(String jwt,String current,String next,String expectedId){
    Map<String,Object> who=Json.map(Json.parse(new String(call("GET","/auth/v1/user",publishable,jwt,null,null,false).body(),java.nio.charset.StandardCharsets.UTF_8)));
    String email=Json.string(who.get("email")), id=Json.string(who.get("id"));
    if(email.isEmpty()||!expectedId.equals(id))throw new Error(401,"Invalid login session");
    // Verify the old password against Supabase instead of only trusting a cached session.
    Map<String,Object> verified;
    try{verified=Json.map(login(email,current));}
    catch(Error e){if(e.status==400||e.status==401||e.status==422)throw new Error(401,"Current password incorrect");throw e;}
    String oldId=Json.string(Json.map(verified.get("user")).get("id"));
    if(!expectedId.equals(oldId))throw new Error(401,"Current password incorrect");
    updatePassword(jwt,next);
  }
  void verifyPassword(String expectedId,String password){
    Object item=db("GET","profiles?select=auth_email&id=eq."+expectedId+"&limit=1",null);
    List<Object> list=Json.list(item);
    if(list.isEmpty())throw new Error(401,"Unknown account");
    String email=Json.string(Json.map(list.get(0)).get("auth_email"));
    if(email.isEmpty())throw new Error(401,"Account has no login identifier");
    Object loginResponse=login(email,password);
    String userId=Json.string(Json.map(Json.map(loginResponse).get("user")).get("id"));
    if(!expectedId.equals(userId))throw new Error(401,"Incorrect password");
  }
    void upload(String path,byte[] raw,String mime){call("POST","/storage/v1/object/minisocial-private/"+path,secret,secret,raw,mime,false);}
  byte[] download(String path){return call("GET","/storage/v1/object/minisocial-private/"+path,secret,secret,null,null,false).body();}
  void deleteUpload(String path){try{call("DELETE","/storage/v1/object/minisocial-private/"+path,secret,secret,null,null,false);}catch(Exception ignore){}}
}
