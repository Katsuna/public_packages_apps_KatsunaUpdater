/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.katsuna.updater;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import com.katsuna.updater.ssl.NullHostNameVerifier;
import com.katsuna.updater.ssl.NullX509TrustManager;

import java.security.SecureRandom;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

public class UpdateApplication extends Application implements
        Application.ActivityLifecycleCallbacks {

    // Leave this off for release
    public static final boolean SSL_VERIFY_ALL_HOSTNAMES = false;

    private boolean mMainActivityActive;
    private RequestQueue mRequestQueue;

    @Override
    public void onCreate() {
        mMainActivityActive = false;
        registerActivityLifecycleCallbacks(this);

        // this needs to run only once per lifecycle
        // Read: https://stackoverflow.com/a/15252178/4008886
        if (SSL_VERIFY_ALL_HOSTNAMES) {
            try {
            HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{new NullX509TrustManager()}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                // noop
            }
        }
        mRequestQueue = Volley.newRequestQueue(this);
    }

    @Override
    public void onActivityCreated (Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed (Activity activity) {
    }

    @Override
    public void onActivityPaused (Activity activity) {
    }

    @Override
    public void onActivityResumed (Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState (Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityStarted (Activity activity) {
        if (activity instanceof UpdatesSettings) {
            mMainActivityActive = true;
        }
    }

    @Override
    public void onActivityStopped (Activity activity) {
        if (activity instanceof UpdatesSettings) {
            mMainActivityActive = false;
        }
    }

    public boolean isMainActivityActive() {
        return mMainActivityActive;
    }

    public RequestQueue getQueue() {
        return mRequestQueue;
    }
}
