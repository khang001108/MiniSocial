package javax.microedition.lcdui;
/** Compile-only TextBox model used by the desktop input smoke test. */
public class TextBox extends Displayable {
  private String value;
  public TextBox(String t,String s,int max,int cons){value=s;}
  public String getString(){return value;}
  public void setString(String s){value=s;}
}
