# Metadata Module — Technical Reference

**Module:** `com.etendoerp.metadata` (v1.2.0)  
**Last updated:** 2026-05-18  
**Status:** Work in progress

---

## Overview

This module provides the REST API layer (`/meta/*`) that powers the new Etendo UI (Next.js frontend, `com.etendoerp.mainui`). It replaces the SmartClient-based metadata delivery of Classic Etendo with a JSON-over-HTTP approach, covering:

- Session and authentication context
- Menu and navigation tree
- Window/tab/field metadata (including selector configuration)
- Dashboard widget system (layout + data resolution)
- Toolbar button definitions
- Saved views, favorites, and user preferences
- Process definitions and execution
- Email composition and sending
- Localization (labels, languages, messages)

---

## Architecture

### Entry Point

All requests enter through `MetadataServlet` (registered at `/meta` and `/sws`). The servlet delegates to `ServiceFactory`, which routes by path:

```
HTTP request → MetadataServlet.process()
                 → ServiceFactory.getService(req, res)
                     → ConcreteService.process()
```

`ServiceFactory` uses two routing strategies:
1. **Exact match** — path must equal the registered key
2. **Prefix match** — path must start with the registered prefix (order matters for overlapping prefixes)

If no match is found, legacy paths are checked and forwarded to the Classic servlet infrastructure via `RequestDispatcher`.

### Service Map

| Path | Service | Match Type | HTTP Methods |
|---|---|---|---|
| `/session` | `SessionService` | Exact | GET |
| `/menu` | `MenuService` | Exact | GET |
| `/message` | `MessageService` | Exact | GET |
| `/labels` | `LabelsService` | Exact | GET |
| `/preferences` | `PreferencesService` | Exact | GET |
| `/widget/classes` | `WidgetClassesService` | Exact | GET |
| `/email/send` | `EmailSendService` | Exact | POST |
| `/email/config` | `EmailConfigService` | Exact | GET |
| `/email/attachments` | `EmailAttachmentService` | Exact | GET |
| `/window/{id}` | `WindowService` | Prefix | GET |
| `/tab/{id}` | `TabService` | Prefix | GET |
| `/language{/code}` | `LanguageService` | Prefix | GET |
| `/location/{id}` | `LocationMetadataService` | Prefix | GET |
| `/toolbar{/windowId}` | `ToolbarService` | Prefix | GET |
| `/saved-views{/...}` | `SavedViewService` | Prefix | GET, POST, PUT, DELETE |
| `/report-and-process/{id}` | `ReportAndProcessService` | Prefix | GET, POST |
| `/process-execution` | `ProcessExecutionService` | Prefix | POST |
| `/process/{id}` | `ProcessMetadataService` | Prefix | GET |
| `/dashboard/layout` | `DashboardService` | Prefix | GET, PUT |
| `/dashboard/widget` | `DashboardService` | Prefix | POST, DELETE, PATCH |
| `/favorites` | `FavoritesService` | Prefix | GET, POST, DELETE |
| `/widget/{instanceId}/data` | `WidgetDataService` | Prefix | GET |
| `/email{/...}` | `EmailService` | Prefix | GET |
| `/legacy{/...}` | `LegacyService` | Prefix | * |

---

## Window/Tab/Field Metadata

### What's Implemented

When the frontend requests metadata for a window, the response includes the full structural definition needed to render the UI:

