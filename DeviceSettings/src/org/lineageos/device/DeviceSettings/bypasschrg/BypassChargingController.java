/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pure state machine for bypass charging logic.
 * No UI concerns, no service management, just state.
 */

package org.lineageos.device.DeviceSettings.bypasschrg;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.utils.FileUtils;

public class BypassChargingController {

    private static final String TAG = "BypassChargingController";
    private static final String BYPASS_ENABLED = "0";
    private static final String BYPASS_DISABLED = "1";
    private static final String KEY_BATTERY_LEVEL = "current_battery_level";
    private static final String KEY_SAVED_CHARGING_ENABLED = "saved_charging_control_enabled";
    private static final String KEY_SAVED_CHARGING_MODE = "saved_charging_control_mode";
    private static final String KEY_SAVED_CHARGING_LIMIT = "saved_charging_control_limit";
    private static final String KEY_SAVED_CHARGING_START_TIME = "saved_charging_control_start_time";
    private static final String KEY_SAVED_CHARGING_TARGET_TIME = "saved_charging_control_target_time";

    private int mBatteryLevel;
    private final Context mContext;
    private final Object mLock = new Object();
    private final Handler mHandler;
    private final Runnable mVerifyRunnable = this::verifyBypassState;
    private ContentObserver mLineageHealthObserver;

    private static BypassChargingController sInstance;

