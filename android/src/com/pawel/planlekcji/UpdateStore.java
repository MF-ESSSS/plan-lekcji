package com.pawel.planlekcji;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.zip.*;

/** Signed, atomic updates of the three web assets; no APK or native code is downloaded. */
public final class UpdateStore {
    private static final String ORIGIN = "https://raw.githubusercontent.com/MF-ESSSS/plan-lekcji/main/updates/";
    private final File root;
    private final PublicKey key;
    private volatile File active;
    private long version;
    public UpdateStore(File directory, byte[] publicKey) throws Exception {
        root = new File(directory,"web-updates");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create update directory");
        key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey));
        try {
            Properties stored = read(new FileInputStream(new File(root,"active.properties")));
            verify(stored);
            File candidate = new File(root,stored.getProperty("sha256"));
            if (complete(candidate)) { active=candidate; version=Long.parseLong(stored.getProperty("version")); }
        } catch (Exception ignored) { /* Bundled assets remain available. */ }
    }
    public File currentDirectory() { return active; }
    private static Properties read(InputStream input) throws IOException {
        try { Properties result = new Properties(); result.load(input); return result; } finally { input.close(); }
    }
    private void verify(Properties props) throws Exception {
        String v=props.getProperty("version",""), hash=props.getProperty("sha256","");
        if (!v.matches("[1-9][0-9]{0,12}") || !hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid update metadata");
        Signature verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((v+"\n"+hash).getBytes("UTF-8"));
        if (!verifier.verify(decode(props.getProperty("signature","")))) throw new SecurityException("Invalid update signature");
    }
    // java.util.Base64 is not available on Android 6; use a strict portable decoder.
    private static byte[] decode(String input) throws IOException {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        ByteArrayOutputStream output=new ByteArrayOutputStream(); int bits=0,value=0;
        for (int i=0;i<input.length();i++) { char c=input.charAt(i); if(c=='=')break;
            int n=alphabet.indexOf(c);if(n<0)throw new IOException("Invalid base64");
            value=(value<<6)|n;bits+=6;if(bits>=8){bits-=8;output.write((value>>bits)&255);}
        } return output.toByteArray();
    }
    private static byte[] download(String suffix,int limit) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(ORIGIN+suffix).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control","no-cache");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Update server unavailable");
            if(connection.getContentLength()>limit)throw new IOException("Update too large");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n,total=0;
                while((n=input.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("Update too large");output.write(buffer,0,n);}
                return output.toByteArray();
            }
        } finally {connection.disconnect();}
    }
    static String hash(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder text=new StringBuilder();
        for(byte b:digest)text.append(String.format(Locale.US,"%02x",b&255));return text.toString();
    }
    private static boolean complete(File directory) {
        return new File(directory,"index.html").isFile()&&new File(directory,"style.css").isFile()&&new File(directory,"app.js").isFile();
    }
    static void extract(byte[] bytes,File target) throws Exception {
        Set<String> remaining=new HashSet<String>(Arrays.asList("index.html","style.css","app.js"));
        int total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()||!remaining.remove(entry.getName()))throw new IOException("Unexpected update file");
                try(FileOutputStream out=new FileOutputStream(new File(target,entry.getName()))){
                    int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>4*1024*1024)throw new IOException("Expanded update too large");out.write(buffer,0,n);}
                    out.getFD().sync();
                }
            }
        }
        if(!remaining.isEmpty())throw new IOException("Incomplete update");
    }
    private static void clearStaging(File folder) {
        File[] files=folder.listFiles();if(files!=null)for(File file:files)file.delete();folder.delete();
    }
    public synchronized boolean check() throws Exception {
        Properties manifest=read(new ByteArrayInputStream(download("latest.properties?t="+System.currentTimeMillis(),16384)));
        verify(manifest);long remoteVersion=Long.parseLong(manifest.getProperty("version"));
        if(remoteVersion<=version)return false;
        String sha=manifest.getProperty("sha256");byte[] archive=download(sha+".zip",2*1024*1024);
        if(!hash(archive).equals(sha))throw new SecurityException("Update checksum mismatch");
        File staging=new File(root,"staging-"+System.nanoTime());if(!staging.mkdir())throw new IOException("Cannot stage update");
        try {
            extract(archive,staging);File candidate=new File(root,sha);
            if(!candidate.exists()){if(!staging.renameTo(candidate))throw new IOException("Cannot store update");}
            if(!complete(candidate))throw new IOException("Incomplete stored update");
            File marker=new File(root,"active.next");
            try(FileOutputStream output=new FileOutputStream(marker)){manifest.store(output,null);output.getFD().sync();}
            if(!marker.renameTo(new File(root,"active.properties")))throw new IOException("Cannot activate update");
            active=candidate;version=remoteVersion;return true;
        } finally { clearStaging(staging); }
    }
}
