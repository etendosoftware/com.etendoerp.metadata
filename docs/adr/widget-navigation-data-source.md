# ADR: Widget Navigation Data Source (RECENT_DOCS / RECENTLY_VIEWED)

**Status:** Deferred — Option B active, Option A pending  
**Date:** 2026-04-21  
**Feature:** ETP-3745 Widget System

---

## Context

The `RECENT_DOCS` and `RECENTLY_VIEWED` widget types need to display the user's navigation
history (recently opened documents and windows). This data was previously tracked exclusively
in the browser's **localStorage** by the frontend navigation layer, and was never persisted
server-side.

When migrating these widgets to the new backend-driven widget system, the `/widget/{id}/data`
endpoint needs to return this data — but it doesn't exist in any backend table.

## Decision: Option B (temporary)

The resolvers return `{"source": "localStorage"}` as a signal. The frontend, upon receiving
this marker, reads data from localStorage instead of rendering backend items.

**Why:** Zero backend changes needed, no risk of breaking existing navigation tracking, fast
to ship.

**Downside:** Data is browser/device-scoped. Clearing localStorage loses history. No
cross-device sync.

## Deferred: Option A (backend tracking)

When time allows, implement full server-side tracking:

1. **New table:** `ETMETA_NAV_LOG` with columns:
   - `ETMETA_NAV_LOG_ID` (PK)
   - `AD_USER_ID` (FK)
   - `WINDOW_ID` or `TABLE_NAME` + `RECORD_ID`
   - `NAV_TYPE` (`WINDOW` | `DOCUMENT`)
   - `TITLE`
   - `ACCESSED_AT` (timestamp)

2. **New endpoint:** `POST /meta/navigation/track`  
   Called by the frontend each time a user opens a window or document.
   Payload: `{ "type": "WINDOW"|"DOCUMENT", "windowId": "...", "recordId": "...", "title": "..." }`
   Keeps last N entries per user (trim on insert).

3. **Update resolvers:** `RecentDocsResolver` and `RecentlyViewedResolver` query
   `ETMETA_NAV_LOG` filtered by `AD_USER_ID` and `NAV_TYPE`, ordered by `ACCESSED_AT DESC`.

4. **Migration:** On first load after deploy, frontend pushes existing localStorage entries
   to `/meta/navigation/track` so history isn't lost.
