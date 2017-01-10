/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2017 Katsuna
 * Copyright (C) 2017 The LineageOS Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.katsuna.updater.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.katsuna.updater.R;
import com.katsuna.updater.misc.Constants;
import com.katsuna.updater.receiver.UpdateAlarmReceiver;
import com.katsuna.updater.service.ABOTAService;
import com.katsuna.updater.service.UpdateCheckService;
import com.katsuna.updater.UpdatePreference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    private Utils() {
        // this class is not supposed to be instantiated
    }

    public static File makeUpdateFolder() {
        return new File(Environment.getExternalStorageDirectory(),
                Constants.UPDATES_FOLDER);
    }

    public static void cancelNotification(Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.not_new_updates_found_title);
        nm.cancel(R.string.not_download_success);
    }

    public static String getDeviceType() {
        return Build.PRODUCT;
    }

    public static String getInstalledVersion() {
        return SystemProperties.get("ro.katsuna.version");
    }

    public static int getInstalledApiLevel() {
        return SystemProperties.getInt("ro.build.version.sdk", 0);
    }

    public static long getInstalledBuildDate() {
        return SystemProperties.getLong("ro.build.date.utc", 0);
    }

    public static String getIncremental() {
        return SystemProperties.get("ro.build.version.incremental");
    }

    public static String getUserAgentString(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.packageName + "/" + pi.versionName;
        } catch (PackageManager.NameNotFoundException nnfe) {
            return null;
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static void scheduleUpdateService(Context context, int updateFrequency) {
        // Load the required settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCheck = prefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0);

        // Get the intent ready
        Intent i = new Intent(context, UpdateAlarmReceiver.class);
        i.setAction(UpdateCheckService.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        if (updateFrequency != Constants.UPDATE_FREQ_NONE) {
            Log.i("UpdateAlarm", "alarm scheduled!");
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, lastCheck + updateFrequency, updateFrequency, pi);
        }
    }

    public static void triggerUpdateAB(Context context, String updateFileName) {
        Intent otaIntent = new Intent(context, ABOTAService.class);
        otaIntent.putExtra(ABOTAService.EXTRA_ZIP_NAME, updateFileName);
        context.startService(otaIntent);
    }

    public static void triggerUpdate(Context context, String updateFileName) throws IOException {
        // Add the update folder/file name
        File primaryStorage = Environment.getExternalStorageDirectory();
        // If the path is emulated, translate it, if not return the original path
        String updatePath = Environment.maybeTranslateEmulatedPathToInternal(
                primaryStorage).getAbsolutePath();
        // Create the path for the update package
        String updatePackagePath = updatePath + "/" + Constants.UPDATES_FOLDER + "/" + updateFileName;

        // Reboot into recovery and trigger the update
        android.os.RecoverySystem.installPackage(context, new File(updatePackagePath));
    }

    public static int getUpdateType() {
        int updateType = Constants.UPDATE_TYPE_NIGHTLY;
        try {
            String cmReleaseType = SystemProperties.get(
                    Constants.PROPERTY_CM_RELEASETYPE);

            // Treat anything that is not SNAPSHOT as NIGHTLY
            if (!cmReleaseType.isEmpty()) {
                if (TextUtils.equals(cmReleaseType,
                        Constants.CM_RELEASETYPE_SNAPSHOT)) {
                    updateType = Constants.UPDATE_TYPE_SNAPSHOT;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return updateType;
    }

    public static void triggerReboot(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot(null);
    }

    public static boolean hasLeanback(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean isABUpdate(Context context, String filename) {
        String zipPath = Utils.makeUpdateFolder().getPath() + "/" + filename;
        List nonABFiles = Arrays.asList("file_contexts.bin",
                                        "install/bin/backuptool.functions",
                                        "install/bin/backuptool.sh",
                                        "install/bin/otasigcheck.sh",
                                        "system.patch.dat",
                                        "system/build.prop",
                                        "META-INF/org/lineageos/releasekey",
                                        "META-INF/com/google/android/updater-script",
                                        "META-INF/com/google/android/update-binary",
                                        "system.new.dat",
                                        "boot.img",
                                        "system.transfer.list");

        List ABOTAFiles = Arrays.asList("payload_properties.txt",
                                        "care_map.txt",
                                        "payload.bin");
        boolean ret = false;

        try {
            ZipInputStream zin = new ZipInputStream(new FileInputStream(zipPath));
            ZipEntry entry;

            while ((entry = zin.getNextEntry()) != null) {
                String file = entry.getName();
                if (nonABFiles.contains(file)) {
                    break;
                } else if (ABOTAFiles.contains(file)) {
                    ret = true;
                    break;
                }
            }
            zin.close();

        } catch (IOException e) {
            Log.e("Utils", "Failed to examine zip", e);
        }

        return ret;
    }

    public static void copy(String src, String dst) throws IOException {
        InputStream in = new FileInputStream(new File(src));
        OutputStream out = new FileOutputStream(new File(dst));

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }
}
