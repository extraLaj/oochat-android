# Internal-testing builds

How to make the build that testers install on their phones and tablets, and
the three ways to get it to them (W16AAPPLE-51). Which route to use is the
client's choice; the build is the same for all three.

This is not a Play Store production release. That also needs a store listing,
a privacy policy and a Data safety form, which are outside this guide.

## What a build is

`scripts/build_internal_release.sh` builds the **release** variant from the
current commit: minified and shrunk by R8, not debuggable, and signed with the
key named in `keystore.properties`. It writes to `dist/<versionName>/`
(git-ignored):

| File | For |
| --- | --- |
| `oochat-<versionName>-<versionCode>.aab` | Google Play internal testing |
| `oochat-<versionName>-<versionCode>.apk` | Firebase App Distribution, or installing directly |
| `SHA256SUMS` | Checking that a downloaded file is intact |
| `BUILD-INFO.txt` | The version, the commit, and the signing certificate's SHA-256 |

Before building, it runs ktlint and the unit tests, as CI does. It refuses a
working tree with uncommitted changes, so every build matches a commit. After
building, it checks that:

- both files are signed;
- the key is not the Android debug key;
- the APK is not debuggable.

The GitHub release workflow ([RELEASE.md](RELEASE.md)) is a different thing:
it publishes a debug-signed APK that anyone can download. GitLab CI keeps no
APK at all, because the CSE GitLab instance refuses artifacts that size (see
`assemble-debug` in `.gitlab-ci.yml`), so internal-testing builds are made on
a developer's machine with this script.

## Signing key

**The client should create and keep the key.** Every update to an installed
copy must be signed with the same key as the first copy. If a build is
signed with a different key, Android refuses to install it over the old one.
Testers then have to uninstall first, which deletes the app's identity:
anyone who has not written down their recovery phrase (Settings, Account; see
USER_GUIDE.md section 10) loses that identity for good.

For the same reason, a tester who has the debug APK from GitHub Releases, or a
copy built from Android Studio, installed under the same package name
(`ai.openonion.oochat`) has to uninstall it before installing this build.

To create a key and point the build at it:

```bash
keytool -genkeypair -v -keystore release.jks -alias connectonion -keyalg RSA -keysize 2048 -validity 10000
```

```bash
cp keystore.properties.example keystore.properties
```

Then fill in the passwords in `keystore.properties`. Both files are
git-ignored and must never be committed. Store the keystore and its passwords
somewhere the client controls, such as a password manager or a secrets vault.

With Google Play, this key is the **upload key**: Play App Signing re-signs
the app with a key Google holds, and a lost upload key can be reset through
Play Console. Outside Play (routes 2 and 3 below), this key is the app's only
signing key, and losing it means every tester has to uninstall and reinstall.

## Version

Every build handed out needs a higher `versionCode` than the last one, and
Play refuses an upload that reuses one. Before building, raise both values in
the `defaultConfig` block of `app/build.gradle.kts`:

- `versionCode`, by one;
- `versionName`, as `major.minor.patch`.

Commit the change, so the build matches a commit.

## Build

```bash
./scripts/build_internal_release.sh
```

It takes several minutes, most of it the unit tests and R8. `--skip-checks`
leaves out ktlint and the tests, for example when the same commit has just
passed CI. `--allow-dirty` builds with uncommitted changes, for trying
something out; its `BUILD-INFO.txt` says so, and that build is not for testers.

## Before handing it out

The unit tests run against the debug build. Only the release build is
minified, and R8 can remove or rename code that is only reached by reflection
or serialization, so install the APK on a real phone and a tablet and check
these by hand:

- **Setup:** create an identity, then restore one from a recovery phrase.
- **Agents:** connect to an agent through the relay and directly, and by
  scanning a QR code.
- **Chat:** send a message and see the reply stream in; answer an approval;
  send a photo.
- **Work Rooms:** open a Claude Code or Codex Work Room and type into it.
- **Control Center:** open an agent's Home page or Control Center.
- **Notifications:** with the app in the background, an approval should post
  a notification.
- **Reconnect:** turn on flight mode for a minute, then turn it off; the chat
  should reconnect and catch up.
- **Logs:** Settings, Logs opens and shows entries.

```bash
adb install -r dist/<versionName>/oochat-<versionName>-<versionCode>.apk
```

## Route 1: Google Play internal testing

Suits testers who all have a Google account and use the Play Store.

1. In Play Console, create the app, or open it if it already exists. The
   package name `ai.openonion.oochat` must be registered to the client's own
   developer account.
2. Under **Test and release > Testing > Internal testing**, create a release
   and upload the `.aab`. A new app is enrolled in Play App Signing on its
   first upload.
3. Add testers as an email list (internal testing allows up to 100), and send
   them the opt-in link. They install and update from the Play Store.

## Route 2: Firebase App Distribution

Suits testers without Play access, and gives install links and release notes
without going through Play.

1. In a Firebase project, add an Android app with the package name
   `ai.openonion.oochat`.
2. Under **App Distribution**, upload the `.apk`. An `.aab` can also be
   uploaded, but only once the Firebase project is linked to the app in Play
   Console.
3. Add testers or a tester group. They get an email and install through the
   Firebase App Tester app.

## Route 3: Direct install

For a handful of testers, or a device in the room.

1. Share the `.apk` together with `SHA256SUMS`.
2. Each tester checks the file (`shasum -a 256 -c SHA256SUMS` on macOS,
   `sha256sum -c SHA256SUMS` on Linux).
3. They open the APK on the phone and allow installing from that source when
   Android asks, or install it with `adb install -r` as above.

There are no automatic updates on this route: each new build is shared and
installed the same way.
