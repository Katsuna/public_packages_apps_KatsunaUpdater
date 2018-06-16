/*
 * Copyright (C) 2018 Katsuna
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.katsuna.updater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.katsuna.updater.service.UpdateCheckService;

public class UpdateAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, UpdateCheckService.class);
        UpdateCheckService.enqueueWork(context, intent);
        Log.i("UpdateAlarm", "Update check job is set!");
    }
}
