# Swoo Changer

An unofficial Android app that replaces cached bank card artwork in Swoo Pay on rooted devices. It includes previews, a system photo picker, local photo history, and undo for the most recent replacement.

## Requirements

- Android 12 or newer
- Root access through a root manager that provides `su`
- A Swoo Pay installation with artwork already cached

Open the card in Swoo Pay first. In Swoo Changer, scan for artwork, select the matching preview, and pick a replacement photo. Swoo Pay may replace the customized image when it refreshes its cache.

## Build on Windows

Install JDK 17, Android SDK Platform 34 and Android SDK Build-Tools 34.0.0. Run:

```powershell
./build.ps1 -SdkRoot 'C:/path/to/Android/Sdk' -JdkRoot 'C:/path/to/jdk-17'
```

The output is `build/swoo-changer-unsigned.apk`. Sign it with your own Android signing key before installation. Updating an installed APK requires the same signing key as the existing installation.
