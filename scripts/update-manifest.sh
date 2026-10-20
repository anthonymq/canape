#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK required}
tag=${2:?Release tag required}
destination=${3:?Manifest output required}
[[ "$tag" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
aapt2=${AAPT2:-${ANDROID_HOME:?ANDROID_HOME or AAPT2 required}/build-tools/37.0.0/aapt2}
badging=$("$aapt2" dump badging "$apk")
[[ "$badging" =~ package:\ name=\'([^\']+)\'\ versionCode=\'([0-9]+)\'\ versionName=\'([^\']+)\' ]] || {
    echo 'Cannot read APK identity' >&2; exit 1;
}
package_name=${BASH_REMATCH[1]}
version_code=${BASH_REMATCH[2]}
version_name=${BASH_REMATCH[3]}
[[ "$badging" =~ (minSdkVersion|sdkVersion):\'([0-9]+)\' ]] || { echo 'Cannot read minSdk' >&2; exit 1; }
min_sdk=${BASH_REMATCH[2]}
case "$package_name" in
    fr.channelfinder.tv) asset=canape-google-tv.apk ;;
    fr.channelfinder.tv.salontest) asset=canape-salon-google-tv-test.apk ;;
    *) echo 'Unknown distribution package' >&2; exit 1 ;;
esac
[[ "$(basename "$apk")" == "$asset" ]] || { echo 'Incorrect APK asset filename' >&2; exit 1; }
digest=$(sha256sum "$apk")
digest=${digest%% *}
size=$(wc -c < "$apk")
jq -n --arg packageName "$package_name" --arg versionName "$version_name" \
    --argjson versionCode "$version_code" --argjson minSdk "$min_sdk" \
    --arg sha256 "$digest" --argjson size "$size" \
    --arg apkUrl "https://github.com/anthonymq/canape/releases/download/$tag/$asset" \
    '{packageName:$packageName,versionCode:$versionCode,versionName:$versionName,minSdk:$minSdk,sha256:$sha256,size:$size,apkUrl:$apkUrl}' > "$destination"
