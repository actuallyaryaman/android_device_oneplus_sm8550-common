# SPDX-License-Identifier: Apache-2.0

PRODUCT_PACKAGES += \
    DeviceWallpapers

# Keep the ROM default until a real default image is supplied.
ifneq ($(wildcard device/oneplus/sm8550-common/wallpapers/default.png),)
PRODUCT_COPY_FILES += \
    device/oneplus/sm8550-common/wallpapers/default.png:$(TARGET_COPY_OUT_PRODUCT)/etc/device_default_wallpaper.png

PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.wallpaper=/product/etc/device_default_wallpaper.png
endif
