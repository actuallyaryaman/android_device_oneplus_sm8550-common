/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.bypasschrg;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.R;

public class BypassChargingTile extends TileService {

    private BypassChargingController mBypassController;
    private boolean mEnabled;

    @Override
    public void onCreate() {
        super.onCreate();
        mBypassController = BypassChargingController.getInstance(this);
    }

    @Override
    public void onStartListening() {
        int status = mBypassController.getBypassChargingStatus();
        mEnabled = status != Constants.BYPASS_OFF;
        updateTileState(status);
    }

    @Override
    public void onClick() {
        boolean enabled = mBypassController.getBypassChargingStatus() != Constants.BYPASS_OFF;
        if (mEnabled == enabled) {
            boolean success;
            if (mEnabled) {
                success = mBypassController.disableBypassCharging();
            } else {
                success = mBypassController.enableBypassCharging();
            }
            if (success) {
                mEnabled = !mEnabled;
                updateTileState(mBypassController.getBypassChargingStatus());
            }
        }
    }

    private void updateTileState(int status) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(status == Constants.BYPASS_OFF ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel(getString(R.string.bypass_charging_title));
        tile.setContentDescription(getString(R.string.bypass_charging_summary));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_bypass_charging));
        tile.updateTile();
    }
}