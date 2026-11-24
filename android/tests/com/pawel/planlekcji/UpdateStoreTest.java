package com.pawel.planlekcji;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.zip.*;
public final class UpdateStoreTest {
 private static void expect(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static byte[] zip(String... names)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){for(String name:names){out.putNextEntry(new ZipEntry(name));out.write("test".getBytes("UTF-8"));out.closeEntry();}}return bytes.toByteArray();}
 private static void rejectArchive(byte[] bytes)throws Exception{File folder=Files.createTempDirectory("plan-reject-").toFile();try{UpdateStore.extract(bytes,folder);throw new AssertionError("Unsafe archive accepted");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  byte[] publicKey=Files.readAllBytes(Paths.get(args[0]));File data=Files.createTempDirectory("plan-update-test-").toFile();
  UpdateStore first=new UpdateStore(data,publicKey);expect(first.currentDirectory()==null,"Fresh app must use bundled assets");
  expect(first.check(),"First signed GitHub update must install");File active=first.currentDirectory();expect(active!=null,"Update activated");
  for(String file:new String[]{"index.html","style.css","app.js"})expect(new File(active,file).length()>0,"Missing update file "+file);
  expect(!first.check(),"Unchanged version must not reload");
  UpdateStore restarted=new UpdateStore(data,publicKey);expect(active.equals(restarted.currentDirectory()),"Offline restart must restore cached update");
  KeyPair wrongKey=KeyPairGenerator.getInstance("RSA").generateKeyPair();UpdateStore invalid=new UpdateStore(Files.createTempDirectory("plan-wrong-key-").toFile(),wrongKey.getPublic().getEncoded());
  try{invalid.check();throw new AssertionError("Untrusted signature accepted");}catch(SecurityException expected){}
  rejectArchive(zip("../escape","style.css","app.js"));rejectArchive(zip("index.html","app.js"));rejectArchive(zip("index.html","style.css","app.js","extra.js"));
  expect(UpdateStore.hash("abc".getBytes("UTF-8")).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA-256 mismatch");
  Files.write(new File(data,"web-updates/active.properties").toPath(),"version=1\nsha256=invalid\nsignature=invalid\n".getBytes("UTF-8"));
  expect(new UpdateStore(data,publicKey).currentDirectory()==null,"Damaged metadata must fall back to bundled assets");
  System.out.println("PASS: live GitHub download, trusted signature, all assets, unchanged version, offline restart, untrusted signature, traversal/incomplete/extra-file rejection, SHA-256 and damaged-cache fallback.");
 }
}
