package javax.microedition.lcdui;
/** Minimal compile-only API facade; test dimensions are not included in the packaged MIDlet. */
public abstract class Canvas extends Displayable {
    public static final int FIRE=8,UP=1,DOWN=6,LEFT=2,RIGHT=5;
    public static int TEST_WIDTH=320,TEST_HEIGHT=240;
    public void setFullScreenMode(boolean b){}
    public int getGameAction(int k){if(k==-101)return UP;if(k==-102)return DOWN;return 0;}
    public int getWidth(){return TEST_WIDTH;}
    public int getHeight(){return TEST_HEIGHT;}
    protected abstract void paint(Graphics g);
    protected void keyPressed(int k){}
    protected void sizeChanged(int w,int h){}
    protected void pointerPressed(int x,int y){}
    public void repaint(){}
}
