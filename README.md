# Love Drive (Android Auto Photo Carousel)

Love Drive runs as an Android Auto **Media** service (`MediaBrowserServiceCompat`), so it stays active in **non-parking mode** and appears directly in **Android Auto Coolwalk split-screen alongside Google Maps** (or full-screen whenever you tap Maps).

## Build APK via GitHub Actions (No Android Studio Needed)

1. **Create a private GitHub repository** (e.g. `love-drive`).
2. **Extract this zip** and upload/push all files inside `LoveDrive/` (including the hidden `.github/workflows/build-apk.yml` folder) to the `main` branch.
3. **Download your APK**:
   - Open your repository on GitHub → **Actions** tab → click the **Build LoveDrive APK** run.
   - Once it finishes (~2 minutes), scroll down to **Artifacts** and download **LoveDrive-debug-apk** (which contains `app-debug.apk`).
4. **Install on your phone**:
   - Unzip `LoveDrive-debug-apk.zip` on your phone and tap `app-debug.apk` to install.

## Android Auto Setup

1. **Add your photos**:
   - Open **Love Drive** on your phone, tap **Add Photos**, and select photos of you and your wife.
   - Set your subtitle and slide interval (5–30 seconds).
2. **Enable Android Auto Unknown Sources** (required for sideloaded apps):
   - On your phone: **Settings → Apps → Android Auto** (or search *Android Auto* in Settings).
   - Scroll to the bottom and tap **Version** 10 times until Developer Mode unlocks.
   - Tap the **⋮** menu (top right) → **Developer settings** → check **Unknown sources**.
3. **Connect to your car**:
   - Open **Google Maps** and **Love Drive** on Android Auto.
   - In Coolwalk split-screen, Google Maps stays on one side while Love Drive's photo carousel cycles on the media card—and you can skip photos with your steering-wheel Next/Previous buttons.
