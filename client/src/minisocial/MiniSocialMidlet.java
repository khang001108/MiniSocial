package minisocial;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.Vector;
import java.util.Hashtable;

/** MiniSocial UI Assets v2; adaptive 240x320, 320x240, 640x360 and 640x480 (MIDP 2.0). */
public final class MiniSocialMidlet extends MIDlet implements CommandListener {
    private Display display; private SocialCanvas screen;
    private static final String DEFAULT_PUBLIC="https://minisocial.xubi.org";
    private static final String DEFAULT_LAN="http://192.168.1.127:18087";
    private String publicHost=DEFAULT_PUBLIC,lanHost=DEFAULT_LAN,customHost="https://minisocial.xubi.org";
    private String host=DEFAULT_PUBLIC;
    private int serverMode=0,serverSelection=0,welcomeSelection=0;
    // Login form fields are held in RAM only. Password is never written to RMS.
    private String loginEmail="", loginPassword="";
    // Registration is a persistent Canvas form, not a sequence of pop-up prompts.
    // Password remains in RAM only and is erased on submit/exit.
    private String[] registerFields={"","",""};
    // First-login setup: display name, detected device model, optional avatar path.
    private String[] onboardingFields={"","",""};
    private int onboardingSelection=0;
    private String deviceModel="Unknown device", avatarMediaId="";
    private boolean onboardingRequired=false;
    private Image profileAvatarImage; private String profileAvatarLoaded="";
    // Small avatar cache capped to four thumbnails to respect Nokia heap constraints.
    private Hashtable avatarCache=new Hashtable();
    private int registerSelection=0;
    // Password recovery: a four-field form, no password/OTP persisted in RMS.
    private String[] recoveryFields={"","","",""}; // email, 6-digit OTP, password, confirm
    private int recoverySelection=0;
    private boolean recoverySent=false;
    // Change password from your own profile, current password always required.
    private String[] changeFields={"","" ,""}; // current, new, confirm
    private int changeSelection=0;
    private static final String[] RECOVERY_LABELS={"Email", "Mã OTP (6 số)", "Mật khẩu mới", "Xác nhận mật khẩu"};
    private static final String[] CHANGE_LABELS={"Mật khẩu hiện tại", "Mật khẩu mới", "Nhập lại mật khẩu"};
    private String authError="";
    private int registerErrorField=-1,loginErrorField=-1;
    private static final String[] REG_LABELS={"Tên tài khoản","Mật khẩu","Nhập lại mật khẩu"};
    private static final String[] REG_HINTS={"a-z, 0-9, _","Tối thiểu 8 ký tự","Xác nhận mật khẩu"};
    private static final String[] SETUP_LABELS={"Tên hiển thị","Model điện thoại","Ảnh đại diện (đường dẫn)"};
    private static final String[] SETUP_HINTS={"Tên bạn bè nhìn thấy","Tự nhận diện hoặc sửa tay","file:///E:/Images/avatar.jpg"};
    private int navSelection=0;
    private boolean navFocus=false, optionsOpen=false, themeDark=false, notifyEnabled=true;
    private int optionsSelection=0,settingsSelection=0,storageSelection=0,historyMode=0;
    private int historyReturnTab=1;
    private Vector historyItems=new Vector();
    private long storageUsed=0,storageLimit=52428800L;
    private String deleteAccountPassword="";
    private static final String[] OPTIONS={"Cá nhân","Cài đặt","Lịch sử","Dung lượng"};
    private static final String[] SETTINGS={"Thông báo","Đổi theme","Đổi tên","Đổi mật khẩu","Xóa tài khoản","Đăng xuất"};
    private String token="", me="", myName="";
    private TextBox editor;private final Command save=new Command("OK",Command.OK,1),cancel=new Command("Huy",Command.CANCEL,2);
    private String mode="", target="",pendingMedia="";
    private boolean busy=false;private String notification="";
    private Vector feed=new Vector(),profilePosts=new Vector(), friends=new Vector(),incoming=new Vector(),messages=new Vector(),comments=new Vector();
    private String peer="",peerName="",profileId="",profileName="",profileHandle="",profileBio="";
    private int tab=1,selection=0;private String photoId="";

