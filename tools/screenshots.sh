#!/usr/bin/env bash
# Installs the debug APK on the emulator and captures the main screens at several sizes.
set -x
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.farias.prismajam.debug
ACT=com.farias.prismajam.MainActivity
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
mkdir -p shots

cap() { # name — save on the device then pull (more reliable than exec-out on a busy emulator)
  adb wait-for-device
  adb shell screencap -p /sdcard/cap.png
  adb pull /sdcard/cap.png "shots/$1.png" >/dev/null
  echo "::notice::print $1 = $(stat -c%s "shots/$1.png" 2>/dev/null || echo missing) bytes"
}

shot() { # name screen [level] [landscape|shatter]
  adb shell am force-stop $PKG
  if [ "$4" = "shatter" ]; then
    adb shell am start -n $PKG/$ACT --es screen "$2" --ei level "${3:-1}" --ez shatter true
  elif [ "$4" = "landscape" ]; then
    adb shell am start -n $PKG/$ACT --es screen "$2" --ei level "${3:-1}" --ez landscape true
  else
    adb shell am start -n $PKG/$ACT --es screen "$2" --ei level "${3:-1}"
  fi
  sleep 14
  cap "$1"
}

for cfg in "phone 1080x2400 420" "tiny 480x854 240" "small 720x1280 320" "tablet 1600x2560 320"; do
  set -- $cfg
  name=$1
  adb shell wm size "$2"
  adb shell wm density "$3"
  sleep 3
  # Warm-up launch: the first start after a resize is slow on the emulator.
  shot "_warmup-$name" home
  shot "$name-home" home
  for lv in 1 12 30; do shot "$name-game$lv" game $lv; done
done
# Extra checks on the normal phone: daily chest popup, themes, stats
adb shell wm size 1080x2400; adb shell wm density 420; sleep 2
shot "phone-chest" chest
shot "phone-themes" themes
shot "phone-stats" stats
shot "phone-map" map
shot "phone-daily" daily
shot "phone-shatter" game 30 shatter
cap phone-shatter2
# Wide window layout (tablet held sideways)
adb shell wm size 1600x2560; adb shell wm density 320; sleep 2
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot "tablet-landscape-game" game 12 landscape
adb shell settings put system user_rotation 0
adb shell wm size reset
adb shell wm density reset
find shots -size 0 -delete
ls -la shots