    public static synchronized BypassChargingController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BypassChargingController(context.getApplicationContext());
        }
        return sInstance;
    }

    private BypassChargingController(Context context) {
        mContext = context.getApplicationContext();
        mHandler = new Handler(Looper.myLooper() != null
                ? Looper.myLooper() : Looper.getMainLooper());
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

    // ===== Lineage Health coordination =====

    private void disableLineageHealthChargingControl() {
        try {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.CHARGING_CONTROL_ENABLED, 0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable Lineage Health charging control", e);
        }
    }

    private void saveLineageHealthState() {
        ContentResolver cr = mContext.getContentResolver();
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(KEY_SAVED_CHARGING_ENABLED,
                        Settings.System.getInt(cr, Settings.System.CHARGING_CONTROL_ENABLED, 0) != 0)
                .putInt(KEY_SAVED_CHARGING_MODE,
                        Settings.System.getInt(cr, Settings.System.CHARGING_CONTROL_MODE, 1))
                .putInt(KEY_SAVED_CHARGING_LIMIT,
                        Settings.System.getInt(cr, Settings.System.CHARGING_CONTROL_LIMIT, 80))
                .putInt(KEY_SAVED_CHARGING_START_TIME,
                        Settings.System.getInt(cr, Settings.System.CHARGING_CONTROL_START_TIME, 79200))
                .putInt(KEY_SAVED_CHARGING_TARGET_TIME,
                        Settings.System.getInt(cr, Settings.System.CHARGING_CONTROL_TARGET_TIME, 21600))
                .apply();
    }

    private void restoreLineageHealthState() {
        ContentResolver cr = mContext.getContentResolver();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        Settings.System.putInt(cr, Settings.System.CHARGING_CONTROL_ENABLED,
                prefs.getBoolean(KEY_SAVED_CHARGING_ENABLED, false) ? 1 : 0);
        Settings.System.putInt(cr, Settings.System.CHARGING_CONTROL_MODE,
                prefs.getInt(KEY_SAVED_CHARGING_MODE, 1));
        Settings.System.putInt(cr, Settings.System.CHARGING_CONTROL_LIMIT,
                prefs.getInt(KEY_SAVED_CHARGING_LIMIT, 80));
        Settings.System.putInt(cr, Settings.System.CHARGING_CONTROL_START_TIME,
                prefs.getInt(KEY_SAVED_CHARGING_START_TIME, 79200));
        Settings.System.putInt(cr, Settings.System.CHARGING_CONTROL_TARGET_TIME,
                prefs.getInt(KEY_SAVED_CHARGING_TARGET_TIME, 21600));
    }

    private void registerLineageHealthObserver() {
        if (mLineageHealthObserver != null) return;
        mLineageHealthObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                int enabled = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.CHARGING_CONTROL_ENABLED, 0);
                if (enabled != 0 && getBypassChargingStatus() != Constants.BYPASS_OFF) {
                    if (Constants.DEBUG) Log.i(TAG, "Lineage Health re-enabled, disabling bypass");
                    disableBypassCharging();
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.CHARGING_CONTROL_ENABLED),
                false, mLineageHealthObserver);
    }

    private void unregisterLineageHealthObserver() {
        if (mLineageHealthObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mLineageHealthObserver);
            mLineageHealthObserver = null;
        }
    }

    private void verifyBypassState() {
        synchronized (mLock) {
            int status = getBypassChargingStatus();
            if (status == Constants.BYPASS_OFF) return;

            String current = FileUtils.readLine(Constants.NODE_BYPASS_CHARGING);
            boolean shouldBeEnabled = (status == Constants.BYPASS_ON);

            if ((BYPASS_ENABLED.equals(current) && !shouldBeEnabled) ||
                    (BYPASS_DISABLED.equals(current) && shouldBeEnabled)) {
                if (Constants.DEBUG) Log.w(TAG, "Bypass state mismatch, reapplying");
                if (shouldBeEnabled) {
                    enableHardwareBypass();
                } else {
                    disableHardwareBypass();
                }
            }
        }
    }

    // ===== Power events =====

    public void handlePowerConnected() {
        synchronized (mLock) {
            if (Constants.DEBUG) Log.i(TAG, "Power connected");

            int status = getBypassChargingStatus();
            if (status != Constants.BYPASS_OFF) {
                disableLineageHealthChargingControl();
            }

            mBatteryLevel = getLevelFromIntent();
            if (isValidLevel(mBatteryLevel)) {
                saveCurrentBatteryLevel(mBatteryLevel);
            }

            int target = getBypassChargingTarget();
            int current = getCurrentBatteryLevel();

            if (status == Constants.BYPASS_ON) {
                if (current >= target) {
                    enableHardwareBypass();
                    if (Constants.DEBUG) Log.i(TAG, "Re-enabled global (level >= target)");
                    mHandler.removeCallbacks(mVerifyRunnable);
                    mHandler.postDelayed(mVerifyRunnable, 500);
                } else {
                    disableHardwareBypass();
                    saveBypassChargingStatus(Constants.BYPASS_WAITING);
                    if (Constants.DEBUG) Log.i(TAG, "Below target, now WAITING");
                }
            } else if (status == Constants.BYPASS_WAITING && current >= target) {
                if (enableHardwareBypass()) {
                    saveBypassChargingStatus(Constants.BYPASS_ON);
                    if (Constants.DEBUG) Log.i(TAG, "Reached target, now ON");
                    mHandler.removeCallbacks(mVerifyRunnable);
                    mHandler.postDelayed(mVerifyRunnable, 500);
                }
            }
        }
    }

    public void handlePowerDisconnected() {
        synchronized (mLock) {
            if (Constants.DEBUG) Log.i(TAG, "Power disconnected");
            mHandler.removeCallbacks(mVerifyRunnable);
            disableHardwareBypass();
        }
    }

    // ===== UI trigger: enable global bypass =====

    public boolean enableBypassCharging() {
        synchronized (mLock) {
            saveLineageHealthState();
            disableLineageHealthChargingControl();
            registerLineageHealthObserver();

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
                    mHandler.removeCallbacks(mVerifyRunnable);
                    mHandler.postDelayed(mVerifyRunnable, 500);
                    return true;
                }
                return false;
            } else {
                saveBypassChargingStatus(Constants.BYPASS_WAITING);
                if (Constants.DEBUG) Log.i(TAG, "Global enabled, waiting for target");
                mHandler.removeCallbacks(mVerifyRunnable);
                mHandler.postDelayed(mVerifyRunnable, 500);
                return true;
            }
        }
    }

    // ===== UI trigger: disable global bypass =====

    public boolean disableBypassCharging() {
        synchronized (mLock) {
            mHandler.removeCallbacks(mVerifyRunnable);
            disableHardwareBypass();
            saveBypassChargingStatus(Constants.BYPASS_OFF);
            restoreLineageHealthState();
            unregisterLineageHealthObserver();
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
