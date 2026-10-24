# EcoRota privacy statement

Effective date: 2026-10-24

EcoRota is designed so that your driving data never leaves your device unless you explicitly export it.

## What the app stores

All of the following is stored in a local database on your phone:

- Trip start and end times, distance, duration, average and maximum speed
- Trip route as a simplified list of coordinates
- Detected driving events and their severity
- Your score breakdown and fuel estimate
- Vehicle name, average fuel consumption, fuel price, and currency
- App settings, including your scoring configuration

The app does not create an account. It does not collect a name, email address, phone number, contacts, or advertising identifier.

## What leaves the device

Nothing by default.

Two features can send data, and both require your action:

1. **Map tiles.** If you enable "Allow map tile downloads" in Settings, the app requests map tiles from OpenStreetMap servers. Those requests include the tile coordinates and standard network metadata such as your IP address. OpenStreetMap's own privacy policy applies to those requests. If you leave the setting off, the app never contacts a tile server, and maps show cached tiles only.
2. **Export.** If you export trips as CSV or GPX, the file is written to the location you choose through Android's document picker. Where it goes after that is your decision. Export is a premium feature and is never automatic.

There is no analytics SDK, crash reporting SDK, advertising SDK, or backend service in this project.

## Permissions

- **Location (fine and coarse).** Used to record your route and to detect when a drive starts and stops. Location is read only while a trip is being recorded or while optional auto-detection is enabled, and always with a visible foreground notification.
- **Background location.** Requested only when you enable auto-detection. It lets the app notice a drive while the app is not open.
- **Foreground service (location).** Keeps recording reliable and shows the ongoing notification.
- **Notifications.** Shows the recording notification and nothing promotional.
- **Internet and network state.** Used only for the optional map tiles described above.

## Retention and deletion

- Trips stay on the device until you delete them.
- You can delete a single trip from its detail screen, or delete all trips in Settings.
- You can configure a retention window in Settings. A background job removes trips older than that window.
- Uninstalling the app deletes the local database.
- Android cloud backup and device transfer are disabled for this app, so trip data is not copied to Google Drive or to a new device automatically.

## Children

The app is not directed at children and does not knowingly collect data from anyone, because it does not collect data at all.

## Changes

Material changes to this statement will appear in the repository and in the app's release notes. The app remains offline by default; a future optional cloud backup feature would require explicit opt-in, a separate privacy review, and an update to this document.

## Contact

Open an issue in the project repository for privacy questions. Do not include personal location data in a public issue.