    public void startApp(){
        display=Display.getDisplay(this);
        String x=getAppProperty("MiniSocial-Api-Url");if(x!=null&&x.startsWith("https://"))publicHost=x;
        readServer();readSession();readDevice();readPreferences();screen=new SocialCanvas();display.setCurrent(screen);
        if(token.length()>0){work(new Task(){public void run()throws Exception{afterLogin();}});}else tab=0;
    }
    private void readPreferences(){
        try{RecordStore r=RecordStore.openRecordStore("ms_prefs_v18",true);
            if(r.getNumRecords()>0){String a=new String(r.getRecord(1),"UTF-8");
                themeDark=a.indexOf('D')>=0;notifyEnabled=a.indexOf('N')>=0;}
            r.closeRecordStore();
        }catch(Exception ignored){} applyTheme();
    }
    private void savePreferences(){
        try{RecordStore r=RecordStore.openRecordStore("ms_prefs_v18",true);
            byte[] b=((themeDark?"D":"L")+(notifyEnabled?"N":"S")).getBytes("UTF-8");
            if(r.getNumRecords()==0)r.addRecord(b,0,b.length);else r.setRecord(1,b,0,b.length);
            r.closeRecordStore();
        }catch(Exception ignored){}
    }
    private void applyTheme(){
        // Modern Messenger blue palette (Light/Dark); all business logic remains unchanged.
        if(themeDark){BLUE=0x6BA9FF;DARK=0xE2ECFA;LIGHT=0x1B2C47;TEXT=0xEDF4FF;
          GREEN=0x3BC978;GREEN_DEEP=0x237FCF;SKY=0x74B4FF;BG=0x0D1626;MUTED=0xA1B1C9;BORDER=0x334661;}
        else{BLUE=0x2D7FF9;DARK=0x172A4B;LIGHT=0xE8F2FF;TEXT=0x273955;
          GREEN=0x35C759;GREEN_DEEP=0x1761CE;SKY=0x4498FF;BG=0xF2F6FD;MUTED=0x667A98;BORDER=0xCBD9EB;}
    }
    private void openOptions(){optionsOpen=true;optionsSelection=0;screen.repaint();}
    private void selectOption(){
        optionsOpen=false;
        if(optionsSelection==0){profileId=me;navigate(2);}
        else if(optionsSelection==1){settingsSelection=0;tab=14;screen.repaint();}
        else if(optionsSelection==2){historyMode=0;selection=0;tab=15;loadHistory();}
        else{storageSelection=0;tab=16;loadStorage();}
    }
    private void loadHistory(){final int mode=historyMode;work(new Task(){public void run()throws Exception{
        historyItems=listing("/m/history?type="+(mode==0?"liked":"posted"));
        notification=historyItems.size()+" bài viết trong lịch sử";
    }});}
    private void loadStorage(){work(new Task(){public void run()throws Exception{
        String[] data=single("/m/storage",null);
        storageUsed=Long.parseLong(data[1]);storageLimit=Long.parseLong(data[2]);
    }});}
    private static String displayMB(long bytes){
        long whole=bytes/1048576L,decimal=(bytes%1048576L)*10L/1048576L;
        return whole+"."+decimal;
    }
    private void clearLocalCache(){avatarCache.clear();profileAvatarImage=null;profileAvatarLoaded="";
        screen.photo=null;screen.loadedPhoto="";notification="Đã xóa bộ nhớ đệm trên điện thoại";screen.repaint();}
    private void clearServerMedia(){work(new Task(){public void run()throws Exception{
        String[] r=single("/m/storage/clear","confirm=CLEAR");
        clearLocalCache();storageUsed=0;tab=16;
        notification="Đã xóa "+piece(r,1)+" ảnh, giữ tin nhắn và bài chữ";
        String[] usage=single("/m/storage",null);
        storageUsed=Long.parseLong(usage[1]);storageLimit=Long.parseLong(usage[2]);
    }});}
    private void submitDeleteAccount(){
        final String pwd=deleteAccountPassword;deleteAccountPassword="";
        if(pwd.length()<8){notification="Cần nhập mật khẩu để xóa tài khoản";screen.repaint();return;}
        work(new Task(){public void run()throws Exception{
            single("/m/account/delete","password="+encode(pwd)+"&confirm=DELETE");
            logout();notification="Tài khoản đã bị xóa";
        }});
    }
    public void pauseApp(){}
    public void destroyApp(boolean unconditional){}
    private void readSession(){try{RecordStore r=RecordStore.openRecordStore("ms_session",true);if(r.getNumRecords()>0){String[]a=split(new String(r.getRecord(1),"UTF-8"),'\n');if(a.length>=2){me=a[0];token=a[1];}}r.closeRecordStore();}catch(Exception ignored){}}
    private void saveSession(){try{RecordStore r=RecordStore.openRecordStore("ms_session",true);byte[]v=(me+"\n"+token).getBytes("UTF-8");if(r.getNumRecords()==0)r.addRecord(v,0,v.length);else r.setRecord(1,v,0,v.length);r.closeRecordStore();}catch(Exception ignored){}}
    // Store only server choices; no secret is stored in this record store.
    private void readServer(){
        try{
            RecordStore r=RecordStore.openRecordStore("ms_hosts_v12",true);
            if(r.getNumRecords()>0){
                String[]a=split(new String(r.getRecord(1),"UTF-8"),'\n');
                if(a.length>=4){
                    publicHost=normalizeHost(a[1],0);lanHost=normalizeHost(a[2],1);
                    customHost=normalizeHost(a[3],2);
                    int m=Integer.parseInt(a[0]);serverMode=m>=0&&m<3?m:0;
                }
            }
            r.closeRecordStore();
        }catch(Exception ignored){serverMode=0;publicHost=DEFAULT_PUBLIC;lanHost=DEFAULT_LAN;customHost=DEFAULT_PUBLIC;}
        host=serverAddress(serverMode);
    }
    private boolean saveServer(){
        try{
            RecordStore r=RecordStore.openRecordStore("ms_hosts_v12",true);
            byte[]b=(serverMode+"\n"+publicHost+"\n"+lanHost+"\n"+customHost).getBytes("UTF-8");
            if(r.getNumRecords()==0)r.addRecord(b,0,b.length);else r.setRecord(1,b,0,b.length);
            r.closeRecordStore();return true;
        }catch(Exception e){notification="Khong luu duoc may chu";return false;}
    }
    private String serverAddress(int idx){return idx==0?publicHost:idx==1?lanHost:customHost;}
    // Network requests use HTTPS publicly; HTTP is permitted only for private LAN/localhost.
    private String normalizeHost(String value,int idx)throws Exception{
        String u=value==null?"":value.trim();
        if(u.length()==0||u.length()>180)throw new Exception("Dia chi may chu khong hop le");
        if(u.indexOf("://")<0)u=(idx==1?"http://":"https://")+u;
        while(u.endsWith("/"))u=u.substring(0,u.length()-1);
        if(u.indexOf(' ')>=0||u.indexOf('@')>=0||u.indexOf('?')>=0||u.indexOf('#')>=0)throw new Exception("Chi nhap ten mien/IP va cong");
        boolean secure=u.startsWith("https://"),plain=u.startsWith("http://");
        if(!secure&&!plain)throw new Exception("Chi ho tro http(s)://, khong phai socket://");
        String site=u.substring(secure?8:7);
        if(site.length()==0||site.indexOf('/')>=0||site.indexOf('\\')>=0||site.indexOf(':')==0)throw new Exception("Chi nhap domain/IP, khong them duong dan");
        if(plain){
            String h=site;int colon=h.indexOf(':');if(colon>=0)h=h.substring(0,colon);
            if(!isLocalIPv4(h))throw new Exception("Internet phai dung HTTPS; HTTP chi danh cho LAN");
        }
        return u;
    }
    private static boolean isLocalIPv4(String h){
        if(h.equals("localhost"))return true;
        String[]a=split(h,'.');if(a.length!=4)return false;
        int[]v=new int[4];
        try{
            for(int i=0;i<4;i++){
                if(a[i].length()==0||a[i].length()>3)return false;
                for(int j=0;j<a[i].length();j++)if(a[i].charAt(j)<'0'||a[i].charAt(j)>'9')return false;
                v[i]=Integer.parseInt(a[i]);if(v[i]>255)return false;
            }
        }catch(Exception e){return false;}
        return v[0]==10 || (v[0]==172&&v[1]>=16&&v[1]<=31)
               || (v[0]==192&&v[1]==168) || (v[0]==127&&v[1]==0&&v[2]==0&&v[3]==1);
    }
    private void setServer(int idx){
        String next=serverAddress(idx);
        if(!next.equals(host)){
            // A Supabase login token from server A must never be sent to server B.
            token="";me="";myName="";loginPassword="";saveSession();
            feed=new Vector();friends=new Vector();messages=new Vector();incoming=new Vector();avatarCache.clear();
        }
        serverMode=idx;host=next;
        if(saveServer())notification="May chu: "+(idx==0?"Internet":idx==1?"Wi-Fi/LAN":"Tuy chinh");
        tab=0;welcomeSelection=0;screen.repaint();
    }
    private void serverEditor(){
        int idx=serverSelection;
        mode=idx==0?"server-public":idx==1?"server-lan":"server-custom";
        String old=serverAddress(idx);
        editor=new TextBox("Dia chi "+(idx==0?"Internet":idx==1?"Wi-Fi/LAN":"tuy chinh"),old,180,TextField.ANY);
        editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);
    }
    private void pingServer(){
        final String address=serverAddress(serverSelection);
        work(new Task(){public void run()throws Exception{
            HttpConnection c=null;InputStream in=null;
            try{
                c=(HttpConnection)Connector.open(address+"/health",Connector.READ_WRITE,true);
                c.setRequestMethod(HttpConnection.GET);
                int code=c.getResponseCode();in=c.openInputStream();
                byte[]b=new byte[160];int n=in.read(b);
                String reply=n>0?new String(b,0,n,"UTF-8"):"";
                if(code!=200||reply.indexOf("\"ok\":true")<0&&reply.indexOf("\"ok\": true")<0)throw new Exception("May chu tra ve HTTP "+code);
                notification="Ket noi thanh cong: "+address;
            }finally{if(in!=null)in.close();if(c!=null)c.close();}
        }});
    }
    // Java ME exposes only an advisory platform string, not a trustworthy device identity.
    // A user can correct it on the first-login screen; no serial/IMEI is accessed.
    private void readDevice(){
        String detected="";
        try{detected=System.getProperty("microedition.platform");}catch(Exception ignored){}
        if(detected==null)detected="";
        String l=detected.toLowerCase();
        if(l.indexOf("302")>=0&&l.indexOf("nokia")>=0)detected="Nokia Asha 302";
        else if(l.indexOf("e72")>=0)detected="Nokia E72";
        else if(l.indexOf("e6")>=0&&l.indexOf("nokia")>=0)detected="Nokia E6";
        else if(l.indexOf("nokia")>=0)detected="Nokia "+detected.replace('\n',' ').trim();
        if(detected.length()==0||detected.length()>64)detected="Unknown device";
        deviceModel=cleanDevice(detected);
        try{RecordStore r=RecordStore.openRecordStore("ms_device_v16",true);
            if(r.getNumRecords()>0)deviceModel=cleanDevice(new String(r.getRecord(1),"UTF-8"));
            r.closeRecordStore();}catch(Exception ignored){}
    }
    /** Only keep one small thumbnail in memory; avoids re-scaling on every repaint. */
    private Image makeAvatarThumbnail(Image src,int pixels){
        int sw=src.getWidth(),sh=src.getHeight();
        if(sw<1||sh<1||((long)sw)*sh>262144L)throw new IllegalArgumentException("Avatar qua lon");
        int[] input=new int[sw*sh],output=new int[pixels*pixels];
        src.getRGB(input,0,sw,0,0,sw,sh);
        int size=sw<sh?sw:sh,left=(sw-size)/2,top=(sh-size)/2;
        for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++)
            output[y*pixels+x]=input[(top+(int)(((long)y)*size/pixels))*sw+left+(int)(((long)x)*size/pixels)];
        return Image.createRGBImage(output,pixels,pixels,false);
    }
    private void cacheFeedAvatars(Vector rows){
        // At most three new thumbnails per feed refresh, no background requests from paint().
        if(avatarCache.size()>4)avatarCache.clear();
        int attempted=0;
        for(int i=0;i<rows.size()&&attempted<3;i++){
            String[] row=(String[])rows.elementAt(i);
            String id=piece(row,9);
            if(id.length()==0||avatarCache.containsKey(id))continue;
            attempted++;
            try{
                byte[] bytes=network("/m/photo?id="+encode(id),null,null,null);
                Image thumb=makeAvatarThumbnail(Image.createImage(bytes,0,bytes.length),screen!=null&&screen.large?45:29);
                avatarCache.put(id,thumb);
            }catch(Exception ignored){}
        }
    }
    private static String cleanDevice(String raw){
        StringBuffer b=new StringBuffer();
        for(int i=0;i<raw.length()&&b.length()<64;i++){
            char c=raw.charAt(i);
            if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c==' '||c=='-'||c=='_'||c=='.'||c=='/')b.append(c);
        }
        String res=b.toString().trim();return res.length()<2?"Unknown device":res;
    }
    private void saveDevice(){
        try{RecordStore r=RecordStore.openRecordStore("ms_device_v16",true);
            byte[] data=deviceModel.getBytes("UTF-8");
            if(r.getNumRecords()==0)r.addRecord(data,0,data.length);else r.setRecord(1,data,0,data.length);
            r.closeRecordStore();}catch(Exception ignored){}
    }
    private void afterLogin()throws Exception{
        String[] p=single("/m/onboarding",null);
        if(p.length>1&&"0".equals(p[1])){
            onboardingFields=new String[]{"",deviceModel,""};onboardingSelection=0;onboardingRequired=true;tab=13;
        }else{onboardingRequired=false;tab=1;feed=listing("/m/feed");cacheFeedAvatars(feed);}
    }
    private void openOnboarding(){
        onboardingRequired=false;onboardingFields=new String[]{profileName.length()>0?profileName:myName,deviceModel,""};
        onboardingSelection=0;authError="";notification="";tab=13;screen.repaint();
    }
    private void editOnboardingField(int index){
        if(index<0||index>=3)return;
        mode="setup-field-"+index;
        editor=new TextBox(SETUP_LABELS[index],onboardingFields[index],index==2?180:index==0?50:64,TextField.ANY);
        editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);
    }
    private void submitOnboarding(){
        final String name=onboardingFields[0].trim(),model=cleanDevice(onboardingFields[1]),path=onboardingFields[2].trim();
        if(name.length()<2||name.length()>50){authError="Tên hiển thị cần 2-50 ký tự";onboardingSelection=0;screen.repaint();return;}
        if(path.length()>0&&!path.startsWith("file:")){authError="Đường dẫn ảnh phải bắt đầu file:///";onboardingSelection=2;screen.repaint();return;}
        authError="";
        work(new Task(){public void run()throws Exception{
            String avatar="";
            if(path.length()>0){
                byte[] raw=readFile(path);
                String mime=path.toLowerCase().endsWith(".png")?"image/png":"image/jpeg";
                String[] r=unpack(new String(network("/m/upload?scope=avatar",null,raw,mime),"UTF-8"));
                if(r.length<2||!r[0].equals("OK"))throw new Exception("Không tải được ảnh đại diện");
                avatar=r[1];
            }
            single("/m/onboarding","name="+encode(name)+"&avatar="+encode(avatar));
            deviceModel=model;saveDevice();myName=name;avatarMediaId=avatar;
            onboardingFields[2]="";authError="";onboardingRequired=false;tab=1;feed=listing("/m/feed");cacheFeedAvatars(feed);
            notification="Đã lưu hồ sơ";
        }});
    }
    private void logout(){token="";me="";myName="";loginPassword="";saveSession();tab=0;welcomeSelection=0;screen.repaint();}
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
            c.setRequestProperty("X-MiniSocial-Device",deviceModel);
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
        if(requested==1){feed=listing("/m/feed");cacheFeedAvatars(feed);}
        else if(requested==2){String[]p=single("/m/profile?user="+encode(profileId.length()==0?me:profileId),null);profileName=p[2];profileHandle=p[3];profileBio=p[4];avatarMediaId=piece(p,6);profilePosts=listing("/m/profileposts?user="+encode(p[1]));cacheFeedAvatars(profilePosts);
            if(avatarMediaId.length()>0&&!avatarMediaId.equals(profileAvatarLoaded)){
                try{byte[] img=network("/m/photo?id="+encode(avatarMediaId),null,null,null);
                    profileAvatarImage=makeAvatarThumbnail(Image.createImage(img,0,img.length),screen!=null&&screen.large?73:screen!=null&&screen.narrow?52:45);profileAvatarLoaded=avatarMediaId;
                }catch(Exception ignored){profileAvatarImage=null;profileAvatarLoaded="";}
            }}
        else if(requested==3){friends=listing("/m/friends");
            if(notifyEnabled){try{incoming=listing("/m/requests");
                if(incoming.size()>0)notification="Bạn có "+incoming.size()+" lời mời kết bạn";
            }catch(Exception ignored){}}
        }
        else if(requested==4){if(peer.length()>0)messages=listing("/m/chat?user="+encode(peer));else friends=listing("/m/friends");}
        else if(requested==5){}else if(requested==6)comments=listing("/m/comments?post="+encode(target));
        else if(requested==7)incoming=listing("/m/requests");
    }});}
    private void navigate(int t){
        if(token.length()==0){tab=0;screen.repaint();return;}
        tab=t;selection=0;navFocus=false;optionsOpen=false;navSelection=t==4?1:t==3?2:0;
        reload();
    }
    private void edit(String m,String title,int constraints){mode=m;editor=new TextBox(title,"",500,constraints);editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);}
    private void auth(boolean signup){
        if(!signup){editLogin(true);return;}
        tab=10;registerSelection=0;registerErrorField=-1;authError="";notification="";
        screen.repaint();
    }
    private void editRegisterField(int index){
        if(index<0||index>=REG_LABELS.length)return;
        registerSelection=index;
        mode="register-field-"+index;
        int constraints=index==0?TextField.ANY:(TextField.ANY|TextField.PASSWORD);
        int limit=index==0?24:128;
        editor=new TextBox(REG_LABELS[index],registerFields[index],limit,constraints);
        editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);
        display.setCurrent(editor);
    }
    private void failRegistration(int field,String reason){
        registerSelection=field;registerErrorField=field;authError=reason;screen.repaint();
    }
    private void submitRegistration(){
        String handle=registerFields[0].trim().toLowerCase(),pass=registerFields[1],confirm=registerFields[2];
        if(handle.length()<3||handle.length()>24){failRegistration(0,"Tên tài khoản: 3-24 ký tự");return;}
        for(int i=0;i<handle.length();i++){
            char c=handle.charAt(i);
            if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_')){
                failRegistration(0,"Tên tài khoản: a-z, 0-9, _");return;
            }
        }
        if(pass.length()<8||pass.length()>128){failRegistration(1,"Mật khẩu phải có 8-128 ký tự");return;}
        if(!pass.equals(confirm)){failRegistration(2,"Mật khẩu nhập lại không khớp");return;}
        final String req="handle="+encode(handle)+"&password="+encode(pass);
        registerFields[1]="";registerFields[2]="";authError="";registerErrorField=-1;
        final String account=handle;
        work(new Task(){public void run()throws Exception{
            try{single("/m/register",req);}catch(Exception ex){
                authError=ex.getMessage()==null?"Đăng ký thất bại":ex.getMessage();
                registerErrorField=authError.toLowerCase().indexOf("tài khoản")>=0?0:-1;
                throw new Exception(authError);
            }
            for(int i=0;i<registerFields.length;i++)registerFields[i]="";
            loginEmail=account;loginPassword="";
            welcomeSelection=1;tab=0;
            notification="Đã tạo tài khoản. Đăng nhập để đặt tên hiển thị và avatar.";
        }});
    }
    private void openRecovery(){
        // Username-only accounts do not need email or OTP. Password resets are admin-managed.
        authError="Quên mật khẩu? Liên hệ quản trị viên MiniSocial.";
        loginErrorField=-1;notification="";tab=0;screen.repaint();
    }
    private void openChange(){
        if(token.length()==0)return;
        changeFields=new String[]{"","",""};changeSelection=0;authError="";notification="";tab=12;screen.repaint();
    }
    private void editPasswordField(boolean recovery,int index){
        String[] fields=recovery?recoveryFields:changeFields;
        String[] labels=recovery?RECOVERY_LABELS:CHANGE_LABELS;
        if(index<0||index>=fields.length)return;
        if(recovery)recoverySelection=index;else changeSelection=index;
        mode=(recovery?"recovery-field-":"change-field-")+index;
        int constraint=(recovery&&index==0)?TextField.EMAILADDR:((recovery&&index==1)?TextField.ANY:(TextField.ANY|TextField.PASSWORD));
        editor=new TextBox(labels[index],fields[index],(recovery&&index==1)?6:((recovery&&index==0)?140:128),constraint);
        editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);
    }
    private boolean validAddress(String email){
        int at=email.indexOf('@');return at>0&&at<email.length()-3&&email.indexOf('.',at)>at+1&&email.indexOf(' ')<0;
    }
    private void sendRecoveryCode(){
        final String email=recoveryFields[0].trim();
        if(!validAddress(email)){authError="Nhập email hợp lệ";recoverySelection=0;screen.repaint();return;}
        authError="";notification="";
        work(new Task(){public void run()throws Exception{
            single("/m/password/recover","email="+encode(email));
            recoverySent=true;recoverySelection=1;
            notification="Nếu email đã đăng ký, mã OTP sẽ được gửi.";
        }});
    }
    private void finishRecovery(){
        final String email=recoveryFields[0].trim(),otp=recoveryFields[1].trim();
        final String next=recoveryFields[2],confirm=recoveryFields[3];
        if(!validAddress(email)){authError="Nhập email hợp lệ";recoverySelection=0;screen.repaint();return;}
        if(otp.length()!=6){authError="Nhập mã OTP 6 số";recoverySelection=1;screen.repaint();return;}
        for(int i=0;i<6;i++)if(otp.charAt(i)<'0'||otp.charAt(i)>'9'){authError="OTP phải gồm 6 chữ số";recoverySelection=1;screen.repaint();return;}
        if(next.length()<8){authError="Mật khẩu cần ít nhất 8 ký tự";recoverySelection=2;screen.repaint();return;}
        if(!next.equals(confirm)){authError="Hai mật khẩu không trùng";recoverySelection=3;screen.repaint();return;}
        recoveryFields[2]="";recoveryFields[3]="";authError="";
        work(new Task(){public void run()throws Exception{
            single("/m/password/reset","email="+encode(email)+"&otp="+encode(otp)+"&password="+encode(next));
            recoveryFields=new String[]{"","","",""};recoverySent=false;loginEmail=email;loginPassword="";
            authError="";tab=0;welcomeSelection=1;notification="Đã đổi mật khẩu. Hãy đăng nhập.";
        }});
    }
    private void finishChange(){
        final String old=changeFields[0],next=changeFields[1],confirm=changeFields[2];
        if(old.length()==0){authError="Nhập mật khẩu cũ";changeSelection=0;screen.repaint();return;}
        if(next.length()<8){authError="Mật khẩu mới cần ít nhất 8 ký tự";changeSelection=1;screen.repaint();return;}
        if(next.equals(old)){authError="Mật khẩu mới phải khác mật khẩu cũ";changeSelection=1;screen.repaint();return;}
        if(!next.equals(confirm)){authError="Hai mật khẩu không trùng";changeSelection=2;screen.repaint();return;}
        changeFields=new String[]{"","",""};authError="";
        work(new Task(){public void run()throws Exception{
            single("/m/password/change","current="+encode(old)+"&password="+encode(next));
            logout();notification="Đã đổi mật khẩu. Hãy đăng nhập lại.";
        }});
    }
    private void editLogin(boolean email){
        mode=email?"login-email":"login-password";
        editor=new TextBox(email?"Tên tài khoản":"Mật khẩu",email?loginEmail:loginPassword,email?140:128,
             email?TextField.ANY:(TextField.ANY|TextField.PASSWORD));
        editor.addCommand(save);editor.addCommand(cancel);editor.setCommandListener(this);display.setCurrent(editor);
    }
    private void submitLogin(){
        final String account=loginEmail.trim().toLowerCase(),pass=loginPassword;
        if(account.length()==0){authError="Nhập tên tài khoản";loginErrorField=0;screen.repaint();return;}
        if(pass.length()==0){authError="Nhập mật khẩu";loginErrorField=1;screen.repaint();return;}
        authError="";loginErrorField=-1;
        // Do not keep the password after launching the login request.
        loginPassword="";
        work(new Task(){public void run()throws Exception{
            String[]r=single("/m/login","handle="+encode(account)+"&password="+encode(pass));
            me=r[1];token=r[2];saveSession();afterLogin();
        }});
    }
    public void commandAction(Command cmd,Displayable item){if(cmd==cancel){display.setCurrent(screen);return;}if(cmd!=save)return;
        final String value=editor.getString();final String selected=mode;
        if(selected.equals("login-email")||selected.equals("login-password")){
            if(selected.equals("login-email")){loginEmail=value.trim();welcomeSelection=1;}
            else{loginPassword=value;welcomeSelection=2;}
            authError="";loginErrorField=-1;notification="";
            display.setCurrent(screen);screen.repaint();return;
        }
        if(selected.startsWith("setup-field-")){
            int i=Integer.parseInt(selected.substring("setup-field-".length()));
            onboardingFields[i]=value;onboardingSelection=Math.min(3,i+1);
            authError="";display.setCurrent(screen);screen.repaint();return;
        }
        if(selected.startsWith("register-field-")){
            int i=Integer.parseInt(selected.substring("register-field-".length()));
            registerFields[i]=i==0?value.trim().toLowerCase():value;
            registerSelection=Math.min(3,i+1);
            authError="";registerErrorField=-1;notification="";
            display.setCurrent(screen);screen.repaint();return;
        }
        if(selected.startsWith("recovery-field-")||selected.startsWith("change-field-")){
            boolean rec=selected.startsWith("recovery-field-");
            int i=Integer.parseInt(selected.substring(rec?"recovery-field-".length():"change-field-".length()));
            if(rec){recoveryFields[i]=value;recoverySelection=(i==3?5:i+1);}
            else{changeFields[i]=value;changeSelection=Math.min(3,i+1);}
            authError="";notification="";display.setCurrent(screen);screen.repaint();return;
        }
        if(selected.startsWith("server-")){
            try{
                int idx=selected.equals("server-public")?0:selected.equals("server-lan")?1:2;
                String address=normalizeHost(value,idx);
                if(idx==0)publicHost=address;else if(idx==1)lanHost=address;else customHost=address;
                if(idx==serverMode&& !address.equals(host)){
                    // Persisting a new active URL also invalidates the previous login.
                    token="";me="";myName="";loginPassword="";saveSession();host=address;
                    tab=8;
                }
                if(saveServer())notification="Da luu URL. Nhan OK de chon may chu.";
                display.setCurrent(screen);screen.repaint();
            }catch(Exception e){notification=e.getMessage();display.setCurrent(screen);screen.repaint();}
            return;
        }
        display.setCurrent(screen);
        if(selected.equals("delete-password")){deleteAccountPassword=value;tab=17;screen.repaint();return;}
        if(selected.equals("post")){final String data="body="+encode(value)+"&media="+encode(pendingMedia);work(new Task(){public void run()throws Exception{single("/m/post",data);pendingMedia="";feed=listing("/m/feed");}});}
        else if(selected.equals("chat")){final String peer0=peer;work(new Task(){public void run()throws Exception{single("/m/message","user="+encode(peer0)+"&body="+encode(value));messages=listing("/m/chat?user="+encode(peer0));}});}
        else if(selected.equals("comment")){final String id0=target;work(new Task(){public void run()throws Exception{single("/m/comments","post="+encode(id0)+"&body="+encode(value));comments=listing("/m/comments?post="+encode(id0));}});}
        else if(selected.equals("profile-name")||selected.equals("profile-bio")){
            final String data=(selected.equals("profile-name")?"name=":"bio=")+encode(value);
            work(new Task(){public void run()throws Exception{
                single("/m/profile",data);String[]p=single("/m/profile?user="+encode(me),null);
                profileName=p[2];profileBio=p[4];notification="Đã cập nhật hồ sơ";
            }});
        }
        else if(selected.equals("friend")){work(new Task(){public void run()throws Exception{single("/m/friend","handle="+encode(value));notification="Da gui loi moi";}});}
        else if(selected.equals("photo-post")||selected.equals("photo-chat")){final String path=value;final boolean chat=selected.equals("photo-chat");final String peer0=peer;work(new Task(){public void run()throws Exception{
            byte[]data=readFile(path);String low=path.toLowerCase();String mime=low.endsWith(".png")?"image/png":"image/jpeg";
            String[]res=unpack(new String(network("/m/upload?scope="+(chat?"chat&user="+encode(peer0):"post"),null,data,mime),"UTF-8"));if(res.length<2||!res[0].equals("OK"))throw new Exception("Upload failed");
            if(chat){single("/m/message","user="+encode(peer0)+"&media="+encode(res[1]));messages=listing("/m/chat?user="+encode(peer0));}
            else {pendingMedia=res[1];notification="Anh da tai len; an 2 de dang bai";}
        }});}
    }
    private byte[]readFile(String path)throws Exception{String url=path.startsWith("file:")?path:"file:///"+path;Connection c=null;InputStream in=null;try{c=Connector.open(url);if(!(c instanceof InputConnection))throw new Exception("JSR-75 FileConnection unavailable");in=((InputConnection)c).openInputStream();ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[]bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1){if(buf.size()+n>180*1024)throw new Exception("Gioi han 180 KB / anh");buf.write(bytes,0,n);}return buf.toByteArray();}finally{if(in!=null)in.close();if(c!=null)c.close();}}
    // Modern Messenger blue palette; high contrast on Nokia E72/E6.
    private static int BLUE=0x2D7FF9, DARK=0x172A4B, LIGHT=0xE8F2FF, TEXT=0x273955;
    private static int GREEN=0x35C759, GREEN_DEEP=0x1761CE, SKY=0x4498FF;
    private static int BG=0xF2F6FD, MUTED=0x667A98, BORDER=0xCBD9EB;
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
        private boolean portrait,narrow,large,compactLandscape,profilePostsMode=false;
        // Lazy image cache: a small working set only; never decode the full v2 pack.
        private Hashtable uiCache=new Hashtable();
        private Vector uiRecent=new Vector();
        private String deviceAssets="";
        /** Resolution tier is based on the shorter side, never orientation alone.
         * 240x320 / 320x240: e72; 360x640 / 640x360: wide;
         * 480x640 / 640x480: e6. The same icons work after a rotation.
         */
        private String uiDevice(){
            int shorter=min(cw,ch);
            if(shorter>=440)return "e6";
            if(shorter>=340)return "wide";
            return "e72";
        }
        private String uiTheme(){return themeDark?"dark":"light";}
        private Image asset(String name){
            String target=uiDevice();
            if(!target.equals(deviceAssets)){
                uiCache.clear();uiRecent.removeAllElements();deviceAssets=target;
            }
            String path=target+"/"+name;
            Image existing=(Image)uiCache.get(path);
            if(existing!=null)return existing;
            InputStream in=null;
            try{
                in=getClass().getResourceAsStream("/assets/"+path);
                if(in==null)return null;
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf=new byte[1024];int n;
                while((n=in.read(buf))!=-1){
                    if(out.size()+n>65000)return null;
                    out.write(buf,0,n);
                }
                byte[] data=out.toByteArray();
                Image image=Image.createImage(data,0,data.length);
                // Keep at most 14 PNGs in the decoded cache, regardless of screen.
                if(uiRecent.size()>=14){
                    String old=(String)uiRecent.elementAt(0);
                    uiRecent.removeElementAt(0);uiCache.remove(old);
                }
                uiRecent.addElement(path);uiCache.put(path,image);
                return image;
            }catch(Exception ignored){return null;}
             catch(OutOfMemoryError oom){uiCache.clear();uiRecent.removeAllElements();return null;}
            finally{if(in!=null)try{in.close();}catch(Exception ignored){}}
        }
        private boolean drawAsset(Graphics g,String name,int x,int y){
            Image im=asset(name);
            if(im==null)return false;
            if(x<0||y<0||x+im.getWidth()>cw||y+im.getHeight()>ch)return false;
            g.drawImage(im,x,y,Graphics.TOP|Graphics.LEFT);return true;
        }
        private boolean uiIcon(Graphics g,String name,int x,int y,boolean white){
            return drawAsset(g,"icons/"+(white?"white":"blue")+"/"+name+".png",x,y);
        }
        private void emptyIcon(Graphics g,String name,int y,String text){
            String key=uiTheme()+"/"+name+".png";
            Image im=asset(key);
            if(im!=null){int x=(cw-im.getWidth())/2;
                if(y+im.getHeight()<=bodyBottom)drawAsset(g,key,x,y);
                y+=im.getHeight()+4;
            }
            if(y+small.getHeight()<bodyBottom){
                txt(g,trim(text,cw-2*pad,small),MUTED,max(pad,(cw-small.stringWidth(trim(text,cw-2*pad,small)))/2),y,small);
            }
        }
        private void defaultAvatar(Graphics g,int x,int y){
            if(!drawAsset(g,"avatars/default_profile.png",x,y)){
                fill(g,themeDark?0x35537B:0xDCEBFF,x,y,large?39:27,large?39:27);
                uiIcon(g,"profile",x+4,y+4,false);
            }
        }

        SocialCanvas(){setFullScreenMode(true);layout();}
        private void layout(){layout(getWidth(),getHeight());}
        private void layout(int width,int height){
            cw=max(1,width);ch=max(1,height);
            int shorter=min(cw,ch);
            portrait=ch>cw;
            narrow=cw<280;
            large=shorter>=340;
            compactLandscape=!portrait&&ch<=400;
            pad=shorter>=440?12:large?9:5;
            headerH=large?(compactLandscape?42:shorter>=440?56:47):34;
            navH=large?(compactLandscape?34:43):30;
            footerH=large?(compactLandscape?28:38):24;
            bodyTop=headerH+navH+4;
            bodyBottom=max(bodyTop+1,ch-footerH-3);
            small=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,large?Font.SIZE_MEDIUM:Font.SIZE_SMALL);
            normal=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            title=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,large?Font.SIZE_LARGE:Font.SIZE_MEDIUM);
            cardH=large?(compactLandscape?88:portrait?115:112):(portrait?95:84);
            rowH=large?(compactLandscape?50:65):(portrait?48:40);
        }
        /** MIDP emits this when the phone or emulator changes orientation.
         * Preserve the selected tab, item and in-progress conversation.
         */
        protected void sizeChanged(int w,int h){
            layout(w,h);
            fitLoadedPhoto();
            repaint();
        }
        private void fitLoadedPhoto(){
            if(photo==null)return;
            int limitW=max(1,cw-2*pad-12),limitH=max(1,ch-headerH-footerH-2*pad-12);
            int w=photo.getWidth(),h=photo.getHeight();
            if(w<=limitW&&h<=limitH)return;
            if(w<=0||h<=0||((long)w)*h>1200000L){photo=null;loadedPhoto="";return;}
            int dw,dh;
            if(((long)w)*limitH>((long)h)*limitW){dw=limitW;dh=max(1,(int)(((long)h)*dw/w));}
            else{dh=limitH;dw=max(1,(int)(((long)w)*dh/h));}
            try{
                int[]src=new int[w*h],dst=new int[dw*dh];
                photo.getRGB(src,0,w,0,0,w,h);
                for(int y=0;y<dh;y++)for(int x=0;x<dw;x++)
                    dst[y*dw+x]=src[(int)(((long)y)*h/dh)*w+(int)(((long)x)*w/dw)];
                photo=Image.createRGBImage(dst,dw,dh,false);
            }catch(OutOfMemoryError ignored){photo=null;loadedPhoto="";}
        }
        private int min(int a,int b){return a<b?a:b;}
        private int max(int a,int b){return a>b?a:b;}
        private void fill(Graphics g,int color,int x,int y,int w,int h){if(w<=0||h<=0)return;g.setColor(color);g.fillRect(x,y,w,h);}
        private void txt(Graphics g,String s,int color,int x,int y,Font f){
            if(s==null||s.length()==0)return;g.setColor(color);g.setFont(f);
            g.drawString(s,x,y,Graphics.TOP|Graphics.LEFT);
        }
        private void grad(Graphics g,int y,int h){
            for(int j=0;j<h;j+=2){
                int t=j*22/max(1,h);
                fill(g,((20+t)<<16)|((87+t)<<8)|Math.min(255,205+t),0,y,cw,min(2,h-j));
            }
        }
        private void brandMark(Graphics g,int x,int y,int size){
            if(!drawAsset(g,"logo.png",x,y)){
                fill(g,0xE2F1FF,x,y,size,size);
                fill(g,0x2D7FF9,x+size/4,y+size/4,max(2,size/2),max(2,size/2));
            }
        }
        protected void paint(Graphics g){
            layout();fill(g,BG,0,0,cw,ch);
            grad(g,0,headerH);
            int markSize=large?27:20;
            brandMark(g,pad+3,max(2,(headerH-markSize)/2),markSize);
            txt(g,"MiniSocial",0xFFFFFF,pad+markSize+9,max(3,(headerH-title.getHeight())/2),title);
            txt(g,clock(),0xE9F4FF,max(pad+10,cw-pad-small.stringWidth(clock())),max(3,(headerH-small.getHeight())/2),small);
            if(tab==8)drawServer(g);
            else if(tab==9)drawInfo(g);
            else if(tab==10||tab==13)drawRegister(g);
            else if(tab==11||tab==12)drawPasswordForm(g,tab==11);
            else if(token.length()==0)drawWelcome(g);
            else if(tab==5)photoView(g);
            else{
                topNav(g);
                if(tab==1)drawFeed(g);
                else if(tab==2)drawProfile(g);
                else if(tab==3)drawFriends(g);
                else if(tab==4)drawChat(g);
                else if(tab==6)drawComments(g);
                else if(tab==7)drawRequests(g);
                else if(tab==14)drawSettings(g);
                else if(tab==15)drawHistory(g);
                else if(tab==16)drawStorage(g);
                else if(tab==17||tab==18||tab==19)drawConfirm(g);
            }
            if(optionsOpen)drawOptions(g);
            if((tab!=0&&tab!=10&&tab!=11&&tab!=12&&tab!=13)&&(busy||notification!=null&&notification.length()>0)){
                int messageH=large?24:16;
                fill(g,themeDark?0x203656:0xDDEBFF,0,ch-footerH-messageH,cw,messageH);
                if(busy)drawAsset(g,uiTheme()+"/spinner.png",pad,ch-footerH-messageH+max(0,(messageH-(large?22:16))/2));
                txt(g,trim(busy?"Đang tải...":notification,cw-2*pad-(busy?30:0),small),DARK,pad+(busy?30:0),ch-footerH-messageH+2,small);
            }
            grad(g,ch-footerH,footerH);
            String left=optionsOpen?"Chọn":tab==8?"Sửa (#)":tab==9?"Thông tin":tab==10||tab==11||tab==12||tab==13?"Chọn":token.length()==0?"Chọn":tab==4&&peer.length()>0?"Gửi":tab==14||tab==15||tab==16||tab==17||tab==18||tab==19?"Chọn":"Tùy chọn";
            txt(g,left,0xFFFFFF,pad+3,ch-footerH+max(2,(footerH-bold.getHeight())/2),bold);
            String back=optionsOpen?"Đóng":tab==19?"Không":tab==17||tab==18?"Hủy":tab==1?"Thoát":tab==13?"Hồ sơ":tab==10||tab==11?"Đăng nhập":tab==12?"Cài đặt":tab==9?"Trở lại":token.length()==0&&tab!=8?"Thoát":"Trở về";
            txt(g,back,0xFFFFFF,cw-pad-3-bold.stringWidth(back),ch-footerH+max(2,(footerH-bold.getHeight())/2),bold);
        }
        /** Compact, responsive web-style login card, rendered without PNG assets. */
        private final class LoginLayout {
            int x,y,w,h,titleY,subY,label1Y,field1Y,label2Y,field2Y,fieldH,buttonY,buttonH,linksY;
            int[] linkX=new int[3],linkW=new int[3];
        }
        private LoginLayout loginLayout(){
            LoginLayout q=new LoginLayout();
            q.w=min(cw-2*(pad+5),cw>=480?520:420);
            q.x=(cw-q.w)/2;
            int room=ch-headerH-footerH-6;
            boolean tight=room<220;
            q.fieldH=tight?24:large?39:33;
            q.buttonH=tight?26:large?39:34;
            int top=tight?8:large?18:15;
            int subtitleGap=tight?0:8;
            int labelGap=tight?5:large?17:10;
            int inside=max(8,q.w/18);
            q.titleY=top;
            q.subY=q.titleY+bold.getHeight()+subtitleGap;
            q.label1Y=tight?q.titleY+bold.getHeight()+labelGap:q.subY+small.getHeight()+labelGap;
            q.field1Y=q.label1Y+small.getHeight()+2;
            q.label2Y=q.field1Y+q.fieldH+(tight?4:large?11:8);
            q.field2Y=q.label2Y+small.getHeight()+2;
            q.buttonY=q.field2Y+q.fieldH+(tight?7:large?15:10);
            q.linksY=q.buttonY+q.buttonH+(tight?8:large?14:12);
            q.h=q.linksY+small.getHeight()+(tight?9:large?22:15);
            if(!tight&&authError.length()>0&&loginErrorField>=0){
                int gap=small.getHeight()+4;
                if(loginErrorField==0){q.label2Y+=gap;q.field2Y+=gap;}
                q.buttonY+=gap;q.linksY+=gap;q.h+=gap;
            }
            // Show validation errors INSIDE the card even at 320x240.
            if(tight&&(authError.length()>0||notification.length()>0)){
                int extra=9;
                q.label1Y+=extra;q.field1Y+=extra;q.label2Y+=extra;
                q.field2Y+=extra;q.buttonY+=extra;q.linksY+=extra;q.h+=extra;
            }
            // On very short viewports, use all the remaining safe space.
            if(q.h>room-5)q.h=room-5;
            q.y=headerH+max(3,(room-q.h)/2);
            String[]links={"Máy chủ","Tạo tài khoản","Thông tin"};
            int sum=0;
            for(int i=0;i<3;i++){q.linkW[i]=small.stringWidth(links[i])+8;sum+=q.linkW[i];}
            int gap=max(4,(q.w-2*inside-sum)/2);
            int total=sum+2*gap;
            int at=q.x+max(inside,(q.w-total)/2);
            for(int i=0;i<3;i++){q.linkX[i]=at;at+=q.linkW[i]+gap;}
            return q;
        }
        private void drawWelcome(Graphics g){
            LoginLayout q=loginLayout();
            fill(g,themeDark?0x10233C:0xD7E6FA,q.x+2,q.y+3,q.w,max(0,q.h-1));
            fill(g,themeDark?0x1B2B43:0xFFFFFF,q.x,q.y,q.w,q.h);
            g.setColor(0xBED1EC);g.drawRect(q.x,q.y,q.w-1,q.h-1);
            int inner=q.x+max(9,q.w/19),width=q.w-2*max(9,q.w/19);
            // Small highlight bar above the login form.
            fill(g,BLUE,inner,q.y+3,min(45,width/4),3);
            txt(g,"Chào mừng trở lại",DARK,inner,q.y+q.titleY,bold);
            boolean tight=ch-headerH-footerH-6<220;
            String loginStatus=(authError.length()>0&&(ch-headerH-footerH-6<220))?authError:notification;
            if(!tight||loginStatus.length()>0)
                txt(g,trim(loginStatus.length()>0?loginStatus:"Đăng nhập để kết nối bạn bè",width,small),loginStatus.length()>0?0xB14830:MUTED,inner,q.y+q.subY,small);
            txt(g,"Tên tài khoản",MUTED,inner,q.y+q.label1Y,small);
            fill(g,themeDark?(welcomeSelection==0?0x2B466A:0x16283E):(welcomeSelection==0?0xE9F3FF:0xF8FAFF),inner,q.y+q.field1Y,width,q.fieldH);
            g.setColor(welcomeSelection==0?BLUE:BORDER);g.drawRect(inner,q.y+q.field1Y,width-1,q.fieldH-1);
            txt(g,trim(loginEmail.length()>0?loginEmail:"Nhập tên tài khoản",width-18,normal),loginEmail.length()>0?TEXT:0x7892B5,inner+8,q.y+q.field1Y+max(2,(q.fieldH-normal.getHeight())/2),normal);
            if(!tight&&authError.length()>0&&loginErrorField==0)
                txt(g,trim(authError,width,small),0xB14830,inner,q.y+q.field1Y+q.fieldH+2,small);
            txt(g,"Mật khẩu",MUTED,inner,q.y+q.label2Y,small);
            String lost="Quên mật khẩu?";
            int lostX=inner+width-small.stringWidth(lost)-2;
            txt(g,lost,welcomeSelection==6?SKY:GREEN_DEEP,lostX,q.y+q.label2Y,small);
            if(welcomeSelection==6)fill(g,SKY,lostX,q.y+q.label2Y+small.getHeight(),small.stringWidth(lost),1);
            fill(g,themeDark?(welcomeSelection==1?0x2B466A:0x16283E):(welcomeSelection==1?0xE9F3FF:0xF8FAFF),inner,q.y+q.field2Y,width,q.fieldH);
            g.setColor(welcomeSelection==1?BLUE:BORDER);g.drawRect(inner,q.y+q.field2Y,width-1,q.fieldH-1);
            String mask="";for(int i=0;i<min(16,loginPassword.length());i++)mask+="*";
            txt(g,mask.length()>0?mask:"Nhập mật khẩu",mask.length()>0?TEXT:0x7892B5,inner+8,q.y+q.field2Y+max(2,(q.fieldH-normal.getHeight())/2),normal);
            if(!tight&&authError.length()>0&&loginErrorField==1)
                txt(g,trim(authError,width,small),0xB14830,inner,q.y+q.field2Y+q.fieldH+2,small);
            fill(g,welcomeSelection==2?SKY:GREEN_DEEP,inner,q.y+q.buttonY,width,q.buttonH);
            if(welcomeSelection==2){g.setColor(0xFFFFFF);g.drawRect(inner+2,q.y+q.buttonY+2,max(0,width-5),max(0,q.buttonH-5));}
            String button="Đăng nhập";
            txt(g,button,0xFFFFFF,inner+max(4,(width-bold.stringWidth(button))/2),q.y+q.buttonY+max(2,(q.buttonH-bold.getHeight())/2),bold);
            String[]links={"Máy chủ","Tạo tài khoản","Thông tin"};
            for(int i=0;i<3;i++){
                int color=welcomeSelection==i+3?SKY:GREEN_DEEP;
                txt(g,links[i],color,q.linkX[i]+4,q.y+q.linksY,small);
                if(welcomeSelection==i+3)fill(g,SKY,q.linkX[i]+3,q.y+q.linksY+small.getHeight()+1,max(2,q.linkW[i]-6),1);
            }
        }
        /** Reused three-field card for signup and first-login profile, scrolls on small Nokia displays. */
        private final class RegisterLayout {
            int x,y,w,h,inner,width,rowY,rowH,first,visible,submitY,buttonH,linkY;
        }
        private RegisterLayout registerLayout(){
            RegisterLayout q=new RegisterLayout();
            q.w=min(cw-2*(pad+5),cw>=480?520:420);
            q.x=(cw-q.w)/2;
            int room=ch-headerH-footerH-8;
            q.h=max(95,room-4);
            q.y=headerH+max(3,(room-q.h)/2);
            int inset=max(9,q.w/20);q.inner=q.x+inset;q.width=q.w-2*inset;
            q.rowY=q.y+(ch<300?bold.getHeight()+21:bold.getHeight()+small.getHeight()+18);
            q.rowH=large?52:narrow?35:26;
            q.buttonH=large?38:27;
            q.linkY=q.y+q.h-small.getHeight()-8;
            q.submitY=q.linkY-q.buttonH-6;
            int errorGap=authError.length()>0&&registerErrorField>=0?small.getHeight()+4:0;
            q.visible=min(3,max(1,(q.submitY-q.rowY-3-errorGap)/q.rowH));
            int focus=(tab==13?onboardingSelection:registerSelection)>=3?2:(tab==13?onboardingSelection:registerSelection);
            q.first=max(0,min(3-q.visible,focus-q.visible+1));
            return q;
        }
        private void drawRegister(Graphics g){
            RegisterLayout q=registerLayout();
            fill(g,themeDark?0x10233C:0xD3E5FB,q.x+2,q.y+3,q.w,q.h);
            fill(g,themeDark?0x1B2B43:0xFFFFFF,q.x,q.y,q.w,q.h);
            g.setColor(BORDER);g.drawRect(q.x,q.y,q.w-1,q.h-1);
            fill(g,BLUE,q.inner,q.y+3,min(56,q.width/4),3);
            txt(g,tab==13?"Thiết lập hồ sơ":"Tạo tài khoản",DARK,q.inner,q.y+8,bold);
            String caption=authError.length()>0?authError:notification.length()>0?notification:tab==13?"Tên, avatar và model thiết bị":"Nhập 3 thông tin để đăng ký";
            if(ch>=300)txt(g,trim(caption,q.width,small),notification.length()>0?0xA83B2B:MUTED,q.inner,q.y+9+bold.getHeight(),small);
            if(q.visible<3){String page=(q.first+1)+"-"+(q.first+q.visible)+"/3";
                txt(g,page,MUTED,q.inner+q.width-small.stringWidth(page),q.y+10,small);
            }
            for(int i=q.first;i<q.first+q.visible;i++){
                int errorGap=authError.length()>0&&registerErrorField>=0?small.getHeight()+4:0;
                int y=q.rowY+(i-q.first)*q.rowH+(i>registerErrorField?errorGap:0);
                int controlH=ch<300?q.rowH-3:q.rowH-small.getHeight()-4;
                int controlY=ch<300?y:y+small.getHeight()+1;
                boolean selected=i==(tab==13?onboardingSelection:registerSelection);
                String[] labels=tab==13?SETUP_LABELS:REG_LABELS;
                String[] hints=tab==13?SETUP_HINTS:REG_HINTS;
                if(ch>=300)txt(g,labels[i],selected?GREEN_DEEP:MUTED,q.inner,y,small);
                fill(g,themeDark?(selected?0x2B466A:0x16283E):(selected?0xE9F3FF:0xF8FAFF),q.inner,controlY,q.width,controlH);
                g.setColor(selected?BLUE:BORDER);g.drawRect(q.inner,controlY,q.width-1,controlH-1);
                String content=(tab==13?onboardingFields:registerFields)[i];
                if(tab==10&&i>=1&&content.length()>0){StringBuffer b=new StringBuffer();for(int z=0;z<min(16,content.length());z++)b.append('*');content=b.toString();}
                String show=content.length()>0?content:hints[i];
                if(ch<300)show=labels[i]+": "+(content.length()>0?content:"...");
                Font f=ch<300?small:normal;
                txt(g,trim(show,q.width-12,f),content.length()>0?TEXT:0x78968B,q.inner+6,controlY+max(1,(controlH-f.getHeight())/2),f);
                if(authError.length()>0&&registerErrorField==i)
                    txt(g,trim(authError,q.width,small),0xAF3F32,q.inner,y+q.rowH+1,small);
            }
            fill(g,(tab==13?onboardingSelection:registerSelection)==3?SKY:GREEN_DEEP,q.inner,q.submitY,q.width,q.buttonH);
            if((tab==13?onboardingSelection:registerSelection)==3){g.setColor(0xFFFFFF);g.drawRect(q.inner+2,q.submitY+2,q.width-5,q.buttonH-5);}
            String label=busy?"Đang lưu...":tab==13?"Hoàn tất":"Đăng ký";
            txt(g,label,0xFFFFFF,q.inner+max(3,(q.width-bold.stringWidth(label))/2),q.submitY+max(1,(q.buttonH-bold.getHeight())/2),bold);
            String back=tab==13?"Quay lại hồ sơ":"Đã có tài khoản? Đăng nhập";
            int linkColor=(tab==13?onboardingSelection:registerSelection)==4?SKY:GREEN_DEEP;
            txt(g,trim(back,q.width,small),linkColor,q.inner+max(0,(q.width-small.stringWidth(back))/2),q.linkY,small);
            if((tab==13?onboardingSelection:registerSelection)==4)fill(g,SKY,q.inner+max(0,(q.width-small.stringWidth(back))/2),q.linkY+small.getHeight()+1,min(q.width,small.stringWidth(back)),1);
        }
        /** Unified OTP recovery/change-password form; rows scroll on Nokia 320x240. */
        private final class SecurityLayout {
            int x,y,w,h,inner,width,first,visible,rowY,rowH,buttonH,btn1Y,btn2Y,backY,count;
        }
        private SecurityLayout securityLayout(boolean recovery){
            SecurityLayout q=new SecurityLayout();q.count=recovery?4:3;
            q.w=min(cw-2*(pad+5),cw>=480?520:420);q.x=(cw-q.w)/2;
            int room=ch-headerH-footerH-8;q.h=max(95,room-4);
            q.y=headerH+max(3,(room-q.h)/2);
            int inset=max(9,q.w/20);q.inner=q.x+inset;q.width=q.w-2*inset;
            q.rowY=q.y+bold.getHeight()+(ch<300?23:48);
            q.rowH=large?52:narrow?36:27;
            q.buttonH=large?38:26;
            q.backY=q.y+q.h-small.getHeight()-7;
            q.btn2Y=q.backY-q.buttonH-5;
            q.btn1Y=recovery?q.btn2Y-q.buttonH-4:q.btn2Y;
            q.visible=min(q.count,max(1,(q.btn1Y-q.rowY-4)/q.rowH));
            int focus=(recovery?recoverySelection:changeSelection);
            if(focus>=q.count)focus=q.count-1;
            q.first=max(0,min(q.count-q.visible,focus-q.visible+1));
            return q;
        }
        private void drawPasswordForm(Graphics g,boolean recovery){
            SecurityLayout q=securityLayout(recovery);
            fill(g,themeDark?0x10233C:0xD3E5FB,q.x+2,q.y+3,q.w,q.h);
            fill(g,themeDark?0x1B2B43:0xFFFFFF,q.x,q.y,q.w,q.h);
            g.setColor(BORDER);g.drawRect(q.x,q.y,q.w-1,q.h-1);
            fill(g,BLUE,q.inner,q.y+3,min(56,q.width/4),3);
            String heading=recovery?"Quên mật khẩu":"Đổi mật khẩu";
            txt(g,heading,DARK,q.inner,q.y+8,bold);
            String info=authError.length()>0?authError:notification.length()>0?notification:(recovery?"Nhập email để nhận OTP":"Yêu cầu mật khẩu hiện tại");
            if(ch>=300)txt(g,trim(info,q.width,small),authError.length()>0?0xB14830:MUTED,q.inner,q.y+bold.getHeight()+13,small);
            int focus=recovery?recoverySelection:changeSelection;
            String[] fields=recovery?recoveryFields:changeFields;
            String[] labels=recovery?RECOVERY_LABELS:CHANGE_LABELS;
            for(int i=q.first;i<q.first+q.visible;i++){
                int ry=q.rowY+(i-q.first)*q.rowH;
                boolean selected=focus==i;
                int fieldY=ch<300?ry:ry+small.getHeight()+1;
                int fieldH=ch<300?q.rowH-3:q.rowH-small.getHeight()-4;
                if(ch>=300)txt(g,labels[i],selected?GREEN_DEEP:MUTED,q.inner,ry,small);
                fill(g,themeDark?(selected?0x2B466A:0x16283E):(selected?0xE9F3FF:0xF8FAFF),q.inner,fieldY,q.width,fieldH);
                g.setColor(selected?BLUE:BORDER);g.drawRect(q.inner,fieldY,q.width-1,fieldH-1);
                String value=fields[i];
                boolean password=recovery?i>=2:true;
                if(recovery&&i==1)password=false;
                if(password&&value.length()>0){StringBuffer m=new StringBuffer();for(int z=0;z<min(value.length(),16);z++)m.append('*');value=m.toString();}
                String label=value.length()==0?(recovery&&i==1?"6 chữ số":labels[i]):value;
                if(ch<300)label=(recovery&&i==1?"OTP":labels[i])+": "+(value.length()==0?"...":value);
                txt(g,trim(label,q.width-14,small),value.length()>0?TEXT:MUTED,q.inner+7,fieldY+max(1,(fieldH-small.getHeight())/2),small);
            }
            if(q.visible<q.count){String page=(q.first+1)+"-"+(q.first+q.visible)+"/"+q.count;
                txt(g,page,MUTED,q.inner+q.width-small.stringWidth(page),q.y+10,small);}
            if(recovery){
                fill(g,focus==4?SKY:GREEN,q.inner,q.btn1Y,q.width,q.buttonH);
                String b1="Gửi mã OTP qua email";
                txt(g,trim(b1,q.width-10,bold),0xFFFFFF,q.inner+max(4,(q.width-bold.stringWidth(b1))/2),q.btn1Y+max(1,(q.buttonH-bold.getHeight())/2),bold);
            }
            String main=recovery?"Xác nhận OTP và đặt mật khẩu":"Đổi mật khẩu";
            int bottomFocus=recovery?5:3;
            fill(g,focus==bottomFocus?SKY:GREEN_DEEP,q.inner,q.btn2Y,q.width,q.buttonH);
            txt(g,trim(main,q.width-8,small),0xFFFFFF,q.inner+max(4,(q.width-small.stringWidth(main))/2),q.btn2Y+max(1,(q.buttonH-small.getHeight())/2),small);
            String back="Trở lại";
            txt(g,back,focus==(recovery?6:4)?SKY:GREEN_DEEP,q.inner+max(1,(q.width-small.stringWidth(back))/2),q.backY,small);
            if(ch<300&&(authError.length()>0||notification.length()>0))
                txt(g,trim(info,q.width,small),authError.length()>0?0xB14830:GREEN_DEEP,q.inner,q.y+8+bold.getHeight()+2,small);
        }
        private void drawInfo(Graphics g){
            int w=min(cw-2*pad-12,400),x=(cw-w)/2,y=headerH+max(12,(bodyBottom-headerH-180)/2);
            int h=min(bodyBottom-y-5,large?225:170);
            panel(g,x,y,w,h,false);
            int ix=x+10;
            txt(g,"Về MiniSocial",DARK,ix,y+12,bold);
            txt(g,"Phiên bản 1.5.0",GREEN_DEEP,ix,y+12+bold.getHeight()+9,normal);
            int ty=y+12+bold.getHeight()+normal.getHeight()+22;
            txt(g,trim("Mạng xã hội dành cho Java ME",w-20,small),TEXT,ix,ty,small);
            txt(g,trim("Xubi + Supabase | Nokia E72 / E6",w-20,small),MUTED,ix,ty+small.getHeight()+7,small);
            txt(g,trim("Phím 0 hoặc Quay lại để đóng",w-20,small),SKY,ix,ty+2*(small.getHeight()+7),small);
        }
        private int serverFirstY(){return headerH+max(14,ch/30)+bold.getHeight()+max(10,ch/45);}
        private int serverGap(){return large?10:5;}
        private int serverRowH(){return max(28,min(103,(bodyBottom-serverFirstY()-2*serverGap()-6)/3));}
        private void drawServer(Graphics g){
            int y=headerH+max(10,ch/30);
            txt(g,trim("Chon may chu",cw-2*pad-10,bold),DARK,pad+7,y,bold);
            int first=serverFirstY(),h=serverRowH(),gap=serverGap();
            String[]name={"4G / Internet","Wi-Fi cung Pi","Tuy chinh"};
            String[]addresses={publicHost,lanHost,customHost};
            for(int i=0;i<3;i++){
                int ry=first+i*(h+gap),x=pad+5,w=cw-2*x;
                panel(g,x,ry,w,h,i==serverSelection);
                int dot=max(8,min(13,h/4));
                fill(g,i==serverMode?GREEN_DEEP:0xB5D2C6,x+8,ry+9,dot,dot);
                if(i==serverMode)fill(g,0xEDF5FF,x+11,ry+12,max(2,dot-6),max(2,dot-6));
                txt(g,trim(name[i],w-dot-26,bold),i==serverSelection?DARK:TEXT,x+dot+14,ry+max(5,(h-(small.getHeight()+bold.getHeight()+8))/2),bold);
                String line=addresses[i];
                txt(g,trim(line,w-15,small),MUTED,x+9,ry+h-small.getHeight()-7,small);
                if(w>210)txt(g,"Sua",SKY,x+w-small.stringWidth("Sua")-12,ry+10,small);
                if(i==serverMode&&w>275)txt(g,"ON",GREEN_DEEP,x+w-small.stringWidth("Sua")-small.stringWidth("ON")-22,ry+10,small);
            }
            int bottom=first+3*h+2*gap+max(3,large?8:4);
            if(bottom+small.getHeight()<bodyBottom)
                txt(g,trim("OK: ket noi | #: sua URL | 5: kiem tra",cw-2*pad-12,small),DARK,pad+6,bottom,small);
            if(bottom+2*small.getHeight()+4<bodyBottom)
                txt(g,trim("HTTP chi trong LAN; ngoai mang dung HTTPS",cw-2*pad-12,small),MUTED,pad+6,bottom+small.getHeight()+3,small);
        }
        private String clock(){
            java.util.Calendar c=java.util.Calendar.getInstance();int h=c.get(java.util.Calendar.HOUR_OF_DAY),m=c.get(java.util.Calendar.MINUTE);
            return (h<10?"0":"")+h+":"+(m<10?"0":"")+m;
        }
        private int navIndex(int i){return (tab==1&&i==0)||(tab==4&&i==1)||(tab==3&&i==2)?1:0;}
        /** Lucide-inspired 1px outline icons, rendered as MIDP Canvas strokes. No PNG/SVG dependencies. */
        private void stroke(Graphics g,int x1,int y1,int x2,int y2){g.drawLine(x1,y1,x2,y2);}
        private void icon(Graphics g,int kind,int x,int y,int size,int color){
            g.setColor(color);int a=Math.max(2,size/5),m=size/2;
            if(kind==0){ // house
                stroke(g,x+1,y+m,x+m,y+1);stroke(g,x+m,y+1,x+size-1,y+m);
                stroke(g,x+3,y+m,x+3,y+size-2);stroke(g,x+size-3,y+m,x+size-3,y+size-2);
                stroke(g,x+3,y+size-2,x+size-3,y+size-2);stroke(g,x+m-2,y+size-2,x+m-2,y+size-a);
                stroke(g,x+m+2,y+size-2,x+m+2,y+size-a);stroke(g,x+m-2,y+size-a,x+m+2,y+size-a);
            }else if(kind==1){ // messages
                g.drawRoundRect(x+1,y+1,size-3,size-5,5,5);
                stroke(g,x+size/3,y+size-4,x+size/4,y+size-1);
                stroke(g,x+size/4,y+size-1,x+size/2,y+size-4);
                stroke(g,x+size/4,y+size/3,x+size*3/4,y+size/3);
                stroke(g,x+size/4,y+size/2,x+size*2/3,y+size/2);
            }else if(kind==2){ // users
                g.drawArc(x+2,y+1,size/2,size/2,0,360);g.drawArc(x+size/2,y+3,size/3,size/3,0,360);
                g.drawArc(x+1,y+size/2,size*2/3,size/2,0,180);
                stroke(g,x+size/2,y+size-3,x+size-1,y+size-3);
            }else if(kind==3){ // user
                g.drawArc(x+size/3,y+1,size/3,size/3,0,360);
                g.drawArc(x+2,y+size/2,size-4,size/2,0,180);
            }else if(kind==4){ // settings
                g.drawArc(x+size/3,y+size/3,size/3,size/3,0,360);
                g.drawRect(x+3,y+3,size-7,size-7);
                stroke(g,x+m,y,x+m,y+3);stroke(g,x+m,y+size-4,x+m,y+size-1);
                stroke(g,x,y+m,x+3,y+m);stroke(g,x+size-4,y+m,x+size-1,y+m);
            }else if(kind==5){ // history
                g.drawArc(x+2,y+2,size-4,size-4,30,290);
                stroke(g,x+1,y+3,x+1,y+size/3);stroke(g,x+1,y+size/3,x+size/3,y+size/3);
                stroke(g,x+m,y+4,x+m,y+m);stroke(g,x+m,y+m,x+m+a,y+m+a);
            }else if(kind==6){ // storage
                g.drawRoundRect(x+1,y+size/4,size-2,size/2,4,4);
                g.drawArc(x+size-7,y+m-2,3,3,0,360);
            }else if(kind==7){ // notification
                g.drawArc(x+size/4,y+size/5,size/2,size/2,0,180);
                stroke(g,x+size/4,y+m,x+size/4,y+size-5);stroke(g,x+size*3/4,y+m,x+size*3/4,y+size-5);
                stroke(g,x+2,y+size-5,x+size-2,y+size-5);
                stroke(g,x+m-2,y+size-3,x+m+2,y+size-3);
            }else if(kind==8){ // moon
                g.drawArc(x+2,y+1,size-4,size-4,60,280);
                stroke(g,x+m,y+2,x+m+a,y+a);stroke(g,x+m+a,y+a,x+size-2,y+m);
            }else if(kind==9){ // pencil
                stroke(g,x+3,y+size-4,x+size-4,y+3);
                stroke(g,x+6,y+size-3,x+size-3,y+6);
                stroke(g,x+3,y+size-4,x+6,y+size-3);
                stroke(g,x+size-4,y+3,x+size-3,y+6);
            }else if(kind==10){ // lock
                g.drawArc(x+size/4,y+1,size/2,size/2,0,180);
                g.drawRoundRect(x+2,y+m-1,size-4,size/2,3,3);
            }else if(kind==11){ // trash
                g.drawRect(x+size/4,y+size/3,size/2,size/2);
                stroke(g,x+2,y+size/4,x+size-2,y+size/4);
                stroke(g,x+size/3,y+2,x+size*2/3,y+2);
            }
        }
        private void topNav(Graphics g){
            fill(g,themeDark?0x17263B:0xFFFFFF,0,headerH,cw,navH);
            String[] labels={"Bảng tin","Chat","Bạn bè"};
            String[] names={"home","chats","groups"};
            for(int i=0;i<3;i++){
                int x=i*cw/3,w=(i+1)*cw/3-x,y=headerH;
                boolean active=navIndex(i)==1,focus=navFocus&&navSelection==i;
                if(active||focus){
                    fill(g,themeDark?0x213E65:0xEAF3FF,x+1,y+1,w-2,navH-2);
                    fill(g,BLUE,x+5,y+navH-3,w-10,3);
                }
                int iconSize=large?23:16,sep=large?8:4;
                int tx=max(x+2,x+(w-small.stringWidth(labels[i])-iconSize-sep)/2);
                int iy=y+max(2,(navH-iconSize)/2);
                if(!uiIcon(g,names[i],tx,iy,themeDark))icon(g,i,tx,iy,iconSize,BLUE);
                txt(g,labels[i],active||focus?DARK:TEXT,tx+iconSize+sep,y+max(1,(navH-small.getHeight())/2),small);
            }
        }
        private void drawOptions(Graphics g){
            int h=large?40:31,w=Math.min(cw-2*pad,large?260:194),x=pad+2,y=ch-footerH-4* h-6;
            if(y<headerH+2)y=headerH+2;
            fill(g,themeDark?0x1C312D:0xFFFFFF,x,y,w,4*h+2);
            g.setColor(BORDER);g.drawRect(x,y,w-1,4*h+1);
            for(int i=0;i<4;i++){
                int yy=y+i*h,icol=themeDark?0xD4EEE2:DARK;
                if(i==optionsSelection)fill(g,themeDark?0x2B5548:0xE3F0FF,x+2,yy+1,w-4,h-2);
                String[] symbols={"profile","settings","clock","file"};
                if(!uiIcon(g,symbols[i],x+9,yy+(h-(large?23:16))/2,themeDark))
                    icon(g,3+i,x+9,yy+(h-18)/2,18,icol);
                txt(g,OPTIONS[i],icol,x+36,yy+max(2,(h-normal.getHeight())/2),normal);
            }
        }
        private void panel(Graphics g,int x,int y,int w,int h,boolean active){
            if(w<=0||h<=0)return;
            g.setColor(themeDark?(active?0x1F3D62:0x17263A):(active?0xE3F0FF:0xFFFFFF));
            g.fillRoundRect(x,y,w,h,large?15:9,large?15:9);
            g.setColor(active?BLUE:BORDER);
            g.drawRoundRect(x,y,w-1,h-1,large?15:9,large?15:9);
        }
        private void headerHint(Graphics g,String s,int y){
            txt(g,trim(s,cw-2*pad-10,small),MUTED,pad+5,y,small);
        }
        private void drawFeed(Graphics g){
            int y=bodyTop+1,h=large?43:33;
            panel(g,pad,y,cw-2*pad,h,false);
            uiIcon(g,"plus",pad+8,y+max(3,(h-(large?23:16))/2),themeDark);
            String text=narrow?"2: Bài viết | 8: Ảnh":"2: Đăng bài     8: Chọn ảnh";
            txt(g,trim(text,cw-2*pad-38,normal),TEXT,pad+(large?39:28),y+max(3,(h-normal.getHeight())/2),normal);
            if(feed.size()==0){emptyIcon(g,"empty_feed",y+h+12,"Chưa có bài viết. Nhấn 2 để đăng.");return;}
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
                Image postAvatar=(Image)avatarCache.get(piece(a,9));
                if(postAvatar!=null)g.drawImage(postAvatar,pad+6,ry+6,Graphics.TOP|Graphics.LEFT);
                else defaultAvatar(g,pad+6,ry+6);
                int textX=pad+avatar+13;
                int nameY=ry+5;
                txt(g,trim(piece(a,2),cw-textX-pad-4,bold),DARK,textX,nameY,bold);
                txt(g,trim(piece(a,3).replace('T',' ')+" · "+piece(a,8),cw-textX-pad-4,small),MUTED,textX,nameY+bold.getHeight()+2,small);
                int contentY=ry+avatar+12;
                int actionY=ry+cardH-small.getHeight()-5;
                int lines=max(1,(actionY-contentY-1)/max(1,normal.getHeight()));
                wrapped(g,piece(a,6),TEXT,pad+8,contentY,cw-2*pad-16,normal,lines);
                String actions="Thích "+piece(a,4)+"   BL "+piece(a,5)+(piece(a,7).length()>0?"   [Ảnh]":"");
                uiIcon(g,"like",pad+8,actionY-1,themeDark);
                txt(g,trim(actions,cw-2*pad-44,small),BLUE,pad+(large?38:29),actionY,small);
            }
        }
        private void sectionTitle(Graphics g,String titleText,String subtitle){
            txt(g,titleText,DARK,pad+8,bodyTop+4,bold);
            txt(g,trim(subtitle,cw-2*pad-16,small),MUTED,pad+8,bodyTop+bold.getHeight()+7,small);
        }
        private int settingsTop(){return bodyTop+bold.getHeight()+small.getHeight()+16;}
        private int settingsRow(){return large?53:29;}
        private void drawSettings(Graphics g){
            sectionTitle(g,"Cài đặt","Cá nhân hóa và quản lý tài khoản");
            int y=settingsTop(),h=settingsRow();
            int count=max(1,(bodyBottom-y)/h),start=max(0,min(settingsSelection-count+1,SETTINGS.length-count));
            for(int i=start;i<min(SETTINGS.length,start+count);i++){
                int ry=y+(i-start)*h;if(ry+h>bodyBottom)break;
                panel(g,pad+3,ry,cw-2*pad-6,h-2,i==settingsSelection);
                String[] symbols={"bell","moon","edit","lock","delete","logout"};
                if(!uiIcon(g,symbols[i],pad+12,ry+(h-(large?23:16))/2,themeDark))
                    icon(g,i==0?7:i==1?8:i==2?9:i==3?10:i==4?11:3,pad+12,ry+(h-17)/2,17,DARK);
                String text=SETTINGS[i];
                if(i==0)text+=" : "+(notifyEnabled?"Bật":"Tắt");
                if(i==1)text+=" : "+(themeDark?"Tối":"Sáng");
                txt(g,trim(text,cw-2*pad-(large?135:95),small),TEXT,pad+(large?49:38),ry+max(2,(h-small.getHeight())/2),small);
                if(i==0||i==1){
                    String toggle=uiTheme()+"/toggle_"+((i==0?notifyEnabled:themeDark)?"on":"off")+".png";
                    Image switchImage=asset(toggle);
                    if(switchImage!=null)drawAsset(g,toggle,cw-pad-switchImage.getWidth()-12,ry+max(1,(h-switchImage.getHeight())/2));
                }
            }
        }
        private void selectSettings(){
            if(settingsSelection==0){notifyEnabled=!notifyEnabled;savePreferences();notification=notifyEnabled?"Đã bật thông báo nội bộ":"Đã tắt thông báo nội bộ";}
            else if(settingsSelection==1){themeDark=!themeDark;applyTheme();savePreferences();}
            else if(settingsSelection==2){profileId=me;edit("profile-name","Đổi tên hiển thị",TextField.ANY);}
            else if(settingsSelection==3){openChange();}
            else if(settingsSelection==4){deleteAccountPassword="";tab=17;}
            else if(settingsSelection==5){logout();}
            screen.repaint();
        }
        private int historyListTop(){return settingsTop()+(large?37:27);}
        private int historyRow(){return large?66:39;}
        private int historyWindowStart(){
            int count=max(1,(bodyBottom-historyListTop())/historyRow());
            return max(0,min(selection-count+1,max(0,historyItems.size()-count)));
        }
        private void drawHistory(Graphics g){
            sectionTitle(g,"Lịch sử","Bài đã thích và đã đăng");
            int y=settingsTop(),buttonH=large?32:23;
            for(int i=0;i<2;i++){
                int x=pad+3+i*(cw-2*pad-6)/2,w=(cw-2*pad-6)/2;
                fill(g,i==historyMode?GREEN_DEEP:(themeDark?0x254439:0xE6F0FF),x,y,w-2,buttonH);
                txt(g,i==0?"Đã thả like":"Đã đăng",i==historyMode?0xFFFFFF:TEXT,x+max(3,(w-small.stringWidth(i==0?"Đã thả like":"Đã đăng"))/2),y+max(2,(buttonH-small.getHeight())/2),small);
            }
            int first=historyWindowStart(),top=historyListTop(),h=historyRow();
            for(int i=first;i<historyItems.size();i++){
                int ry=top+(i-first)*h;if(ry+h>bodyBottom)break;
                String[]item=(String[])historyItems.elementAt(i);
                panel(g,pad+3,ry,cw-2*pad-6,h-3,i==selection);
                txt(g,trim(piece(item,2),cw-2*pad-22,bold),DARK,pad+10,ry+3,bold);
                txt(g,trim(piece(item,6),cw-2*pad-22,small),TEXT,pad+10,ry+4+bold.getHeight(),small);
            }
            if(historyItems.size()==0)headerHint(g,"Chưa có bài viết trong mục này",top+8);
        }
        private int storageBtnY(){return large?settingsTop()+146:bodyTop+66;}
        private int storageBtnH(){return large?38:29;}
        private void drawStorage(Graphics g){
            if(large){
                sectionTitle(g,"Dung lượng","Giới hạn sử dụng Supabase");
                int y=settingsTop()+7,w=cw-2*pad-16;
                panel(g,pad+8,y,w,138,false);
                txt(g,"Đã sử dụng",MUTED,pad+17,y+9,small);
                txt(g,trim(displayMB(storageUsed)+" / "+displayMB(storageLimit)+" MB",w-16,bold),DARK,pad+17,y+small.getHeight()+15,bold);
                int py=y+small.getHeight()+bold.getHeight()+25,bw=w-24;
                fill(g,themeDark?0x33564B:0xDBEAE3,pad+20,py,bw,10);
                int used=(int)Math.min((long)bw,storageLimit<=0?0:storageUsed*bw/storageLimit);
                fill(g,used*10>bw*9?0xDC6B4A:GREEN,pad+20,py,used,10);
            }else{
                txt(g,"Dung lượng",DARK,pad+8,bodyTop+3,bold);
                String value=displayMB(storageUsed)+" / "+displayMB(storageLimit)+" MB";
                txt(g,value,TEXT,pad+8,bodyTop+7+bold.getHeight(),normal);
                int bw=cw-2*pad-16,py=bodyTop+12+bold.getHeight()+normal.getHeight();
                fill(g,themeDark?0x33564B:0xDBEAE3,pad+8,py,bw,6);
                int used=(int)Math.min((long)bw,storageLimit<=0?0:storageUsed*bw/storageLimit);
                fill(g,used*10>bw*9?0xDC6B4A:GREEN,pad+8,py,used,6);
            }
            int by=storageBtnY(),bh=storageBtnH(),w=cw-2*pad-16;
            for(int i=0;i<2;i++){
                int ry=by+i*(bh+4);if(ry+bh>bodyBottom)break;
                panel(g,pad+8,ry,w,bh,storageSelection==i);
                icon(g,i==0?6:11,pad+17,ry+(bh-16)/2,16,i==0?DARK:0xBE6154);
                txt(g,i==0?"Xóa cache máy":"Xóa sạch ảnh đã tải",TEXT,pad+40,ry+max(2,(bh-small.getHeight())/2),small);
            }
        }
        private void selectStorage(){
            if(storageSelection==0)clearLocalCache();
            else {tab=18;screen.repaint();}
        }
        private void drawConfirm(Graphics g){
            int y=bodyTop+8,h=large?150:115;
            panel(g,pad+6,y,cw-2*pad-12,h,false);
            String title0=tab==17?"Xóa tài khoản?":tab==18?"Xóa toàn bộ ảnh?":"Thoát MiniSocial?";
            txt(g,title0,tab==19?DARK:0xC2664D,pad+17,y+12,bold);
            String msg=tab==17?"Không thể khôi phục sau khi xóa":tab==18?"Giữ lại bài chữ và tin nhắn":"Chưa gửi tin nhắn sẽ bị mất";
            txt(g,trim(msg,cw-2*pad-34,small),TEXT,pad+17,y+17+bold.getHeight(),small);
            if(tab==17){txt(g,trim(deleteAccountPassword.length()>0?"Đã nhập mật khẩu":"1: Nhập mật khẩu",cw-2*pad-34,small),MUTED,pad+17,y+25+bold.getHeight()+small.getHeight(),small);}
            txt(g,"Chọn: Đồng ý  |  Trở về: Hủy",DARK,pad+17,y+h-small.getHeight()-9,small);
        }
        private int profileHeaderHeight(){return large?(portrait?148:133):(portrait?115:96);}
        private boolean profileNeedsPaging(){return bodyBottom-(bodyTop+2+profileHeaderHeight()+6)<cardH;}
        private void drawProfile(Graphics g){
            if(profileNeedsPaging()&&profilePostsMode){
                headerHint(g,"5: Thông tin | Bài viết",bodyTop+4);
                drawPosts(g,bodyTop+24,profilePosts);return;
            }
            int y=bodyTop+2,ph=profileHeaderHeight();
            panel(g,pad,y,cw-2*pad,ph,false);
            drawAsset(g,uiTheme()+(portrait?"/cover_portrait.png":"/cover.png"),pad,y+1);
            int avatar=large?(portrait?60:73):45;
            int ax=pad+8, ay=y+(large?23:14);
            if(profileAvatarImage!=null&&avatarMediaId.equals(profileAvatarLoaded))
                g.drawImage(profileAvatarImage,ax,ay,Graphics.TOP|Graphics.LEFT);
            else if(!drawAsset(g,"profile_avatar.png",ax,ay))defaultAvatar(g,ax,ay);
            int tx=pad+avatar+16;
            int nameY=y+(large?53:39);
            txt(g,trim(profileName.length()>0?profileName:"Tài khoản",cw-tx-pad-7,bold),DARK,tx,nameY,bold);
            txt(g,trim("@"+profileHandle,cw-tx-pad-7,small),MUTED,tx,nameY+bold.getHeight()+1,small);
            int bioY=y+(large?99:63);
            wrapped(g,profileBio,TEXT,pad+8,bioY,cw-2*pad-16,small,1);
            headerHint(g,profileNeedsPaging()?"5: Bài viết | 6: Tên | 7: Bio":"6: Tên | 7: Giới thiệu",y+ph-small.getHeight()-6);
            if(profileId.equals(me)&&cw>=340){String change="8: Đổi MK | 9: Hồ sơ";
                txt(g,change,BLUE,cw-pad-8-small.stringWidth(change),y+ph-small.getHeight()-6,small);}
            if(!profileNeedsPaging())drawPosts(g,y+ph+6,profilePosts);
        }
        private int friendListY(){return bodyTop+(large?65:53);}
        private void drawFriends(Graphics g){
            headerHint(g,"6: Kết bạn bằng @handle",bodyTop+5);
            headerHint(g,"7: Xem lời mời kết bạn",bodyTop+7+small.getHeight());
            int y=friendListY(),gap=large?5:3,first=windowStart(friends,y,rowH+gap);
            for(int i=first;i<friends.size();i++){
                int ry=y+(i-first)*(rowH+gap);if(ry+rowH>bodyBottom)break;
                String[]f=(String[])friends.elementAt(i);panel(g,pad,ry,cw-2*pad,rowH,i==selection);
                defaultAvatar(g,pad+8,ry+max(2,(rowH-(large?39:27))/2));
                int start=pad+(large?57:43);
                txt(g,trim(piece(f,1),cw-start-pad-70,bold),TEXT,start,ry+5,bold);
                txt(g,trim("@"+piece(f,2),cw-start-pad-70,small),MUTED,start,ry+7+bold.getHeight(),small);
                String label="Chat >";txt(g,label,BLUE,cw-pad-small.stringWidth(label)-9,ry+max(6,(rowH-small.getHeight())/2),small);
            }
            if(friends.size()==0)emptyIcon(g,"empty_friends",y+5,"Chưa có bạn bè. Nhấn 6 để kết bạn.");
        }
        private int chatListY(){return bodyTop+(large?63:51);}
        private int chatInputHeight(){return compactLandscape?33:large?42:29;}
        /** Returns text lines needed for chat bubbles, capped for a tiny viewport. */
        private int wrappedLines(String s,int maxWidth,Font f,int limit){
            if(s==null||s.length()==0)return 1;
            int pos=0,count=0;
            while(pos<s.length()&&count<limit){
                int end=pos,last=-1;
                while(end<s.length()){
                    char c=s.charAt(end);if(c=='\n')break;
                    if(f.stringWidth(s.substring(pos,end+1))>maxWidth)break;
                    if(c==' ')last=end;
                    end++;
                }
                if(end==pos)end=min(s.length(),pos+1);
                else if(end<s.length()&&s.charAt(end)!='\n'&&last>pos)end=last+1;
                pos=end;
                while(pos<s.length()&&(s.charAt(pos)==' '||s.charAt(pos)=='\n'))pos++;
                count++;
            }
            return max(1,count);
        }
        private void drawChat(Graphics g){
            if(peer.length()==0){
                headerHint(g,"Chọn bạn bè để bắt đầu chat",bodyTop+6);
                int y=bodyTop+small.getHeight()+15,h=large?57:38;
                for(int i=0;i<friends.size();i++){
                    int ry=y+i*(h+3);if(ry+h>bodyBottom)break;
                    String[] fr=(String[])friends.elementAt(i);
                    panel(g,pad+2,ry,cw-2*pad-4,h,i==selection);
                    defaultAvatar(g,pad+9,ry+max(2,(h-(large?39:27))/2));
                    int start=pad+(large?57:42);
                    txt(g,trim(piece(fr,1),cw-start-pad-32,normal),TEXT,start,ry+max(3,(h-normal.getHeight())/2),normal);
                    uiIcon(g,"chats",cw-pad-(large?30:25),ry+max(2,(h-(large?23:16))/2),themeDark);
                }
                if(friends.size()==0)emptyIcon(g,"chat_empty",y+3,"Chưa có cuộc chat. Vào Bạn bè để kết bạn.");
                return;
            }
            int headerTop=bodyTop+2,peerH=large?31:23;
            panel(g,pad,headerTop,cw-2*pad,peerH,false);
            uiIcon(g,"chats",pad+6,headerTop+max(2,(peerH-(large?23:16))/2),themeDark);
            txt(g,trim(peerName,cw-2*pad-74,bold),DARK,pad+(large?40:28),headerTop+max(2,(peerH-bold.getHeight())/2),bold);
            int inputH=chatInputHeight();
            int inputY=bodyBottom-inputH;
            panel(g,pad,inputY,cw-2*pad,inputH-2,false);
            int iconSize=large?23:16;
            uiIcon(g,"attach",pad+6,inputY+max(1,(inputH-iconSize)/2),themeDark);
            txt(g,trim("2: Soạn tin   8: Ảnh",cw-2*pad-(large?100:67),small),MUTED,pad+(large?39:27),inputY+max(3,(inputH-small.getHeight())/2),small);
            uiIcon(g,"send",cw-pad-(large?33:23),inputY+max(1,(inputH-iconSize)/2),themeDark);
            // A dynamic message viewport: bubbles hug their content, stay within
            // the active width, and accumulate bottom-up as messaging apps do.
            int top=headerTop+peerH+4,bottom=inputY-3;
            int gap=compactLandscape?4:large?6:3;
            int maxWidth=max(90,min(cw-2*pad-(large?45:25),portrait?cw-2*pad-34:(cw*3)/4));
            int minWidth=min(maxWidth,large?112:85);
            int cursor=bottom;
            for(int i=messages.size()-1;i>=0;i--){
                String[]m=(String[])messages.elementAt(i);
                boolean mine=piece(m,1).equals(me);
                String value=piece(m,3).length()>0?"[Ảnh] "+piece(m,2):piece(m,2);
                String time=piece(m,4);
                if(time.length()>=16&&time.charAt(10)=='T')time=time.substring(11,16);
                else if(time.length()>5)time=time.substring(max(0,time.length()-5));
                int ideal=normal.stringWidth(value)+24;
                int width=max(minWidth,min(maxWidth,ideal));
                int textWidth=max(1,width-14);
                int availableHeight=max(1,bottom-top);
                int maxLines=max(1,(availableHeight-small.getHeight()-17)/max(1,normal.getHeight()+1));
                int lines=wrappedLines(value,textWidth,normal,min(large?5:4,maxLines));
                int bubbleH=max(compactLandscape?45:large?52:39,
                    10+lines*(normal.getHeight()+1)+small.getHeight()+7);
                cursor-=bubbleH;
                if(cursor<top)break;
                int x=mine?cw-pad-width:pad;
                g.setColor(mine?(themeDark?0x164474:0xD1E8FF):(themeDark?0x24344B:0xFFFFFF));
                g.fillRoundRect(x,cursor,width,bubbleH,large?18:12,large?18:12);
                g.setColor(mine?BLUE:BORDER);
                g.drawRoundRect(x,cursor,width-1,bubbleH-1,large?18:12,large?18:12);
                wrapped(g,value,TEXT,x+7,cursor+4,textWidth,normal,lines);
                txt(g,trim(time,width-28,small),MUTED,x+7,cursor+bubbleH-small.getHeight()-4,small);
                // Do not fabricate delivery/read status: use server field only.
                String readStatus=piece(m,6);
                if(mine&&(readStatus.equals("read")||readStatus.equals("seen")))
                    drawAsset(g,uiTheme()+"/read_double.png",x+width-(large?30:23),cursor+bubbleH-(large?19:15));
                cursor-=gap;
            }
            if(messages.size()==0)headerHint(g,"Chưa có tin nhắn. Nhấn 2 để soạn.",top+8);
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
                txt(g,trim(piece(a,4),cw-2*pad-16,small),MUTED,pad+8,ry+6+bold.getHeight(),small);
                wrapped(g,piece(a,2),TEXT,pad+8,ry+8+bold.getHeight()+small.getHeight(),cw-2*pad-16,normal,large?2:1);
            }
            if(comments.size()==0)headerHint(g,"Chua co binh luan",y+8);
        }
        private int requestsListY(){return bodyTop+(large?45:34);}
        private void drawRequests(Graphics g){
            headerHint(g,"Lời mời - Enter để chấp nhận",bodyTop+4);
            int y=requestsListY(),height=large?66:42,gap=large?7:4;
            int first=windowStart(incoming,y,height+gap);
            for(int i=first;i<incoming.size();i++){
                int ry=y+(i-first)*(height+gap);if(ry+height>bodyBottom)break;
                String[]a=(String[])incoming.elementAt(i);
                panel(g,pad,ry,cw-2*pad,height,i==selection);
                uiIcon(g,"add_friend",pad+8,ry+max(3,(height-(large?23:16))/2),themeDark);
                int start=pad+(large?42:31);
                txt(g,trim(piece(a,1),cw-start-pad-10,bold),DARK,start,ry+5,bold);
                txt(g,trim("@"+piece(a,2),cw-start-pad-10,small),MUTED,start,ry+7+bold.getHeight(),small);
            }
            if(incoming.size()==0)emptyIcon(g,"empty_notifications",y+3,"Hiện chưa có lời mời kết bạn.");
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
            if(optionsOpen){optionsOpen=false;screen.repaint();return;}
            if(tab==19||tab==17||tab==18){tab=tab==17?14:tab==18?16:1;deleteAccountPassword="";screen.repaint();return;}
            if(tab==14||tab==15||tab==16){tab=1;selection=0;navSelection=0;screen.repaint();return;}
            if(tab==1){tab=19;screen.repaint();return;}
            if(tab==13){
                if(onboardingRequired)logout();else {tab=2;screen.repaint();}
                return;
            }
            if(tab==11){recoveryFields=new String[]{"","","",""};recoverySent=false;authError="";notification="";tab=0;screen.repaint();return;}
            if(tab==12){changeFields=new String[]{"","",""};authError="";notification="";tab=14;screen.repaint();return;}
            if(tab==8||tab==9||tab==10){
                if(tab==10){registerFields[1]="";registerFields[2]="";authError="";registerErrorField=-1;notification="";}
                tab=token.length()==0?0:1;screen.repaint();return;
            }
            if(tab==5){tab=4;reload();}
            else if(tab==6){if(historyReturnTab==15){tab=15;historyReturnTab=1;screen.repaint();}else{tab=1;reload();}}
            else if(tab==7){tab=3;reload();}
            else if(tab==4){if(peer.length()>0){peer="";peerName="";navigate(4);}else navigate(1);}
            else navigate(1);
        }
        private int listSize(){
            if(tab==15)return historyItems.size();if(tab==4&&peer.length()==0)return friends.size();
            if(tab==1)return feed.size();if(tab==2)return profilePosts.size();
            if(tab==3)return friends.size();if(tab==6)return comments.size();
            if(tab==7)return incoming.size();return 0;
        }
        private void chooseWelcome(){
            if(welcomeSelection==0)editLogin(true);
            else if(welcomeSelection==1)editLogin(false);
            else if(welcomeSelection==2)submitLogin();
            else if(welcomeSelection==3){serverSelection=serverMode;tab=8;repaint();}
            else if(welcomeSelection==4)auth(true);
            else if(welcomeSelection==5){tab=9;repaint();}
            else openRecovery();
        }
        private void chooseNav(int index){
            if(index==0)navigate(1);
            else if(index==1){peer="";peerName="";navigate(4);}
            else if(index==2)navigate(3);
        }
        private void openSelectedChat(){
            if(friends.size()==0)return;
            String[] fr=(String[])friends.elementAt(min(selection,friends.size()-1));
            peer=piece(fr,0);peerName=piece(fr,1);navigate(4);
        }
        private void confirmAction(){
            if(tab==17)submitDeleteAccount();
            else if(tab==18)clearServerMedia();
            else if(tab==19){MiniSocialMidlet.this.destroyApp(true);MiniSocialMidlet.this.notifyDestroyed();}
        }
        protected void keyPressed(int k){
            if(busy){if(k==-7){back();}return;}
            int action=0;try{action=getGameAction(k);}catch(Exception ignored){}
            if(optionsOpen){
                if(k==-7||k=='0'){optionsOpen=false;repaint();return;}
                if(action==Canvas.UP)optionsSelection=(optionsSelection+3)%4;
                else if(action==Canvas.DOWN)optionsSelection=(optionsSelection+1)%4;
                else if(action==Canvas.FIRE||k==-6){selectOption();return;}
                repaint();return;
            }
            if(token.length()>0){
                if(tab==17&&k=='1'){edit("delete-password","Nhập mật khẩu để xóa",TextField.ANY|TextField.PASSWORD);return;}
                if(tab==17||tab==18||tab==19){
                    if(k==-7||k=='0'){back();return;}
                    if(k==-6||action==Canvas.FIRE){confirmAction();return;}
                    return;
                }
                if(tab==14||tab==15||tab==16){
                    if(k==-7||k=='0'){back();return;}
                    if(tab==14){
                        if(action==Canvas.UP)settingsSelection=(settingsSelection+SETTINGS.length-1)%SETTINGS.length;
                        else if(action==Canvas.DOWN)settingsSelection=(settingsSelection+1)%SETTINGS.length;
                        else if(k==-6||action==Canvas.FIRE)selectSettings();
                    }else if(tab==15){
                        if(action==Canvas.LEFT||action==Canvas.RIGHT||k=='1'||k=='2'){
                            historyMode=(k=='1'?0:k=='2'?1:1-historyMode);selection=0;loadHistory();return;}
                        if(action==Canvas.UP)selection=max(0,selection-1);
                        else if(action==Canvas.DOWN)selection=min(max(0,historyItems.size()-1),selection+1);
                        else if(k==-6||action==Canvas.FIRE){if(historyItems.size()>0){target=piece((String[])historyItems.elementAt(min(selection,historyItems.size()-1)),0);historyReturnTab=15;navigate(6);}}
                    }else {
                        if(action==Canvas.UP||action==Canvas.DOWN)storageSelection=1-storageSelection;
                        else if(k==-6||action==Canvas.FIRE)selectStorage();
                    }
                    repaint();return;
                }
                if(k==-6){if(tab==4&&peer.length()>0)edit("chat","Gửi tin nhắn",TextField.ANY);else openOptions();return;}
            }
            // Tested game-action constants are used instead of raw arrow key codes.
            if(tab==11||tab==12){
                boolean rec=tab==11;
                int current=rec?recoverySelection:changeSelection;
                int count=rec?7:5;
                if(k==-7||k=='0'){back();return;}
                if(action==Canvas.UP||action==Canvas.LEFT){current=(current+count-1)%count;}
                else if(action==Canvas.DOWN||action==Canvas.RIGHT){current=(current+1)%count;}
                else if(action==Canvas.FIRE||k==-6){
                    if(current<(rec?4:3))editPasswordField(rec,current);
                    else if(rec&&current==4)sendRecoveryCode();
                    else if(rec&&current==5)finishRecovery();
                    else if(!rec&&current==3)finishChange();
                    else back();
                    return;
                }else if(current<(rec?4:3)&&((k>=32&&k<=126)||k==8||k==127)){
                    String[] fields=rec?recoveryFields:changeFields;
                    String value=fields[current];
                    if(k==8||k==127){if(value.length()>0)fields[current]=value.substring(0,value.length()-1);}
                    else if(value.length()<(rec&&current==1?6:rec&&current==0?140:128))fields[current]=value+(char)k;
                    authError="";notification="";
                }
                if(rec)recoverySelection=current;else changeSelection=current;
                repaint();return;
            }
            if(tab==9){
                if(k==-7||k==-6||k=='0'||action==Canvas.FIRE){tab=0;repaint();}
                return;
            }
            if(tab==10||tab==13){
                boolean setup=tab==13;
                int current=setup?onboardingSelection:registerSelection;
                if(k==-7){back();return;}
                if(action==Canvas.UP||action==Canvas.LEFT)current=(current+4)%5;
                else if(action==Canvas.DOWN||action==Canvas.RIGHT)current=(current+1)%5;
                else if(action==Canvas.FIRE||k==-6){
                    if(current<3){if(setup)editOnboardingField(current);else editRegisterField(current);}
                    else if(current==3){if(setup)submitOnboarding();else submitRegistration();}
                    else back();
                    return;
                }
                if(setup)onboardingSelection=current;else registerSelection=current;
                // Nokia E72 QWERTY can type directly into the selected Canvas field.
                // OK still offers native TextBox editing (also supports emulator/touch).
                if(current<3){
                    String[] fields=setup?onboardingFields:registerFields;
                    String prev=fields[current];
                    if(k==8||k==127||k==-8){
                        if(prev.length()>0)fields[current]=prev.substring(0,prev.length()-1);
                    }else if(k>=32&&k<=126){
                        int lim=setup?(current==0?50:current==1?64:180):(current==0?24:current==1?140:128);
                        if(prev.length()<lim)fields[current]=prev+(char)k;
                    }
                    authError="";registerErrorField=-1;notification="";repaint();
                }
                return;
            }
            if(tab==8){
                if(k==-7||k=='0'){back();return;}
                if(action==Canvas.UP){serverSelection=(serverSelection+2)%3;repaint();return;}
                if(action==Canvas.DOWN){serverSelection=(serverSelection+1)%3;repaint();return;}
                if(action==Canvas.LEFT){serverSelection=(serverSelection+2)%3;repaint();return;}
                if(action==Canvas.RIGHT){serverSelection=(serverSelection+1)%3;repaint();return;}
                if(k=='1'||k=='2'||k=='3'){serverSelection=k-'1';repaint();return;}
                if(k=='#'||k==-6){serverEditor();return;}
                if(k=='5'){pingServer();return;}
                if(action==Canvas.FIRE){setServer(serverSelection);return;}
                return;
            }
            if(token.length()==0){
                if(action==Canvas.UP){welcomeSelection=(welcomeSelection+6)%7;repaint();return;}
                if(action==Canvas.DOWN){welcomeSelection=(welcomeSelection+1)%7;repaint();return;}
                if(action==Canvas.LEFT){welcomeSelection=welcomeSelection>=3?3+(welcomeSelection-3+2)%3:(welcomeSelection+2)%3;repaint();return;}
                if(action==Canvas.RIGHT){welcomeSelection=welcomeSelection>=3?3+(welcomeSelection-3+1)%3:(welcomeSelection+1)%3;repaint();return;}
                if(k=='1')welcomeSelection=0;
                else if(k=='2')welcomeSelection=1;
                else if(k=='3')welcomeSelection=2;
                else if(k=='4'||k=='#')welcomeSelection=3;
                else if(k=='5')welcomeSelection=4;
                else if(k=='6')welcomeSelection=5;
                else if(k=='7')welcomeSelection=6;
                else if(k==-7){MiniSocialMidlet.this.destroyApp(true);MiniSocialMidlet.this.notifyDestroyed();return;}
                if(action==Canvas.FIRE||k==-6)chooseWelcome();
                repaint();return;
            }
            if(k==-7||k=='0'){back();repaint();return;}
            if(k=='#'){logout();return;}
            if(tab!=5){
                if(action==Canvas.LEFT||action==Canvas.RIGHT){
                    if(!navFocus){navFocus=true;navSelection=tab==4?1:tab==3?2:0;}
                    navSelection=(navSelection+(action==Canvas.RIGHT?1:2))%3;
                    repaint();return;
                }
                if(navFocus&&action==Canvas.FIRE){chooseNav(navSelection);repaint();return;}
                if(action==Canvas.UP||action==Canvas.DOWN)navFocus=false;
                if(tab==2&&k=='9'&&profileId.equals(me)){openOnboarding();return;}
                if(k=='1'){navigate(1);return;}
                if(k=='3'){peer="";navigate(4);return;}
                if(k=='4'){navigate(3);return;}
                if(k=='9'){reload();return;}
            }
            if(action==Canvas.UP){selection=max(0,selection-1);repaint();return;}
            if(action==Canvas.DOWN){selection=min(max(0,listSize()-1),selection+1);repaint();return;}
            if(tab==1){
                if(k=='2')edit("post","Noi dung bai viet",TextField.ANY);
                else if(k=='8')edit("photo-post","file:///E:/Images/anh.jpg",TextField.ANY);
                else if(k=='*')toggleLike();else if(k=='5')showPostImage();else if(action==Canvas.FIRE)openComments();
            }else if(tab==2){
                if(k=='8'&&profileId.equals(me)){openChange();return;}
                if(k=='5'&&profileNeedsPaging()){profilePostsMode=!profilePostsMode;selection=0;repaint();return;}
                if(k=='6'&&profileId.equals(me))edit("profile-name","Ten hien thi",TextField.ANY);
                else if(k=='7'&&profileId.equals(me))edit("profile-bio","Gioi thieu",TextField.ANY);
                else if(k=='*')toggleLike();else if(action==Canvas.FIRE)openComments();
            }else if(tab==3){
                if(k=='6')edit("friend","Them ban theo @handle",TextField.ANY);
                else if(k=='7')navigate(7);else if(action==Canvas.FIRE)selectFriend();
            }else if(tab==4){
                if(peer.length()==0){if(action==Canvas.FIRE)openSelectedChat();}
                else if(k=='2')edit("chat","Nhập tin nhắn",TextField.ANY);
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
            if(optionsOpen){
                if(y>=ch-footerH){if(x<cw/2)selectOption();else{optionsOpen=false;repaint();}return;}
                int oh=large?40:31, oy=max(headerH+2,ch-footerH-4*oh-6);
                if(y>=oy&&y<oy+4*oh&&x<pad+2+min(cw-2*pad,large?260:194)){
                    optionsSelection=(y-oy)/oh;selectOption();return;}
                optionsOpen=false;repaint();return;
            }
            if(token.length()>0&&y>=ch-footerH){
                if(x>=cw/2){back();repaint();return;}
                if(tab==14)selectSettings();else if(tab==15){if(historyItems.size()>0){target=piece((String[])historyItems.elementAt(min(selection,historyItems.size()-1)),0);historyReturnTab=15;navigate(6);}}
                else if(tab==16)selectStorage();else if(tab==17||tab==18||tab==19)confirmAction();
                else if(tab==4&&peer.length()>0)edit("chat","Gửi tin nhắn",TextField.ANY);else openOptions();
                return;
            }
            if(tab==14||tab==15||tab==16||tab==17||tab==18||tab==19){
                if(tab==14){int top=settingsTop(),h=settingsRow(),i=(y-top)/max(1,h);int count=max(1,(bodyBottom-top)/h);int start=max(0,min(settingsSelection-count+1,SETTINGS.length-count));i+=start;if(y>=top&&i>=0&&i<SETTINGS.length){settingsSelection=i;selectSettings();}}
                else if(tab==15){int top=settingsTop(),bh=large?32:23;if(y>=top&&y<top+bh){historyMode=x<cw/2?0:1;selection=0;loadHistory();}
                    else if(y>=historyListTop()){int i=historyWindowStart()+(y-historyListTop())/historyRow();if(i>=0&&i<historyItems.size()){selection=i;target=piece((String[])historyItems.elementAt(i),0);historyReturnTab=15;navigate(6);}}}
                else if(tab==16){int by=storageBtnY(),bh=storageBtnH();int i=(y-by)/(bh+5);if(y>=by&&i>=0&&i<2){storageSelection=i;selectStorage();}}
                else if(tab==17&&y<bodyBottom)edit("delete-password","Nhập mật khẩu để xóa",TextField.ANY|TextField.PASSWORD);
                return;
            }
            if(y>=0&&y<headerH){
                if(tab==8||tab==9||tab==11||tab==12)back();
                else if(token.length()>0){tab=8;serverSelection=serverMode;}
                repaint();return;
            }
            if(tab==8){
                int first=serverFirstY(),stride=serverRowH()+serverGap();
                int idx=(y-first)/max(1,stride);
                if(y>=first&&idx>=0&&idx<3){
                    serverSelection=idx;
                    if(x>cw*3/4&&cw>210)serverEditor();
                    else setServer(idx);
                }
                else if(y>=ch-footerH){back();}
                return;
            }
            if(tab==9){if(y>=ch-footerH)back();else back();repaint();return;}
            if(tab==11||tab==12){
                boolean rec=tab==11;
                SecurityLayout q=securityLayout(rec);
                int count=rec?4:3;
                if(x>=q.inner&&x<q.inner+q.width){
                    for(int i=q.first;i<q.first+q.visible;i++){
                        int ry=q.rowY+(i-q.first)*q.rowH;
                        if(y>=ry&&y<ry+q.rowH){if(rec)recoverySelection=i;else changeSelection=i;editPasswordField(rec,i);return;}
                    }
                }
                if(rec&&y>=q.btn1Y&&y<q.btn1Y+q.buttonH){recoverySelection=4;sendRecoveryCode();return;}
                if(y>=q.btn2Y&&y<q.btn2Y+q.buttonH){if(rec){recoverySelection=5;finishRecovery();}else{changeSelection=3;finishChange();}return;}
                if(y>=q.backY-3||y<q.y){back();return;}
                repaint();return;
            }
            if(tab==10||tab==13){
                boolean setup=tab==13;
                RegisterLayout r=registerLayout();
                if(x>=r.inner&&x<r.inner+r.width){
                    int errGap=authError.length()>0&&registerErrorField>=0?small.getHeight()+4:0;
                    for(int i=r.first;i<r.first+r.visible;i++){
                        int iy=r.rowY+(i-r.first)*r.rowH+(i>registerErrorField?errGap:0);
                        if(y>=iy&&y<iy+r.rowH){if(setup){onboardingSelection=i;editOnboardingField(i);}else{registerSelection=i;editRegisterField(i);}return;}
                    }
                }
                if(y>=r.submitY&&y<r.submitY+r.buttonH){if(setup){onboardingSelection=3;submitOnboarding();}else{registerSelection=3;submitRegistration();}return;}
                if(y>=r.linkY-2&&y<r.y+r.h+4){if(setup)onboardingSelection=4;else registerSelection=4;back();return;}
                if(y>=ch-footerH){back();return;}
                repaint();return;
            }
            if(token.length()==0){
                LoginLayout q=loginLayout();int inner=q.x+max(9,q.w/19),right=inner+q.w-2*max(9,q.w/19);
                if(x>=inner&&x<=right){
                    if(y>=q.y+q.field1Y&&y<q.y+q.field1Y+q.fieldH){welcomeSelection=0;chooseWelcome();return;}
                    if(y>=q.y+q.field2Y&&y<q.y+q.field2Y+q.fieldH){welcomeSelection=1;chooseWelcome();return;}
                    if(y>=q.y+q.buttonY&&y<q.y+q.buttonY+q.buttonH){welcomeSelection=2;chooseWelcome();return;}
                }
                String lost="Quên mật khẩu?";
                int lostX=inner+(right-inner)-small.stringWidth(lost)-2;
                if(x>=lostX&&x<=right&&y>=q.y+q.label2Y-2&&y<q.y+q.label2Y+small.getHeight()+5){
                    welcomeSelection=6;openRecovery();return;
                }
                if(y>=q.y+q.linksY-3&&y<=q.y+q.linksY+small.getHeight()+8){
                    for(int i=0;i<3;i++)if(x>=q.linkX[i]&&x<q.linkX[i]+q.linkW[i]){
                        welcomeSelection=i+3;chooseWelcome();return;
                    }
                }
                if(y>=ch-footerH){if(x<cw/2)chooseWelcome();else{MiniSocialMidlet.this.destroyApp(true);MiniSocialMidlet.this.notifyDestroyed();return;}repaint();}
                return;
            }
            if(y>=ch-footerH){if(x>=cw/2)back();repaint();return;}
            if(tab!=5&&y>=headerH&&y<headerH+navH){
                int i=min(2,3*x/max(1,cw));chooseNav(i);
                repaint();return;
            }
            if(tab==1){
                int firstY=bodyTop+1+(large?43:33)+5;
                if(y<firstY){edit("post","Noi dung bai viet",TextField.ANY);return;}
                int gap=large?8:4,first=windowStart(feed,firstY,cardH+gap);
                int i=first+(y-firstY)/max(1,cardH+gap);
                if(i>=0&&i<feed.size()){selection=i;if(y>=firstY+(i-first)*(cardH+gap)+cardH-small.getHeight()-8)toggleLike();else openComments();}
            }else if(tab==2){
                int py=bodyTop+2;
                if(profileId.equals(me)&&y>=py+profileHeaderHeight()-small.getHeight()-12&&y<py+profileHeaderHeight()+2){openChange();return;}
                if(profileNeedsPaging()&&!profilePostsMode){profilePostsMode=true;repaint();return;}
                int firstY=profileNeedsPaging()?bodyTop+24:bodyTop+2+profileHeaderHeight()+6;
                if(y>=firstY){int gap=large?8:4,first=windowStart(profilePosts,firstY,cardH+gap);int i=first+(y-firstY)/(cardH+gap);if(i>=0&&i<profilePosts.size()){selection=i;openComments();}}
            }else if(tab==3){
                int firstY=friendListY(),first=windowStart(friends,firstY,rowH+(large?5:3));
                if(y<firstY){if(y>bodyTop+small.getHeight()+6)navigate(7);else edit("friend","Them ban theo @handle",TextField.ANY);return;}
                int i=first+(y-firstY)/(rowH+(large?5:3));if(i>=0&&i<friends.size()){selection=i;selectFriend();}
            }else if(tab==4){if(peer.length()==0){int start=bodyTop+small.getHeight()+15;int h=large?57:38;int i=(y-start)/(h+3);if(y>=start&&i>=0&&i<friends.size()){selection=i;openSelectedChat();}}
                else if(y>=bodyBottom-chatInputHeight()){
                    if(x<pad+(large?39:27))edit("photo-chat","file:///E:/Images/anh.jpg",TextField.ANY);
                    else edit("chat","Nhập tin nhắn",TextField.ANY);
                }else if(y<chatListY())edit("chat","Nhập tin nhắn",TextField.ANY);}
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
