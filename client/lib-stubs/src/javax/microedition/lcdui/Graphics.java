package javax.microedition.lcdui;
/** Compile-only facade with optional lightweight layout checks for the desktop smoke test. */
public class Graphics {
    public static final int TOP=16,LEFT=4;
    private Font font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
    public int painted=0,errors=0;
    public void setColor(int c){}
    private void validate(int x,int y,int w,int h){
        painted++;
        if(x<0||y<0||w<0||h<0||x+w>Canvas.TEST_WIDTH+2||y+h>Canvas.TEST_HEIGHT+2){
            errors++;
            System.err.println("Out of screen: "+x+","+y+" "+w+"x"+h+" in "+Canvas.TEST_WIDTH+"x"+Canvas.TEST_HEIGHT);
        }
    }
    public void drawLine(int x1,int y1,int x2,int y2){validate(Math.min(x1,x2),Math.min(y1,y2),Math.abs(x2-x1),Math.abs(y2-y1));}
    public void drawArc(int x,int y,int w,int h,int startAngle,int arcAngle){validate(x,y,w,h);}
    public void drawRoundRect(int x,int y,int w,int h,int arcWidth,int arcHeight){validate(x,y,w,h);}
    public void fillRoundRect(int x,int y,int w,int h,int arcWidth,int arcHeight){validate(x,y,w,h);}
    public void fillRect(int x,int y,int w,int h){validate(x,y,w,h);}
    public void drawRect(int x,int y,int w,int h){validate(x,y,w,h);}
    public void setFont(Font f){font=f;}
    public void drawString(String s,int x,int y,int a){validate(x,y,Math.min(Canvas.TEST_WIDTH-x,Math.max(0,font.stringWidth(s))),font.getHeight());}
    public void drawImage(Image i,int x,int y,int a){validate(x,y,i.getWidth(),i.getHeight());}
}
