# PSI Home & Office (iPhone and iPad)

This folder holds the iPhone and iPad app's source code, written in SwiftUI with a WidgetKit widget. The [main README](../README.md) explains what the app does, how to use it on iPhone and iPad, and how to install it.

Every push to `psi-ios/` builds the app on a GitHub Mac runner. The build then takes screenshots on an iPhone simulator and an iPad simulator, and attaches them to the **PSI iPhone app (preview)** release. The repo is private, so these Mac builds use GitHub Actions minutes, which count 10 times faster than Linux builds.
