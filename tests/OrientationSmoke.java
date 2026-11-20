import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.lang.reflect.*;
import java.util.Vector;

/** Desktop-only smoke tests: same app/Canvas survives portrait-landscape changes. */
public final class OrientationSmoke {
    private static Field getField(Class<?> c,String n)throws Exception {
        Field f=c.getDeclaredField(n);f.setAccessible(true);return f;
    }
    private static void put(Object o,String n,Object value)throws Exception {
        getField(o.getClass(),n).set(o,value);
    }
    private static Object get(Object o,String n)throws Exception {
        return getField(o.getClass(),n).get(o);
    }
    private static void ok(boolean condition,String description){
        if(!condition)throw new AssertionError(description);
    }
    private static Vector messages(){
        Vector v=new Vector();
        v.addElement(new String[]{"m1","someone","Xin chao!", "", "2026-11-21T10:12:00", "E72",""});
        v.addElement(new String[]{"m2","me","Tin nhan tieng Viet rat dai, co the hien thi tren nhieu dong, khong cat doi, nhat la khi xoay man hinh mot cach lien tuc tai E72 va Nokia E6, 240x320 640x360 360x640.","", "2026-11-21T10:15:00", "E6", "read"});
        return v;
    }
    public static void main(String[] args)throws Exception {
        int[][] sizes={{320,240},{240,320},{320,240},{640,360},{360,640},{640,360},{640,480},{480,640},{640,480}};
        String[] expected={"e72","e72","e72","wide","wide","wide","e6","e6","e6"};
        Canvas.TEST_WIDTH=sizes[0][0];Canvas.TEST_HEIGHT=sizes[0][1];
        MiniSocialMidlet app=new MiniSocialMidlet();app.startApp();
        put(app,"token","TEST_TOKEN");put(app,"me","me");
        put(app,"tab",Integer.valueOf(4));put(app,"peer","someone");
        put(app,"peerName","Alex");put(app,"messages",messages());
        Object canvas=get(app,"screen");
        Class<?> cl=canvas.getClass();
        Method change=cl.getDeclaredMethod("sizeChanged",int.class,int.class);change.setAccessible(true);
        Method paint=cl.getDeclaredMethod("paint",Graphics.class);paint.setAccessible(true);
        Method tier=cl.getDeclaredMethod("uiDevice");tier.setAccessible(true);
        int drawCalls=0;
        for(int i=0;i<sizes.length;i++){
            int w=sizes[i][0],h=sizes[i][1];
            Canvas.TEST_WIDTH=w;Canvas.TEST_HEIGHT=h;
            change.invoke(canvas,Integer.valueOf(w),Integer.valueOf(h));
            ok(((Integer)get(canvas,"cw")).intValue()==w,"Wrong width "+w);
            ok(((Integer)get(canvas,"ch")).intValue()==h,"Wrong height "+h);
            ok(((Boolean)get(canvas,"portrait")).booleanValue()==(h>w),"Orientation mismatch");
            ok(expected[i].equals(tier.invoke(canvas)),"Incorrect tier for "+w+"x"+h);
            ok(((Integer)get(app,"tab")).intValue()==4,"Tab lost on rotation");
            ok("someone".equals(get(app,"peer")),"Chat peer lost on rotation");
            Graphics g=new Graphics();paint.invoke(canvas,g);
            ok(g.errors==0,"Draw outside screen at "+w+"x"+h+" errors="+g.errors);
            drawCalls+=g.painted;
        }
        System.out.println("PASS orientation changes on same Canvas, 9 viewport transitions, "+drawCalls+" drawing operations; tab/chat maintained");
    }
}
