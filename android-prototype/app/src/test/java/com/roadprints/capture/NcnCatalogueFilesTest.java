package com.roadprints.capture;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NcnCatalogueFilesTest {
 private Context app(){return RuntimeEnvironment.getApplication();}
 private JSONObject cached(String value,long revision)throws Exception{
  File dir=NcnCatalogueFiles.directory(app()),temporary=new File(dir,"fixture");byte[] data=new byte[4096];java.util.Arrays.fill(data,(byte)value.charAt(0));Files.write(temporary.toPath(),data);
  String hash=NcnCatalogueFiles.digest(temporary);File database=new File(dir,hash+".db");Files.move(temporary.toPath(),database.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  return new JSONObject().put("schema_version",2).put("scope","national-verified-open-checkpoint").put("whole_published_route_ready",false).put("supported_identities",1).put("revision",revision).put("sha256",hash).put("database_bytes",4096).put("compressed_sha256","a".repeat(64)).put("compressed_bytes",100).put("filename","collections/published/ncn/"+hash+"/catalogue.db.gz");
 }
 @Test public void offlineOpenUsesVerifiedCacheWithoutFetchingSeed()throws Exception{
  JSONObject m=cached("a",1);NcnCatalogueFiles.write(new File(NcnCatalogueFiles.directory(app()),"active.json"),m);
  assertEquals(m.getString("sha256"),NcnCatalogueFiles.open(app()).manifest.getString("sha256"));
 }
 @Test public void corruptSameLengthActiveFallsBackToPreviousOffline()throws Exception{
  JSONObject previous=cached("a",1),active=cached("b",2);File dir=NcnCatalogueFiles.directory(app());NcnCatalogueFiles.write(new File(dir,"previous.json"),previous);NcnCatalogueFiles.write(new File(dir,"active.json"),active);
  File file=new File(dir,active.getString("sha256")+".db");Files.write(file.toPath(),new byte[4096]);
  assertEquals(previous.getString("sha256"),NcnCatalogueFiles.cached(app()).manifest.getString("sha256"));
 }
 @Test public void brokenPointerRetainsRollbackAndUncommittedFilesAreIgnored()throws Exception{
  JSONObject previous=cached("a",1);File dir=NcnCatalogueFiles.directory(app());NcnCatalogueFiles.write(new File(dir,"previous.json"),previous);Files.write(new File(dir,"active.json").toPath(),"incomplete".getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(new File(dir,"unverified.tmp").toPath(),new byte[4096]);
  assertEquals(1,NcnCatalogueFiles.cached(app()).manifest.getInt("revision"));
 }
 @Test public void remoteManifestCannotChangeOriginOrLiftLimits()throws Exception{
  JSONObject m=cached("a",1);m.put("filename","https://other.invalid/db.gz");try{NcnCatalogueFiles.validate(m);fail();}catch(IllegalArgumentException expected){}
  m=cached("a",1);m.put("compressed_bytes",300L*1024*1024);try{NcnCatalogueFiles.validate(m);fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void failedColdInstallIsDeferredWithoutRemovingAnyOfflineGeneration()throws Exception{
  Context context=new android.content.ContextWrapper(app()) {
   @Override public File getFilesDir(){File root=new File(super.getFilesDir(),"cold-retry");root.mkdirs();return root;}
  };
  android.content.SharedPreferences prefs=context.getSharedPreferences("ncn_reference_updates",0);
  prefs.edit().clear().commit();
  System.setProperty("roadprints.referenceNetworkDisabled","true");
  try{NcnCatalogueFiles.open(context);fail("No local fixture means no install");}catch(IOException expected){}
  assertTrue(prefs.getLong("install_retry_after",0)>System.currentTimeMillis());
  try{NcnCatalogueFiles.open(context);fail("Retry must be deferred");}
  catch(IOException expected){assertTrue(expected.getMessage().contains("retry deferred"));}
 }
 @Test public void cancelledColdOpenDoesNotCreateAnInstallRetryDeadline()throws Exception{
  Context context=new android.content.ContextWrapper(app()) {
   @Override public File getFilesDir(){File root=new File(super.getFilesDir(),"cancelled-retry");root.mkdirs();return root;}
  };
  android.content.SharedPreferences prefs=context.getSharedPreferences("ncn_reference_updates",0);
  prefs.edit().clear().commit();Thread.currentThread().interrupt();
  try{NcnCatalogueFiles.open(context);fail();}catch(InterruptedIOException expected){}
  finally{Thread.interrupted();}
  assertEquals(0,prefs.getLong("install_retry_after",0));
 }
}
