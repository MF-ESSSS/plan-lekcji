package com.pawel.planlekcji;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.widget.FrameLayout;
import android.graphics.drawable.ColorDrawable;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class MainActivity extends Activity {
 private WebView view;
 private UpdateStore updates;
 private InstallCounter counter;
 private volatile File displayedBundle;
 private volatile boolean destroyed;
 private final AtomicBoolean checking = new AtomicBoolean(false);
 private long lastCheck;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); view = new WebView(this); view.setBackgroundColor(0xff292735);
  view.getSettings().setJavaScriptEnabled(true);
  view.getSettings().setAllowFileAccess(false); view.getSettings().setAllowContentAccess(false);
  view.getSettings().setAllowFileAccessFromFileURLs(false); view.getSettings().setAllowUniversalAccessFromFileURLs(false);
  view.getSettings().setBlockNetworkLoads(false);
  counter=new InstallCounter(getSharedPreferences("install-counter",MODE_PRIVATE));
  counter.registerAsync();
  try { ByteArrayOutputStream bytes=new ByteArrayOutputStream(); InputStream input=getAssets().open("update-public.der");try { byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n); } finally {input.close();} updates=new UpdateStore(getFilesDir(),bytes.toByteArray(),15); displayedBundle=updates.currentDirectory(); } catch(Exception ignored) {}
  view.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView web,String url){return !url.equals("https://appassets.androidplatform.net/index.html");}
   @Override public WebResourceResponse shouldInterceptRequest(WebView web,String url){
    String prefix="https://appassets.androidplatform.net/";String name=url.startsWith(prefix)?url.substring(prefix.length()):"";
    if(name.equals("counter/status"))return new WebResourceResponse("application/json","UTF-8",new ByteArrayInputStream(counter.stats().getBytes(java.nio.charset.Charset.forName("UTF-8"))));
    if(!name.equals("index.html")&&!name.equals("app.js")&&!name.equals("style.css"))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
    String mime=name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css":"text/html";
    try {File bundle=displayedBundle;InputStream input=bundle==null?getAssets().open(name):new FileInputStream(new File(bundle,name));return new WebResourceResponse(mime,"UTF-8",input);}catch(IOException ignored){return new WebResourceResponse(mime,"UTF-8",new ByteArrayInputStream(new byte[0]));}
   }
  });
  getWindow().setBackgroundDrawable(new ColorDrawable(0xff292735));
  try { getWindow().getClass().getMethod("setStatusBarColor",int.class).invoke(getWindow(),0xff292735); } catch(Exception ignored) {}
  try { getWindow().getClass().getMethod("setNavigationBarColor",int.class).invoke(getWindow(),0xff292735); } catch(Exception ignored) {}
  FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xff292735); root.setFitsSystemWindows(true);
  root.addView(view,new FrameLayout.LayoutParams(-1,-1));
  setContentView(root); view.loadUrl("https://appassets.androidplatform.net/index.html");
  checkUpdates();
  view.postDelayed(new Runnable(){public void run(){if(destroyed)return;counter.registerAsync();view.postDelayed(this,60000);}},60000);
 }
 @Override protected void onPause(){super.onPause();view.onPause();view.pauseTimers();}
 private void checkUpdates(){
  if(updates==null||destroyed||System.currentTimeMillis()-lastCheck<60000||!checking.compareAndSet(false,true))return;
  lastCheck=System.currentTimeMillis();
  Thread worker=new Thread(new Runnable(){public void run(){try{if(updates.check())runOnUiThread(new Runnable(){public void run(){if(!destroyed){displayedBundle=updates.currentDirectory();view.reload();}}});}catch(Exception ignored){/* Offline: keep the last working version. */}finally{checking.set(false);}}},"schedule-updater");worker.setDaemon(true);worker.start();
 }
 @Override protected void onResume(){super.onResume();if(view!=null){view.onResume();view.resumeTimers();view.loadUrl("javascript:if(typeof tick==='function')tick()");checkUpdates();if(counter!=null)counter.registerAsync();}}
 @Override protected void onDestroy(){destroyed=true;view.destroy();super.onDestroy();}
}
