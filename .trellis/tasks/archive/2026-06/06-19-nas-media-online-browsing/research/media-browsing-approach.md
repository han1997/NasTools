# Media Browsing Approach Research

## Context

The app is Android-native Kotlin/Compose. It already has:

* OkHttp-based WebDAV access.
* A `StorageAdapter` abstraction with list/stat/upload/download.
* WebDAV GET with optional HTTP `Range` header.
* Coil 2.5.0 and an app-wide `ImageLoader`.
* No video playback dependency yet.

The user asked for online browsing of NAS images and videos, and mentioned C/C++/Rust for performance if useful.

## Comparable implementation patterns

### Pattern A: Android-native image loader plus Media3 video playback

* Images: use Coil for request lifecycle, caching, downsampling, and Compose integration.
* Videos: use AndroidX Media3/ExoPlayer, which delegates decoding to platform codecs and supports seek/range-based playback through data sources.
* Remote authenticated WebDAV access can be bridged through a custom Media3 `DataSource` backed by the existing adapter/client, or through a local loopback proxy that translates ExoPlayer range requests into WebDAV GET requests.

Why this is common:

* Android's platform/media stack already handles codec acceleration.
* Avoids loading full media into app memory.
* Keeps implementation in Kotlin and Compose.

### Pattern B: Download-to-cache then open local file

* Fetch the remote media into an app cache file.
* View images/videos from local cache.

Pros:

* Simple player integration because local files are easy for Android APIs.
* Works even when the remote server has incomplete Range support.

Cons:

* Not true online video playback for large files.
* Consumes storage and delays first frame.
* Needs cache eviction and partial-download cleanup.

### Pattern C: Native C/C++/Rust media/proxy layer

* Build native code for protocol streaming, transcoding, or thumbnail extraction.

Pros:

* Useful only if custom high-throughput I/O, native thumbnail extraction, or transcoding is required.

Cons:

* More build complexity, ABI surface, crash modes, and maintenance cost.
* Android hardware decoding is already available through Media3/platform codecs.
* Not justified until profiling shows Kotlin/OkHttp/Media3 cannot meet requirements.

## Constraints from this repo

* `StorageAdapter.download()` returns `ByteArray`, so it should not be used directly for whole video playback.
* `WebDavClient.get(path, start, end)` already supports Range and can be reused as a primitive.
* Current `StorageAdapter` does not expose streaming `ResponseBody` or direct URL/auth metadata.
* Browser UI currently treats non-directory entries as inert; this is a clear extension point.
* The project already uses Coil, so image preview should not add another image stack.

## Recommended MVP

Use Pattern A:

1. Add media type detection by extension for common images/videos.
2. Add a media viewer route/screen opened from the existing browser.
3. Render images with Coil using a custom model/fetcher or a repository method that fetches bytes with size guard and cache.
4. Render videos with Media3/ExoPlayer using a range-capable data source or a lightweight local proxy.
5. Keep native code out of scope until there is a measured bottleneck.

## Risk notes

* WebDAV servers vary in Range behavior; video playback must surface clear errors when the server cannot serve seekable media.
* Authenticated/private media URLs should not be exposed to other apps.
* Image loading must avoid full-size OOM; use Coil sizing/downsampling and avoid unbounded `ByteArray` for large files.
* Video playback must release player resources when leaving the screen.
