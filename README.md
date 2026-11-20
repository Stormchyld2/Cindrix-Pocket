# Cindrix-Pocket

Offline emergency cards, checklists and books on your phone. Works with no server and no network.

| | |
|---|---|
| Android app ID | `com.cindrix.pocket` |
| Platform | Android first |
| Status | Built on branch `field-tools` (version 0.2.0): scanner, stock list, GPS tracks, outbox, emergency cards, Academy with quizzes, and joining the Hub by invite QR. Built by CI, not yet run on a real phone. |

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


## Skills and season (added 2026-11-15)

The **SKILLS** button (screen title "Skills and season") carries two things from Cindrix Ops (docs/51 there) and needs no Hub or network:

- **In season now:** the wild foods and game in season this month for the central Appalachians (West Virginia and Tennessee), from `forage-seasons.csv`. Pick all, West Virginia or Tennessee. Always check the state's seasons and licences, and be certain of every plant.
- **My skills:** the skills list (`skills.csv`), grouped by area. Tap a skill to mark that you can really do it; a skill backed by an Academy course fills in when every lesson is done. The ticks stay on the phone. They are not sent to the Hub: the Hub's grid counts a skill only when someone signs it off there (sending the ticks as a log is a later step).

`scripts/sync-content.sh` now also carries `skills.csv` and `forage-seasons.csv`, and the cards and Academy were refreshed from Ops (five new courses, the reworked cards, the wild-food and trades cards). Compiled and unit-tested in CI only (`SkillsTest`).

## Plans (added 2026-11-15)

The **PLANS** button opens the plans carried from Cindrix Ops (`content/plans`, 43 files: the long emergency, the remains and water protection plan, keep-it-from-failing plans, and the rest), grouped by kind. Needs no Hub or network.

- Open a plan to read its steps. Tap a step to tick it; tap again to untick. Steps that repeat (`every:`) come back after that many days. A step tied to an Academy lesson ticks itself when the lesson is done.
- Buttons that open a screen in the Ops program are shown as a plain note ("In the Ops program: ...").
- Ticks stay on the phone (`planTicks` in `Store`); like the skill ticks they are not sent to the Hub yet.
- `scripts/sync-content.sh` now also copies `content/plans/*.md` (not the `_` templates). Compiled and unit-tested in CI only (`PlansTest`).

## Intel

The INTEL button on the home screen opens the intel-gathering part of Cindrix Overwatch: world map, Intel dashboard (top 10 for early warning, channels, troop movements), feed, brief, DEFCON readings and your own feeds. The code is ported from the Overwatch Android app and lives in `app/src/main/java/com/cindrix/pocket/intel`.

- Field view stays quiet: Intel makes no network requests until you tap Refresh. Saved history (30 days) is readable with no internet.
- Not carried over from Overwatch: Watchtower cameras, the lock, the VPN and the AI helper.
- The Hub's erase order also removes the saved Intel history and settings.
