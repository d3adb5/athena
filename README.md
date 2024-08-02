# Moonlight Android Noir

An open source client for [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight Noir is currently the best fork with loads of optimizations for office usage.

# Features

Mostly based on https://github.com/Axixi2233/moonlight-android

If you switch back to the main stream version, you'll be missing the following awesome features which are very unlikely to be added there:

1. Custom virtual buttons with import and export support.
2. [Custom resolutions](https://github.com/moonlight-stream/moonlight-android/pull/1349).
3. Custom bitrates.
4. [Multiple mouse mode switching](https://github.com/moonlight-stream/moonlight-android/pull/1304) (normal mouse, [multi-touch](https://github.com/moonlight-stream/moonlight-android/pull/1364), touchpad, disabled, local cursor mode).
5. Optimized virtual gamepad skins and free joystick.
6. External monitor mode.
7. Joycon D-pad support.
8. Simplified performance information display.
9. [Game back menu](https://github.com/moonlight-stream/moonlight-android/pull/1171).
10. Custom shortcut commands.
11. Easy soft keyboard switching.
12. Portrait mode.
13. Display on top mode, useful for foldable phones.
14. [Virtual touchpad space and sensitivity adjustment](https://github.com/moonlight-stream/moonlight-android/issues/1348#issuecomment-2236344729) for playing right-click view games, such as Warcraft.
15. Force use device's own vibration motor (in case your gamepad's vibration is not effective).
16. Gamepad debugging page to view gamepad vibration and gyroscope information, as well as Android kernel version information.
17. Trackpad tap/scrolling support
18. Natural track pad mode with touch screen
19. Non-QWERTY keyboard layout support
20. Quick Meta key with physicl BACK button
21. Frame rate lock fix for some devices
22. Video scale mode: Fit/Fill/Stretch
23. View pan/zoom support
24. Rotate screen in-game
25. Add option to quit app directly
26. Samsung DeX scrolling support
27. Proper click/scroll/right-click for trackpad on generic Android tablet when using local cursor

Also checkout https://github.com/ClassicOldSong/Widecar if you want a seamless experience like Apple Sidecar for your Windows PC with Sunshine and Moonlight.

# Disclaimer

This is the `go away` version of Moonlight Android.

I got kicked from Moonlight and Sunshine's Discord server literally for helping people out.

![image](https://github.com/user-attachments/assets/f01fc57f-5199-4495-9b96-68cfa017b7ff)

This is what I got for finding a bug, opened an issue, getting no response, troubleshoot myself, fixed the issue myself, shared it by PR to the main repo hoping my efforts can help someone else during the maintainance gap.

Yes, I'm going away. Fixes and improvements on this fork are not necessarily be merged to the main repo either. I have also started [a fork of Sunshine called Apollo](https://github.com/ClassicOldSong/Apollo) and will add useful features that will never get merged by the main repo shortly. [Apollo](https://github.com/ClassicOldSong/Apollo) and [Moonlight Noir](https://github.com/ClassicOldSong/moonlight-android) will no longer be compatible with OG Sunshine and OG Moonlight eventually, but they'll work even better with much more carefully designed features.

The main repo had stayed silent for 5 months, with nobody actually responding to issues, and people are getting totally no help besides the limited FAQ in their Discord server. I tried to answer issues and questions, solve problems within my ablilty but I got kicked out just for helping others. The funniest thing is, the repo starts updating after they got me banned!

**PRs for feature improvements are welcomed here unlike the main repo, your ideas are more likely to be appreciated and your efforts are actually being respected. We welcome people who can and willing to share their efforts, helping yourselves and other people in need.**

## Downloads
* [APK](https://github.com/ClassicOldSong/moonlight-android/releases)

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
