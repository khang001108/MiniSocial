import java.lang.reflect.*;
import java.util.*;

/** Pure backend preview-index test without internet/Supabase or credentials. */
public final class ConversationIndexSmoke {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Map<String,Object> row(String... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return m;}
 public static void main(String[]args)throws Exception{
   List<Object> people=Arrays.asList(row("friend_id","id-a","display_name","Alex","handle","alex"),row("friend_id","id-b","display_name","Maria","handle","maria"));
   List<Object> msgs=Arrays.asList(
    row("sender_id","id-b","recipient_id","me","body","Xin chào!","media_id","","created_at","2026-11-21T10:00:00"),
    row("sender_id","me","recipient_id","id-a","body","","media_id","photo-uuid","created_at","2026-11-21T09:00:00"),
    row("sender_id","stranger","recipient_id","me","body","PRIVATE","media_id","","created_at","2026-11-21T11:00:00"));
   Class<?> server=Class.forName("minisocial.Server");
   Method m=server.getDeclaredMethod("conversationRows",String.class,List.class,List.class);m.setAccessible(true);
   List<?> result=(List<?>)m.invoke(null,"me",people,msgs);
   check(result.size()==2,"No messages from strangers allowed");
   check(result.get(0).toString().indexOf("Maria")>=0,"Recent conversation first");
   check(result.get(1).toString().indexOf("%5B")>=0,"Photo indicator encoded correctly");
   for(Object line:result)check(line.toString().indexOf("PRIVATE")<0,"Information disclosure");
   System.out.println("PASS backend index: sort by recency, image preview, friend access control");
 }
}
