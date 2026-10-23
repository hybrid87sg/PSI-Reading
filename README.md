# PSI Home & Office

An Android app that shows Singapore's **24-hour PSI** for the places you care about: your **home**, your **office**, and up to ten more of your own, like the gym or your parents' place. It also has a home-screen widget, so you can check the air without opening anything.

**Download:** [psi-app.apk](https://github.com/hybrid87sg/PSI-Reading/releases/download/psi-app-latest/psi-app.apk) (latest build)

<img src="https://github.com/hybrid87sg/PSI-Reading/releases/download/psi-app-latest/psi-app-screenshot.png" alt="The PSI Home & Office app showing Home and Office gauges" width="300">

---

## The idea

During haze season, you usually want to know two things: is the air OK where I live, and is it OK where I work? The NEA website and the general weather apps show the whole island, or one location at a time. You have to look up the right region and remember what the numbers mean.

This app answers those two questions at a glance:

- **A card for each place**, starting with Home and Office. Each has a ring gauge showing the current 24-hr PSI for that part of Singapore, and a one-line health tip, on a dark turquoise card.
- **Your own places:** add up to 12 locations, each with its own name and icon.
- **Everything fits on one screen:** the cards resize to fit how many places you have.
- **The whole screen changes colour** with the air: fresh green on a good day, blue when it's moderate, and warming to orange, red and maroon as the haze gets worse. The colour follows whichever of your places is worst.
- **A home-screen widget** with your first two places, in the same colours, which keeps itself up to date.

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

1. The first time you open the app, it asks **Where is your home?** and then **Where is your office?** Each region in the list names a few towns in it, so you can tell which one you're in.
2. The app fetches the latest readings, and the gauges fill up to the current values.
3. That's it. The app remembers your choices.

**Adding a place:** tap the **＋** button at the top, then **＋ Add a location**. Give it a name (for example "Gym" or "Mum's place"), pick an icon, choose its region, and tap **Add**. You can have up to 12 places.

**Editing or deleting a place:** tap its card, or tap **＋** and pick it from the list. You can change the name, icon and region, or tap **Delete**. You always keep at least one place.

**Finding the region for you:** you don't need to know which region a place is in.

- **By postal code or address:** in the add or edit screen, type a postal code (for example 521123) or an address (for example "Tampines Ave 5") and tap **Find**. The app shows what it found, for example "123 Tampines Street 11 → East", and selects that region. Check it, then tap **Add** or **Save**.
- **From where you are now:** tap the location button (the target icon) on any card, or **📍 Use my current location** in the edit screen. The first time, Android asks for permission to use your location. The app only asks for your approximate location, and only uses it to work out the region. Location needs to be turned on in your phone's settings.

Regions are approximate. NEA doesn't publish exact borders, so the app picks the region whose centre is closest. A place near a border could fit either way, and you can always change it. Address search uses your phone's built-in maps lookup. If that finds nothing, a 6-digit postal code still works offline from its postal district.

**Changing just the region:** tap the region chip on a card (for example **East ▾**).

**How the cards fit:** the app tries to show all your places on one screen. With one or two places you get big cards with a health tip. As you add more, the cards get smaller and sit two per row. Place names can wrap onto a second line, so they aren't cut off.

- **Up to 8 places:** the cards never get smaller than that two-per-row size. If they don't all fit, for example 7 or 8 places on a smaller phone, the screen scrolls a little.
- **9 to 12 places:** the cards switch to mini chips, with a small gauge and the band and name beside it, so they still fit on one screen.

**Rearranging the cards:** long-press any card (including Across Singapore) and drag it to a new spot. The app remembers the order.

**Across Singapore** shows all five regions as coloured circles. Regions with one of your places are outlined and show that place's icon. The round refresh button fetches the readings again.

**Foldables and wide screens:** on an unfolded foldable, a tablet or a phone turned sideways, more cards fit on each row: two big cards, or three smaller ones when you have more places. Folding it back returns to the phone layout.

## The home-screen widget

1. Long-press an empty spot on your home screen and tap **Widgets**.
2. Find **PSI Home & Office** and drag one of its two widgets onto your screen:
   - **PSI Home & Office**: the regular widget, 3 × 2 cells to start.
   - **PSI (1 row)**: a slim strip, 4 × 1 cells, for a crowded home screen.

The widget shows your places in the same order as your cards, on a background coloured by the worst reading it shows, plus the time of NEA's latest update.

**Resizing it:** long-press the widget, then drag the handles on its edges. It shows as many places as fit:

- **One row tall:** a compact strip, with each place's reading next to its name and band. A 4-cell strip fits about four places.
- **Smallest (2 cells wide):** just your first place.
- **Default (3 × 2):** your first two places side by side.
- **Wider:** up to four places per row.
- **Taller:** up to three rows, so a large widget can show up to 12 places. The numbers get a little smaller when it's crowded.

To change which places appear first, move those cards to the top in the app. It:

- refreshes about once an hour (Android decides the exact timing to save battery);
- also refreshes whenever you open the app or change a region;
- keeps showing the last reading it got if your phone is offline;
- opens the app when you tap it.

## Installing it

The app isn't on the Play Store, so you install it directly:

1. On your Android phone, download [psi-app.apk](https://github.com/hybrid87sg/PSI-Reading/releases/download/psi-app-latest/psi-app.apk). You can also find it under **Releases → PSI app (latest)** on this repo's page.
2. Open the downloaded file. Android will ask you to allow your browser to install apps. Allow it, then tap **Install**.
   - If the app is already installed, Android shows **Update** instead, and your Home and Office choices are kept.
   - If it isn't installed yet, this does a fresh install.
3. To update later, download from the same link again and tap **Update**.

**Installed a version from before 22 October 2026?** Those early builds were signed differently, so Android won't update them. Uninstall the old app once (long-press its icon → **Uninstall**), then install the new one. Every update after that works normally.

It needs Android 7.0 or newer.

## Ideas for later

- Pick your region automatically from a postal code or your phone's location.
- Send a notification when PSI at home or the office crosses into Unhealthy.
- Show a small chart of the last 24 hours.
