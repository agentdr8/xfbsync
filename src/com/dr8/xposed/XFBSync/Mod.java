package com.dr8.xposed.XFBSync;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

@SuppressLint("SdCardPath")
public class Mod implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
	private static String targetpkg = "com.facebook.katana";
	private static String contactspkg = "com.android.providers.contacts";
	private static final String TAG = "XFBS";
	private static boolean DEBUG = true;

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
		} else {
			return;
		}
	}
}