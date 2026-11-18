package minisocial;

import java.util.*;

/** Minimal JSON reader/writer for JDK 17; no third-party dependencies. */
public final class Json {
    private final String s; private int p;
    private Json(String src) { s=src; }
    public static Object parse(String s) { Json j=new Json(s); Object o=j.value(); j.ws(); if(j.p!=s.length()) throw new IllegalArgumentException("Trailing JSON"); return o; }
    private void ws(){while(p<s.length() && Character.isWhitespace(s.charAt(p))) p++;}
    private char take(){if(p>=s.length()) throw new IllegalArgumentException("Unexpected end of JSON"); return s.charAt(p++);}
    private Object value(){ws(); char c=take(); switch(c){
      case '{': {Map<String,Object> m=new LinkedHashMap<>();ws();if(p<s.length()&&s.charAt(p)=='}'){p++;return m;} do {ws();if(take()!='"')throw new IllegalArgumentException("Expected key");String k=str();ws();if(take()!=':')throw new IllegalArgumentException("Expected colon");m.put(k,value());ws();c=take();}while(c==',');if(c!='}')throw new IllegalArgumentException("Expected }");return m;}
      case '[': {List<Object>a=new ArrayList<>();ws();if(p<s.length()&&s.charAt(p)==']'){p++;return a;}do{a.add(value());ws();c=take();}while(c==',');if(c!=']')throw new IllegalArgumentException("Expected ]");return a;}
      case '"': return str();
      case 't':literal("rue");return Boolean.TRUE;
      case 'f':literal("alse");return Boolean.FALSE;
      case 'n':literal("ull");return null;
      default: {int start=p-1;while(p<s.length() && "0123456789+-.eE".indexOf(s.charAt(p))>=0)p++;return Double.valueOf(s.substring(start,p));}
    }}
    private void literal(String remainder){for(int i=0;i<remainder.length();i++) if(take()!=remainder.charAt(i))throw new IllegalArgumentException("Bad JSON literal");}
    private String str(){StringBuilder b=new StringBuilder();while(true){char c=take();if(c=='"')return b.toString();if(c=='\\'){c=take();switch(c){case '"':case '\\':case '/':b.append(c);break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'u':{if(p+4>s.length())throw new IllegalArgumentException("Bad unicode");b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;break;}default:throw new IllegalArgumentException("Bad escape");}}else b.append(c);}}
    public static String stringify(Object o){if(o==null)return "null";if(o instanceof String)return "\""+escape((String)o)+"\"";if(o instanceof Number||o instanceof Boolean)return o.toString();if(o instanceof Map){StringBuilder b=new StringBuilder("{");for(Object key:((Map<?,?>)o).keySet()){if(b.length()>1)b.append(',');b.append(stringify(String.valueOf(key))).append(':').append(stringify(((Map<?,?>)o).get(key)));}return b.append('}').toString();}if(o instanceof Iterable){StringBuilder b=new StringBuilder("[");for(Object v:(Iterable<?>)o){if(b.length()>1)b.append(',');b.append(stringify(v));}return b.append(']').toString();}return stringify(String.valueOf(o));}
    private static String escape(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')b.append('\\').append(c);else if(c=='\n')b.append("\\n");else if(c=='\r')b.append("\\r");else if(c=='\t')b.append("\\t");else if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}return b.toString();}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object o){return o instanceof Map?(Map<String,Object>)o:Collections.emptyMap();}
    @SuppressWarnings("unchecked") public static List<Object> list(Object o){return o instanceof List?(List<Object>)o:Collections.emptyList();}
    public static String string(Object o){return o==null?"":String.valueOf(o);}
}
