# PSI Home & Office for iPhone and iPad

An iPhone and iPad app that shows Singapore's **24-hour PSI** for the places you care about: your **home**, your **office**, and up to ten more of your own, like the gym or your parents' place. It also has a home-screen widget, so you can check the air without opening anything.

<img src="https://github.com/hybrid87sg/PSI-Reading/releases/download/psi-ios-preview/psi-ios-iphone.png" alt="PSI Home & Office on an iPhone, showing Home and Office gauges" width="300">

> This branch (`ios-app`) holds the iPhone and iPad version. The Android version is on the `main` branch.

---

## The idea

During haze season, you usually want to know two things: is the air OK where I live, and is it OK where I work? The NEA website and the general weather apps show the whole island, or one location at a time. You have to look up the right region and remember what the numbers mean.

This app answers those two questions at a glance:

- **A card for each place**, starting with Home and Office. Each has a ring gauge showing the current 24-hr PSI for that part of Singapore, and a one-line health tip, on a dark turquoise card.
- **Your own places:** add up to 12 locations, each with its own name and icon.
- **Everything fits on one screen:** the cards resize to fit how many places you have.
- **The whole screen changes colour** with the air: fresh green on a good day, blue when it's moderate, and warming to orange, red and maroon as the haze gets worse. The colour follows whichever of your places is worst.
- **A home-screen widget** with both readings, in the same colours.

It's deliberately small. There are no accounts, no ads and no tracking. The app only goes online to fetch the PSI data.

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

## Using the app on iPhone

1. The first time you open the app, a panel slides up asking **Where is your home?** Tap your region. Each region lists a few towns, so you can tell which one you're in. Then do the same for **Where is your office?**
2. The app fetches the latest readings, and the gauges fill up to the current values.
3. That's it. The app remembers your choices.

**Adding a place:** tap the **＋** button at the top, then **Add a location**. Give it a name (for example "Gym" or "Mum's place"), pick an icon, choose its region, and tap **Add**. You can have up to 12 places.

**Editing or deleting a place:** tap its card, or tap **＋** and pick it from the list. You can change the name, icon and region, or tap **Delete location**. You always keep at least one place.

**Finding the region for you:** you don't need to know which region a place is in.

- **By postal code or address:** in the add or edit screen, type a postal code (for example 521123) or an address (for example "Tampines Ave 5") and tap **Find**. The app shows what it found, for example "Tampines Street 11 → East", and selects that region. Check it, then tap **Add** or **Save**.
- **From where you are now:** tap the location button (the arrow) on any card, or **Use my current location** in the edit screen. The first time, iOS asks for permission to use your location. The app only needs your rough location, and only to work out the region.

Regions are approximate. NEA doesn't publish exact borders, so the app picks the region whose centre is closest. A place near a border could fit either way, and you can always change it. Address search uses Apple's built-in maps lookup. If that finds nothing, a 6-digit postal code still works offline from its postal district.

**Changing just the region:** tap the region on a card, for example **East ⌄**.

**How the cards fit:** the app tries to show all your places on one screen. With one or two places you get big cards with a health tip. As you add more, the cards get smaller and sit two per row (three on iPad). Place names can wrap onto a second line, so they aren't cut off.

- **Up to 8 places:** the cards never get smaller than that two-per-row size. If they don't all fit, for example 7 or 8 places on a smaller iPhone, the screen scrolls a little.
- **9 to 12 places:** the cards switch to mini chips, with a small gauge and the band and name beside it, so they still fit on one screen.

**Refreshing:** tap the round refresh button at the top. The app also refreshes by itself each time you come back to it.

**Rearranging the cards:** touch and hold any card (including Across Singapore) until it lifts, then drag it over another card and let go. The app remembers the order.

**Turning your iPhone sideways:** more cards fit on each row. Turn it back upright to return to the usual layout.

**Across Singapore** shows all five regions as coloured circles. Regions with one of your places are outlined and show that place's icon.

## Using the app on iPad

Everything works the same as on iPhone, with a layout made for the bigger screen:

- **More cards per row:** two big cards per row, or three smaller ones when you have more places, in both portrait and landscape. Across Singapore always gets a full row. The cards stop growing on very large screens, so they don't stretch across the whole display.
- **Split View, Slide Over and Stage Manager:** if you make the app's window narrow, it switches to the iPhone layout. Widen it again for more per row.
- **Rearranging with a trackpad or mouse:** click and hold a card, then drag it. With your finger, touch and hold, then drag.
- **Region choices** open in a panel in the middle of the screen instead of sliding up from the bottom.

## The home-screen widget

**Adding it on iPhone:**

1. Touch and hold an empty spot on your Home Screen until the apps jiggle, then tap **Edit** (top-left) → **Add Widget**.
2. Search for **PSI**, pick **PSI Home & Office**, and tap **Add Widget**.
3. Tap **Done**.

**Adding it on iPad:** the same steps work. You can also put the widget in the **Today View** (swipe right on the first Home Screen page).

**Choosing the widget's regions:** touch and hold the widget, tap **Edit Widget**, then choose the regions for its two slots (labelled **Home** and **Office**). The widget keeps its own regions; it doesn't use the custom names and icons from the app.

The widget shows Home and Office side by side, on a background coloured by the worse of the two, plus the time of NEA's latest update. It refreshes about once an hour (iOS decides the exact timing to save battery), and whenever you open the app. If you're offline, it keeps showing the last reading it got. Tap it to open the app.

The widget comes in the medium size (it takes the space of 4 app icons across and 2 down).

## Installing it

Apple only lets apps onto an iPhone or iPad if they're signed with an Apple account, so there isn't a direct download link like the Android version has. There are two ways in:

**Option 1: free, with a Mac.** You need a Mac with Xcode (free from the Mac App Store), your Apple ID, and a USB cable for your iPhone or iPad.

1. On the Mac, install [Homebrew](https://brew.sh), then run `brew install xcodegen` in Terminal.
2. Download this branch (`ios-app`). In Terminal, go to the `psi-ios` folder and run `xcodegen generate`.
3. Open `PSI.xcodeproj` in Xcode.
4. For both the **PSI** and **PSIWidget** targets, open **Signing & Capabilities** and pick your Apple ID under **Team**. If Xcode says the bundle ID is taken, change `sg.psi.app` to something unique, such as `sg.yourname.psi`, and the widget's ID to match (`sg.yourname.psi.widget`).
5. Connect your iPhone or iPad, choose it at the top of the Xcode window and press **Run** (▶).
6. The first time, Xcode may ask you to turn on **Developer Mode**. On the iPhone or iPad, go to **Settings → Privacy & Security → Developer Mode**, turn it on and restart.
7. Also the first time, go to **Settings → General → VPN & Device Management**, tap your Apple ID and tap **Trust**.

With a free Apple ID, the app stops opening after 7 days. Connect the device and press **Run** in Xcode again to renew it. Your regions and card order are kept.

**Option 2: TestFlight, with a paid Apple Developer account** (US$99 a year). New versions arrive on your iPhone and iPad through Apple's free TestFlight app, like an App Store update. You don't need a Mac after the one-time setup, and there's no 7-day limit. Setting this up needs signing certificates from your developer account, added to this repo as GitHub secrets.

It needs **iOS 17 or iPadOS 17** or newer.

## Ideas for later

- Pick your region automatically from a postal code or your phone's location.
- Send a notification when PSI at home or the office crosses into Unhealthy.
- Show a small chart of the last 24 hours.
- Add small and large widget sizes, and a Lock Screen widget.
