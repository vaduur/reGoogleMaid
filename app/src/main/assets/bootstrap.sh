#!/system/bin/sh
# GPL-3.0-only. Bounded startup test, explicitly approved by the device owner.
package=com.oplus.appplatform
lock=/data/local/tmp/ace6t-discover-bootstrap.lock
native_bound() {
    dumpsys activity services com.google.android.googlequicksearchbox | grep 'DrawerOverlayService c:com.android.launcher' >/dev/null
}
if native_bound; then echo NATIVE_ALREADY_ACTIVE; exit 0; fi
[ "$(getprop ro.product.model)" = PLR110 ] || { echo ERROR_UNREVIEWED_MODEL; exit 1; }
version=$(dumpsys package com.android.launcher | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
[ "$version" = 160060017 ] || { echo ERROR_UNREVIEWED_LAUNCHER; exit 1; }
dumpsys package "$package" | grep 'User 0:' | grep -q 'enabled=0 ' || { echo ERROR_APPPLATFORM_NOT_DEFAULT; exit 1; }
settings put secure assistant_screen_type 2 || exit 1
mkdir "$lock" 2>/dev/null || { echo ERROR_BOOTSTRAP_ALREADY_RUNNING; exit 1; }
restore() { pm default-state --user 0 "$package"; rmdir "$lock" 2>/dev/null; }
trap restore EXIT HUP INT TERM
# A detached second shell restores the package even if this client disappears.
nohup sh -c 'sleep 20; pm default-state --user 0 com.oplus.appplatform' </dev/null >/data/local/tmp/ace6t-discover-failsafe.log 2>&1 &
echo BOOTSTRAP_START
pm disable-user --user 0 "$package" || exit 1
am force-stop com.android.launcher || exit 1
am start -a android.intent.action.MAIN -c android.intent.category.HOME || exit 1
sleep 12
restore
trap - EXIT HUP INT TERM
if native_bound; then echo NATIVE_READY; else echo ERROR_NATIVE_NOT_BOUND; exit 1; fi
