import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.*;
import java.lang.reflect.*;

/** Unit test for compulsory first-login onboarding layout, no real Supabase I/O. */
public class OnboardingSmoke {
 static Object get(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void put(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static int num(Object o,String n)throws Exception {return ((Integer)get(o,n)).intValue();}
 static void press(Object o,int k)throws Exception {Method m=o.getClass().getDeclaredMethod("keyPressed",int.class);m.setAccessible(true);m.invoke(o,Integer.valueOf(k));}
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] a)throws Exception {
  for(int[] wh:new int[][]{{240,320},{320,240},{360,640},{640,480}}){
   Canvas.TEST_WIDTH=wh[0];Canvas.TEST_HEIGHT=wh[1];
   MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();Object canvas=get(app,"screen");
   put(app,"token","dummy-session");put(app,"me","uid");
   put(app,"onboardingFields",new String[]{"","Nokia Asha 302",""});put(app,"tab",Integer.valueOf(13));
   press(canvas,-105);check("setup-field-0".equals(get(app,"mode")),"edit display name");
   TextBox e=(TextBox)get(app,"editor");e.setString("khazg");app.commandAction((Command)get(app,"save"),e);
   check("khazg".equals(((String[])get(app,"onboardingFields"))[0]),"display name retained");
   check(num(app,"onboardingSelection")==1,"move to model");
   press(canvas,-105);check("setup-field-1".equals(get(app,"mode")),"edit phone model");
   e=(TextBox)get(app,"editor");e.setString("Nokia Asha 302");app.commandAction((Command)get(app,"save"),e);
   check(num(app,"onboardingSelection")==2,"move to avatar path");
   press(canvas,-105);check("setup-field-2".equals(get(app,"mode")),"edit avatar path");
   e=(TextBox)get(app,"editor");e.setString("file:///E:/Images/avatar.jpg");app.commandAction((Command)get(app,"save"),e);
   Method layout=canvas.getClass().getDeclaredMethod("registerLayout");layout.setAccessible(true);Object l=layout.invoke(canvas);
   Field v=l.getClass().getDeclaredField("visible");v.setAccessible(true);
   check(((Integer)v.get(l)).intValue()>=1,"onboarding layout visible");
  }
  System.out.println("PASS onboarding fields and navigation in four resolutions");
 }
}
