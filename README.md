# PSI Home & Office

An Android app that shows Singapore's **24-hour PSI** for the two places you care about most: your **home** and your **office**. It also has a home-screen widget, so you can check the air without opening anything.

**Download:** [psi-app.apk](https://github.com/hybrid87sg/Claude-projects/releases/download/psi-app-latest/psi-app.apk) (latest build)

<img src="https://github.com/hybrid87sg/Claude-projects/releases/download/psi-app-latest/psi-app-screenshot.png" alt="The PSI Home & Office app showing Home and Office gauges" width="300">

---

## The idea

During haze season, you usually want to know two things: is the air OK where I live, and is it OK where I work? The NEA website and the general weather apps show the whole island, or one location at a time. You have to look up the right region and remember what the numbers mean.

This app answers those two questions at a glance:

- **Two cards, Home and Office**, each with a ring gauge showing the current 24-hr PSI for that part of Singapore, and a one-line health tip, on a dark turquoise card.
- **The whole screen changes colour** with the air: fresh green on a good day, blue when it's moderate, and warming to orange, red and maroon as the haze gets worse. The colour follows whichever of your two places is worse.
- **A home-screen widget** with both readings, in the same colours, which keeps itself up to date.

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
| 101–200 | Unhealthy | Orange |
| 201–300 | Very unhealthy | Red |
| Above 300 | Hazardous | Maroon |

For what each band means for outdoor activity, see NEA's health advisory on [haze.gov.sg](https://www.haze.gov.sg).

## Using the app

1. Open the app. It fetches the latest readings straight away, and the gauges fill up to the current values.
2. On the **Home** card, tap the region chip (for example **East ▾**) and pick your home's region. Do the same on the **Office** card.
3. That's it. The app remembers your choices.

Below the cards, **Across Singapore** shows all five regions as coloured circles, with your home and office regions outlined. The round button at the top right fetches the readings again.

## The home-screen widget

1. Long-press an empty spot on your home screen and tap **Widgets**.
2. Find **PSI Home & Office** and drag it onto your screen.

The widget shows your Home and Office readings side by side on a background coloured by the worse of the two, plus the time of NEA's latest update. It:

- refreshes about once an hour (Android decides the exact timing to save battery);
- also refreshes whenever you open the app or change a region;
- keeps showing the last reading it got if your phone is offline;
- opens the app when you tap it.

To change which regions the widget shows, change the region chips in the app.

## Installing it

The app isn't on the Play Store, so you install it directly:

1. On your Android phone, download [psi-app.apk](https://github.com/hybrid87sg/Claude-projects/releases/download/psi-app-latest/psi-app.apk). You can also find it under **Releases → PSI app (latest)** on this repo's page.
2. Open the downloaded file. Android will ask you to allow your browser to install apps. Allow it, then tap **Install**.
3. To update later, download from the same link and install over the old version. Your settings are kept.

It needs Android 7.0 or newer.

## Ideas for later

- Pick your region automatically from a postal code or your phone's location.
- Send a notification when PSI at home or the office crosses into Unhealthy.
- Show a small chart of the last 24 hours.
