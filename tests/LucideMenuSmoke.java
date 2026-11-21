import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.*;
import java.lang.reflect.*;
import java.util.Vector;
/** Desktop-only UI smoke test for 3 tabs, softkeys, menu, settings, history and quota. */
public final class LucideMenuSmoke {
 static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void put(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static int number(Object o,String n)throws Exception{return ((Integer)get(o,n)).intValue();}
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static void press(Object o,int n)throws Exception{Method m=o.getClass().getDeclaredMethod("keyPressed",int.class);m.setAccessible(true);m.invoke(o,n);}
 static void paint(Object c)throws Exception{Method m=c.getClass().getDeclaredMethod("paint",Graphics.class);m.setAccessible(true);Graphics g=new Graphics();m.invoke(c,g);check(g.errors==0,"Drawing out-of-bounds: "+g.errors);}
 static Vector rows(){Vector r=new Vector();r.addElement(new String[]{"id","user","Khazg","2026-11-21","1","2","Nokia Asha 302", "", "Nokia Asha 302"});return r;}
 public static void main(String[]args)throws Exception{
  int[][] sizes={{240,320},{320,240},{360,640},{640,360},{640,480}};int count=0;
  for(int[] sz:sizes){
   Canvas.TEST_WIDTH=sz[0];Canvas.TEST_HEIGHT=sz[1];
   MiniSocialMidlet a=new MiniSocialMidlet();a.startApp();Object c=get(a,"screen");
   put(a,"token","test-token");put(a,"me","user-id");put(a,"tab",Integer.valueOf(1));
   put(a,"feed",rows());put(a,"historyItems",rows());put(a,"storageUsed",Long.valueOf(55555));
   for(int tab:new int[]{1,3,4,14,15,16,17,18,19}){put(a,"tab",Integer.valueOf(tab));paint(c);count++;}
   put(a,"tab",Integer.valueOf(1));
   press(c,-6);check(((Boolean)get(a,"optionsOpen")).booleanValue(),"left softkey opens menu");paint(c);
   press(c,'5');check(number(a,"tab")==14,"option settings via keypad 5");
   press(c,-102);press(c,-105);check(((Boolean)get(a,"themeDark")).booleanValue(),"dark theme switch");paint(c);
   press(c,-7);check(number(a,"tab")==1,"back to home");
   press(c,-7);check(number(a,"tab")==19,"right softkey on selected home tab opens exit confirmation");
   press(c,-7);check(number(a,"tab")==1,"cancel exit");
   put(a,"tab",Integer.valueOf(17));press(c,'1');check("delete-password".equals(get(a,"mode")),"delete requires password editor");
  }
  System.out.println("PASS " + count + " new screen layouts, menu, theme and Nokia softkeys across 5 resolutions");
 }
}
