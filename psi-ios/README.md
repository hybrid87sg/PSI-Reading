# PSI Home & Office (iPhone)

The iPhone and iPad version of PSI Home & Office, written in SwiftUI. It works like the Android app:

- **Home and Office cards** with a ring gauge, the PSI band and a short health tip, on dark turquoise cards.
- **The background changes colour** with the air quality: green, blue, orange, red or maroon, following the worse of your two places.
- **Across Singapore** shows all five regions, with your home and office outlined.
- **First launch asks where your home and office are.** Each region lists a few towns so you can tell which one you're in. Change them later with the location button at the top, or by tapping the region on a card.
- **Hold and drag a card** to rearrange the cards. The app remembers the order.
- **On iPad, or an iPhone turned sideways,** the cards sit two per row.
- **A home-screen widget** (medium size) shows Home and Office side by side. To choose its regions, long-press the widget and tap **Edit Widget**.

It needs iOS 17 or newer.

## Getting it onto your iPhone

Apple only lets apps onto an iPhone if they're signed by an Apple developer account, so there's no APK-style download link like on Android. You have two options:

**Option 1: free, with a Mac.** You need a Mac with Xcode (free from the Mac App Store) and your Apple ID.

1. On the Mac, install [Homebrew](https://brew.sh), then run `brew install xcodegen`.
2. Download this branch, open Terminal in the `psi-ios` folder and run `xcodegen generate`.
3. Open `PSI.xcodeproj` in Xcode.
4. For both the **PSI** and **PSIWidget** targets, open **Signing & Capabilities** and pick your Apple ID under **Team**. If Xcode says the bundle ID is taken, change `sg.psi.app` to something unique, such as `sg.yourname.psi`, and the widget's ID to match (`sg.yourname.psi.widget`).
5. Plug in your iPhone, select it at the top of Xcode and press **Run**.
6. The first time, on the iPhone go to **Settings → General → VPN & Device Management**, and trust your Apple ID.

With a free Apple ID, the app stops opening after 7 days. Press **Run** in Xcode again to renew it.

**Option 2: TestFlight, with a paid Apple Developer account** (US$99 a year). Builds can then go to your iPhone through Apple's TestFlight app, with no Mac needed after setup and no 7-day limit. Setting this up needs signing certificates from your developer account, added to the repo as GitHub secrets.

## How it's checked

Every push to `psi-ios/` builds the app on a GitHub Mac runner. The build then takes screenshots on an iPhone simulator and an iPad simulator, and attaches them to the **PSI iPhone app (preview)** release. The repo is private, so these Mac builds use GitHub Actions minutes, which count 10 times faster than Linux builds.
