# Cindrix-Pocket: Architecture

Status: draft, 2026-11-01. Nothing is built. Items marked **proposal** are suggestions to confirm.

## 1. Goal

Put the useful part of the Cindrix library on a phone so it can be read with no server, no Wi-Fi and no internet. Read-only: Pocket consumes content and never changes the drive.

## 2. Components

```
  Cindrix Ops (publishes bundle)
        |
        |  Hub invite QR + download        or        AirLink transfer
        v                                            v
  +------------------------- Pocket ---------------------------+
  |  Sync        ->   Content store   ->   Index   ->   Reader  |
  |  (manifest,       (files on the        (local       (cards,  |
  |   hashes)          phone)              search)      lists,   |
  |                                                     books)   |
  +---------------------------------------------------------------+
```

- **Sync.** Reads the bundle manifest, compares file hashes with what is already on the phone, and downloads only new or changed files. Can resume if the link drops.
- **Content store.** Plain files in the app's storage, laid out like the bundle. No database is needed to read them.
- **Index.** A small local search index built after each sync. Rebuildable from the files at any time.
- **Reader.** Screens for cards, checklists and books.

## 3. Getting content

1. **Hub invite QR.** The Hub already uses a one-use invite QR to make a family phone a trusted device. Pocket reuses that join step, then downloads the bundle over the Hub's Wi-Fi as plain file downloads (the same mechanism as the download icon on books).
2. **AirLink.** A phone that already has the content can send it to another phone with no Hub in reach. Pocket watches its receive folder and picks the bundle up.
3. **Manual copy.** Files copied into the shared Cindrix folder are picked up on the next refresh.

## 4. Bundle (see Cindrix-Formats)

- A manifest lists each item with a relative path, size, SHA-256 hash and a type (`card`, `checklist`, `book`).
- Ops builds the manifest from the books marked for download and the cards and checklists it holds.
- Pocket treats unknown item types as files it can store but not display.

## 5. Offline rules

- No network permission is needed for reading. The network is used only during a sync the person starts.
- No account, no analytics, no ads, no cloud backup of the content.
- The app must open and read everything after a fresh install plus one sync, even if the Hub is destroyed.

## 6. Storage and size

- Books can be large. The person chooses which marked items to keep on the phone.
- Show free space before a sync and stop cleanly when it runs out.
- Hash-check files after download so a bad copy is never shown as good.

## 7. Open questions

1. ~~Which book formats must the reader open on day one?~~ **Decided (owner, 2026-11-08): the same formats as the Windows version.** Today that is PDF, Markdown (`.md`) and plain text (`.txt`). Pocket keeps no list of its own: when the Windows Shelf gains a format, Pocket gains it.

Also decided (owner, 2026-11-08): **the phone carries the full emergency contact card**, not a trimmed one. Ops must put it in the bundle for a trusted phone (the Family page is host-only today). Pocket stores it on the phone only, never in a shared or guest-readable place, and the Hub's erase order wipes it with everything else Cindrix keeps there.
2. Do cards and checklists reach Pocket as the same files Ops displays, or as a lighter export?
3. Does Pocket also receive Ops-written `.cmap` maps, or does that stay with the map viewer?
4. Framework choice. AirLink is built from LocalSend (Flutter). Reusing it would let Pocket share pairing and transfer code.
