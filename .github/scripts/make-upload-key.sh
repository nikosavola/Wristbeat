#!/bin/sh
# Creates the upload key that Play and Galaxy Store take, and prints the four values the release
# workflow reads as secrets.
#
#   .github/scripts/make-upload-key.sh
#
# Writes wristbeat-upload.jks and key.properties, both gitignored, in the repository root. Keep the
# keystore somewhere you will still have it in ten years. Play App Signing can reissue a lost upload
# key, but Galaxy Store and anyone sideloading cannot, and a different key means an app that will not
# install over the one already on the watch.
set -eu

KEYSTORE=wristbeat-upload.jks
PROPERTIES=key.properties
ALIAS=wristbeat

[ -f "wear/build.gradle.kts" ] || { echo "run this from the root of the repository" >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool is not on PATH: it comes with a JDK" >&2; exit 1; }
[ ! -e "$KEYSTORE" ] || { echo "$KEYSTORE is already here; move it aside rather than making another" >&2; exit 1; }
[ ! -e "$PROPERTIES" ] || { echo "$PROPERTIES is already here; move it aside rather than making another" >&2; exit 1; }

printf 'Password for the upload key: '
stty -echo
read -r password
stty echo
printf '\n'

# PKCS12, the keystore type Java has defaulted to since 9, holds one password for the store and the
# key, so this asks once and uses it for both rather than tripping over ignored -keypass.
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$password" \
  -dname "CN=Wristbeat, O=Niko Savola, C=FI"

printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
  "$KEYSTORE" "$password" "$ALIAS" "$password" > "$PROPERTIES"

echo
echo "Wrote $KEYSTORE and $PROPERTIES."
echo
echo "Set these as repository secrets:"
echo
printf 'UPLOAD_KEYSTORE_BASE64\n'
base64 < "$KEYSTORE" | tr -d '\n'
echo
echo
echo "UPLOAD_STORE_PASSWORD  the password you just typed"
echo "UPLOAD_KEY_ALIAS       $ALIAS"
echo "UPLOAD_KEY_PASSWORD    the password you just typed"
