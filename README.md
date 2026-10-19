<p align="center">
  <img src="docs/assets/icon-512.png" width="96" alt="Piko launcher icon">
</p>

<h1 align="center">Piko</h1>

<p align="center">
  <b>A minimal, fast, search-first Android launcher.</b><br>
  ~280&nbsp;KB · starts in ~0.3&nbsp;s · runs on Android 1.0 and up · zero dependencies · no ads · no tracking
</p>

<p align="center">
  <a href="https://github.com/mkalmousli/Piko/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/mkalmousli/Piko?color=7C9CFF&label=release"></a>
  <a href="https://github.com/mkalmousli/Piko/actions/workflows/ci.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/mkalmousli/Piko/ci.yml?branch=main"></a>
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/github/license/mkalmousli/Piko?color=7C9CFF"></a>
  <img alt="Android 1.0+" src="https://img.shields.io/badge/Android-1.0%2B-3DDC84">
  <img alt="APK size" src="https://img.shields.io/badge/APK-~280%20KB-7C9CFF">
  <a href="https://al-mo.de/apps/piko/"><img alt="Website" src="https://img.shields.io/badge/website-al-mo.de%2Fapps%2Fpiko-7C9CFF"></a>
</p>

<p align="center">
  <a href="https://github.com/mkalmousli/Piko/releases/latest"><b>⬇ Download the APK</b></a> ·
  <a href="https://al-mo.de/apps/piko/">Website</a> ·
  <a href="PRIVACY.md">Privacy</a> ·
  <a href="https://al-mo.de">Author</a>
</p>

<p align="center">
  <img src="docs/assets/collage.png" alt="Piko launcher screenshots on a Moto G7 Play: home with gradient background, contact actions, calculator, web search, background settings and page overview">
</p>

Piko opens on a search bar. Type a few letters and launch an app, call a contact, do the maths,
convert a currency or hand the query to your favourite search engine or AI chat. Swipe once to
reach a clean, configurable icon grid. That's it.

## Why Piko

- **Runs on everything.** One APK supports Android 1.0 (API 1) through current releases. Features that need a newer Android (widgets, shortcuts pinned by other apps) switch off on older versions instead of breaking the launcher.
- **Tiny.** The release APK is about 280 KB, with no libraries inside: no AndroidX, no Material, nothing to update or audit.
- **Fast.** Cold start takes about 0.3 s on a Moto G7 Play, an entry-level 2019 phone, and the app uses about 37 MB of RAM. Search runs against an in-memory index.

Measured on a Moto G7 Play (Android 10) with the release build. Versions before Android 10 are covered by API-level lint rather than real-device testing.

## Features

**Search-first home**
- Opens straight into search; remembers which result you pick for a query and ranks launch frequency, so results improve with use.
- Multi-word matching with highlighted matches; search by custom **tags** (e.g. `photos` finds your camera).
- **Hidden apps**: keep apps out of results without uninstalling them; the picker has a live filter.
- **Icon packs**: choose any installed ADW/Nova/Apex icon pack.
- **Permissions screen** that explains each permission and lets you grant it. Nothing is required.
- **Phone numbers**: typing digits matches contacts by number and offers call/message for numbers not in your address book.

