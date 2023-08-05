/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.doze;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
 
import org.lineageos.settings.utils.FileUtils;

public class DozeService extends Service {
    private static final String TAG = "DozeService";
    private static final boolean DEBUG = false;
    private static final String HBM_SWITCH = "switch_hbm";
    private static final String HBM_NODE = "/sys/class/drm/card0-DSI-1/disp_param";

    private ProximitySensor mProximitySensor;
    private PickupSensor mPickupSensor;
    private HBMObserver hbmObserver;
    private SharedPreferences sharedPrefs;

    @Override
    public void onCreate() {
        if (DEBUG)
            Log.d(TAG, "Creating service");
        mProximitySensor = new ProximitySensor(this);
        mPickupSensor = new PickupSensor(this);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        hbmObserver = new HBMObserver(new Handler());
        getContentResolver().registerContentObserver(Settings.System.getUriFor(HBM_SWITCH), true, hbmObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        super.onDestroy();
        this.unregisterReceiver(mScreenStateReceiver);
        this.getContentResolver().unregisterContentObserver(hbmObserver);
        mProximitySensor.disable();
        mPickupSensor.disable();
        getContentResolver().registerContentObserver(Settings.System.getUriFor(HBM_SWITCH), false, hbmObserver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onDisplayOn() {
        if (DEBUG) Log.d(TAG, "Display on");
        
        new Thread(new Runnable() {
               @Override
               public void run() {
                   try {
                       Thread.sleep(30);
                   } catch(Exception e) {}
                   if (Settings.System.getInt(getContentResolver(), HBM_SWITCH, 0) == 1)
                       FileUtils.writeLine(HBM_NODE, "0x1d20FE0");
               }
           }).start();
           
        if (DozeUtils.isPickUpEnabled(this)) {
            mPickupSensor.disable();
        }
        if (DozeUtils.isHandwaveGestureEnabled(this) ||
                DozeUtils.isPocketGestureEnabled(this)) {
            mProximitySensor.disable();
        }
    }

    private void onDisplayOff() {
        if (DEBUG) Log.d(TAG, "Display off");
        if (DozeUtils.isPickUpEnabled(this)) {
            mPickupSensor.enable();
        }
        if (DozeUtils.isHandwaveGestureEnabled(this) ||
                DozeUtils.isPocketGestureEnabled(this)) {
            mProximitySensor.enable();
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                onDisplayOn();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                onDisplayOff();
            }
        }
    };

    class HBMObserver extends ContentObserver {

        private Thread mThread = null;

        public HBMObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            int hbmEnabled_i = Settings.System.getInt(getContentResolver(), HBM_SWITCH, 0);
            if (DEBUG)
                Log.d(TAG, "hbmEnabled: " + hbmEnabled_i);
            if (hbmEnabled_i == 0) {
                FileUtils.writeLine(HBM_NODE, "0x20f0F20");
                if (mThread != null && mThread.isAlive()) {
                    mThread.interrupt();
                    mThread = null;
                }
                return;
            }
            if (mThread == null || !mThread.isAlive())
                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException e) {
                            return;
                        }
                        int hbmEnabled_ii = Settings.System.getInt(getContentResolver(), HBM_SWITCH, 0);
                        if (DEBUG)
                            Log.d(TAG, "Thread: hbmEnabled: " + hbmEnabled_ii);
                        FileUtils.writeLine(HBM_NODE, hbmEnabled_ii == 1 ? "0x1d20FE0" : "0x20f0F20");
                    }
                });
            mThread.start();
        }
    }
}
