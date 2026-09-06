<p align="center">
  <img src="brand/kern-lockup.png" alt="Kern" width="400">
</p>

# Kern

<!-- Language & platform -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
<!-- UI -->
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.05.01-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-757575?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Navigation Compose](https://img.shields.io/badge/Navigation%20Compose-2.8.3-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose/navigation)
<!-- Build & libraries -->
[![Gradle](https://img.shields.io/badge/Gradle-8.10.2-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![AGP](https://img.shields.io/badge/Android%20Gradle%20Plugin-8.7.2-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/build/releases/gradle-plugin)
[![Apache POI](https://img.shields.io/badge/Apache%20POI-5.5.1-D22128?style=flat-square&logo=apache&logoColor=white)](https://poi.apache.org/)
[![OpenCSV](https://img.shields.io/badge/OpenCSV-5.12.0-08427B?style=flat-square)](https://opencsv.sourceforge.net/)
[![jsoup](https://img.shields.io/badge/jsoup-1.23.2-1D9BF0?style=flat-square)](https://jsoup.org/)
<!-- PDF engine -->
<!-- Qyra and MuPDF have no shields.io logos. Swap these plain badges for logo badges if logos become available. -->
[![Qyra](https://img.shields.io/badge/Qyra-JNI%2FNDK%20bridge-DEA584?style=flat-square&logo=rust&logoColor=white)](https://github.com/zParik/Qyra)
[![MuPDF](https://img.shields.io/badge/MuPDF-PDF%20engine-C0392B?style=flat-square)](https://mupdf.com/)
<!-- Project -->
[![Version](https://img.shields.io/github/v/tag/verycareful/kern?style=flat-square&label=version&color=3F5B8B&sort=date)](https://github.com/verycareful/kern/tags)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-A42E2B?style=flat-square)](LICENSE)
[![Status: Active](https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square)](.)

A privacy-first, fully offline, open-source Android document toolkit.
No cloud sync. No data collection. No network access. Completely free. AGPL-3.0.

Kern is in early alpha.

## What Kern does

Kern integrates with Android's "Open with" system, so any file on your device - in Downloads, Documents, or anywhere else - can be opened directly in Kern. There is no separate app folder and no syncing. Kern reads and writes files in place; your files stay yours, on your device.

## Document formats

Kern works with common document types:

- Spreadsheets - `.xlsx`, `.xls`, `.csv`
- Documents - `.docx`
- Presentations - `.pptx`
- PDF - `.pdf`
- EPUB - `.epub`

## Tech stack

- **Language:** Kotlin + Jetpack Compose
- **Office formats:** Apache POI (xlsx, docx, pptx) + OpenCSV
- **PDF engine:** MuPDF via the [Qyra](https://github.com/zParik/Qyra) Rust JNI/NDK bridge (GPL-3.0)
- **License:** AGPL-3.0
- **Min Android API:** 26 (Android 8.0+)

## Permissions

Kern requests:
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (older Android versions)
- `READ_MEDIA_*` (Android 13+)
- `MANAGE_EXTERNAL_STORAGE` - guided toward Documents and Downloads
- "Open with" intent filters for supported document types

No network permissions. Ever.

## Project structure

```
kern/
├── src/
│   ├── browser/          # File browser + "Open with" intent handling
│   ├── editors/          # One module per format (csv, excel, word, pdf, pptx)
│   ├── pdf-bridge/       # Native PDF bridge (JNI/NDK)
│   └── shared/           # Theme, shared UI, utilities
├── res/                  # Android resources (launcher icon, themes, strings)
├── tests/                # Mirrors src/ structure
├── docs/                 # Architecture notes
├── brand/                # Logo assets
├── config/               # Lint configuration
├── scripts/              # Dev tooling
└── sample-files/         # Sample documents
```

## Versioning

This project uses an `A.B.C.D` version scheme. Released versions are tagged in git: the current version is whatever the version badge above shows (see the [tags](https://github.com/verycareful/kern/tags)).

## Getting started

```bash
git clone https://github.com/verycareful/kern.git
```

Open the folder in Android Studio and press Run. Studio provisions the matching Gradle version (8.10.x, pinned in `gradle/wrapper/gradle-wrapper.properties`) automatically. Prebuilt debug APKs are also attached to each [GitHub Release](https://github.com/verycareful/kern/releases) and to the CI run artifacts.

See [docs/architecture.md](docs/architecture.md) for system design and [CONTRIBUTING.md](CONTRIBUTING.md) for contributor guidelines.

## License

Copyright © 2026 Sricharan Suresh (github.com/verycareful)

Kern is licensed under the **[GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html)**.
You may use, study, modify and redistribute it freely. Any derivative work must
also be released under AGPL-3.0, including its source. Unlike the plain GPL, the
AGPL extends that obligation to network use: if you run a modified version and
let others interact with it over a network, you must offer them the source of
your modified version.

Kern requests no network permissions, so the network clause is unlikely to bite
in normal use. It applies to anyone who forks Kern and adds networking.

See the [LICENSE](LICENSE) file for the full license text.

**PDF engine:** MuPDF via the [Qyra](https://github.com/zParik/Qyra) Rust JNI/NDK
bridge, licensed GPL-3.0. GPL-3.0 code inside an AGPL-3.0 project is compatible;
both license headers must be present in the distributed build.
