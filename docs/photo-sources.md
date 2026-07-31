# Photo sources and Immich

Portal's rotating photo background is split into a provider-neutral coordinator and provider adapters.
The first adapter is Immich.

## Ownership

`PhotoSource` owns provider I/O:

- provider identity
- album enumeration
- logical paged asset enumeration
- display-sized image fetches

`PhotoCoordinator` owns shared behavior:

- album reconciliation
- ordering or shuffle
- cadence and refresh timing
- prefetch
- retry/backoff
- bounded disk cache
- offline restart and fallback
- sanitized status

Provider assets contain stable IDs, dimensions, capture timestamps, and provider-scoped cache keys. Raw provider URLs are not stored in asset or cache metadata.

## Local configuration

Choose **Immich album** under Settings → App → Wallpaper, then configure:

- Immich base URL
- a replacement API key (write-only after saving)
- albums selected from the server-backed picker
- refresh interval
- frame cadence
- shuffle
- optional insecure HTTP for a trusted LAN

Use **Test connection** before selecting albums. Portal fetches the account's album list and only
persists valid Immich v4 UUID album IDs. Refresh and slideshow cadence use bounded presets rather
than arbitrary slider values. The HTTP opt-in displays an explicit warning because the API key and
requests are unencrypted on that path.

HTTPS is required by default. HTTP requires the explicit local opt-in. Redirects are disabled in the default HTTP transport, including HTTPS-to-HTTP redirects, so an API key is never forwarded to a redirect target.
JSON responses are capped at 10 MiB and image responses at 25 MiB before they are buffered.

The API key uses Portal's encrypted preference store. The UI reports only whether a key is configured. Removing the provider clears the credential, provider configuration, current frame, and Immich cache entries.

## Immich API behavior

The adapter is pinned to the Immich **3.1** API contract and uses:

- `GET /api/server/ping`
- `GET /api/albums`
- `POST /api/search/metadata` with `albumIds`, `type: IMAGE`, and real page/size fields
- `GET /api/assets/{id}/thumbnail?size=preview|thumbnail`

Immich v3 removed album assets from the album-detail response. Portal therefore performs paged
metadata searches for each selected album, follows the bounded `nextPage` signal, and asks Immich
to filter to still images server-side. Portal fetches thumbnails/previews, never originals.

## Cache and offline behavior

The cache lives under the app-private `files/photos` directory. Filenames are SHA-256-derived opaque names; provider URLs and credentials never appear in filenames or metadata. Image and manifest replacements are atomic and all manifest/file transactions share one lock.

The cache is bounded by entry count and total bytes. Oldest entries are evicted first. Startup
sweeps crash-orphaned image/temp files and stale manifest entries. Album refresh removes cached
assets no longer present in the selected albums. Empty album selection or explicit provider removal
removes that provider's cached entries.

The app process owns coordinator lifetime; Compose backgrounds only observe it and cannot stop the
launcher from a preview. At coordinator startup, valid cached metadata reconstructs the playlist
before network access. If Immich is unavailable, cached frames continue rotating independently of
network retry backoff. With no usable cached frame, the UI falls back to Portal's neutral gradient.

## Home Assistant and MQTT

Portal publishes one read-only diagnostic entity:

- discovery: `homeassistant/sensor/<device>_photo_status/config`
- state: `portal/<device>/photo/status`
- attributes: `portal/<device>/photo/attributes`

State and attributes are retained at QoS 1 and refreshed every five seconds. The Home Assistant entity expires after 15 seconds without a heartbeat.

Published fields are bounded to provider, health, last successful refresh, selected album label, cache counts, and a fixed error category. Credentials, base URLs, asset IDs, and file paths are never published. There is no photo command topic and no remote configuration surface.

## V1 limits

- Immich only
- still images only
- local configuration only
- up to 20 selected album IDs
- thumbnails/previews rather than originals
- oldest-entry cache eviction rather than access-time LRU
