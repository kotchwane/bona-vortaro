Bona vortaro
============

Bona vortaro is an Android dictionary of Esperanto. It contains the
whole Reta Vortaro (ReVo), the open source dictionary of Esperanto,
with its translations into many languages, so it works without
internet access.

It is a modified version of PReVo, by Neil Roberts:

 http://www.busydoingnothing.co.uk/prevo/

with a new interface (Jetpack Compose), a new design, new features and
up-to-date dictionary data. Like PReVo, it is free software under the
GNU General Public License, version 2.

Building
--------

This git repo does not include the assets containing the dictionary
data. Instead they are built from the ReVo data and the prevodb
program which are in git submodules. In order to get these modules, be
sure to run the following git command:

```bash
git submodule update --init
```

The prevodb program will be built as part of the app build in order to
generate the dictionary data, so you need to make sure you have a
compiler for the host machine installed. It will also need the
developer packages for expat and glib.

Assuming you have the Android SDK installed correctly, you can build
the app either with Android Studio or the command line as follows.

Debug mode:

    ./gradlew assembleDebug

Release mode (the APK must be signed with your own key, eg. from
Android Studio: Build › Generate Signed App Bundle or APK):

    ./gradlew assembleRelease

You should then have the final package in either
`app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`
depending on the build type.

History
-------

The releases of PReVo, up to 0.27, are tagged and signed in this git
repo with the key of Neil Roberts:

 http://www.busydoingnothing.co.uk/neilroberts.asc

Bona vortaro starts again from version 0.1.
