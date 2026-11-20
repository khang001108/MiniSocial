import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.*;
import java.lang.reflect.*;

/** Confirms registration is one focusable form, never an automatic 4-step TextBox wizard. */
public class RegistrationFormSmoke {
  static Object get(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
  static void field(Object o,String key,Object value)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
  static int number(Object o,String key)throws Exception{return ((Integer)get(o,key)).intValue();}
  static void press(Object o,int k)throws Exception{Method m=o.getClass().getDeclaredMethod("keyPressed",int.class);m.setAccessible(true);m.invoke(o,Integer.valueOf(k));}
  static void submit(MiniSocialMidlet app)throws Exception{Method m=app.getClass().getDeclaredMethod("submitRegistration");m.setAccessible(true);m.invoke(app);}
  static void expect(boolean yes,String note){if(!yes)throw new AssertionError(note);}
  public static void main(String[]a)throws Exception{
    for(int[]wh:new int[][]{{240,320},{320,240},{360,640},{640,480}}){
      Canvas.TEST_WIDTH=wh[0];Canvas.TEST_HEIGHT=wh[1];
      MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();Object canvas=get(app,"screen");
      // From login, select link registration and open a single form.
      field(app,"welcomeSelection",Integer.valueOf(4));press(canvas,-105);
      expect(number(app,"tab")==10,"registration form tab for "+wh[0]+"x"+wh[1]);
      expect(get(app,"editor")==null,"registration must NOT automatically launch TextBox");
      String[]d=(String[])get(app,"registerFields");expect(d.length==3,"three persistent fields");
      // Verify native editor edits just one field and returns to Canvas form.
      press(canvas,-105);
      expect("register-field-0".equals(get(app,"mode")),"focus handle only");
      TextBox editor=(TextBox)get(app,"editor");editor.setString("khazg");
      app.commandAction((Command)get(app,"save"),editor);
      expect(number(app,"tab")==10,"returns to form");
      expect("khazg".equals(d[0]),"handle retained");
      expect(number(app,"registerSelection")==1,"moves focus to next field");
      // Direct E72 keyboard typing without launching native editor.
      for(char c : "Secret123".toCharArray())press(canvas,(int)c);
      expect("Secret123".equals(d[1]),"direct QWERTY typing into password");
      press(canvas,-102);expect(number(app,"registerSelection")==2,"navigation down");
      submit(app);expect(number(app,"registerSelection")==2,"validation focuses confirm password");
      expect(((String)get(app,"authError")).length()>0,"inline validation error");
      // Test that tiny window selected row is visible and action button is within card.
      Class<?> layoutCls=Class.forName("minisocial.MiniSocialMidlet$SocialCanvas$RegisterLayout");
      Method layout=canvas.getClass().getDeclaredMethod("registerLayout");layout.setAccessible(true);
      for(int i=0;i<=4;i++){
        field(app,"registerSelection",Integer.valueOf(i));Object l=layout.invoke(canvas);
        int first=number(l,"first"),visible=number(l,"visible");
        expect(first>=0&&visible>=1&&first+visible<=3,"registration scroll bounds");
        if(i<3)expect(i>=first&&i<first+visible,"focused field visible at index "+i);
        int y=number(l,"y"),h=number(l,"h"),by=number(l,"submitY");
        expect(by>=y&&by<y+h,"button inside card");
      }
      field(app,"registerSelection",Integer.valueOf(4));press(canvas,-105);
      expect(number(app,"tab")==0,"back to login");
      expect("".equals(d[1])&&"".equals(d[2]),"passwords erased on exit");
    }
    System.out.println("PASS registration form, field editor, QWERTY, validation, scroll, return, secrets");
  }
}
