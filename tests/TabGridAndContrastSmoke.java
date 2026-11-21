import minisocial.MiniSocialMidlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.lang.reflect.*;
/** Tests exact header/footer paint coverage, 2x3 menu and Nokia arrows/touch. */
public final class TabGridAndContrastSmoke {
    static Object get(Object o,String key)throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    static void put(Object o,String key,Object v)throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String name,Class[] types,Object[] args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    static void key(Object c,int k)throws Exception {call(c,"keyPressed",new Class[]{int.class},new Object[]{Integer.valueOf(k)});}
    static class Coverage extends Graphics {
        int color; int[] seen;
        Coverage(int height){seen=new int[height];}
        public void setColor(int c){color=c;}
        public void fillRect(int x,int y,int w,int h){super.fillRect(x,y,w,h);if(x==0 && w==Canvas.TEST_WIDTH)for(int k=Math.max(0,y);k<Math.min(seen.length,y+h);k++)seen[k]=color;}
    }
    public static void main(String[]args)throws Exception {
        int[][] sizes={{240,320},{320,240},{360,640},{640,360},{480,640},{640,480}};
        int counter=0;
        for(int[] xy:sizes){
            Canvas.TEST_WIDTH=xy[0];Canvas.TEST_HEIGHT=xy[1];
            MiniSocialMidlet a=new MiniSocialMidlet();a.startApp();Object c=get(a,"screen");
            put(a,"token","token");put(a,"tab",Integer.valueOf(1));put(a,"navFocus",Boolean.TRUE);
            call(c,"layout",new Class[0],new Object[0]);
            int header=((Integer)get(c,"headerH")).intValue(),footer=((Integer)get(c,"footerH")).intValue();
            Coverage top=new Coverage(xy[1]);
            call(c,"grad",new Class[]{Graphics.class,int.class,int.class},new Object[]{top,Integer.valueOf(0),Integer.valueOf(header)});
            for(int y=0;y<header;y++)ok(top.seen[y]!=0,"Header background missing: "+xy[0]+"x"+xy[1]+" y="+y);
            Coverage bottom=new Coverage(xy[1]);
            call(c,"grad",new Class[]{Graphics.class,int.class,int.class},new Object[]{bottom,Integer.valueOf(xy[1]-footer),Integer.valueOf(footer)});
            for(int y=xy[1]-footer;y<xy[1];y++)ok(bottom.seen[y]!=0,"Footer background missing: "+xy[0]+"x"+xy[1]+" y="+y);
            key(c,-6);ok(((Boolean)get(a,"optionsOpen")).booleanValue(),"Tab opens");
            Graphics g=new Graphics();call(c,"paint",new Class[]{Graphics.class},new Object[]{g});ok(g.errors==0,"Grid overflow at "+xy[0]+"x"+xy[1]);
            int x=((Integer)call(c,"tabSheetX",new Class[0],new Object[0])).intValue(), margin=((Integer)call(c,"tabSheetMargin",new Class[0],new Object[0])).intValue();
            int cellW=((Integer)call(c,"tabSheetCellW",new Class[0],new Object[0])).intValue(),cellH=((Integer)call(c,"tabSheetCellH",new Class[0],new Object[0])).intValue(),gap=((Integer)call(c,"tabSheetGap",new Class[0],new Object[0])).intValue();
            int sheetY=((Integer)call(c,"tabSheetTop",new Class[0],new Object[0])).intValue(),titleH=((Integer)call(c,"tabSheetTitleH",new Class[0],new Object[0])).intValue();
            for(int i=0;i<6;i++){
                int cx=x+margin+(i%2)*(cellW+gap)+cellW/2;
                int cy=sheetY+titleH+margin+(i/2)*(cellH+gap)+cellH/2;
                int idx=((Integer)call(c,"tabSheetIndexAt",new Class[]{int.class,int.class},new Object[]{Integer.valueOf(cx),Integer.valueOf(cy)})).intValue();
                ok(idx==i,"Grid index wrong at "+xy[0]+"x"+xy[1]+" index "+i+" got "+idx);
                counter++;
            }
            key(c,-104);ok(((Integer)get(a,"optionsSelection")).intValue()==1,"Right moves to next column");
            key(c,-102);ok(((Integer)get(a,"optionsSelection")).intValue()==3,"Down moves to next row");
            key(c,-103);ok(((Integer)get(a,"optionsSelection")).intValue()==2,"Left moves to previous column");
            key(c,-101);ok(((Integer)get(a,"optionsSelection")).intValue()==0,"Up moves to previous row");
            key(c,'5');ok(((Integer)get(a,"tab")).intValue()==14,"Shortcut 5 must open Settings");
            key(c,-7);ok(((Integer)get(a,"tab")).intValue()==1,"Back from Settings");
            key(c,-6);
            int index=4,cx=x+margin+(index%2)*(cellW+gap)+cellW/2,cy=sheetY+titleH+margin+(index/2)*(cellH+gap)+cellH/2;
            call(c,"pointerPressed",new Class[]{int.class,int.class},new Object[]{Integer.valueOf(cx),Integer.valueOf(cy)});
            ok(((Integer)get(a,"tab")).intValue()==14,"Touch Settings tile");
        }
        System.out.println("PASS 6 resolutions, 36 tile hitboxes, contrast coverage, grid arrows, shortcut and touch");
    }
}
