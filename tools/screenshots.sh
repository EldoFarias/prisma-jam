#!/usr/bin/env bash
# Installs the debug APK on the emulator and captures the main screens at several sizes.
set -x
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.farias.prismajam.debug
ACT=com.farias.prismajam.MainActivity
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
mkdir -p shots

shot() { # name screen [landscape]
  adb shell am force-stop $PKG
  if [ "$3" = "landscape" ]; then
    adb shell am start -n $PKG/$ACT --es screen "$2" --ez landscape true
  else
    adb shell am start -n $PKG/$ACT --es screen "$2"
  fi
  sleep 14
  adb exec-out screencap -p > "shots/$1.png"
}

for cfg in "tiny 480x854 240" "small 720x1280 320" "phone 1080x2400 420" "tablet 1600x2560 320"; do
  set -- $cfg
  name=$1
  adb shell wm size "$2"
  adb shell wm density "$3"
  sleep 3
  # Warm-up launch: the first start after a resize is slow on the emulator.
  shot "_warmup-$name" home
  for s in home game adventure_game map; do shot "$name-$s" "$s"; done
done
# Extra checks on the normal phone: daily chest popup, themes, stats
adb shell wm size 1080x2400; adb shell wm density 420; sleep 2
shot "phone-chest" chest
shot "phone-themes" themes
shot "phone-stats" stats
# Wide window layout (tablet held sideways)
adb shell wm size 1600x2560; adb shell wm density 320; sleep 2
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot "tablet-landscape-game" game landscape
adb shell settings put system user_rotation 0
adb shell wm size reset
adb shell wm density reset
ls -la shots
