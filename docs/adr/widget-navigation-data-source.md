# ADR: Widget Navigation Data Source (RECENT_DOCS / RECENTLY_VIEWED)

**Status:** RECENT_DOCS — Option A implemented. RECENTLY_VIEWED — Option B still active, Option A pending.
**Date:** 2026-04-21 (updated 2026-08-26)
**Feature:** ETP-3745 Widget System

---

## Context

The `RECENT_DOCS` and `RECENTLY_VIEWED` widget types need to display the user's navigation
history (recently opened documents and windows). This data was previously tracked exclusively
in the browser's **localStorage** by the frontend navigation layer, and was never persisted
server-side.

When migrating these widgets to the new backend-driven widget system, the `/widget/{id}/data`
endpoint needs to return this data — but it doesn't exist in any backend table.

## Decision (RECENTLY_VIEWED only): Option B (temporary)

`RecentlyViewedResolver` still returns `{"source": "localStorage"}` as a signal. The frontend,
upon receiving this marker, reads data from localStorage instead of rendering backend items.
This widget type (recently opened menu entries/windows) was out of scope for the RECENT_DOCS
implementation below and remains a client-only feature for now.

**Why:** Zero backend changes needed, no risk of breaking existing navigation tracking, fast
to ship.

**Downside:** Data is browser/device-scoped. Clearing localStorage loses history. No
cross-device sync.

## Implemented (RECENT_DOCS): Option A (backend tracking)

Server-side tracking of individually viewed records is now implemented:

1. **New table:** `ETMETA_RECENT_DOCUMENT` with columns:
   - `ETMETA_RECENT_DOCUMENT_ID` (PK)
   - `AD_USER_ID`, `AD_ROLE_ID` (FK, required) — segmentation key
   - `AD_WINDOW_ID`, `AD_TAB_ID` (FK, required)
   - `RECORD_ID` (VARCHAR, required — no FK, target table varies per tab)
   - `IDENTIFIER` (VARCHAR, required — display value snapshot at view time)
   - `TAB_LEVEL` (required)
   - `VIEWED_AT` (timestamp, required)
   - Unique constraint on `(AD_USER_ID, AD_ROLE_ID, AD_WINDOW_ID, AD_TAB_ID, RECORD_ID)` — same
     row is updated (bumping `VIEWED_AT`) on repeat views instead of duplicating.

   Deviated from the original `ETMETA_NAV_LOG` sketch above in two ways: a dedicated table per
   the `ETMETA_USER_FAVORITE` precedent (rather than a generic nav-log table shared with
   `RECENTLY_VIEWED`), and segmentation by **user + role** (not just user), matching the
   per-role list the classic UI and this module's Favorites feature already use.

2. **New endpoint:** `RecentDocumentsService` at `GET|POST /meta/recent-documents`.
   - `POST` body: `{"windowId","tabId","recordId","identifier","tabLevel"}` — upserts by the
     unique key above (bumping `VIEWED_AT` on repeat views) and trims to the 10 most recent
     rows per user + role.
   - `GET` returns `{"items":[{"recordId","identifier","windowId","windowTitle","tabId",
     "tabLevel","viewedAt"}, ...]}`, ordered by `VIEWED_AT DESC`, filtered to windows the
     current role still has `AD_Window_Access` for (so a document is hidden again once the
     role's access to its window is revoked).

3. **`RecentDocsResolver`** now queries `ETMETA_RECENT_DOCUMENT` the same way, so the
   `RECENT_DOCS` dashboard widget and the dedicated endpoint stay consistent (mirrors how
   `FavoritesResolver` duplicates `FavoritesService`'s list query).

4. **No migration step:** existing localStorage entries are simply superseded — the frontend's
   `useRecentDocuments` hook now reads from the backend exclusively, so old local history is
   dropped rather than migrated.