**Instant answers**
- Scientific calculator: `2(3+4)^2`, `sqrt(16)+3!`, `sin(30deg)`, `50% of 200`, `10 mod 3`, `log2(8)`, constants `pi`/`e`.
- **Calculator** inline: `12*8+4` → `100`; any number you type gets a calculator row.
- **Currency conversion** inline: `100 usd to eur` (live ECB rates via [Frankfurter](https://www.frankfurter.app)).

**Home screen**
- Long-press an icon to lift it, then drag: a highlighted drop target shows where it lands, occupied cells swap, drag to the top strip to remove, or release without moving for the item menu.
- Hold an icon at a screen edge to carry it to the next page (a new page is created past the last one).
- **Free-form placement**: icons and widgets sit exactly where you drop them (switch to snap-to-grid in Settings); widgets resize freely from eight handles.
- Page overview has a **page strip** at the bottom: swipe through pages, drag a thumbnail to reorder, long-press a widget in the picker to choose where it goes.
- Long-press empty space to **zoom out** into page overview: add, delete, reorder pages, tidy a page onto the grid, or add apps/widgets.
- Drag an icon to the bar at the top to remove, uninstall, open app info, edit, or toggle its name.
- Hide app names globally or per app.
- 3D page swing between pages, rubber-band overscroll and haptic feedback.

**Web & AI in one tap**
- One result row per engine: Google, Brave, DuckDuckGo, Bing, ChatGPT, Claude, Perplexity.
- Enable, reorder, or add your **own engine or AI chat** from any URL with a query parameter.

**Contacts**
- Search contacts by name or **by phone number**; unknown numbers get an **Add to contacts** row.
- Each contact is one compact result that **expands into action chips** (**Call, SMS, WhatsApp message, WhatsApp call**, open, copy); long-press copies the number. Email addresses get write / add / copy actions.
- Few search engines collapse into a single row; calculator results keep a **history**.
- Choose what tapping a result does; copy numbers; optional direct-call mode that skips the dialer.
- Contacts are optional: decline the permission and everything else still works.

**Shortcuts & background**
- **Apps can add shortcuts to Piko** (Chrome's "Add to Home screen" and any app using Android's pin-shortcut request); they get the app's icon and name. Restart an app that was open before you made Piko your launcher.
- **Edit shortcuts**: change name, target (number, address, link or search text) and icon from the drag bar or long-press menu.
- Type a web address to get an **Open website** result you can add to home.
- **Backgrounds**: solid colour, gradient, system wallpaper or your own image, with adjustable darkening (Settings → Background).

**Quick actions & shortcuts**
- Piko quick actions (settings, setup, overview, ...) appear in search; switch them off in setup.
- Long-press any action result (call, message, copy, web search, ...) to **add it to home** as a shortcut.
- Pick results with the keyboard: up/down moves the highlight, Enter activates it.

**Home screen**
- Configurable grid (columns × rows); pages are managed in the overview, not in settings.
- **Folders** (second level) to group apps; choose any page, including search, as the **default page**.
- Long-press menu: app info, **rename / tags / icon**, uninstall, folder, remove from home.
- Icons come from your apps, an icon pack or an image; small built-in vector icons look the same on every device.
- Add apps and **widgets** (with resize handles) from the overview.
- First-run **setup** asks a few simple questions, including whether the keyboard opens automatically.

**Small & private**
- Pure Android SDK: no AndroidX, no Material, no analytics, no ad or crash-reporting SDKs.
- The only network use is the currency lookup and opening web searches you trigger.

## Screenshots

All captured on a **Moto G7 Play** (720×1512).

| Home | Drag bar | Overview |
|---|---|---|
| <img src="docs/screenshots/01-home.png" width="220" alt="Piko home grid with a gradient background"> | <img src="docs/screenshots/02-home-icon-menu.png" width="220" alt="Drag an icon to the top for remove, uninstall, info, edit and rename"> | <img src="docs/screenshots/03-overview.png" width="220" alt="Page overview with page strip"> |

| App search | Contact actions | Website |
|---|---|---|
| <img src="docs/screenshots/04-app-search.png" width="220" alt="App search results"> | <img src="docs/screenshots/09-contacts.png" width="220" alt="Contact result expanded into call, SMS and WhatsApp chips"> | <img src="docs/screenshots/15-website.png" width="220" alt="Typed address as an Open website result"> |

| Calculator | Currency | Web &amp; AI search |
|---|---|---|
| <img src="docs/screenshots/05-calculator.png" width="220" alt="Inline calculator"> | <img src="docs/screenshots/06-currency.png" width="220" alt="Inline currency conversion"> | <img src="docs/screenshots/07-web-search.png" width="220" alt="Search-the-web row with multiple engines"> |

| Edit shortcut | Edit app | Background |
|---|---|---|
| <img src="docs/screenshots/16-edit-shortcut.png" width="220" alt="Edit shortcut dialog"> | <img src="docs/screenshots/17-app-edit.png" width="220" alt="Rename, tags and icon for an app"> | <img src="docs/screenshots/14-background.png" width="220" alt="Background settings with gradient"> |

| Settings | Search sources | Engines &amp; AI chats |
|---|---|---|
| <img src="docs/screenshots/08-settings.png" width="220" alt="Layout and behaviour settings"> | <img src="docs/screenshots/10-search-settings.png" width="220" alt="Search source toggles"> | <img src="docs/screenshots/11-web-search-engines.png" width="220" alt="Reorderable web search engines and AI chats"> |

| Contact buttons | Hidden apps |
|---|---|
| <img src="docs/screenshots/12-contact-actions.png" width="220" alt="Contact result buttons and tap action"> | <img src="docs/screenshots/13-hidden-apps.png" width="220" alt="Hidden apps list"> |

## Install

1. Download `Piko-<version>.apk` from the [latest release](https://github.com/mkalmousli/Piko/releases/latest).
2. Open it and allow installs from your browser/file manager if asked.
3. Press Home and choose **Piko → Always**.

Runs on Android 1.0 (API 1) and newer. Features that need a newer Android (widgets need 4.1, shortcuts pinned by other apps 8.0, shortcut icons 7.1) are simply disabled on older versions. Regularly tested on Android 10; older versions are covered by API-level lint, not real devices. Releases include a SHA-256 checksum.

## Permissions

| Permission | Why |
|---|---|
| `READ_CONTACTS` | Contact search (optional) |
| `CALL_PHONE` | Direct-call mode (optional, falls back to the dialer) |
| `INTERNET` | Currency rates and opening web searches |
| `REQUEST_DELETE_PACKAGES` | Uninstall action in the long-press menu |

See [PRIVACY.md](PRIVACY.md).

## Build from source

Requires JDK 17 and the Android SDK (API 34).

```bash
git clone https://github.com/mkalmousli/Piko.git
cd Piko
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # minified; signed if PIKO_KEYSTORE_* env vars are set
```

## Releases

Pushing a tag like `v1.0.0` triggers [the release workflow](.github/workflows/release.yml): it builds a
signed APK, attaches it with a checksum and generates release notes. Pushes and pull requests run
[CI](.github/workflows/ci.yml). The [project site](https://al-mo.de/apps/piko/) is deployed from
[`docs/`](docs) by [the Pages workflow](.github/workflows/pages.yml).

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Author

Made by **[Mohamad Almousli](https://al-mo.de)** · [al-mo.de](https://al-mo.de) · [@mkalmousli](https://github.com/mkalmousli)

## License

[MIT](LICENSE) © 2026 Mohamad Almousli
