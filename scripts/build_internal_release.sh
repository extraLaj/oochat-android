#!/usr/bin/env bash
# build_internal_release.sh - the signed build handed over for internal testing (W16AAPPLE-51).
#
# Usage:  ./scripts/build_internal_release.sh [--skip-checks] [--allow-dirty]
#
# Builds the release variant (minified, not debuggable) from the current commit, signed with
# the key named in keystore.properties, and puts it in dist/<versionName>/:
#
#   oochat-<versionName>-<versionCode>.aab   for Google Play internal testing
#   oochat-<versionName>-<versionCode>.apk   for Firebase App Distribution or a direct install
#   SHA256SUMS                                checksums of both
#   BUILD-INFO.txt                            version, commit, signing certificate, date
#
# Before building it runs ktlint and the unit tests, as CI does (--skip-checks leaves them out),
# and it refuses a working tree with uncommitted changes, so a build always matches a commit
# (--allow-dirty builds one anyway and says so in BUILD-INFO.txt). After building it checks
# that both files are signed, that the signing key is not the Android debug key, and that the
# APK is not debuggable.
#
# Exit:   0 = built and verified, 1 = a precondition or check failed, 2 = bad usage.
#
# docs/internal-testing.md explains the keystore, the version bump and each distribution route.
# Portable to bash 3.2 (macOS default), like the other scripts here.

set -euo pipefail

run_checks=true
allow_dirty=false
for arg in "$@"; do
    case "$arg" in
        --skip-checks) run_checks=false ;;
        --allow-dirty) allow_dirty=true ;;
        -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg (see --help)" >&2; exit 2 ;;
    esac
done

fail() {
    echo "build_internal_release: $*" >&2
    exit 1
}

cd "$(dirname "$0")/.."

# -- Preconditions ---------------------------------------------------------------------------

[ -f keystore.properties ] || fail "no keystore.properties. Without it the release build is
unsigned and cannot be distributed. Copy keystore.properties.example and see
docs/internal-testing.md, \"Signing key\"."

prop() {
    # The value of one key in keystore.properties, or empty.
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" keystore.properties | tail -n 1
}
store_file=$(prop storeFile)
key_alias=$(prop keyAlias)
[ -n "$store_file" ] && [ -n "$key_alias" ] || fail "keystore.properties needs storeFile and keyAlias."
# Gradle resolves storeFile against the repository root, so do the same here.
case "$store_file" in
    /*) store_path=$store_file ;;
    *) store_path="$PWD/$store_file" ;;
esac
[ -f "$store_path" ] || fail "the keystore $store_path named in keystore.properties does not exist."

version_name=$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
version_code=$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)$/\1/p' app/build.gradle.kts)
[ -n "$version_name" ] && [ -n "$version_code" ] || fail "could not read versionName and versionCode from app/build.gradle.kts."

commit=$(git rev-parse HEAD)
dirty=no
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    $allow_dirty || fail "the working tree has uncommitted changes, so the build would match no
commit. Commit or stash them, or pass --allow-dirty for a build that is not handed out."
    dirty=yes
fi

# apksigner ships with the Android SDK's build-tools; take the newest.
sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk_dir" ] && [ -f local.properties ]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' local.properties)
fi
[ -n "$sdk_dir" ] && [ -d "$sdk_dir/build-tools" ] || fail "cannot find the Android SDK (set ANDROID_HOME or sdk.dir in local.properties)."
build_tools=$(ls -d "$sdk_dir"/build-tools/* | sort -V | tail -n 1)
apksigner="$build_tools/apksigner"
aapt2="$build_tools/aapt2"
[ -x "$apksigner" ] && [ -x "$aapt2" ] || fail "no apksigner or aapt2 in $build_tools."
command -v jarsigner >/dev/null 2>&1 || fail "jarsigner (part of the JDK) is not on PATH."

echo "Building oochat $version_name ($version_code) from $commit"

# -- Checks and build ------------------------------------------------------------------------

if $run_checks; then
    ./gradlew :app:ktlintCheck :app:testDebugUnitTest
fi
./gradlew :app:bundleRelease :app:assembleRelease

aab=app/build/outputs/bundle/release/app-release.aab
apk=app/build/outputs/apk/release/app-release.apk
[ -f "$aab" ] || fail "no $aab after the build."
# An unsigned release APK is named app-release-unsigned.apk.
[ -f "$apk" ] || fail "no signed $apk after the build; is the signing config in keystore.properties right?"

# -- Verify ----------------------------------------------------------------------------------

certs=$("$apksigner" verify --print-certs "$apk") || fail "the APK's signature does not verify."
cert_digest=$(printf '%s\n' "$certs" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
cert_dn=$(printf '%s\n' "$certs" | sed -n 's/^Signer #1 certificate DN: //p')
case "$cert_dn" in
    *"CN=Android Debug"*) fail "the APK is signed with the Android debug key, not a release key." ;;
esac

# Each tool's output is captured before it is read. Piped straight into `grep -q`, the tool can
# be killed by SIGPIPE once grep has its match, and with pipefail that turns the result around.

# jarsigner exits 0 for an unsigned file too, saying "jar is unsigned", so read what it says.
jar_check=$(jarsigner -verify "$aab" 2>&1) || true
case "$jar_check" in
    *"jar verified."*) ;;
    *) fail "the bundle is unsigned or its signature does not verify." ;;
esac

badging=$("$aapt2" dump badging "$apk" 2>/dev/null) || fail "aapt2 could not read the APK."
case "$badging" in
    *application-debuggable*) fail "the APK is debuggable; a build for testers must not be." ;;
esac

# -- Package ---------------------------------------------------------------------------------

out="dist/$version_name"
base="oochat-$version_name-$version_code"
rm -rf "$out"
mkdir -p "$out"
cp "$aab" "$out/$base.aab"
cp "$apk" "$out/$base.apk"

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$out" && sha256sum "$base.aab" "$base.apk" > SHA256SUMS)
else
    (cd "$out" && shasum -a 256 "$base.aab" "$base.apk" > SHA256SUMS)
fi

cat > "$out/BUILD-INFO.txt" <<EOF
oochat for Android, internal-testing build
version:      $version_name ($version_code)
commit:       $commit
uncommitted:  $dirty
built:        $(date -u +%Y-%m-%dT%H:%M:%SZ)
checks run:   $run_checks
signed by:    $cert_dn
cert SHA-256: $cert_digest
EOF

echo
echo "Ready in $out:"
ls -l "$out" | sed 1d
echo
echo "Signing certificate SHA-256: $cert_digest"
if [ "$dirty" = yes ]; then
    echo "Built with uncommitted changes: for trying out only, not for testers."
fi
