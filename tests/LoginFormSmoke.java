import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.*;
import java.lang.reflect.*;

/** Verifies the web-style login focus and safe in-memory fields. */
public final class LoginFormSmoke {
  private static Object get(Object o,String name)throws Exception {
    Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
  }
  private static int num(Object o,String name)throws Exception{return ((Integer)get(o,name)).intValue();}
  private static void press(Object c,int code)throws Exception{
    Method m=c.getClass().getDeclaredMethod("keyPressed",int.class);m.setAccessible(true);m.invoke(c,Integer.valueOf(code));
  }
  private static void expect(boolean b,String desc){if(!b)throw new AssertionError(desc);}
  public static void main(String[] args)throws Exception {
    Canvas.TEST_WIDTH=320;Canvas.TEST_HEIGHT=240;
    MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();Object canvas=get(app,"screen");
    expect(num(app,"welcomeSelection")==0,"first focus email");
    press(canvas,-105);expect("login-email".equals(get(app,"mode")),"FIRE opens email edit");
    TextBox email=(TextBox)get(app,"editor");email.setString("khazg");
    app.commandAction((Command)get(app,"save"),email);
    expect("khazg".equals(get(app,"loginEmail")),"email stored in RAM for form");
    expect(num(app,"welcomeSelection")==1,"next focus password");
    press(canvas,-105);expect("login-password".equals(get(app,"mode")),"FIRE opens password edit");
    TextBox pass=(TextBox)get(app,"editor");pass.setString("TEST_PRIVATE_PASSWORD");
    app.commandAction((Command)get(app,"save"),pass);
    expect(num(app,"welcomeSelection")==2,"next focus submit");
    expect("TEST_PRIVATE_PASSWORD".equals(get(app,"loginPassword")),"password stored only in form RAM");
    press(canvas,-102);expect(num(app,"welcomeSelection")==3,"server link reachable");
    press(canvas,-102);expect(num(app,"welcomeSelection")==4,"signup link reachable");
    press(canvas,-102);expect(num(app,"welcomeSelection")==5,"info link reachable");
    press(canvas,-105);expect(num(app,"tab")==9,"info opens");
    press(canvas,-105);expect(num(app,"tab")==0,"info back");
    // Server selection clears credentials only on a URL change; no password in RMS.
    Method swap=MiniSocialMidlet.class.getDeclaredMethod("setServer",int.class);swap.setAccessible(true);
    swap.invoke(app,Integer.valueOf(1));
    expect("".equals(get(app,"loginPassword")),"switch server clears password");
    System.out.println("PASS login focus, input, 3 small links, info, password isolation");
  }
}
