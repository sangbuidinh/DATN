package com.example.chatapp.SharedPreferencesManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SharedPreferencesManager {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_USER_ID = "current_user_id";

    public static void saveUsername(Context context, String userId) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
    }

    public static String getUsername(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public static void clearUsername(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_USER_ID)) {
            String username = prefs.getString(KEY_USER_ID, null);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_USER_ID);
            editor.apply();
            Log.d("SharedPreferences", "Username cleared: " + username); // Log username trước khi xóa
        } else {
            Log.d("SharedPreferences", "No username to clear."); // Log khi không có username để xóa
        }
    }
}