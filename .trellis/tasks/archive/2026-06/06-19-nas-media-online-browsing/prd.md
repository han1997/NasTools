# NAS Media Online Browsing

## Goal

Add online browsing for images and videos stored on the configured NAS so users can open media directly from the existing remote file browser without downloading files manually first. The feature should start with a maintainable Android-native implementation and only introduce C/C++/Rust if a concrete performance bottleneck requires it.

## What I already know

* The user wants NAS video and image online browsing, and is open to C/C++/Rust for performance if needed.
* The app is an Android-native Kotlin + Jetpack Compose app with Hilt, Room, OkHttp, Coil, and WebDAV support.
* The current remote browser supports listing, grid/list layout, upload entry points, folder creation, and remote delete confirmation.
* `StorageAdapter.download(path, start, end)` exists, and `WebDavClient.get()` already sends an HTTP `Range` header.
* `StorageAdapter.download()` returns a `ByteArray`, which is acceptable for small image preview paths but not suitable for video playback or large media streaming.
* `ImageModule` already provides a Coil `ImageLoader` with memory and disk cache limits.
* No video playback dependency is currently present in `app/build.gradle.kts`.
* README and project status already list image/media preview as a planned enhancement.

## Assumptions (temporary)

* MVP should support WebDAV first because SMB is not implemented yet.
* MVP should open common image and video files by extension from the current browser.
* The first implementation should use Android/Kotlin libraries: Coil for images and AndroidX Media3/ExoPlayer for video, with a streaming data source or lightweight local proxy if needed.
* Native C/C++/Rust is out of scope unless profiling shows decoding, thumbnail generation, or protocol handling cannot meet requirements with platform libraries.

## Open Questions

* Final confirmation before implementation.

## Requirements (evolving)

* Detect image/video media files in the remote browser.
* Let users open supported media files from list and grid views.
* Support image preview and basic video playback in the first MVP.
* Video playback should include play/pause and progress seeking through the platform player controls where possible.
* Support switching to the previous/next media item within the current directory from the media viewer.
* The media sequence should include supported image and video files from the directory listing and skip unsupported files/directories.
* Surface a clear playback/preview error when the server, network, auth, or media format prevents online viewing.
* Keep directories opening as they do today.
* Avoid loading full videos into memory.
* Preserve existing upload, delete, list/grid, and navigation behavior.

## Acceptance Criteria (evolving)

* [ ] Image files can be opened from the remote browser and viewed in-app.
* [ ] Video files can be opened from the remote browser and played in-app without reading the whole file into memory.
* [ ] From an opened image/video, users can move to the previous or next supported media item in the same directory when one exists.
* [ ] Previous/next controls are disabled or hidden at the ends of the directory media sequence.
* [ ] Unsupported files remain non-destructive no-ops or show a clear message.
* [ ] Media load/playback failures show a clear in-app error instead of crashing or hanging indefinitely.
* [ ] Existing browser operations still work.
* [ ] Lint/type-check/tests pass.

## Definition of Done (team quality bar)

* Tests added/updated where behavior is unit-testable.
* Lint / typecheck / relevant Gradle checks pass.
* Docs/notes updated if behavior or dependencies change.
* Rollout/rollback considered if risky.

## Out of Scope (explicit)

* SMB/SFTP media browsing.
* Server-side transcoding.
* Native C/C++/Rust media pipeline unless justified by measured bottlenecks.
* Full photo management features such as albums, tagging, or editing.
* Thumbnail grid/wall for remote media.
* Advanced gallery gestures, metadata panels, favorite/tag management, or slideshow mode.
* Download task manager.

## Technical Notes

* Existing remote browser: `app/src/main/java/com/nastools/app/presentation/browser/BrowserScreen.kt`
* Existing browser state/actions: `app/src/main/java/com/nastools/app/presentation/browser/BrowserViewModel.kt`
* Storage abstraction: `app/src/main/java/com/nastools/app/data/network/StorageAdapter.kt`
* WebDAV GET/Range implementation: `app/src/main/java/com/nastools/app/data/network/WebDavClient.kt`
* Image cache module: `app/src/main/java/com/nastools/app/di/ImageModule.kt`
* Navigation graph: `app/src/main/java/com/nastools/app/presentation/navigation/NavGraph.kt`

## Research References

* [`research/media-browsing-approach.md`](research/media-browsing-approach.md) - Recommend Android-native Coil + Media3 first; defer C/C++/Rust until profiling proves a bottleneck.

## Research Notes

### Feasible approaches

**Approach A: Android-native online viewer** (Recommended)

* How it works: Coil handles images; AndroidX Media3/ExoPlayer handles video; WebDAV Range is bridged through a streaming data source or local loopback proxy.
* Pros: Uses mature platform decoding/caching, avoids native build complexity, supports true video streaming.
* Cons: Requires careful data-source/proxy integration and WebDAV Range error handling.

**Approach B: Cache then view**

* How it works: Download remote media into app cache, then open the cached file in an image/video viewer.
* Pros: Simpler integration and easier to debug.
* Cons: Large videos have slow first play, consume disk, and are less like true online browsing.

**Approach C: Native media/proxy layer**

* How it works: Add C/C++/Rust for protocol streaming, thumbnails, or transcoding.
* Pros: Useful for specialized performance work after measurement.
* Cons: Higher maintenance and ABI risk; not justified for MVP while Android platform codecs and Media3 exist.

## Decision (ADR-lite)

**Context**: The feature needs online viewing for remote images and videos. The repo already has OkHttp WebDAV, Coil, and a browser UI, but the current download abstraction returns `ByteArray`, which is not safe for large video playback.

**Decision**: Implement the first iteration as image preview plus basic video playback using Android-native libraries. Use Coil for images and AndroidX Media3/ExoPlayer for video. Bridge authenticated WebDAV media through a range-capable data source or lightweight local proxy. Include previous/next switching across supported media items in the current directory. Do not introduce C/C++/Rust in the MVP.

**Consequences**: This keeps the implementation maintainable and relies on Android hardware codecs. The main technical risk is robust Range streaming across different WebDAV servers; native code remains a future option only after profiling or concrete protocol limitations justify it.

## Technical Approach

* Add shared media type detection for supported image/video extensions.
* Extend browser file open behavior: directories still navigate, supported media opens the media viewer, unsupported files show a clear message.
* Add a media viewer route/screen that receives the NAS config, current remote path, and same-directory media list context.
* Images use Coil with the existing `ImageLoader`; implementation must avoid unbounded full-size loads.
* Videos use AndroidX Media3/ExoPlayer and a range-capable WebDAV bridge so playback does not materialize whole videos as `ByteArray`.
* Previous/next controls use the current directory listing order filtered to supported media files.
* Media errors are surfaced in the viewer with retry/back navigation rather than crashing.

## Implementation Plan

* PR1: Media model and routing
  * Add media type detection utilities and tests.
  * Add navigation route and browser click handling for supported media.
  * Preserve existing directory/upload/delete behavior.
* PR2: Viewer UI and image support
  * Add media viewer Compose screen with top bar, previous/next controls, loading/error states.
  * Wire image preview through Coil and remote WebDAV fetch support.
* PR3: Video streaming support
  * Add Media3 dependency and ExoPlayer-based video viewer.
  * Implement range-capable WebDAV streaming bridge or local proxy.
  * Verify playback resource release and failure handling.
* PR4: Verification and docs
  * Add focused unit tests where possible.
  * Run compile/test/lint checks.
  * Update README/project status if dependency or user-facing behavior changes.
