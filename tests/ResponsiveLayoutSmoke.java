import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.lang.reflect.*;
import java.util.Vector;

/** Desktop-only layout smoke test; does not emulate Nokia's graphics, verifier or TLS. */
public class ResponsiveLayoutSmoke {
    private static void field(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    private static Vector data(int n,String[]row){Vector v=new Vector();for(int i=0;i<n;i++)v.addElement(row);return v;}
    public static void main(String[]args)throws Exception{
        int[][] sizes={{240,320},{320,240},{360,640},{640,480}};
        Class<?> sc=Class.forName("minisocial.MiniSocialMidlet$SocialCanvas");
        Constructor<?> c=sc.getDeclaredConstructors()[0];c.setAccessible(true);
        Method paint=sc.getDeclaredMethod("paint",Graphics.class);paint.setAccessible(true);
        Field tab=MiniSocialMidlet.class.getDeclaredField("tab");tab.setAccessible(true);
        Field selection=MiniSocialMidlet.class.getDeclaredField("selection");selection.setAccessible(true);
        int screens=0,operations=0;
        for(int[]size:sizes){
            Canvas.TEST_WIDTH=size[0];Canvas.TEST_HEIGHT=size[1];
            MiniSocialMidlet app=new MiniSocialMidlet();
            field(app,"token","TEST_TOKEN");field(app,"me","uid-test");
            field(app,"feed",data(12,new String[]{"post-1","owner","Nokia Fan","2026-11-19T08:00","12","3","MiniSocial Java ME tren nhieu man hinh", ""}));
            field(app,"profilePosts",data(8,new String[]{"post-2","owner","Nokia Fan","2026-11-19T08:00","5","2","Bai viet tu Nokia E72, E6", ""}));
            field(app,"profileName","Nguyen Van Minh");field(app,"profileHandle","nokiafan");field(app,"profileBio","Yeu thich Nokia va lap trinh Java ME.");
            field(app,"friends",data(15,new String[]{"uid-peer","Nokia Fan","nokiafan"}));
            field(app,"incoming",data(8,new String[]{"uid-peer","Nokia Fan","nokiafan"}));
            field(app,"comments",data(12,new String[]{"comment-1","Nokia Fan","Binh luan demo"}));
            field(app,"messages",data(18,new String[]{"msg-1","uid-peer","Chao ban! Nokia Java ME", ""}));
            field(app,"peerName","Nokia Fan");
            Object canvas=c.newInstance(app);
            for(int t=0;t<=8;t++){
                tab.setInt(app,t);field(app,"token",t==0?"":"TEST_TOKEN");
                for(int sel:new int[]{0,6,11}){
                    selection.setInt(app,sel);
                    Graphics g=new Graphics();paint.invoke(canvas,g);
                    if(g.errors>0)throw new AssertionError(size[0]+"x"+size[1]+" tab="+t+" errors="+g.errors);
                    screens++;operations+=g.painted;
                }
            }
        }
        System.out.println("PASS " + screens + " screen configurations across four resolutions; "+operations+" draw operations checked.");
        System.out.println("Note: this is a layout smoke test, not a Nokia device/emulator validation.");
    }
}
