package com.datausage;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class PackageManagerHelper {
  public static boolean isPackage(Context context, CharSequence s) {
    PackageManager packageManager = context.getPackageManager();
    try {
      packageManager.getPackageInfo(s.toString(), PackageManager.GET_META_DATA);
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
    return true;
  }

  public static int getPackageUid(Context context) {
    PackageManager packageManager = context.getPackageManager();
    int uid = -1;
    try {
      PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      uid = packageInfo.applicationInfo.uid;
    } catch (PackageManager.NameNotFoundException e) {
    }
    return uid;
  }
}
