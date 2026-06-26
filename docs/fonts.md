# Bundled fonts (res/font/)

Kern has zero network permissions, so the design fonts cannot be loaded as
downloadable Google Fonts. They must be bundled here as `.ttf` files.

Until these files are present, `src/shared/theme/Type.kt` falls back to the
closest system faces (sans-serif / monospace) so the project still builds.

## Files to add

Drop the following `.ttf` files into this folder. Android generates an
`R.font.<filename>` id from each filename, so the names must match exactly
(all lowercase, underscores only).

| Family        | Weight      | File name to use here          |
|---------------|-------------|--------------------------------|
| Outfit        | 400 Regular | `outfit_regular.ttf`           |
| Outfit        | 500 Medium  | `outfit_medium.ttf`            |
| Outfit        | 600 SemiBold| `outfit_semibold.ttf`          |
| Outfit        | 700 Bold    | `outfit_bold.ttf`              |
| Quicksand     | 700 Bold    | `quicksand_bold.ttf`           |
| IBM Plex Mono | 400 Regular | `ibm_plex_mono_regular.ttf`    |
| IBM Plex Mono | 500 Medium  | `ibm_plex_mono_medium.ttf`     |
| IBM Plex Mono | 600 SemiBold| `ibm_plex_mono_semibold.ttf`   |
| Sora          | 400 Regular | `sora_regular.ttf`             |
| Sora          | 500 Medium  | `sora_medium.ttf`              |

## Where to get them (all SIL Open Font License, redistribution OK)

- Outfit:        https://fonts.google.com/specimen/Outfit
- Quicksand:     https://fonts.google.com/specimen/Quicksand
- IBM Plex Mono: https://fonts.google.com/specimen/IBM+Plex+Mono
- Sora:          https://fonts.google.com/specimen/Sora

Google Fonts ships static instances named like `Outfit-Regular.ttf`; rename each
to the lowercase form in the table above. Keep the OFL license text with the
project (the OFL files can live in `res/font/` too, they are ignored by aapt).

## Then enable them

In `src/shared/theme/Type.kt`, swap the four system-fallback `…Family` values
for the bundled `FontFamily(Font(R.font.…))` block (it is written out, commented,
directly beneath them). No other changes are needed: every screen reads the
families through `KernType` / `OutfitFamily` etc.
