# Kern PDF bridge (Qyra native engine)

Kern's PDF edit tools (merge, split) call PDF operations ported from Qyra through
a small JNI surface. This directory is the Kotlin side; the Rust side is a
standalone crate in the Qyra fork at `qyra/kern-bridge/`.

## Boundary

| Side | File | Symbol |
|------|------|--------|
| Kotlin | [QyraPdf.kt](QyraPdf.kt) | `dev.kern.pdfbridge.QyraPdf` |
| Rust | `qyra/kern-bridge/src/lib.rs` | `Java_dev_kern_pdfbridge_QyraPdf_*` |

The Kotlin `external` declarations map 1:1 to the Rust exports. Both are
Android-only. The ops are pure `lopdf` (no MuPDF, no `ndk-context`), so they take
plain filesystem paths: Kern copies the picked document(s) into its app cache and
passes absolute paths in; the bridge writes outputs into a cache dir, and Kern
saves them out via SAF.

## Why a separate `kern-bridge` crate (not the main `qyra` crate)

The full `qyra` crate depends on `mupdf` + `fontconfig`, whose Android
cross-compile needs an elaborate, Linux-only pipeline (LD wrapper, sysroot stubs,
`-UHAVE_ANDROID`, bindgen sysroot args - see Qyra's `build-android*.yml`).
Merge/split need NONE of that: they are pure `lopdf`. So `qyra/kern-bridge/` is a
tiny cdylib depending only on `lopdf` + `jni`, which builds with a plain
`cargo ndk`. (The logic is ported from `src-tauri/src/commands/{merge,split}.rs`;
de-duplicating via a shared `qyra-pdf-core` crate is the clean-PR follow-up.)

## Building the .so (in WSL / any Linux)

Produces `libkern_pdf.so`; Kern calls `System.loadLibrary("kern_pdf")`. The file
is NOT checked in (`jniLibs/` is gitignored) - build it and drop it into Kern's
`jniLibs/<abi>/`.

One-time setup in WSL (Ubuntu):

```
# Rust + Android targets
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk

# Android NDK (if not already): install via sdkmanager or Android Studio, then
export ANDROID_NDK_HOME=/path/to/Android/Sdk/ndk/<version>
```

Build (from the Qyra fork; adjust the path to where Kern's repo lives in WSL):

```
cd qyra/kern-bridge
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o /path/to/kern/jniLibs build --release
```

`-o .../kern/jniLibs` writes `jniLibs/<abi>/libkern_pdf.so` in the layout Gradle
expects (Kern's `build.gradle.kts` points `jniLibs.srcDirs` at `jniLibs/` and
sets matching `abiFilters`). When the file is absent, `QyraPdf.available` is false
and the tools surface degrades gracefully instead of crashing.

Tip: build just `arm64-v8a` first (`-t arm64-v8a`) - it covers most modern
phones and is the fastest to iterate on.

## Next ops (mechanical, same pattern)

rotate, reorder, remove pages, metadata - all pure `lopdf`, port from the
matching `src-tauri/src/commands/*.rs` into `kern-bridge`. The MuPDF render/edit
ops need `ndk-context` + the `content://` path and come later.

## License

This bridge straddles AGPL-3.0 (Kern) and GPL-3.0 (Qyra); both headers are in
[QyraPdf.kt](QyraPdf.kt). GPL-3.0 inside AGPL-3.0 is upward-compatible. Changes to
the Rust boundary must be coordinated with the Qyra maintainers before merging.
