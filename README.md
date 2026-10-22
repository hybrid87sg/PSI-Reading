# PSI Home & Office

An Android app that shows Singapore's **24-hour PSI** for the two places you care about most: your **home** and your **office**. It also has a home-screen widget, so you can check the air without opening anything.

**Download:** [psi-app.apk](https://github.com/hybrid87sg/Claude-projects/releases/download/psi-app-latest/psi-app.apk) (latest build)

---

## The idea

During haze season, you usually want to know two things: is the air OK where I live, and is it OK where I work? The NEA website and the general weather apps show the whole island, or one location at a time. You have to look up the right region and remember what the numbers mean.

This app answers those two questions at a glance:

- **Two cards, Home and Office**, each showing the current 24-hr PSI for that part of Singapore.
- **Colour-coded bands**, so you can tell right away whether it's fine or whether you should limit time outdoors.
- **A home-screen widget** with both readings, which keeps itself up to date.

It's deliberately small. There are no accounts, no ads and no tracking. The app only uses its internet permission to fetch the PSI data.

## Where the data comes from

The readings are the official ones from the **National Environment Agency (NEA)**, published on [data.gov.sg](https://data.gov.sg). NEA updates them every hour.

NEA reports PSI for **five regions**: North, South, East, West and Central. It doesn't publish readings for specific addresses, so the app doesn't either. You pick the region your home and office are in.

The app shows the **24-hour PSI**. That's the figure NEA uses for its health advisories, because it reflects your exposure over a whole day rather than a momentary spike.

## What the colours mean

| 24-hr PSI | Band | Colour |
|---|---|---|
| 0–50 | Good | Green |
| 51–100 | Moderate | Blue |
| 101–200 | Unhealthy | Amber |
| 201–300 | Very unhealthy | Orange-red |
| Above 300 | Hazardous | Dark red |

For what each band means for outdoor activity, see NEA's health advisory on [haze.gov.sg](https://www.haze.gov.sg).

## Using the app

1. Open the app. It fetches the latest readings straight away.
2. On the **Home** card, use the dropdown to pick your home's region. Do the same on the **Office** card.
3. That's it. The app remembers your choices.

You'll also see a line showing all five regions, and a **Refresh** button for fetching the readings again.

## The home-screen widget

1. Long-press an empty spot on your home screen and tap **Widgets**.
2. Find **PSI Home & Office** and drag it onto your screen.

The widget shows your Home and Office readings side by side, coloured by band, plus the time of NEA's latest update. It:

- refreshes about once an hour (Android decides the exact timing to save battery);
- also refreshes whenever you open the app or change a region;
- keeps showing the last reading it got if your phone is offline;
- opens the app when you tap it.

To change which regions the widget shows, change the dropdowns in the app.

## Installing it

The app isn't on the Play Store, so you install it directly:

1. On your Android phone, download [psi-app.apk](https://github.com/hybrid87sg/Claude-projects/releases/download/psi-app-latest/psi-app.apk). You can also find it under **Releases → PSI app (latest)** on this repo's page.
2. Open the downloaded file. Android will ask you to allow your browser to install apps. Allow it, then tap **Install**.
3. To update later, download from the same link and install over the old version. Your settings are kept.

It needs Android 7.0 or newer.

## How it's built

The app is intentionally simple: plain Java with Android's built-in libraries only, and no third-party dependencies.

| File | What it does |
|---|---|
| `psi-app/app/src/main/java/sg/psi/app/PsiClient.java` | Fetches and parses the NEA readings, caches the last good one, and holds the region names and colour bands. |
| `psi-app/app/src/main/java/sg/psi/app/MainActivity.java` | The main screen: the Home and Office cards, the region pickers and Refresh. |
| `psi-app/app/src/main/java/sg/psi/app/PsiWidget.java` | The home-screen widget. |
| `psi-app/app/src/main/res/` | Layouts, colours and the widget configuration. |
| `.github/workflows/psi-app.yml` | Builds the APK and publishes it to the release. |

**Automatic builds:** every change pushed to `psi-app/` triggers GitHub Actions to build the APK and replace the file in the **PSI app (latest)** release, so the download link always gives you the newest version.

**Building it yourself:** open the `psi-app` folder in Android Studio and click **Run**.

## Ideas for later

- Pick your region automatically from a postal code or your phone's location.
- Send a notification when PSI at home or the office crosses into Unhealthy.
- Show a small chart of the last 24 hours.
