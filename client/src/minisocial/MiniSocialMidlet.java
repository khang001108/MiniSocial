package minisocial;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.Vector;

/** Responsive MiniSocial for 240x320, 320x240, 360x640 and 640x480, CLDC 1.1 / MIDP 2.0. */
public final class MiniSocialMidlet extends MIDlet implements CommandListener {
    private Display display; private SocialCanvas screen;
    private String host="https://minisocial.xubi.org";
    private String token="", me="", myName="";
    private TextBox editor;private final Command save=new Command("OK",Command.OK,1),cancel=new Command("Huy",Command.CANCEL,2);
    private String mode="", target="",pendingMedia="";
    private int entryStep=0; private String[] entry=new String[4];
    private boolean busy=false;private String notification="";
    private Vector feed=new Vector(),profilePosts=new Vector(), friends=new Vector(),incoming=new Vector(),messages=new Vector(),comments=new Vector();
    private String peer="",peerName="",profileId="",profileName="",profileHandle="",profileBio="";
    private int tab=1,selection=0;private String photoId="";

    public void startApp(){
        display=Display.getDisplay(this);String x=getAppProperty("MiniSocial-Api-Url");if(x!=null&&x.startsWith("https://"))host=x;
        while(host.endsWith("/"))host=host.substring(0,host.length()-1);
        readSession();screen=new SocialCanvas();display.setCurrent(screen);if(token.length()>0)reload();
    }
    public void pauseApp(){}
    public void destroyApp(boolean unconditional){}
    private void readSession(){try{RecordStore r=RecordStore.openRecordStore("ms_session",true);if(r.getNumRecords()>0){String[]a=split(new String(r.getRecord(1),"UTF-8"),'\n');if(a.length>=2){me=a[0];token=a[1];}}r.closeRecordStore();}catch(Exception ignored){}}
    private void saveSession(){try{RecordStore r=RecordStore.openRecordStore("ms_session",true);byte[]v=(me+"\n"+token).getBytes("UTF-8");if(r.getNumRecords()==0)r.addRecord(v,0,v.length);else r.setRecord(1,v,0,v.length);r.closeRecordStore();}catch(Exception ignored){}}
    private void logout(){token="";me="";myName="";saveSession();tab=0;screen.repaint();}
    private interface Task{void run()throws Exception;}
    private void work(final Task t){if(busy)return;busy=true;notification="";screen.repaint();new Thread(new Runnable(){public void run(){try{t.run();}catch(Exception e){notification=e.getMessage();}finally{busy=false;screen.repaint();}}}).start();}
    private static String encode(String v){try{byte[]b=v.getBytes("UTF-8");StringBuffer s=new StringBuffer();String hex="0123456789ABCDEF";for(int i=0;i<b.length;i++){int c=b[i]&255;if(c>=65&&c<=90||c>=97&&c<=122||c>=48&&c<=57||c==45||c==95||c==46){s.append((char)c);}else if(c==32)s.append('+');else{s.append('%');s.append(hex.charAt(c>>4));s.append(hex.charAt(c&15));}}return s.toString();}catch(Exception e){return "";}}
    private static String decode(String v){try{ByteArrayOutputStream b=new ByteArrayOutputStream();for(int i=0;i<v.length();i++){char c=v.charAt(i);if(c=='+')b.write(32);else if(c=='%'&&i+2<v.length()){int n=Integer.parseInt(v.substring(i+1,i+3),16);b.write(n);i+=2;}else {byte[]raw=String.valueOf(c).getBytes("UTF-8");b.write(raw,0,raw.length);}}return new String(b.toByteArray(),"UTF-8");}catch(Exception e){return v;}}
    private static String[] split(String s,char ch){Vector v=new Vector();int a=0;for(int i=0;i<s.length();i++)if(s.charAt(i)==ch){v.addElement(s.substring(a,i));a=i+1;}v.addElement(s.substring(a));String[]r=new String[v.size()];v.copyInto(r);return r;}
    private static String[] unpack(String line){String[]f=split(line,'|');for(int i=0;i<f.length;i++)f[i]=decode(f[i]);return f;}
    private byte[] network(String path,String data,byte[]upload,String mime)throws Exception{
        HttpConnection c=null;InputStream in=null;OutputStream os=null;
        try{
            c=(HttpConnection)Connector.open(host+path,Connector.READ_WRITE,true);
            boolean isPost=data!=null||upload!=null;c.setRequestMethod(isPost?HttpConnection.POST:HttpConnection.GET);
            c.setRequestProperty("Accept","text/plain");if(token.length()>0)c.setRequestProperty("Authorization","Bearer "+token);
            if(isPost){c.setRequestProperty("Content-Type",mime==null?"application/x-www-form-urlencoded; charset=UTF-8":mime);os=c.openOutputStream();byte[]payload=upload==null?data.getBytes("UTF-8"):upload;os.write(payload);os.flush();os.close();os=null;}
            int status=c.getResponseCode();in=c.openInputStream();ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[]bytes=new byte[1024];int n;int max=upload==null&&mime==null&&path.startsWith("/m/photo")?210000:120000;
            while((n=in.read(bytes))!=-1){if(buf.size()+n>max)throw new Exception("Response too big");buf.write(bytes,0,n);}
            byte[]raw=buf.toByteArray();if(status!=200){String err=new String(raw,"UTF-8");String[]a=unpack(err);throw new Exception(a.length>1?a[1]:"HTTP "+status);}return raw;
        }finally{try{if(in!=null)in.close();}catch(Exception e){}try{if(os!=null)os.close();}catch(Exception e){}try{if(c!=null)c.close();}catch(Exception e){}}
    }
    private String request(String path,String data)throws Exception{return new String(network(path,data,null,null),"UTF-8");}
    private String[] single(String path,String data)throws Exception{String[]v=unpack(request(path,data).trim());if(v.length==0||!v[0].equals("OK"))throw new Exception(v.length>1?v[1]:"Bad server reply");return v;}
    private Vector listing(String path)throws Exception{String raw=request(path,null);String[]lines=split(raw,'\n');if(lines.length==0||!lines[0].startsWith("OK"))throw new Exception("Bad list");Vector out=new Vector();for(int i=1;i<lines.length;i++){if(lines[i].length()>0)out.addElement(unpack(lines[i]));}return out;}
    private void reload(){if(token.length()==0){tab=0;screen.repaint();return;}final int requested=tab;work(new Task(){public void run()throws Exception{
        if(requested==1)feed=listing("/m/feed");
        else if(requested==2){String[]p=single("/m/profile?user="+encode(profileId.length()==0?me:profileId),null);profileName=p[2];profileHandle=p[3];profileBio=p[4];profilePosts=listing("/m/profileposts?user="+encode(p[1]));}
        else if(requested==3)friends=listing("/m/friends");
        else if(requested==4){if(peer.length()>0)messages=listing("/m/chat?user="+encode(peer));}
        else if(requested==5){}else if(requested==6)comments=listing("/m/comments?post="+encode(target));
        else if(requested==7)incoming=listing("/m/requests");
    }});}
    private void navigate(int t){if(token.length()==0){tab=0;screen.repaint();return;}tab=t;selection=0;reload();}
    private void edit(String m,String title,int constraints){mode=m;editor=new TextBox(title,"",500,constraints);editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);}
    private void auth(boolean signup){entryStep=0;entry=new String[4];edit(signup?"register":"login",signup?"Ten hien thi":"Email",TextField.ANY);}
    public void commandAction(Command cmd,Displayable item){if(cmd==cancel){display.setCurrent(screen);return;}if(cmd!=save)return;
        final String value=editor.getString();final String selected=mode;
        if(selected.equals("login")){if(entryStep==0){entry[0]=value;entryStep++;edit("login","Mat khau",TextField.ANY|TextField.PASSWORD);return;}final String email=entry[0],pass=value;display.setCurrent(screen);work(new Task(){public void run()throws Exception{String[]r=single("/m/login","email="+encode(email)+"&password="+encode(pass));me=r[1];token=r[2];saveSession();tab=1;feed=listing("/m/feed");}});return;}
        if(selected.equals("register")){entry[entryStep++]=value;if(entryStep<4){String[]names={"Ten hien thi","Tai khoan (@handle)","Email","Mat khau (>=8)"};edit("register",names[entryStep],entryStep==3?TextField.ANY|TextField.PASSWORD:TextField.ANY);return;}
            display.setCurrent(screen);final String[]a=entry;work(new Task(){public void run()throws Exception{single("/m/register","name="+encode(a[0])+"&handle="+encode(a[1])+"&email="+encode(a[2])+"&password="+encode(a[3]));notification="Da dang ky, hay xac nhan email va dang nhap";tab=0;}});return;}
        display.setCurrent(screen);
        if(selected.equals("post")){final String data="body="+encode(value)+"&media="+encode(pendingMedia);work(new Task(){public void run()throws Exception{single("/m/post",data);pendingMedia="";feed=listing("/m/feed");}});}
        else if(selected.equals("chat")){final String peer0=peer;work(new Task(){public void run()throws Exception{single("/m/message","user="+encode(peer0)+"&body="+encode(value));messages=listing("/m/chat?user="+encode(peer0));}});}
        else if(selected.equals("comment")){final String id0=target;work(new Task(){public void run()throws Exception{single("/m/comments","post="+encode(id0)+"&body="+encode(value));comments=listing("/m/comments?post="+encode(id0));}});}
        else if(selected.equals("profile-name")||selected.equals("profile-bio")){final String data="name="+encode(selected.equals("profile-name")?value:profileName)+"&bio="+encode(selected.equals("profile-bio")?value:profileBio);work(new Task(){public void run()throws Exception{single("/m/profile",data);String[]p=single("/m/profile?user="+encode(me),null);profileName=p[2];profileBio=p[4];}});}
        else if(selected.equals("friend")){work(new Task(){public void run()throws Exception{single("/m/friend","handle="+encode(value));notification="Da gui loi moi";}});}
        else if(selected.equals("photo-post")||selected.equals("photo-chat")){final String path=value;final boolean chat=selected.equals("photo-chat");final String peer0=peer;work(new Task(){public void run()throws Exception{
            byte[]data=readFile(path);String low=path.toLowerCase();String mime=low.endsWith(".png")?"image/png":"image/jpeg";
            String[]res=unpack(new String(network("/m/upload?scope="+(chat?"chat&user="+encode(peer0):"post"),null,data,mime),"UTF-8"));if(res.length<2||!res[0].equals("OK"))throw new Exception("Upload failed");
            if(chat){single("/m/message","user="+encode(peer0)+"&media="+encode(res[1]));messages=listing("/m/chat?user="+encode(peer0));}
            else {pendingMedia=res[1];notification="Anh da tai len; an 2 de dang bai";}
        }});}
    }
    private byte[]readFile(String path)throws Exception{String url=path.startsWith("file:")?path:"file:///"+path;Connection c=null;InputStream in=null;try{c=Connector.open(url);if(!(c instanceof InputConnection))throw new Exception("JSR-75 FileConnection unavailable");in=((InputConnection)c).openInputStream();ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[]bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1){if(buf.size()+n>180*1024)throw new Exception("Gioi han 180 KB / anh");buf.write(bytes,0,n);}return buf.toByteArray();}finally{if(in!=null)in.close();if(c!=null)c.close();}}
    private static int BLUE=0x0750B6, DARK=0x033C8C, LIGHT=0xD7E7FF, TEXT=0x15233C;
    private static int num(String v){try{return Integer.parseInt(v);}catch(Exception e){return 0;}}
    private static String piece(String[]f,int ix){return ix<f.length?f[ix]:"";}
    /**
     * Screen-size aware Java ME Canvas. All positions are calculated from the
     * actual canvas width/height; no assumption that a Nokia is 320x240.
     * MIDP text uses platform fonts (there is no portable Graphics.scale()).
     */
    private final class SocialCanvas extends Canvas {
        private Font small, normal, bold, title;
        private Image photo;
        private String loadedPhoto="";
        private int cw,ch,pad,headerH,navH,footerH,bodyTop,bodyBottom,cardH,rowH;
        private boolean portrait,narrow,large,profilePostsMode=false;
        SocialCanvas(){setFullScreenMode(true);layout();}
        private void layout(){
            cw=Math.max(1,getWidth());ch=Math.max(1,getHeight());
            portrait=ch>cw;narrow=cw<280;large=(cw>=480||ch>=480);
            pad=large?(cw>=480?12:9):5;
            headerH=large?(cw>=480?56:47):34;
            navH=narrow?(large?66:54):(large?43:28);
            footerH=large?38:24;
            bodyTop=headerH+navH+4;bodyBottom=ch-footerH-3;
            small=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,large?Font.SIZE_MEDIUM:Font.SIZE_SMALL);
            normal=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            title=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            cardH=large?(portrait?115:112):(portrait?95:84);
            rowH=large?65:(portrait?48:40);
        }
        protected void sizeChanged(int w,int h){layout();repaint();}
        private int min(int a,int b){return a<b?a:b;}
        private int max(int a,int b){return a>b?a:b;}
        private void fill(Graphics g,int color,int x,int y,int w,int h){if(w<=0||h<=0)return;g.setColor(color);g.fillRect(x,y,w,h);}
        private void txt(Graphics g,String s,int color,int x,int y,Font f){
            if(s==null||s.length()==0)return;g.setColor(color);g.setFont(f);
            g.drawString(s,x,y,Graphics.TOP|Graphics.LEFT);
        }
        private void grad(Graphics g,int y,int h){
            for(int j=0;j<h;j+=2){int t=j*50/max(1,h);fill(g,((23+t)<<16)|((90+t)<<8)|(170+t),0,y,cw,2);}
        }
        protected void paint(Graphics g){
            layout();fill(g,0xF1F5FC,0,0,cw,ch);
            grad(g,0,headerH);
            txt(g,"MiniSocial",0xFFFFFF,pad+5,max(3,(headerH-title.getHeight())/2),title);
            txt(g,clock(),0xFFFFFF,max(pad+10,cw-pad-small.stringWidth(clock())),max(3,(headerH-small.getHeight())/2),small);
            if(token.length()==0)drawWelcome(g);
            else if(tab==5)photoView(g);
            else{
                topNav(g);
                if(tab==1)drawFeed(g);
                else if(tab==2)drawProfile(g);
                else if(tab==3)drawFriends(g);
                else if(tab==4)drawChat(g);
                else if(tab==6)drawComments(g);
                else if(tab==7)drawRequests(g);
            }
            if(busy||notification!=null&&notification.length()>0){
                int messageH=large?24:16;
                fill(g,busy?0xFFEECC:0xFFE4D9,0,ch-footerH-messageH,cw,messageH);
                txt(g,trim(busy?"Dang tai...":notification,cw-2*pad,small),busy?0x6C3D08:0x9C1C1C,pad,ch-footerH-messageH+2,small);
            }
            grad(g,ch-footerH,footerH);
            txt(g,token.length()==0?"Chon":"Tuy chon",0xFFFFFF,pad+3,ch-footerH+max(2,(footerH-bold.getHeight())/2),bold);
            String back="Quay lai";
            txt(g,back,0xFFFFFF,cw-pad-3-bold.stringWidth(back),ch-footerH+max(2,(footerH-bold.getHeight())/2),bold);
        }
        private void drawWelcome(Graphics g){
            int y=headerH+max(14,ch/13);
            txt(g,trim("Mang xa hoi Java ME",cw-2*pad,bold),DARK,pad+6,y,bold);
            y+=bold.getHeight()+max(20,ch/18);
            panel(g,pad+4,y,cw-2*(pad+4),max(35,normal.getHeight()+14),true);
            txt(g,"1 - Dang nhap",DARK,pad+14,y+9,normal);
            y+=max(48,normal.getHeight()+27);
            panel(g,pad+4,y,cw-2*(pad+4),max(35,normal.getHeight()+14),false);
            txt(g,"2 - Dang ky",DARK,pad+14,y+9,normal);
            if(y+70<ch-footerH)txt(g,"Xubi + Supabase / HTTPS",TEXT,pad+6,ch-footerH-small.getHeight()-14,small);
        }
        private String clock(){
            java.util.Calendar c=java.util.Calendar.getInstance();int h=c.get(java.util.Calendar.HOUR_OF_DAY),m=c.get(java.util.Calendar.MINUTE);
            return (h<10?"0":"")+h+":"+(m<10?"0":"")+m;
        }
        private int navIndex(int i){return (tab==1&&i==0)||(tab==2&&i==1)||(tab==3&&i==2)||(tab==4&&i==3)?1:0;}
        private void topNav(Graphics g){
            fill(g,0xD9E7FC,0,headerH,cw,navH);
            String[]full={"Trang chu","Ho so","Ban be","Chat"};
            String[]shortNames={"Chu","Ho so","Ban be","Chat"};
            for(int i=0;i<4;i++){
                int cols=narrow?2:4,rows=narrow?2:1,cellW=cw/cols,cellH=navH/rows;
                int x=i%cols*cellW, y=headerH+i/cols*cellH;
                boolean active=navIndex(i)==1;
                if(active)fill(g,DARK,x+1,y+1,cellW-2,cellH-2);
                String s=narrow?shortNames[i]:full[i];
                int dx=x+max(3,(cellW-small.stringWidth(s))/2);
                txt(g,s,active?0xFFFFFF:DARK,dx,y+max(1,(cellH-small.getHeight())/2),small);
            }
        }
        private void panel(Graphics g,int x,int y,int w,int h,boolean active){
            if(w<=0||h<=0)return;
            fill(g,active?0xDEEAFD:0xFFFFFF,x,y,w,h);
            g.setColor(active?BLUE:0xB3C4E0);g.drawRect(x,y,w-1,h-1);
        }
        private void headerHint(Graphics g,String s,int y){
            txt(g,trim(s,cw-2*pad-10,small),0x53637A,pad+5,y,small);
        }
        private void drawFeed(Graphics g){
            int y=bodyTop+1,h=large?43:33;
            panel(g,pad,y,cw-2*pad,h,false);
            String text=narrow?"2: Bai viet   8: Anh":"2: Dang bai    8: Chon anh";
            txt(g,trim(text,cw-2*pad-15,normal),0x53637A,pad+8,y+max(3,(h-normal.getHeight())/2),normal);
            drawPosts(g,y+h+5,feed);
        }
        private int visible(int y,int height){return max(1,(bodyBottom-y)/max(1,height));}
        private int windowStart(Vector items,int y,int height){
            int count=visible(y,height),n=items.size();
            if(n==0)return 0;
            selection=max(0,min(selection,n-1));
            return max(0,min(selection-count+1,max(0,n-count)));
        }
        private void drawPosts(Graphics g,int y,Vector items){
            if(items.size()==0){headerHint(g,"Chua co bai viet. Nhan 2 de dang.",y+10);return;}
            int gap=large?8:4,stride=cardH+gap;
            int first=windowStart(items,y,stride);
            for(int i=first;i<items.size();i++){
                int ry=y+(i-first)*stride;
                if(ry+cardH>bodyBottom)break;
                String[]a=(String[])items.elementAt(i);
                panel(g,pad,ry,cw-2*pad,cardH,i==selection);
                int avatar=large?45:29;
                fill(g,0xA7C8EF,pad+6,ry+6,avatar,avatar);
                txt(g,"@",0xFFFFFF,pad+6+avatar/4,ry+6+avatar/4,bold);
                int textX=pad+avatar+13;
                int nameY=ry+5;
                txt(g,trim(piece(a,2),cw-textX-pad-4,bold),DARK,textX,nameY,bold);
                txt(g,trim(piece(a,3).replace('T',' '),cw-textX-pad-4,small),0x687A91,textX,nameY+bold.getHeight()+2,small);
                int contentY=ry+avatar+12;
                int actionY=ry+cardH-small.getHeight()-5;
                int lines=max(1,(actionY-contentY-1)/max(1,normal.getHeight()));
                wrapped(g,piece(a,6),TEXT,pad+8,contentY,cw-2*pad-16,normal,lines);
                String actions="<3 "+piece(a,4)+"  BL "+piece(a,5)+(piece(a,7).length()>0?"  [Anh]":"");
                txt(g,trim(actions,cw-2*pad-14,small),BLUE,pad+8,actionY,small);
            }
        }
        private int profileHeaderHeight(){return large?(portrait?148:133):(portrait?115:96);}
        private boolean profileNeedsPaging(){return bodyBottom-(bodyTop+2+profileHeaderHeight()+6)<cardH;}
        private void drawProfile(Graphics g){
            if(profileNeedsPaging()&&profilePostsMode){
                headerHint(g,"5: Thong tin  |  Bai viet",bodyTop+4);
                drawPosts(g,bodyTop+24,profilePosts);return;
            }
            int y=bodyTop+2,ph=profileHeaderHeight();
            panel(g,pad,y,cw-2*pad,ph,false);
            int avatar=large?73:(narrow?52:45);
            fill(g,0xA2C3ED,pad+8,y+8,avatar,avatar);
            txt(g,"@",0xFFFFFF,pad+8+avatar/3,y+8+avatar/3,bold);
            int tx=pad+avatar+16;
            txt(g,trim(profileName,cw-tx-pad-7,bold),DARK,tx,y+10,bold);
            txt(g,trim("@"+profileHandle,cw-tx-pad-7,small),0x60728A,tx,y+12+bold.getHeight(),small);
            int bioY=y+avatar+15;
            wrapped(g,profileBio,TEXT,pad+8,bioY,cw-2*pad-16,small,large?2:1);
            headerHint(g,profileNeedsPaging()?"5: Bai viet | 6: Ten | 7: Gioi thieu":"6: Sua ten  |  7: Sua gioi thieu",y+ph-small.getHeight()-6);
            if(!profileNeedsPaging())drawPosts(g,y+ph+6,profilePosts);
        }
        private int friendListY(){return bodyTop+(large?65:53);}
        private void drawFriends(Graphics g){
            headerHint(g,"6: Them ban theo @handle",bodyTop+5);
            headerHint(g,"7: Loi moi ket ban",bodyTop+7+small.getHeight());
            int y=friendListY(),gap=large?5:3,first=windowStart(friends,y,rowH+gap);
            for(int i=first;i<friends.size();i++){
                int ry=y+(i-first)*(rowH+gap);if(ry+rowH>bodyBottom)break;
                String[]f=(String[])friends.elementAt(i);panel(g,pad,ry,cw-2*pad,rowH,i==selection);
                txt(g,trim(piece(f,1),cw-2*pad-90,bold),TEXT,pad+8,ry+5,bold);
                txt(g,trim("@"+piece(f,2),cw-2*pad-90,small),0x76869C,pad+8,ry+7+bold.getHeight(),small);
                String label="Chat >";txt(g,label,BLUE,cw-pad-small.stringWidth(label)-7,ry+max(6,(rowH-small.getHeight())/2),small);
            }
            if(friends.size()==0)headerHint(g,"Chua co ban be",y+8);
        }
        private int chatListY(){return bodyTop+(large?63:51);}
        private void drawChat(Graphics g){
            txt(g,trim("Voi: "+peerName,cw-2*pad-8,bold),DARK,pad+5,bodyTop+4,bold);
            headerHint(g,narrow?"2: Chat  8: Anh  5: Xem":"2: Tin nhan  8: Gui anh  5: Xem anh",bodyTop+7+bold.getHeight());
            int y=chatListY(),bubbleH=large?74:46,gap=large?9:4;
            int count=visible(y,bubbleH+gap),first=max(0,messages.size()-count);
            for(int i=first;i<messages.size();i++){
                int ry=y+(i-first)*(bubbleH+gap);if(ry+bubbleH>bodyBottom)break;
                String[]m=(String[])messages.elementAt(i);boolean mine=piece(m,1).equals(me);
                int space=large?65:30;int x=mine?pad+space:pad;int width=cw-2*pad-space;
                panel(g,x,ry,width,bubbleH,false);
                String value=piece(m,3).length()>0?"[Anh] "+piece(m,2):piece(m,2);
                wrapped(g,value,mine?BLUE:TEXT,x+7,ry+6,width-14,normal,max(1,(bubbleH-12)/max(1,normal.getHeight())));
            }
            if(messages.size()==0)headerHint(g,"Chua co tin nhan",y+8);
        }
        private int commentsListY(){return bodyTop+(large?45:32);}
        private void drawComments(Graphics g){
            headerHint(g,"Binh luan - 2: Viet binh luan",bodyTop+4);
            int y=commentsListY(),height=large?83:55,gap=large?7:3;
            int first=windowStart(comments,y,height+gap);
            for(int i=first;i<comments.size();i++){
                int ry=y+(i-first)*(height+gap);if(ry+height>bodyBottom)break;
                String[]a=(String[])comments.elementAt(i);panel(g,pad,ry,cw-2*pad,height,i==selection);
                txt(g,trim(piece(a,1),cw-2*pad-16,bold),DARK,pad+8,ry+5,bold);
                wrapped(g,piece(a,2),TEXT,pad+8,ry+7+bold.getHeight(),cw-2*pad-16,normal,large?2:1);
            }
            if(comments.size()==0)headerHint(g,"Chua co binh luan",y+8);
        }
        private int requestsListY(){return bodyTop+(large?45:34);}
        private void drawRequests(Graphics g){
            headerHint(g,"Loi moi - Enter de chap nhan",bodyTop+4);
            int y=requestsListY(),height=large?66:42,gap=large?7:4;
            int first=windowStart(incoming,y,height+gap);
            for(int i=first;i<incoming.size();i++){
                int ry=y+(i-first)*(height+gap);if(ry+height>bodyBottom)break;
                String[]a=(String[])incoming.elementAt(i);panel(g,pad,ry,cw-2*pad,height,i==selection);
                txt(g,trim(piece(a,1),cw-2*pad-15,bold),DARK,pad+8,ry+5,bold);
                txt(g,trim("@"+piece(a,2),cw-2*pad-15,small),0x687A91,pad+8,ry+8+bold.getHeight(),small);
            }
            if(incoming.size()==0)headerHint(g,"Khong co loi moi",y+8);
        }
        private void photoView(Graphics g){
            int y=headerH+pad,h=ch-headerH-footerH-2*pad;
            panel(g,pad,y,cw-2*pad,h,false);
            if(photo==null||!loadedPhoto.equals(photoId))headerHint(g,"Dang tai anh (5: tai lai)",y+20);
            else{
                int xx=(cw-photo.getWidth())/2,yy=y+(h-photo.getHeight())/2;
                g.drawImage(photo,xx,yy,Graphics.TOP|Graphics.LEFT);
            }
        }
        private void wrapped(Graphics g,String s,int color,int x,int y,int maxWidth,Font font,int lines){
            if(s==null||maxWidth<=0)return;
            int pos=0;
            for(int line=0;line<lines&&pos<s.length();line++){
                int end=pos,last=-1;
                while(end<s.length()){
                    char c=s.charAt(end);if(c=='\n')break;
                    if(font.stringWidth(s.substring(pos,end+1))>maxWidth)break;
                    if(c==' ')last=end;end++;
                }
                if(end==pos)end=min(s.length(),pos+1);
                else if(end<s.length()&&s.charAt(end)!='\n'&&last>pos)end=last+1;
                String portion=s.substring(pos,end).trim();
                if(line==lines-1&&end<s.length())portion=trim(portion,maxWidth,font);
                txt(g,portion,color,x,y+line*(font.getHeight()+1),font);
                pos=end;
                while(pos<s.length()&&(s.charAt(pos)==' '||s.charAt(pos)=='\n'))pos++;
            }
        }
        private String trim(String s,int width,Font font){
            if(s==null)return "";if(width<=0)return "";
            if(font.stringWidth(s)<=width)return s;
            String ellipsis="...";
            while(s.length()>0&&font.stringWidth(s+ellipsis)>width)s=s.substring(0,s.length()-1);
            return s+ellipsis;
        }
        private void loadPhoto(final String id){
            if(id==null||id.length()==0)return;
            photoId=id;photo=null;loadedPhoto="";tab=5;
            work(new Task(){public void run()throws Exception{
                byte[]bytes=network("/m/photo?id="+encode(id),null,null,null);
                Image source=Image.createImage(bytes,0,bytes.length);
                int width=max(1,getWidth()-2*pad-12),height=max(1,getHeight()-headerH-footerH-2*pad-12);
                int sw=source.getWidth(),sh=source.getHeight();
                if(sw<=0||sh<=0||((long)sw)*sh>1200000L)throw new Exception("Anh qua lon de hien thi");
                int dw=sw,dh=sh;
                if(sw>width||sh>height){
                    if(((long)sw)*height>((long)sh)*width){dw=width;dh=max(1,(int)(((long)sh)*width/sw));}
                    else{dh=height;dw=max(1,(int)(((long)sw)*height/sh));}
                    int[]orig=new int[sw*sh],dest=new int[dw*dh];
                    source.getRGB(orig,0,sw,0,0,sw,sh);
                    for(int yy=0;yy<dh;yy++)for(int xx=0;xx<dw;xx++)dest[yy*dw+xx]=orig[(int)(((long)yy)*sh/dh)*sw+(int)(((long)xx)*sw/dw)];
                    source=Image.createRGBImage(dest,dw,dh,false);
                }
                photo=source;loadedPhoto=id;
            }});
        }
        private void back(){
            if(tab==5){tab=4;reload();}
            else if(tab==6){tab=1;reload();}
            else if(tab==7){tab=3;reload();}
            else if(tab==4){tab=3;reload();}
            else navigate(1);
        }
        private int listSize(){
            if(tab==1)return feed.size();if(tab==2)return profilePosts.size();
            if(tab==3)return friends.size();if(tab==6)return comments.size();
            if(tab==7)return incoming.size();return 0;
        }
        protected void keyPressed(int k){
            if(busy)return;
            int action=0;try{action=getGameAction(k);}catch(Exception ignored){}
            if(token.length()==0){if(k=='1')auth(false);else if(k=='2')auth(true);repaint();return;}
            if(k==-7||k=='0'){back();repaint();return;}
            if(tab!=5){if(k=='1'){navigate(1);return;}if(k=='3'){profileId=me;navigate(2);return;}if(k=='4'){navigate(3);return;}if(k=='9'){reload();return;}}
            if(k=='#'){logout();return;}
            if(action==Canvas.UP){selection=max(0,selection-1);repaint();return;}
            if(action==Canvas.DOWN){selection=min(max(0,listSize()-1),selection+1);repaint();return;}
            if(tab==1){
                if(k=='2')edit("post","Noi dung bai viet",TextField.ANY);
                else if(k=='8')edit("photo-post","file:///E:/Images/anh.jpg",TextField.ANY);
                else if(k=='*')toggleLike();else if(k=='5')showPostImage();else if(action==Canvas.FIRE)openComments();
            }else if(tab==2){
                if(k=='5'&&profileNeedsPaging()){profilePostsMode=!profilePostsMode;selection=0;repaint();return;}
                if(k=='6'&&profileId.equals(me))edit("profile-name","Ten hien thi",TextField.ANY);
                else if(k=='7'&&profileId.equals(me))edit("profile-bio","Gioi thieu",TextField.ANY);
                else if(k=='*')toggleLike();else if(action==Canvas.FIRE)openComments();
            }else if(tab==3){
                if(k=='6')edit("friend","Them ban theo @handle",TextField.ANY);
                else if(k=='7')navigate(7);else if(action==Canvas.FIRE)selectFriend();
            }else if(tab==4){
                if(k=='2')edit("chat","Nhap tin nhan",TextField.ANY);
                else if(k=='8')edit("photo-chat","file:///E:/Images/anh.jpg",TextField.ANY);
                else if(k=='5'||action==Canvas.FIRE)showChatImage();
            }else if(tab==6){if(k=='2')edit("comment","Binh luan",TextField.ANY);}
            else if(tab==7){if(action==Canvas.FIRE)acceptInvite();}
            else if(tab==5){if(k=='5')loadPhoto(photoId);}
            repaint();
        }
        /** Pointer UI is optional: key navigation remains available on E72. */
        protected void pointerPressed(int x,int y){
            if(busy)return;
            layout();
            if(token.length()==0){
                int cy=headerH+max(14,ch/13)+bold.getHeight()+max(20,ch/18);
                if(y>=cy&&y<cy+max(35,normal.getHeight()+14))auth(false);
                else if(y>=cy+max(48,normal.getHeight()+27)&&y<cy+max(48,normal.getHeight()+27)+max(35,normal.getHeight()+14))auth(true);
                return;
            }
            if(y>=ch-footerH){if(x>=cw/2)back();repaint();return;}
            if(tab!=5&&y>=headerH&&y<headerH+navH){
                int cols=narrow?2:4,cellW=cw/cols,cellH=navH/(narrow?2:1);
                int i=(y-headerH)/max(1,cellH)*cols+x/max(1,cellW);
                if(i==0)navigate(1);else if(i==1){profileId=me;navigate(2);}
                else if(i==2)navigate(3);else if(i==3){if(peer.length()>0)navigate(4);else notification="Chon ban be de chat";}
                repaint();return;
            }
            if(tab==1){
                int firstY=bodyTop+1+(large?43:33)+5;
                if(y<firstY){edit("post","Noi dung bai viet",TextField.ANY);return;}
                int gap=large?8:4,first=windowStart(feed,firstY,cardH+gap);
                int i=first+(y-firstY)/max(1,cardH+gap);
                if(i>=0&&i<feed.size()){selection=i;if(y>=firstY+(i-first)*(cardH+gap)+cardH-small.getHeight()-8)toggleLike();else openComments();}
            }else if(tab==2){
                if(profileNeedsPaging()&&!profilePostsMode){profilePostsMode=true;repaint();return;}
                int firstY=profileNeedsPaging()?bodyTop+24:bodyTop+2+profileHeaderHeight()+6;
                if(y>=firstY){int gap=large?8:4,first=windowStart(profilePosts,firstY,cardH+gap);int i=first+(y-firstY)/(cardH+gap);if(i>=0&&i<profilePosts.size()){selection=i;openComments();}}
            }else if(tab==3){
                int firstY=friendListY(),first=windowStart(friends,firstY,rowH+(large?5:3));
                if(y<firstY){if(y>bodyTop+small.getHeight()+6)navigate(7);else edit("friend","Them ban theo @handle",TextField.ANY);return;}
                int i=first+(y-firstY)/(rowH+(large?5:3));if(i>=0&&i<friends.size()){selection=i;selectFriend();}
            }else if(tab==4){if(y<chatListY())edit("chat","Nhap tin nhan",TextField.ANY);}
            else if(tab==6){if(y<commentsListY())edit("comment","Binh luan",TextField.ANY);}
            else if(tab==7){
                int firstY=requestsListY(),height=large?66:42,gap=large?7:4;
                int i=windowStart(incoming,firstY,height+gap)+(y-firstY)/(height+gap);
                if(y>=firstY&&i>=0&&i<incoming.size()){selection=i;acceptInvite();}
            }else if(tab==5){back();}
            repaint();
        }
        private void toggleLike(){
            final Vector arr=tab==2?profilePosts:feed;if(arr.size()==0)return;
            final String id=piece((String[])arr.elementAt(min(selection,arr.size()-1)),0);
            work(new Task(){public void run()throws Exception{single("/m/like","post="+encode(id));
                if(tab==2)profilePosts=listing("/m/profileposts?user="+encode(profileId));else feed=listing("/m/feed");}});
        }
        private void openComments(){
            Vector arr=tab==2?profilePosts:feed;if(arr.size()==0)return;
            target=piece((String[])arr.elementAt(min(selection,arr.size()-1)),0);navigate(6);
        }
        private void showPostImage(){
            Vector arr=tab==2?profilePosts:feed;if(arr.size()==0)return;
            String id=piece((String[])arr.elementAt(min(selection,arr.size()-1)),7);
            if(id.length()>0)loadPhoto(id);else notification="Bai nay khong co anh";
        }
        private void selectFriend(){
            if(friends.size()==0)return;
            String[]f=(String[])friends.elementAt(min(selection,friends.size()-1));
            peer=piece(f,0);peerName=piece(f,1);navigate(4);
        }
        private void showChatImage(){
            for(int i=messages.size()-1;i>=0;i--){String[]a=(String[])messages.elementAt(i);
                if(piece(a,3).length()>0){loadPhoto(piece(a,3));return;}}
            notification="Chua co anh trong chat";
        }
        private void acceptInvite(){
            if(incoming.size()==0)return;
            final String[]a=(String[])incoming.elementAt(min(selection,incoming.size()-1));
            work(new Task(){public void run()throws Exception{single("/m/accept","user="+encode(piece(a,0)));incoming=listing("/m/requests");}});
        }
    }
}
