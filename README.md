# Moonlight Android Nior

Based on https://github.com/Axixi2233/moonlight-android

- This project implements modifications to the official Moonlight Android app.
- If you particularly like certain features, you can extract the relevant code and submit a merge request to the official repository without mentioning that it comes from this project.
- Finally, do not use this project's code in commercial software.
- If you like digital gadgets and games, you can follow my Bilibili account (https://space.bilibili.com/16893379).

This project version mainly implements the following features:
1. Custom virtual buttons with import and export support.
2. Custom resolutions.
3. Custom bitrates.
4. Multiple mouse mode switching (normal mouse, multi-touch, touchpad, disable touch operations, local mouse mode).
5. Optimized virtual gamepad skins and free joystick.
6. External monitor mode.
7. Joycon D-pad support.
8. Simplified performance information display.
9. Game shortcut menu options.
10. Custom shortcut commands.
11. Easy soft keyboard switching.
12. Portrait mode.
13. SBS entertainment mode.
14. Topmost display for the screen, useful for foldable screens.
15. Virtual touchpad space and sensitivity adjustment for playing right-click view games, such as Warcraft.
16. Force the use of the device's own vibration motor (in case your gamepad's vibration is not effective).
17. Gamepad debugging page to view gamepad vibration and gyroscope information, as well as Android kernel version information.
18. Trackpad tap/scrolling support

# Moonlight Android

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
