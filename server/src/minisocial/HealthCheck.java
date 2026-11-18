package minisocial;

import java.net.HttpURLConnection;
import java.net.URL;

public final class HealthCheck {
  public static void main(String[] args) {
    try {
      HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+System.getenv().getOrDefault("PORT","8080")+"/health").openConnection();
      c.setConnectTimeout(2000);c.setReadTimeout(2000);
      if(c.getResponseCode()!=200)System.exit(1);
      c.disconnect();
    }catch(Exception e){System.exit(1);}
  }
}
