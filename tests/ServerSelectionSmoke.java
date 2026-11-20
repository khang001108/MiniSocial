import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.Canvas;
import java.lang.reflect.*;

/** Simulated keypad navigation and server URL checks; not a handset test. */
public final class ServerSelectionSmoke {
  private static Object get(Object o,String n)throws Exception{
    Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);
  }
  private static void set(Object o,String n,Object v)throws Exception{
    Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); f.set(o,v);
  }
  private static int number(Object o,String n)throws Exception{return ((Integer)get(o,n)).intValue();}
  private static void expect(boolean b,String msg){if(!b)throw new AssertionError(msg);}
  private static void press(Object screen,int key)throws Exception{
    Method m=screen.getClass().getDeclaredMethod("keyPressed",int.class);m.setAccessible(true);m.invoke(screen,Integer.valueOf(key));
  }
  private static String normalize(MiniSocialMidlet app,String url,int mode)throws Exception{
    Method m=MiniSocialMidlet.class.getDeclaredMethod("normalizeHost",String.class,int.class);m.setAccessible(true);
    return (String)m.invoke(app,url,Integer.valueOf(mode));
  }
  private static void reject(MiniSocialMidlet app,String url,int mode)throws Exception{
    try{normalize(app,url,mode);throw new AssertionError("Accepted insecure URL: "+url);}
    catch(InvocationTargetException expected){}
  }
  public static void main(String[]args)throws Exception{
    Canvas.TEST_WIDTH=320;Canvas.TEST_HEIGHT=240;
    MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();
    Object screen=get(app,"screen");
    expect(number(app,"tab")==0,"welcome tab");
    press(screen,-102);expect(number(app,"welcomeSelection")==1,"down moves welcome selection");
    press(screen,-102);expect(number(app,"welcomeSelection")==2,"second down moves to servers");
    press(screen,-105);expect(number(app,"tab")==8,"FIRE opens server chooser");
    press(screen,-102);expect(number(app,"serverSelection")==1,"down selects Wi-Fi/LAN");
    press(screen,-105);expect(number(app,"tab")==0,"FIRE applies server");
    expect("http://192.168.1.127:18087".equals(get(app,"host")),"LAN host applied");
    set(app,"token","test-token-for-other-server");set(app,"me","test-user");set(app,"tab",Integer.valueOf(8));set(app,"serverSelection",Integer.valueOf(0));
    press(screen,-105);
    expect("https://minisocial.xubi.org".equals(get(app,"host")),"public host applied");
    expect("".equals(get(app,"token")),"changing server clears previous token");
    expect("http://192.168.1.127:18087".equals(normalize(app,"192.168.1.127:18087",1)),"LAN URL shortcut");
    expect("https://minisocial.xubi.org".equals(normalize(app,"minisocial.xubi.org",0)),"public URL shortcut");
    reject(app,"http://minisocial.xubi.org",0);
    reject(app,"http://192.168.bad.example",1);
    reject(app,"socket://minisocial.xubi.org:22123",0);
    reject(app,"http://123.45.67.89:18087",1);
    reject(app,"https://minisocial.xubi.org/m/login",0);
    System.out.println("PASS server selection, FIRE, saved server isolation, URL validation");
  }
}
