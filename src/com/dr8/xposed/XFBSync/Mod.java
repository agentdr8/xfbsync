package com.dr8.xposed.XFBSync;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.ContactsContract;

@SuppressLint("SdCardPath")
public class Mod implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
	private static String targetpkg = "com.facebook.katana";
	private static String contactspkg = "com.android.providers.contacts";
	private static final String TAG = "XFBS";
	private static boolean DEBUG = true;
	private static String fbid = "";
	private static String bigurl = "";

	private static void log(String msg) {
		Calendar c = Calendar.getInstance();
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String formattedDate = df.format(c.getTime());
		XposedBridge.log("[" + formattedDate + "] " + TAG + ": " + msg);
	}

	public static void touch(File file) throws IOException {
		if(!file.exists()) {
			File parent = file.getParentFile();
			if(parent != null) 
				if(!parent.exists())
					if(!parent.mkdirs())
						throw new IOException("Cannot create parent directories for file: " + file);

			file.createNewFile();
		}

		boolean success = file.setLastModified(System.currentTimeMillis());
		if (!success)
			throw new IOException("Unable to set the last modification time for " + file);
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam)
			throws Throwable {
		if (resparam.packageName.equals(contactspkg)) {
			if (DEBUG) log("init contacts providers package");

			File touched = new File("/data/data/com.android.providers.contacts/files/dbmodded");

			if (!touched.exists()) {
				SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/data/com.android.providers.contacts/databases/contacts2.db", null, 0);
				if (db.isOpen() && !db.isDbLockedByCurrentThread()) {
					if (DEBUG) log("db is open and not locked, querying table");
					Cursor cursor = db.rawQuery("SELECT * FROM raw_contacts LIMIT 0,1", null);
					String col = "is_restricted";
					if (cursor.getColumnIndex(col) != -1) {
						if (DEBUG) log("is_restricted column already present in database: index " + cursor.getColumnIndex(col));
						cursor.close();
						db.close();
						if (DEBUG) log("touching dbmodded file");
						Mod.touch(touched);
						return;
					} else {
						if (DEBUG) log("Adding is_restricted column to database");
						db.beginTransaction();
						try {
							String sql = "ALTER TABLE raw_contacts ADD is_restricted INTEGER NOT NULL DEFAULT 0";
							SQLiteStatement stmt = db.compileStatement(sql);
							stmt.execute();
							db.setTransactionSuccessful();
						} finally {
							db.endTransaction();
						}
						db.close();
						if (DEBUG) log("touching dbmodded file");
						Mod.touch(touched);
						return;
					}
				} else {
					if (DEBUG) log("db not open, error");
					return;
				}

			} else {
				if (DEBUG) log("db has been modded, no need to check");
			}
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {


		if (lpparam.packageName.equals(targetpkg)) { 
			if (DEBUG) log("in facebook platformstorage class");
			findAndHookMethod("com.facebook.contactsync.PlatformStorage", lpparam.classLoader, "a", Context.class, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam mparam) throws Throwable {
					if (DEBUG) log("found our a method, setting bool to true");
					mparam.setResult(true);
				}
			});
			findAndHookMethod("com.facebook.contactsync.ProfileImageSyncHelper", lpparam.classLoader, "a", Long.class, Map.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam mparam) throws Throwable {
					if (DEBUG) log("reading profile map...");
					Context ctx = (Context) getObjectField(mparam.thisObject, "b");
					@SuppressWarnings("unchecked")
					Map<String, String> localmap = (Map<String, String>) mparam.args[1];
					String hash = localmap.get("sync_hash");
					String[] projection = new String[1];
					String[] args = new String[1];
					args[0] = hash;
					projection[0] = ContactsContract.RawContacts.SOURCE_ID;
					String oldurl = localmap.get("profile_pic_url");
					Cursor cursor = ctx.getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, projection, "sync1=?", args, null);
					if (cursor.getCount() == 1) {
						cursor.moveToFirst();
						fbid = cursor.getString(cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID));
						cursor.close();
					} else {
						if (DEBUG) log("no sync1 hash match");
					}

					SQLiteDatabase fbdb = SQLiteDatabase.openDatabase("/data/data/com.facebook.katana/databases/contacts_db2", null, 0);
					if (fbdb.isOpen() && !fbdb.isDbLockedByCurrentThread()) {
						if (DEBUG) log("fbdb is open and not locked, querying table");
						Cursor cursor2 = fbdb.rawQuery("SELECT big_picture_url FROM contacts WHERE fbid = '" + fbid + "'", null);
						if (cursor2.getCount() == 1) {
							cursor2.moveToFirst();
							bigurl = cursor2.getString(cursor2.getColumnIndex("big_picture_url"));
							cursor2.close();
							fbdb.close();
							if (DEBUG) log("replacing old url: " + oldurl + " with new url: " + bigurl);
							localmap.put("profile_pic_url", bigurl);
						} else {
							if (DEBUG) log("no fbid match");
						}
					}
					
					//					for (Entry<String, String> entry : localmap.entrySet()) {
					//					    String key = entry.getKey();
					//					    String value = entry.getValue();
					//						if (DEBUG) log("our key is: " + key + " and value is: " + value);
					//					}
				}
			});
		} else {
			return;
		}
	}
}