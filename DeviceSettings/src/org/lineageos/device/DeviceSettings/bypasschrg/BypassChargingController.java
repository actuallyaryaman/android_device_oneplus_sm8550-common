/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pure state machine for bypass charging logic.
 * No UI concerns, no service management, just state.
 */

package org.lineageos.device.DeviceSettings.bypasschrg;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.utils.FileUtils;

public class BypassChargingController {

    private static final String TAG = "BypassChargingController";
    private static final String BYPASS_ENABLED = "0";
    private static final String BYPASS_DISABLED = "1";
    private static final String KEY_BATTERY_LEVEL = "current_battery_level";

    private int mBatteryLevel;
    private final Context mContext;
    private final Object mLock = new Object();

    private static BypassChargingController sInstance;

    public static synchronized BypassChargingController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BypassChargingController(context.getApplicationContext());
        }
        return sInstance;
    }

    private BypassChargingController(Context context) {
        mContext = context.getApplicationContext();
        mBatteryLevel = getLevelFromIntent();
        if (isValidLevel(mBatteryLevel)) {
            saveCurrentBatteryLevel(mBatteryLevel);
        }
    }

    // ===== Battery helpers =====

    private int getLevelFromIntent() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = mContext.registerReceiver(null, filter);
        if (intent == null) {
            if (Constants.DEBUG) Log.w(TAG, "Battery intent null");
            return -1;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;
    }

    private int getPlugTypeFromIntent() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = mContext.registerReceiver(null, filter);
        if (intent == null) return 0;
        return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
    }

    private boolean isPlugged() {
        return getPlugTypeFromIntent() != 0;
    }

    private boolean isValidLevel(int level) {
        return level >= 0 && level <= 100;
    }

    // ===== Hardware control =====

    private boolean enableHardwareBypass() {
        try {
            FileUtils.writeLine(Constants.NODE_BYPASS_CHARGING, BYPASS_ENABLED);
            String verify = FileUtils.readLine(Constants.NODE_BYPASS_CHARGING);
            if (!BYPASS_ENABLED.equals(verify)) {
                Log.e(TAG, "Hardware bypass enable verification failed");
                return false;
            }
            if (Constants.DEBUG) Log.i(TAG, "Hardware bypass enabled");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable hardware bypass", e);
            return false;
        }
    }

    private boolean disableHardwareBypass() {
        try {
            FileUtils.writeLine(Constants.NODE_BYPASS_CHARGING, BYPASS_DISABLED);
            String verify = FileUtils.readLine(Constants.NODE_BYPASS_CHARGING);
            if (!BYPASS_DISABLED.equals(verify)) {
                Log.e(TAG, "Hardware bypass disable verification failed");
                return false;
            }
            if (Constants.DEBUG) Log.i(TAG, "Hardware bypass disabled");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable hardware bypass", e);
            return false;
        }
    }

    public boolean isBypassChargingSupported() {
        try {
            FileUtils.readLine(Constants.NODE_BYPASS_CHARGING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== Power events =====

    public void handlePowerConnected() {
        synchronized (mLock) {
            if (Constants.DEBUG) Log.i(TAG, "Power connected");

            mBatteryLevel = getLevelFromIntent();
            if (isValidLevel(mBatteryLevel)) {
                saveCurrentBatteryLevel(mBatteryLevel);
            }

            int status = getBypassChargingStatus();
            int target = getBypassChargingTarget();
            int current = getCurrentBatteryLevel();

            if (status == Constants.BYPASS_ON) {
                if (current >= target) {
                    enableHardwareBypass();
                    if (Constants.DEBUG) Log.i(TAG, "Re-enabled global (level >= target)");
                } else {
                    disableHardwareBypass();
                    saveBypassChargingStatus(Constants.BYPASS_WAITING);
                    if (Constants.DEBUG) Log.i(TAG, "Below target, now WAITING");
                }
            } else if (status == Constants.BYPASS_WAITING && current >= target) {
                if (enableHardwareBypass()) {
                    saveBypassChargingStatus(Constants.BYPASS_ON);
                    if (Constants.DEBUG) Log.i(TAG, "Reached target, now ON");
                }
            }
        }
    }

    public void handlePowerDisconnected() {
        synchronized (mLock) {
            if (Constants.DEBUG) Log.i(TAG, "Power disconnected");
            disableHardwareBypass();
        }
    }

    // ===== UI trigger: enable global bypass =====

    public boolean enableBypassCharging() {
        synchronized (mLock) {
            int current = getCurrentBatteryLevel();
            if (!isValidLevel(current)) {
                Log.w(TAG, "Invalid battery level: " + current);
                return false;
            }

            int target = getBypassChargingTarget();

            if (current >= target) {
                if (enableHardwareBypass()) {
                    saveBypassChargingStatus(Constants.BYPASS_ON);
                    if (Constants.DEBUG) Log.i(TAG, "Global enabled immediately");
                    return true;
                }
                return false;
            } else {
                saveBypassChargingStatus(Constants.BYPASS_WAITING);
                if (Constants.DEBUG) Log.i(TAG, "Global enabled, waiting for target");
                return true;
            }
        }
    }

    // ===== UI trigger: disable global bypass =====

    public boolean disableBypassCharging() {
        synchronized (mLock) {
            disableHardwareBypass();
            saveBypassChargingStatus(Constants.BYPASS_OFF);
            if (Constants.DEBUG) Log.i(TAG, "Global disabled");
            return true;
        }
    }

    // ===== Preferences =====

    private void saveBypassChargingStatus(int status) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putInt(Constants.KEY_BYPASS_CHARGING, status)
                .apply();
    }

    public int getBypassChargingStatus() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(Constants.KEY_BYPASS_CHARGING, Constants.BYPASS_OFF);
    }

    private void saveBypassChargingTarget(int target) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putInt(Constants.KEY_BYPASS_CHARGING_TARGET, target)
                .apply();
    }

    public int getBypassChargingTarget() {
        int target = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(Constants.KEY_BYPASS_CHARGING_TARGET, Constants.BYPASS_TARGET_DEFAULT);
        if (target < Constants.BYPASS_TARGET_MIN || target > Constants.BYPASS_TARGET_MAX) {
            Log.w(TAG, "Invalid target: " + target);
            saveBypassChargingTarget(Constants.BYPASS_TARGET_DEFAULT);
            return Constants.BYPASS_TARGET_DEFAULT;
        }
        return target;
    }

    private void saveCurrentBatteryLevel(int level) {
        if (isValidLevel(level)) {
            PreferenceManager.getDefaultSharedPreferences(mContext)
                    .edit()
                    .putInt(KEY_BATTERY_LEVEL, level)
                    .apply();
        }
    }

    public int getCurrentBatteryLevel() {
        int level = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(KEY_BATTERY_LEVEL, -1);
        if (!isValidLevel(level)) {
            level = getLevelFromIntent();
            if (isValidLevel(level)) {
                saveCurrentBatteryLevel(level);
            }
        }
        return level;
    }
}