#### Window Level (`WindowBuilder`)
- Window ID, name, description, type
- Role-based access check (throws 401 if no access, 404 if window doesn't exist)
- Ordered list of tabs with role-based tab visibility
- Tab access permissions (read-only vs. read-write at tab level)

#### Tab Level (`TabBuilder`)
- Tab ID, name, entity name, table ID, HQL filter clause
- Tab hierarchy (parent tab, tab level, sequence)
- Ordered list of fields with full metadata

#### Field Level (`FieldBuilder`, `FieldBuilderWithColumn`, `FieldBuilderWithoutColumn`)

Each field in the JSON response includes:

| Property | Description | Source |
|---|---|---|
| `id` | AD_Field ID | `AD_FIELD.AD_FIELD_ID` |
| `name` | Translated field name | `AD_FIELD.NAME` (translated) |
| `columnName` | DB column name | `AD_COLUMN.COLUMNNAME` |
| `property` | HQL/ORM property name | Derived from entity model |
| `type` | Field type for rendering | See "Field Types" below |
| `required` | Mandatory flag | `AD_COLUMN.ISMANDATORY` |
| `readOnly` | Read-only flag | Combined from field access + column settings |
| `displayed` | Show/hide flag | `AD_FIELD.ISDISPLAYED` + display logic evaluation |
| `displayLogic` | Dynamic display expression | `AD_FIELD.DISPLAYLOGIC` (parsed) |
| `readOnlyLogic` | Dynamic read-only expression | `AD_COLUMN.READONLYLOGIC` (parsed) |
| `defaultValue` | Default value expression | `AD_COLUMN.DEFAULTVALUE` |
| `fieldGroup` | Field group name | `AD_FIELDGROUP.NAME` |
| `startRow` / `colSpan` / `rowSpan` | Layout position | From field positioning data |
| `selector` | Selector configuration (see below) | For foreign-key/selector fields |
| `listValues` | Dropdown options | For list-reference fields |
| `colorFieldName` | Color source field | For columns with color reference |

### Field Types

The `type` property maps AD reference types to frontend-renderable types:

- **String types:** `string`, `text` (multiline), `richText`
- **Numeric types:** `integer`, `decimal`, `price`, `quantity`, `generalQuantity`
- **Date types:** `date`, `dateTime`, `time`, `absoluteDateTime`, `absoluteTime`
- **Boolean:** `yesNo`
- **Selection types:** `list` (dropdown), `selector` (search popup), `treeSelector`, `tableDir`, `table`, `search`
- **Special:** `button`, `buttonList`, `image`, `color`, `window` (link)
- **Audit fields:** `creationDate`, `createdBy`, `updated`, `updatedBy` — auto-populated, read-only

### Selector Metadata

When a field uses a selector (foreign key, OBUISEL selector, table, table dir, search, or tree reference), the JSON includes a `selector` object:

```json
{
  "selector": {
    "datasourceName": "ComboTableDatasourceService",
    "_selectorDefinitionId": "...",
    "fieldId": "...",
    "displayField": "_identifier",
    "valueField": "id",
    "selectedProperties": "id,name,description",
    "additionalProperties": "...",
    "outFields": [...]
  }
}
```

**Selector types handled:**
- **Table / TableDir** — Uses `ComboTableDatasourceService`, resolves display/value from the referenced table
- **Search** — Same as Table but with a popup search dialog
- **OBUISEL Selector** — Custom selector definition with configurable fields, datasource, and out-fields
- **Tree Reference** — Uses tree-specific datasource (`90034CAE96E847D78FBEF6D38CB1930D`)

### Out-Fields (Selector Field Mappings)

*Implemented in ETP-3757 (May 2026)*

When an OBUISEL selector has out-fields configured, the metadata JSON includes an `outFields` array inside the selector object. This tells the frontend which fields to auto-populate when the user selects a value.

Two types of out-field entries:

#### Type: `"field"` — Direct Field Mapping
The selector field maps to an `AD_FIELD` in the same tab via the `obuiselOutfield` column.

```json
{
  "type": "field",
  "selectorFieldProperty": "businessPartnerCategory",
  "targetColumnName": "C_BP_Group_ID",
  "targetHqlName": "businessPartnerCategory",
  "suffix": "_R"
}
```

The frontend uses `targetColumnName` / `targetHqlName` to identify which form field to populate with the selector field's value.

#### Type: `"calloutInput"` — Callout Input Mapping
The selector field has a suffix but no AD_FIELD mapping. Used to populate callout input parameters.

```json
{
  "type": "calloutInput",
  "selectorFieldProperty": "invoiceTerms",
  "targetColumnName": null,
  "targetHqlName": null,
  "suffix": "_BTN"
}
```

**Resolution logic:** For each active out-field in the selector:
1. Look for `AD_FIELD` records in the tab whose `obuiselOutfield` references this selector field → emit `"field"` entry
2. If no field match but the selector field has a suffix → emit `"calloutInput"` entry
3. If neither condition is met → skip (no entry emitted)

---

## Dashboard Widget System

### Data Model

Three database tables:

- **`ETMETA_WIDGET_CLASS`** — Widget type definitions (name, type, title, HQL query, external URL, default dimensions, refresh interval)
- **`ETMETA_WIDGET_PARAM`** — Configurable parameters per widget class (typed: TEXT, NUMBER, BOOLEAN, DATE, LIST, FK)
- **`ETMETA_DASHBOARD_WIDGET`** — Widget instances (layout position, layer, visibility, parameter overrides as JSON)

### Three-Layer Personalization

Widget layout uses layer inheritance: **SYSTEM > CLIENT > USER**.

1. `SYSTEM` records apply to all users
2. `CLIENT` records override SYSTEM for a specific client (`ad_client_id`)
3. `USER` records override CLIENT/SYSTEM for a specific user+role pair (`ad_user_id` + `ad_role_id`)

Resolution in `DashboardLayoutResolver`:
- Load all active rows matching current user context (SYSTEM + matching CLIENT + matching USER)
- Group by `ETMETA_WIDGET_CLASS_ID`; highest-priority layer wins
- Rows with `ISVISIBLE = 'N'` are excluded from the response

When a user modifies a SYSTEM/CLIENT widget (position, size, visibility, params), a `USER`-layer override record is upserted — the original record is never mutated.

### Widget API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/meta/dashboard/layout` | GET | Resolved widget layout for current user (layer-merged) |
| `/meta/dashboard/layout` | PUT | Save position/size/visibility changes (batch) |
| `/meta/dashboard/widget` | POST | Add a new widget instance to the dashboard |
| `/meta/dashboard/widget/{id}` | DELETE | Remove widget (USER layer = hard delete; SYSTEM/CLIENT = shadow hide via `ISVISIBLE=N` USER record) |
| `/meta/dashboard/widget/{id}/params` | PATCH | Update widget instance parameters |
| `/meta/widget/{instanceId}/data` | GET | Fetch data for a specific widget instance |
| `/meta/widget/classes` | GET | List all available widget class definitions |

### Widget Resolver System

Each widget type has a corresponding `WidgetDataResolver` implementation. Resolvers are registered via CDI (`WidgetResolverRegistry`) and looked up by the `TYPE` value from `ETMETA_WIDGET_CLASS`.

**Interface contract:**
```java
public interface WidgetDataResolver {
    String getType();                          // Must match ETMETA_WIDGET_CLASS.TYPE
    default boolean isAvailable() { return true; } // Optional: check dependencies
    JSONObject resolve(WidgetDataContext ctx);  // Return data payload
}
```

The `isAvailable()` method allows resolvers to gracefully degrade when optional dependencies (e.g., Copilot module) are not installed. The layout endpoint includes an `"available"` flag per widget.

### Implemented Resolvers

| Resolver | Type | Data Source | Notes |
|---|---|---|---|
| `FavoritesResolver` | FAVORITES | `ETMETA_USER_FAVORITE` table | User-bookmarked records |
| `RecentDocsResolver` | RECENT_DOCS | Signaled from frontend (localStorage) | Temporary impl; backend nav log planned |
| `RecentlyViewedResolver` | RECENTLY_VIEWED | Signaled from frontend (localStorage) | Temporary impl; backend nav log planned |
| `NotificationResolver` | NOTIFICATION | `AD_Note` table | System alerts for current user |
| `StockAlertResolver` | STOCK_ALERT | `M_Storage` / `M_Product` | Products below minimum stock |
| `KPIResolver` | KPI | HQL query from `ETMETA_WIDGET_CLASS.HQL_QUERY` | Single numeric value with optional trend |
| `QueryListResolver` | QUERY_LIST | HQL query from `ETMETA_WIDGET_CLASS.HQL_QUERY` | Tabular data; supports named parameters |
| `CopilotResolver` | COPILOT | Copilot external API | AI recommendations; checks module availability |
| `HTMLResolver` | HTML | Static content from widget class | Renders HTML content |
| `URLResolver` | URL | External URL from widget class | Iframe embedding |
| `ProcessResolver` | PROCESS | Etendo process execution | Runs a process and returns output |
| `CalendarResolver` | CALENDAR | Calendar external endpoint | Calendar data feed |
| `ProxyResolver` | PROXY | `EXTERNAL_DATA_URL` field | HTTP proxy with Etendo bearer token forwarding |

### Widget Parameter Handling

Widget parameters allow user-level configuration (e.g., number of rows to display, product category filter):

- Defined in `ETMETA_WIDGET_PARAM` per widget class
- Instance-level overrides stored as JSON in `ETMETA_DASHBOARD_WIDGET.PARAMETERS_JSON`
- **Validation:** Any parameter value containing `:` is validated as a URL — must be `https://` with a parseable host (prevents `javascript:`, `data:` injection)
- Parameter types: `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, `LIST` (comma-separated `value:label`), `FK` (foreign key with table/display column)

### Classic Widget Migration

A SQL migration script (`src-db/database/prescript/migrate_classic_widgets.sql`) migrates Classic widgets from `OBKMO_WidgetClass`/`OBKMO_WidgetInstance` to the new `ETMETA_*` tables. Supported: QUERY_LIST (9 widgets), URL (1 widget). Others are logged for manual review.

---

## AD Configuration (Application Dictionary)

### Windows

This module defines three AD windows:

| Window | Purpose | Menu Location |
|---|---|---|
| **Widget Class** | Manage widget class definitions (types, resolvers, queries, params) | Workspace folder |
| **Dashboard Widget** | Configure default dashboard layouts by layer (SYSTEM/CLIENT) | Workspace folder |
| **Toolbar** | Configure toolbar buttons for the main UI | General Setup > Application |

### Tables

| Table | Description |
|---|---|
| `ETMETA_WIDGET_CLASS` | Widget type registry |
| `ETMETA_WIDGET_PARAM` | Widget class parameters |
| `ETMETA_DASHBOARD_WIDGET` | Widget instances / layout |
| `ETMETA_TOOLBAR_BUTTON` | Custom toolbar button definitions |
| `ETMETA_TOOLBAR_BUTTON_WINDOW` | Toolbar button → window assignments |
| `ETMETA_SAVEDVIEW` | Saved view definitions (filters, sorts, columns per tab) |
| `ETMETA_USER_FAVORITE` | User bookmark/favorite records |

---

## Other Implemented Services

### Toolbar (`ToolbarService` / `ToolbarBuilder`)
- Returns toolbar button definitions for a given window
- Supports window-specific and global buttons
- Buttons defined in `ETMETA_TOOLBAR_BUTTON` + `ETMETA_TOOLBAR_BUTTON_WINDOW`

### Saved Views (`SavedViewService`)
- CRUD for saved views (filter/sort/column configurations per tab)
- Stored in `ETMETA_SAVEDVIEW`
- Supports default view per user

### Favorites (`FavoritesService`)
- Add/remove/list bookmarked records
- Stored in `ETMETA_USER_FAVORITE`

### Processes (`ProcessMetadataService`, `ProcessExecutionService`, `ReportAndProcessService`)
- `GET /meta/process/{id}` — Process definition metadata (parameters, their types and selectors)
- `POST /meta/process-execution` — Execute a process asynchronously
- `GET/POST /meta/report-and-process/{id}` — Report generation and legacy process support

### Session (`SessionService` / `SessionBuilder`)
- Returns authenticated user context: user ID, role, client, org, warehouse, language, etc.

### Menu (`MenuService` / `MenuBuilder`)
- Returns the full menu tree for the current role
- Resolves menu actions: Window, Form, Process, External link
- Supports recent items and favorites integration

### Labels & Localization
- `GET /meta/labels` — UI translation strings for the current language
- `GET /meta/language{/code}` — Language definition and formatting rules
- `GET /meta/message` — System message lookups

### Location Metadata (`LocationMetadataService`)
- Returns location/address field configuration for a given country

### Email (`EmailService`, `EmailSendService`, `EmailConfigService`, `EmailAttachmentService`)
- Email composition, sending, configuration, and attachment management

### Legacy Forwarding
- Requests to classic paths (e.g., `/info`, `/Selector`) are forwarded to the original Classic servlets
- Session attributes are bridged for backward compatibility (e.g., UsedByLink window+record context)

---

## Caching

`MetadataCacheManager` provides in-memory caching for expensive metadata queries. Cache invalidation is event-driven via `MetadataCacheInvalidationObserver`, which listens for AD entity changes.

---

## Error Handling

All exceptions are caught at `MetadataServlet` level:
- Each error response includes a **correlation ID** (UUID) for log tracing
- HTTP status codes are derived from exception type (401, 404, 405, 422, 500)
- Response format: JSON (if client accepts it or `isc_dataFormat=json`) or HTML fallback

Custom exception types:
- `UnauthorizedException` (401)
- `NotFoundException` (404)
- `MethodNotAllowedException` (405)
- `UnprocessableContentException` (422)
- `InternalServerException` (500)

---

## What's NOT Yet Implemented / Known Gaps

### Backend Navigation Log (ETMETA_NAV_LOG)
- **Status:** Deferred (ADR: `docs/adr/widget-navigation-data-source.md`)
- **Current state:** `RECENT_DOCS` and `RECENTLY_VIEWED` widgets rely on a frontend localStorage signal. The backend stores a marker, but full server-side navigation tracking (`ETMETA_NAV_LOG` table with timestamp, entity, record) is not yet implemented.
- **Impact:** Navigation history is lost on browser clear / device switch.

### Widget Translation (TRL Tables)
- Widget class titles and parameter display names support translation via `_TRL` convention, but the translation tables and translation workflow for new widget classes have not been set up.

### Widget Parameter FK Selector UI
- The `FK` parameter type (foreign key selector) is defined in the schema but the frontend config form for FK selectors is not yet implemented. Currently only TEXT, NUMBER, BOOLEAN, DATE, and LIST types are fully usable.

### Process Output Widgets
- The `PROCESS` widget type executes a process and shows output, but long-running process streaming and progress reporting in the widget context are not yet supported.

### Widget Refresh Interval
- `REFRESH_INTERVAL` is a class-level setting returned in the metadata. Instance-level override is not supported — all instances of a widget class share the same refresh interval.

### Read-Only Mode Enforcement on Tab-Level Permissions
- Tab-level read/write permissions are returned in metadata but their enforcement on data submission (write operations) is handled by core DAL, not by this module.

### Callout Integration with Out-Fields
- Out-field metadata of type `"calloutInput"` is emitted but the actual callout invocation chain (passing the suffix-based values to the Classic callout engine) depends on frontend implementation.

---

## Project Structure Quick Reference

```
com.etendoerp.metadata/
  src/com/etendoerp/metadata/
    http/             # Servlets, filters, request handling
    service/          # Business logic (one service per route group)
    builders/         # JSON builders (Field, Window, Tab, Menu, Session, etc.)
    widgets/          # Widget resolver interface, registry, layout resolver
    widgets/resolvers/  # Concrete resolver implementations (one per widget type)
    data/             # Data models (ReferenceSelectors, AuthData, etc.)
    cache/            # Metadata caching and invalidation
    auth/             # Authentication utilities
    utils/            # Constants, helper methods
    exceptions/       # HTTP-mapped exception types
  src-test/           # JUnit 5 + Mockito tests (123 files)
  src-db/
    database/model/   # Table definitions (DDL)
    database/sourcedata/  # AD reference data (XML)
    database/prescript/   # Migration scripts
  docs/               # Design specs, ADRs, plans
  config/             # Spring/CDI servlet bean config
```
