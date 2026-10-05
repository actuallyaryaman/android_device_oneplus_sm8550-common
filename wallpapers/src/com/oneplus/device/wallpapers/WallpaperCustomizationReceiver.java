// SPDX-License-Identifier: Apache-2.0
package com.oneplus.device.wallpapers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Marks this package as a source of wallpaper customization resources. */
public final class WallpaperCustomizationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // The picker reads package resources; no broadcast work is needed.
    }
}
