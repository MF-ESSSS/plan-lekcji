package com.pawel.planlekcji;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records one anonymous random ID per installation, never profile or timetable data. */
public final class InstallCounter {
 private static final String ORIGIN="https://plan-lekcji-counter.ssua.workers.dev";
 private final SharedPreferences preferences;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 public InstallCounter(SharedPreferences store){preferences=store;}
 private static String request(String path,String body)throws IOException{
  HttpURLConnection connection=(HttpURLConnection)new URL(ORIGIN+path).openConnection();
  connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
  try{
   if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");byte[] bytes=body.getBytes("UTF-8");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
   if(connection.getResponseCode()!=200)throw new IOException("Counter unavailable");
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>4096)throw new IOException("Counter response too large");out.write(buffer,0,n);}return new String(out.toByteArray(),"UTF-8");}
  }finally{connection.disconnect();}
 }
 public void registerAsync(){
  if(preferences.getBoolean("registered",false)||!busy.compareAndSet(false,true))return;
  Thread worker=new Thread(new Runnable(){public void run(){try{
   String id=preferences.getString("installation-id",null);
   if(id==null){id=UUID.randomUUID().toString();if(!preferences.edit().putString("installation-id",id).commit())return;}
   String response=request("/register","{\"id\":\""+id+"\"}");
   if(response.matches("(?s).*\"ok\"\\s*:\\s*true.*"))preferences.edit().putBoolean("registered",true).commit();
  }catch(Exception ignored){/* Retry after connectivity returns. */}finally{busy.set(false);}}},"install-counter");worker.setDaemon(true);worker.start();
 }
 public String stats(){try{return request("/stats",null);}catch(Exception ignored){return "{\"error\":\"offline\"}";}}
}
