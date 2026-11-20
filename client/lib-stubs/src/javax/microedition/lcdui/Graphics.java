package javax.microedition.lcdui;
/** Compile-only facade with optional lightweight layout checks for the desktop smoke test. */
public class Graphics {
    public static final int TOP=16,LEFT=4;
    private Font font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
    private int clipX=0,clipY=0,clipW=Canvas.TEST_WIDTH,clipH=Canvas.TEST_HEIGHT;
    public int getClipX(){return clipX;}
    public int getClipY(){return clipY;}
    public int getClipWidth(){return clipW;}
    public int getClipHeight(){return clipH;}
    public void setClip(int x,int y,int w,int h){
        clipX=Math.max(0,x);clipY=Math.max(0,y);
        clipW=Math.max(0,Math.min(Canvas.TEST_WIDTH, x+w)-clipX);
        clipH=Math.max(0,Math.min(Canvas.TEST_HEIGHT, y+h)-clipY);
    }
    public int painted=0,errors=0;
    public void setColor(int c){}
    private void validate(int x,int y,int w,int h){
        painted++;
        if(w<0||h<0){errors++;return;}
        // Offscreen source geometry is valid if the actual Graphics clip hides it.
        int visibleL=Math.max(x,clipX), visibleT=Math.max(y,clipY);
        int visibleR=Math.min(x+w,clipX+clipW),visibleB=Math.min(y+h,clipY+clipH);
        if(visibleL>=visibleR||visibleT>=visibleB)return;
        if(visibleL<0||visibleT<0||visibleR>Canvas.TEST_WIDTH+2||visibleB>Canvas.TEST_HEIGHT+2){
            errors++;System.err.println("Visible out of screen: "+x+","+y+" "+w+"x"+h);
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
