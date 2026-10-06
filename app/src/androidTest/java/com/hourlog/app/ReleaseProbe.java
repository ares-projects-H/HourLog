package com.hourlog.app;

import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Objects;

/** No target/library class names: also runs against the R8-optimized APK. */
public class ReleaseProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected,actual)) throw new AssertionError("Preserved data mismatch");
    }
    private static String text(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context app = getTargetContext();
            JSONObject expected = new JSONObject(text(new File(app.getFilesDir(),"reboot-expected.json")));
            JSONArray entries = expected.getJSONArray("entries");
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(app.getDatabasePath("hourlog.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                try (android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM work_entries",null)) {
                    if(!cursor.moveToFirst()) throw new AssertionError(); equal(entries.length(),cursor.getInt(0));
                }
                for(int i=0;i<entries.length();i++) {
                    JSONObject e = entries.getJSONObject(i);
                    try (android.database.Cursor cursor = db.rawQuery("SELECT date,start,end,zoneId,note FROM work_entries WHERE id=?",new String[]{e.getString("id")})) {
                        if(!cursor.moveToFirst()) throw new AssertionError();
                        equal(e.getString("date"),cursor.getString(0)); equal(e.getLong("start"),cursor.getLong(1));
                        equal(e.getLong("end"),cursor.getLong(2)); equal(e.getString("zoneId"),cursor.getString(3)); equal(e.getString("note"),cursor.getString(4));
                    }
                }
            }
            String bytes = text(new File(app.getFilesDir(),"datastore/hourlog_preferences.preferences_pb"));
            JSONObject prefs = new JSONObject(bytes.substring(bytes.indexOf('{'),bytes.lastIndexOf('}')+1));
            JSONObject expectedPrefs = expected.getJSONObject("preferences");
            for(Iterator<String> keys=expectedPrefs.keys();keys.hasNext();) {
                String key=keys.next(); equal(expectedPrefs.get(key).toString(),prefs.get(key).toString());
            }
            String id = text(new File(app.getFilesDir(),"reboot-work-id.txt"));
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(app.getNoBackupFilesDir(),"androidx.work.workdb").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                 android.database.Cursor cursor = db.rawQuery("SELECT state FROM WorkSpec WHERE id=?",new String[]{id})) {
                if(!cursor.moveToFirst()) throw new AssertionError(); equal(0,cursor.getInt(0));
            }
            result.putString("stream","PASS: release entries, settings and same scheduled work preserved\n"); finish(-1,result);
        } catch(Throwable e) { result.putString("stream","FAIL: "+e.toString()+"\n"); finish(0,result); }
    }
}
