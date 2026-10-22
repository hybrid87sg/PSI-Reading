# PSI Home & Office (Android)

A simple Android app that shows Singapore's **24-hr PSI** for two places: your home and your office.

- Data: NEA readings published on data.gov.sg (`psi_twenty_four_hourly`). NEA reports this for 5 regions: North, South, East, West, Central.
- Pick the region for **Home** and **Office** with the dropdown on each card. The app remembers your choice.
- Each card shows the reading with a colour band: Good (0–50), Moderate (51–100), Unhealthy (101–200), Very unhealthy (201–300), Hazardous (300+).
- The data refreshes each time you open the app, or when you tap **Refresh**.

## Home-screen widget

Long-press your home screen, choose **Widgets**, and drag **PSI Home & Office** onto it.
It shows the Home and Office readings side by side, coloured by band, with the time of the last NEA update.
It refreshes about once an hour and whenever you open the app. Tap it to open the app.
To change which regions it shows, change the dropdowns in the app.

## Getting the APK

The GitHub Actions workflow `Build PSI app APK` builds a debug APK on every push to `psi-app/`.
Open the workflow run in the repo's **Actions** tab and download the `psi-app-apk` artifact.
Unzip it, copy `app-debug.apk` to your phone and open it. You need to allow "install unknown apps" for this.

You can also open the `psi-app` folder in Android Studio (it will offer to set up Gradle) and click Run.
