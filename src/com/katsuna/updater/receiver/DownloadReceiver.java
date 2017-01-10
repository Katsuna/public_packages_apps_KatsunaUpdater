/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2017 Katsuna
 * Copyright (C) 2017 The LineageOS Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.katsuna.updater.receiver;

import android.app.DownloadManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.JobIntentService;
import android.util.Log;
import android.widget.Toast;

import com.katsuna.updater.R;
import com.katsuna.updater.misc.Constants;
import com.katsuna.updater.misc.UpdateInfo;
import com.katsuna.updater.service.DownloadCompleteIntentService;
import com.katsuna.updater.service.DownloadService;
import com.katsuna.updater.utils.Utils;

import java.io.IOException;

public class DownloadReceiver extends BroadcastReceiver{
    private static final String TAG = "DownloadReceiver";

    public static final String ACTION_START_DOWNLOAD = "com.katsuna.updater.action.START_DOWNLOAD";
    public static final String EXTRA_UPDATE_INFO = "update_info";

    public static final String ACTION_DOWNLOAD_STARTED = "com.katsuna.updater.action.DOWNLOAD_STARTED";

    static final String ACTION_INSTALL_UPDATE = "com.katsuna.updater.action.INSTALL_UPDATE";
    public static final String ACTION_INSTALL_REBOOT = "com.katsuna.updater.action.INSTALL_REBOOT";
    static final String EXTRA_FILENAME = "filename";
    static final String EXTRA_IS_AB_UPDATE = "is_ab_update";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_START_DOWNLOAD.equals(action)) {
            UpdateInfo ui = (UpdateInfo) intent.getParcelableExtra(EXTRA_UPDATE_INFO);
            handleStartDownload(context, ui);
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            handleDownloadComplete(context, id);
        } else if (ACTION_INSTALL_UPDATE.equals(action)) {
            StatusBarManager sb = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
            sb.collapsePanels();
            String fileName = intent.getStringExtra(EXTRA_FILENAME);
            boolean isABUpdate = intent.getBooleanExtra(EXTRA_IS_AB_UPDATE, false);
            if (isABUpdate) {
                Utils.cancelNotification(context);
                Utils.triggerUpdateAB(context, fileName);
            } else {
                try {
                    Utils.triggerUpdate(context, fileName);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to reboot into recovery mode", e);
                    Toast.makeText(context, R.string.apply_unable_to_reboot_toast,
                                Toast.LENGTH_SHORT).show();
                    Utils.cancelNotification(context);
                }
            }
        } else if (ACTION_INSTALL_REBOOT.equals(action)) {
            Utils.triggerReboot(context);
        }
    }

    private void handleStartDownload(Context context, UpdateInfo ui) {
        DownloadService.start(context, ui);
    }

    private void handleDownloadComplete(Context context, long id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long enqueued = prefs.getLong(Constants.DOWNLOAD_ID, -1);
        if (enqueued < 0 || id < 0 || id != enqueued) {
            return;
        }

        String downloadedMD5 = prefs.getString(Constants.DOWNLOAD_MD5, "");
        String incrementalFor = prefs.getString(Constants.DOWNLOAD_INCREMENTAL_FOR, null);

        // Send off to DownloadCompleteIntentService
        Intent intent = new Intent(context, DownloadCompleteIntentService.class);
        intent.putExtra(Constants.DOWNLOAD_ID, id);
        intent.putExtra(Constants.DOWNLOAD_MD5, downloadedMD5);
        intent.putExtra(Constants.DOWNLOAD_INCREMENTAL_FOR, incrementalFor);
        JobIntentService.enqueueWork(context, DownloadCompleteIntentService.class,
                Constants.DOWNLOAD_COMPLETED_JOB_ID, intent);

        // Clear the shared prefs
        prefs.edit()
                .remove(Constants.DOWNLOAD_MD5)
                .remove(Constants.DOWNLOAD_ID)
                .remove(Constants.DOWNLOAD_INCREMENTAL_FOR)
                .apply();
    }
}
