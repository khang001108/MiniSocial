import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.*;
import java.lang.reflect.*;
/** Tests recovery + signed-in password change forms without hitting network. */
public final class PasswordFormsSmoke {
  private static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
  private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
  private static int num(Object o,String n)throws Exception{return ((Integer)get(o,n)).intValue();}
  private static void call(Object o,String m)throws Exception{Method f=o.getClass().getDeclaredMethod(m);f.setAccessible(true);f.invoke(o);}
  private static void key(Object o,int n)throws Exception{Method f=o.getClass().getDeclaredMethod("keyPressed",int.class);f.setAccessible(true);f.invoke(o,Integer.valueOf(n));}
  private static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
  public static void main(String[] args)throws Exception{
    for(int[] wh:new int[][]{{240,320},{320,240},{360,640},{640,480}}){
      Canvas.TEST_WIDTH=wh[0];Canvas.TEST_HEIGHT=wh[1];
      MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();Object canvas=get(app,"screen");
      set(app,"welcomeSelection",Integer.valueOf(6));key(canvas,-105);
      ok(num(app,"tab")==11,"forgot page on "+wh[0]);
      ok(get(app,"editor")==null,"persistent form (not wizard)");
      String[] recover=(String[])get(app,"recoveryFields");
      ok(recover.length==4,"recovery form fields");
      key(canvas,-105);ok("recovery-field-0".equals(get(app,"mode")),"edit email");
      TextBox editor=(TextBox)get(app,"editor");editor.setString("me@sample.com");
      app.commandAction((Command)get(app,"save"),editor);
      ok("me@sample.com".equals(recover[0]),"email entered");
      set(app,"recoverySelection",Integer.valueOf(1));
      key(canvas,-105);ok("recovery-field-1".equals(get(app,"mode")),"edit OTP");
      editor=(TextBox)get(app,"editor");editor.setString("123456");app.commandAction((Command)get(app,"save"),editor);
      ok("123456".equals(recover[1]),"OTP entered");
      // Local validation must not contact server.
      call(app,"finishRecovery");ok(num(app,"recoverySelection")==2,"reset needs password");
      // The old form must not persist passwords on back.
      key(canvas,-7);ok(num(app,"tab")==0,"go back login");
      ok("".equals(((String[])get(app,"recoveryFields"))[1]),"OTP cleared");
      // Simulate an authenticated owner and open the change-password form.
      set(app,"token","example-token-only-in-RAM");call(app,"openChange");
      ok(num(app,"tab")==12,"change form tab");
      String[] change=(String[])get(app,"changeFields");ok(change.length==3,"old new confirm");
      set(app,"changeSelection",Integer.valueOf(1));key(canvas,-105);
      ok("change-field-1".equals(get(app,"mode")),"edit next password");
      editor=(TextBox)get(app,"editor");editor.setString("new_password_secret");app.commandAction((Command)get(app,"save"),editor);
      ok("new_password_secret".equals(change[1]),"new password held in RAM");
      key(canvas,-7);ok(num(app,"tab")==2,"return to profile");
      ok("".equals(((String[])get(app,"changeFields"))[1]),"new password cleared");
    }
    System.out.println("PASS OTP/recovery/change forms, keyboard, secret cleanup in 4 resolutions");
  }
}
