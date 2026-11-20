import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.lang.reflect.*;
import java.util.Vector;

/** Geometry & keypad/touch behavior across 6 requested viewports; desktop stubs, not a handset emulator. */
public final class AdaptiveChatSmoke {
    static Object get(Object o,String name)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    static void put(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void fire(Object o,String method,Class<?>[] types,Object...args)throws Exception {
        Method m=o.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(o,args);
    }
    static int number(Object o,String name)throws Exception{return ((Integer)get(o,name)).intValue();}
    static Vector rows(){
        Vector v=new Vector();
        for(int i=0;i<13;i++)v.addElement(new String[]{"friend-"+i,"Bạn "+i,"ban"+i,"Xin chào từ Nokia!", "2026-11-21T09:11:00"});
        return v;
    }
    static Vector longMessages(){
        Vector v=new Vector();
        String text="Một tin nhắn dài thử xuống dòng: ";
        for(int j=0;j<9;j++)text+="Từ Nokia E72, E6, màn hình ngang và dọc, đọc hết tin nhắn. ";
        for(int i=0;i<22;i++)v.addElement(new String[]{"id"+i,i%2==0?"me":"friend-0",text,"","2026-11-21T09:11:00","Nokia", ""});
        return v;
    }
    public static void main(String[]args)throws Exception {
        int[][] sizes={{240,320},{320,240},{360,640},{640,360},{480,640},{640,480}};
        int paints=0,scrollChecks=0;
        for(int[] size:sizes){
            Canvas.TEST_WIDTH=size[0];Canvas.TEST_HEIGHT=size[1];
            MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();
            put(app,"token","test-token");put(app,"me","me");
            put(app,"conversations",rows());put(app,"messages",longMessages());
            put(app,"peer","friend-0");put(app,"peerName","Bạn 0");put(app,"tab",Integer.valueOf(4));
            Object c=get(app,"screen");
            fire(c,"sizeChanged",new Class[]{int.class,int.class},Integer.valueOf(size[0]),Integer.valueOf(size[1]));
            Graphics g=new Graphics();fire(c,"paint",new Class[]{Graphics.class},g);
            check(g.errors==0,"Chat paint out of bounds: "+size[0]+"x"+size[1]);
            paints++;
            boolean shouldSplit=size[0]>=560&&size[0]>size[1];
            Method split=c.getClass().getDeclaredMethod("splitChat");split.setAccessible(true);
            check(((Boolean)split.invoke(c)).booleanValue()==shouldSplit,"Split orientation wrong");
            check(number(c,"chatMaxScroll")>300,"History should be scrollable");
            fire(c,"keyPressed",new Class[]{int.class},Integer.valueOf(-101));
            check(number(c,"chatScrollPx")>0,"UP must reveal old messages");scrollChecks++;
            fire(c,"keyPressed",new Class[]{int.class},Integer.valueOf(-102));
            check(number(c,"chatScrollPx")==0,"DOWN must return to newest messages");scrollChecks++;
            fire(c,"keyPressed",new Class[]{int.class},Integer.valueOf('#'));
            check("test-token".equals(get(app,"token")),"# must not log out");
            fire(c,"pointerPressed",new Class[]{int.class,int.class},Integer.valueOf(size[0]-25),Integer.valueOf(number(c,"bodyTop")+100));
            fire(c,"pointerDragged",new Class[]{int.class,int.class},Integer.valueOf(size[0]-25),Integer.valueOf(number(c,"bodyTop")+55));
            check(number(c,"chatScrollPx")>0,"Swipe must reveal old messages");scrollChecks++;
            fire(c,"pointerReleased",new Class[]{int.class,int.class},Integer.valueOf(size[0]-25),Integer.valueOf(60));
            // Switch to list on same Canvas (no async reload) and verify arrow selection.
            put(app,"peer","");put(app,"tab",Integer.valueOf(4));put(app,"selection",Integer.valueOf(0));
            g=new Graphics();fire(c,"paint",new Class[]{Graphics.class},g);
            check(g.errors==0,"Conversation list out of bounds");paints++;
            fire(c,"keyPressed",new Class[]{int.class},Integer.valueOf(-102));
            check(number(app,"selection")==1,"Down selects next conversation");
            // Simulate rotation with active chat preserved.
            put(app,"peer","friend-0");put(app,"peerName","Bạn 0");
            int w=size[1],h=size[0];Canvas.TEST_WIDTH=w;Canvas.TEST_HEIGHT=h;
            fire(c,"sizeChanged",new Class[]{int.class,int.class},Integer.valueOf(w),Integer.valueOf(h));
            g=new Graphics();fire(c,"paint",new Class[]{Graphics.class},g);
            check(g.errors==0,"Rotated chat out of bounds");paints++;
            check("friend-0".equals(get(app,"peer")),"Peer lost on rotation");
        }
        System.out.println("PASS adaptive chat: "+paints+" layouts, "+scrollChecks+" scrolling checks, split/portrait, # safety and rotation");
    }
}
