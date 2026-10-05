# Device wallpapers

This scaffold uses the existing wallpaper picker's partner-customization support.
All changes live in `device/oneplus/sm8550-common` and apply to devices inheriting
that tree, including aston. No picker or framework changes are needed.

The package starts with an empty collection, which the picker ignores. Without
`default.png`, the existing ROM default stays in use. There are no placeholder
wallpaper images.

## Add your collection

1. Copy your PNG, JPG, or WebP images into `res/drawable-nodpi/`. Use resource
   filenames containing only lowercase letters, digits, and underscores, such
   as `wallpaper_01.png`. Do not use the same resource basename twice.
2. Add an entry inside the category in `res/xml/wallpapers.xml` for each image:

   ```xml
   <static-wallpaper id="wallpaper_01" src="@drawable/wallpaper_01" />
   <static-wallpaper id="wallpaper_02" src="@drawable/wallpaper_02" />
   ```

   IDs must be unique. Resource references omit the filename extension. Entries
   appear in XML order. The first image serves as the collection thumbnail.
3. Change `wallpaper_collection_title` in `res/values/strings.xml` to your desired
   collection name. No extra thumbnail files are required.

## Choose the default

Place a real PNG image named `default.png` beside this README. If your chosen
image uses another format, export it as PNG instead of just renaming it.
For the default to also appear in the collection, include the image in
`res/drawable-nodpi/` and register it as described above.

When `default.png` exists, `wallpapers.mk` copies it to
`/product/etc/device_default_wallpaper.png` and sets `ro.config.wallpaper` to that
path. Android reads this property before falling back to its framework drawable.
Existing user-selected wallpapers are preserved on upgrades. Check the initial
default on a fresh installation or after resetting the wallpaper to its default.

## Build and verify

Build and device testing are left to the maintainer. On your build, verify the
collection appears in the existing wallpaper picker, preview and select each
image, and check the default on a fresh installation. Check cropping on home
and lock screens. The scaffold adds no launcher activity, service, or permissions.

Both `DeviceWallpapers` and the optional default image are included through the
common device makefile. Keep this directory in your maintained device-tree branch
when updating upstream sources.
