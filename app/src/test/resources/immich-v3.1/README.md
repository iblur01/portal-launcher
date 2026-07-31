# Immich 3.1 contract fixtures

These fixtures model the fields Portal consumes from Immich 3.1.0.

Source of truth: `https://raw.githubusercontent.com/immich-app/immich/main/open-api/immich-openapi-specs.json`, verified with `info.version = 3.1.0` on 2026-07-31.

Relevant schemas/endpoints:

- `GET /server/ping`
- `GET /albums` → album summaries
- `POST /search/metadata` with `MetadataSearchDto`
- `SearchResponseDto.assets` → `SearchAssetResponseDto`

Keep paging fixtures separate so tests assert one-based request pages and `nextPage` handling.
