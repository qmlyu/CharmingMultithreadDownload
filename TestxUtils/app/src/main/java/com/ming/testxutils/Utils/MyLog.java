package com.ming.testxutils.Utils;

import android.util.Log;

/**
 * Created by CharmingLee on 2015/5/20.
 */
public class MyLog {
    private static String TAG = "app";
    public static void d(String message){
        Log.d(TAG,message);
    }
    public static void e(String message){
        Log.e(TAG,message);
    }
}
