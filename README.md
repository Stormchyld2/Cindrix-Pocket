# Cindrix-Pocket

Offline emergency cards, checklists and books on your phone. Works with no server and no network.

| | |
|---|---|
| Android app ID | `com.cindrix.pocket` |
| Platform | Android first |
| Status | Planning. No app code yet. |

## What it is

The Cindrix Ops Hub is a server, and in a bad day it may not be in reach. Pocket keeps a copy of what matters on the phone itself:

- emergency cards (first aid, water treatment, navigation and the rest)
- the full emergency contact card for the family
- checklists
- the books and manuals you marked for download (PDF, Markdown and text, the same formats as the Windows version)

Once filled, it needs no network at all.

## How it gets its content

- **From the Hub:** scan the Hub's one-use invite QR, then pull the bundle over the Hub's Wi-Fi.
- **From another phone:** receive it over Cindrix AirLink.
- **From the drive:** copy the files in by hand.

The content is described by a bundle manifest. Pocket downloads only what changed.

## Planned for the first version

- Browse and search cards, checklists and books offline
- Mark items as favourites for quick access
- Fill and refresh from the Hub QR or AirLink
- Dark charcoal screens with blue accents, like the rest of the line

## How it fits in the Cindrix line

Pocket reads files that Cindrix Ops publishes. It never writes to the drive. The bundle manifest format lives in [Cindrix-Formats](https://github.com/Stormchyld2/Cindrix-Formats).

Design notes are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Not chosen yet.


## What is built (field tools, version 0.1.0)

Pocket's first build is the **field view tools**: the parts that work with no Hub in reach. It is **not** the whole Pocket yet (no cards, Academy, reader, maps or Hub link; see docs/ARCHITECTURE.md).

- **Scanner** and **new item from a scan**: the Cindrix Scan code (CameraX and ZXing, all decoding on the phone). Scan a barcode; a barcode it has not met asks for a name, quantity, unit and use-by month and becomes a new line; a known one offers ADD STOCK or TAKE OUT. Beep and vibrate on a read. Add by hand any time. A stock list from the Hub (IN STOCK) shows what is on hand.
- **GPS tracking**: TRACK records where you walk with the phone's own GPS (distance, time, climb and drop, height), keeps running with the screen off (a notice stays in the drawer), and saves every fix as it comes so a dead battery loses nothing. SAVE TO THE OUTBOX makes a track sheet.
- **Outbox**: files wait on the phone until they reach the Hub. Inventory lists go to `inbox/inventory/`, track sheets to `inbox/pocket/`, inside the Cindrix folder (`Documents/Cindrix` by default, or the folder chosen in Settings). AirLink can carry a file instead. The Hub keeps nothing until the person accepts it there.
- Pocket never writes anything but logs: lists and tracks. Nothing is changed on the Hub without being accepted.

Not done yet: joining the Hub by the invite QR (so the phone's code is the Hub's device ID, not a name made from the callsign), count sheets and check-in sheets, the evacuation and response cards, maps, and the "hub view". The APK is built by GitHub Actions (`CindrixPocket-<version>-android.apk`). Nothing here has been run on a real phone by Claude: it is compiled and unit-tested in CI only.
