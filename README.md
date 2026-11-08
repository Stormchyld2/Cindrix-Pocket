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
