/*
 * Copyright (C) 2018-2024 crDroid Android Project
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

package org.lineageos.device.DeviceSettings;

public class Constants {

    /* Debug flag */
    public static final boolean DEBUG = true;

    /* Bypass Charging */
    public static final String NODE_BYPASS_CHARGING = "/sys/class/oplus_chg/battery/mmi_charging_enable";
    public static final String KEY_BYPASS_CHARGING = "bypass_charging";
    public static final String KEY_BYPASS_CHARGING_TARGET = "bypass_charging_target";

    public static final int BYPASS_OFF = 0;
    public static final int BYPASS_WAITING = 1;
    public static final int BYPASS_ON = 2;

    public static final int BYPASS_TARGET_MIN = 1;
    public static final int BYPASS_TARGET_MAX = 99;
    public static final int BYPASS_TARGET_DEFAULT = BYPASS_TARGET_MIN;
}