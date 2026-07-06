# Widget System — Implementation Guide

**Module:** `com.etendoerp.metadata`  
**Last updated:** 2026-05-18  
**Status:** Work in progress

---

## 1. Conceptual Overview

The widget system provides a configurable dashboard for the new Etendo UI. It replaces the legacy SmartClient-based widget system (`org.openbravo.client.myob` / `OBKMO_WidgetClass`) with a REST API approach.

### Core Concepts

- **Widget Class** — A _type definition_: what kind of widget is this, how does it get its data, what are its defaults. Think of it as the blueprint.
- **Widget Param** — A configurable parameter attached to a widget class. Defines what can be customized per instance (e.g., a URL, a filter, number of rows).
- **Dashboard Widget** — An _instance_ of a widget class placed on a user's dashboard. Contains position, size, visibility, and parameter overrides.
- **Resolver** — The Java class that fetches data for a given widget type. Each widget TYPE has one resolver.
- **Layer** — The personalization scope: SYSTEM (all users), CLIENT (per client), USER (per user+role pair).

### Data Flow

```
User opens dashboard
  → Frontend calls GET /meta/dashboard/layout
    → DashboardLayoutResolver loads all ETMETA_DASHBOARD_WIDGET rows
    → Applies layer inheritance (SYSTEM < CLIENT < USER)
    → Returns merged widget list with positions and metadata

User clicks a widget
  → Frontend calls GET /meta/widget/{instanceId}/data
    → WidgetDataService looks up the widget class TYPE
    → WidgetResolverRegistry finds the matching resolver
    → Resolver fetches data (HQL, external API, etc.)
    → Returns JSON data payload
```

---

## 2. Database Model

### 2.1 ETMETA_WIDGET_CLASS

Defines widget types. Each record is a kind of widget that can be instantiated on dashboards.

| Column | Type | Description |
|---|---|---|
| `NAME` | VARCHAR(60) | **Unique** internal identifier. Lowercase, hyphenated (e.g., `best-sellers`, `stock-alert`). Used as a key to identify widget classes programmatically. |
| `TYPE` | VARCHAR(30) | **Enum** — determines which Java resolver processes data requests. Constrained to: `FAVORITES`, `RECENT_DOCS`, `RECENTLY_VIEWED`, `NOTIFICATION`, `STOCK_ALERT`, `KPI`, `COPILOT`, `QUERY_LIST`, `HTML`, `URL`, `PROCESS`, `CALENDAR`, `PROXY`. |
| `TITLE` | VARCHAR(255) | Display name shown in the dashboard UI and widget picker. |
| `DESCRIPTION` | VARCHAR(2000) | Human-readable explanation. For `HTML` type widgets, this field contains the actual HTML content rendered by the resolver. |
| `RESOLVER_CLASS` | VARCHAR(255) | Optional FQCN of a custom Java resolver. If null, the system uses the built-in resolver matching the `TYPE` enum. If both `RESOLVER_CLASS` and `EXTERNAL_DATA_URL` are null, falls back to the TYPE-based resolver. |
| `HQL_QUERY` | CLOB | HQL query string. Used by `QUERY_LIST` and `KPI` resolvers. Supports named parameters that match `ETMETA_WIDGET_PARAM.NAME` values. Also supports automatic context parameters (`:client`, `:user`, `:organizationList`). |
| `EXTERNAL_DATA_URL` | VARCHAR(1000) | For `PROXY` type or when no resolver class is set. The backend proxies GET requests to this URL, forwarding the Etendo bearer token. Must be `https://`. |
| `DEFAULT_WIDTH` | NUMBER | Default grid columns (1-4). Applied when creating new dashboard instances. |
| `DEFAULT_HEIGHT` | NUMBER | Default grid rows. Applied when creating new dashboard instances. |
| `REFRESH_INTERVAL` | NUMBER | Seconds between auto-refresh. `0` = disabled. This is a class-level setting — all instances share it. |
| `AD_MODULE_ID` | VARCHAR(32) | Module that provides this widget class. |

**Constraints:**
- `NAME` has a unique index (`ETMETA_WCL_NAME`)
- `TYPE` is constrained by a CHECK to the enum values above

### 2.2 ETMETA_WIDGET_PARAM

Defines configurable parameters per widget class. Parameters can be used in HQL queries (as named parameters) or consumed by the frontend for widget configuration.

| Column | Type | Description |
|---|---|---|
| `ETMETA_WIDGET_CLASS_ID` | VARCHAR(32) FK | Parent widget class. |
| `NAME` | VARCHAR(60) | Parameter key. If used in HQL, must match a `:paramName` in the query (e.g., `pname` matches `:pname`). |
| `DISPLAY_NAME` | VARCHAR(255) | Label shown in the widget configuration UI. |
| `TYPE` | VARCHAR(20) | **Enum**: `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, `LIST`, `FK`. Determines the input control type. |
| `DEFAULT_VALUE` | VARCHAR(255) | Default value applied when creating a new instance. For `LIST` params, must match one of the values in `LIST_VALUES`. For the special `links` parameter, this is a JSON object mapping column aliases to entity navigation metadata. |
| `LIST_VALUES` | VARCHAR(2000) | For `TYPE=LIST` only. Comma-separated `value:label` pairs, e.g., `last_week:Last Week,last_month:Last Month`. |
| `FK_TABLE` | VARCHAR(60) | For `TYPE=FK` only. Entity/table name for the FK selector (e.g., `M_Product_Category`). |
| `FK_DISPLAY_COLUMN` | VARCHAR(60) | For `TYPE=FK` only. Column to show in the selector dropdown. Defaults to the entity identifier if null. |
| `IS_REQUIRED` | CHAR(1) | Whether the parameter must have a value. |
| `IS_FIXED` | CHAR(1) | If `Y`, the parameter is not editable by users — its value is always the `DEFAULT_VALUE`. Used for internal/system parameters like `links`. |
| `SEQNO` | NUMBER | Display order in the configuration form. |

**Constraints:**
- `TYPE` is constrained by CHECK to: `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, `LIST`, `FK`
- Indexed by `(ETMETA_WIDGET_CLASS_ID, SEQNO)`

### 2.3 ETMETA_DASHBOARD_WIDGET

Instances of widgets placed on the dashboard. Each record represents one widget "card" visible to users.

| Column | Type | Description |
|---|---|---|
| `ETMETA_WIDGET_CLASS_ID` | VARCHAR(32) FK | Which widget class this is an instance of. |
| `LAYER` | VARCHAR(10) | **Enum**: `SYSTEM`, `CLIENT`, `USER`. Determines the scope (see Layer Inheritance below). |
| `AD_USER_ID` | VARCHAR(32) FK | Only for `USER` layer — the specific user. |
| `AD_ROLE_ID` | VARCHAR(32) FK | Only for `USER` layer — the specific role. |
| `COL_POSITION` | NUMBER | Grid column position (0-based). |
| `ROW_POSITION` | NUMBER | Grid row position (0-based). |
| `WIDTH` | NUMBER | Grid columns occupied (1-4). |
| `HEIGHT` | NUMBER | Grid rows occupied. |
| `ISVISIBLE` | CHAR(1) | `Y`/`N`. If `N`, the widget is hidden. Used in USER layer to "shadow-hide" SYSTEM/CLIENT widgets. |
| `SEQNO` | NUMBER | Ordering priority when positions overlap. |
| `PARAMETERS_JSON` | CLOB | JSON object with instance-level parameter overrides. Keys must match `ETMETA_WIDGET_PARAM.NAME` values from the parent class. |

**Constraints:**
- `LAYER` is constrained to: `SYSTEM`, `CLIENT`, `USER`
- Indexed by `(AD_USER_ID, ETMETA_WIDGET_CLASS_ID)` for fast user-specific lookups

---

## 3. Layer Inheritance

The layer system provides three levels of dashboard personalization:

```
SYSTEM (base)  →  CLIENT (override)  →  USER (override)
```

### Resolution Rules

1. Load all active `ETMETA_DASHBOARD_WIDGET` rows where:
   - `LAYER = 'SYSTEM'`, OR
   - `LAYER = 'CLIENT'` AND `AD_CLIENT_ID` matches the current client, OR
   - `LAYER = 'USER'` AND `AD_USER_ID` + `AD_ROLE_ID` match the current user+role
2. Group by `ETMETA_WIDGET_CLASS_ID`. If multiple layers exist for the same class, the highest-priority layer wins: USER > CLIENT > SYSTEM.
3. Entries with `ISVISIBLE = 'N'` are excluded from the final output.

### Practical Implications

| Scenario | What happens |
|---|---|
| Admin creates a SYSTEM widget | All users see it in their dashboard |
| Admin creates a CLIENT widget | Users of that client see it; overrides SYSTEM widget of same class |
| User moves a SYSTEM widget | A USER-layer override record is upserted with the new position; the SYSTEM record is untouched |
| User deletes a SYSTEM widget | A USER-layer "shadow" record is created with `ISVISIBLE=N`; the SYSTEM record is untouched |
| User adds a widget from the picker | A USER-layer record is inserted |
| User re-adds a previously deleted widget | The shadow record (`ISVISIBLE=N`) is deleted, and the SYSTEM/CLIENT record becomes visible again |

---

## 4. Widget Types and Resolvers

### 4.1 Built-in Types

| Type | Resolver Class | Data Source | Configurable | Description |
|---|---|---|---|---|
| `FAVORITES` | `FavoritesResolver` | `ETMETA_USER_FAVORITE` table | No | User-bookmarked records and windows |
| `RECENT_DOCS` | `RecentDocsResolver` | Frontend localStorage signal | No | Recently created/modified documents |
| `RECENTLY_VIEWED` | `RecentlyViewedResolver` | Frontend localStorage signal | No | Recently navigated windows |
| `NOTIFICATION` | `NotificationResolver` | `AD_Note` table | No | System alerts for current user |
| `STOCK_ALERT` | `StockAlertResolver` | `M_Storage` / `M_Product` | No | Products below minimum stock |
| `KPI` | `KPIResolver` | `HQL_QUERY` field | Via params | Single numeric metric with trend |
| `QUERY_LIST` | `QueryListResolver` | `HQL_QUERY` field | Via params | Tabular data from parameterized HQL |
| `HTML` | `HTMLResolver` | `DESCRIPTION` field | No | Static HTML content |
| `URL` | `URLResolver` | Widget params (`src`) | Yes (`src` param) | Iframe-embedded external URL |
| `PROCESS` | `ProcessResolver` | Etendo process execution | Via params | Runs a process and displays output |
| `CALENDAR` | `CalendarResolver` | External calendar endpoint | No | Fiscal calendar data feed |
| `COPILOT` | `CopilotResolver` | Copilot external API | No | AI-powered recommendations (requires Copilot module) |
| `PROXY` | `ProxyResolver` | `EXTERNAL_DATA_URL` field | No | HTTP proxy to external endpoint with Etendo token |

### 4.2 Resolver Availability

Each resolver implements `isAvailable()` which checks whether its dependencies are present. For example, `CopilotResolver` checks if the Copilot module is installed. The layout endpoint returns an `"available": true/false` flag per widget so the frontend can handle gracefully.

### 4.3 Automatic Context Parameters

For `QUERY_LIST` and `KPI` widgets, the following HQL named parameters are automatically injected (no need to define them as widget params):

| Parameter | Value | Description |
|---|---|---|
| `:client` | Current client ID | `OBContext.getCurrentClient().getId()` |
| `:user` | Current user ID | `OBContext.getUser().getId()` |
| `:organizationList` | Accessible org IDs | Natural tree of accessible organizations |

Any `:paramName` in the HQL that matches an `ETMETA_WIDGET_PARAM.NAME` will be bound from the instance's parameter overrides (or default value).

For text-type filter params (like `:pname`, `:documentNo`), the `QueryListResolver` wraps them with `%` wildcards for LIKE matching. If the user doesn't provide a value, the default is `%` (match all).

---

## 5. Preconfigured Widgets (Shipped with Module)

The module ships with 21 widget class definitions:

### Navigation Widgets (no HQL)
| Name | Type | Description | Refresh |
|---|---|---|---|
| `favorites` | FAVORITES | Bookmarked workspace elements | 0 |
| `recent-docs` | RECENT_DOCS | Recently accessed documents | 0 |
| `recently-viewed` | RECENTLY_VIEWED | Recently viewed windows/entities | 0 |
| `notifications` | NOTIFICATION | User alerts from AD_Note | 60s |

### Data Widgets (HQL-based)
| Name | Type | Description | Refresh |
|---|---|---|---|
| `best-sellers` | QUERY_LIST | Top products by ordered quantity | 0 |
| `pending-goods-receipt` | QUERY_LIST | PO lines past delivery date | 0 |
| `invoices-to-collect` | QUERY_LIST | Unpaid sales invoices | 0 |
| `invoices-to-pay` | QUERY_LIST | Unpaid purchase invoices | 0 |
| `stock-by-warehouse` | QUERY_LIST | Stock levels by warehouse/product | 0 |
| `simple-stock-by-warehouse` | QUERY_LIST | Simplified stock view | 0 |
| `my-current-timesheets` | QUERY_LIST | Current month timesheet lines | 0 |
| `quotations` | QUERY_LIST | Quotation status summary | 0 |
| `payment-in-awaiting` | QUERY_LIST | Received payments awaiting execution | 0 |
| `payment-out-awaiting` | QUERY_LIST | Outgoing payments awaiting execution | 0 |
| `stock-alert` | STOCK_ALERT | Products below minimum stock | 300s |
| `kpi` | KPI | Generic KPI metric (configure HQL) | 300s |

### External/Integration Widgets
| Name | Type | Description | Refresh |
|---|---|---|---|
| `google-calendar` | URL | Embeddable Google Calendar iframe | 0 |
| `fiscal-calendar` | CALENDAR | ERP fiscal calendar periods | 0 |
| `copilot` | COPILOT | Copilot AI assistant | 0 |
| `proxy` | PROXY | External data via HTTP proxy | 300s |
| `html-content` | HTML | Custom HTML content | 0 |
| `process` | PROCESS | Execute an AD_Process | 0 |

### Navigation Links Parameter

Most `QUERY_LIST` widgets include a fixed `links` parameter that enables click-through navigation from widget data to the corresponding Etendo record. The format is:

```json
{
  "columnAlias": {
    "idCol": "hqlAliasContainingTheRecordId",
    "entity": "DALEntityName"
  }
}
```

Example for "Invoices to Collect":
```json
{
  "documentNo": {"idCol": "invoiceId", "entity": "Invoice"},
  "businessPartnerName": {"idCol": "businessPartnerId", "entity": "BusinessPartner"}
}
```

This tells the frontend: "when the user clicks the `documentNo` cell, navigate to the Invoice window for the record whose ID is in the `invoiceId` column".

---

## 6. AD Windows (User Interface)

Two windows are available in the **Workspace** menu folder for managing widgets:

### 6.1 Widget Class Window

**Menu path:** Workspace > Widget Class  
**Purpose:** Define and manage widget type definitions.

**Header tab (Widget Class):**

| Field | Description | When to use |
|---|---|---|
| Name | Internal identifier (unique, lowercase, hyphenated) | Always required |
| Type | Widget type enum (determines resolver) | Always required |
| Title | Display name in the dashboard | Always required |
| Description | Explanation or HTML content (for HTML type) | Recommended |
| Resolver Class | Custom Java resolver FQCN | Only for custom resolver types |
| HQL Query | Query string for QUERY_LIST/KPI widgets | Required for QUERY_LIST and KPI types |
| External Data URL | HTTPS endpoint for PROXY type or custom data | Only for PROXY type or external integration |
| Default Width | Initial grid column span (1-4) | Always has a default (2) |
| Default Height | Initial grid row span | Always has a default (1) |
| Refresh Interval | Auto-refresh seconds (0=disabled) | Set >0 for live-data widgets |

**Child tab (Widget Params):**

| Field | Description | When to use |
|---|---|---|
| Name | Parameter key (must match `:paramName` in HQL if used) | Always required |
| Display Name | Label in the configuration form | Recommended |
| Type | Input type: TEXT, NUMBER, BOOLEAN, DATE, LIST, FK | Always required |
| Default Value | Initial value when creating an instance | Recommended |
| List Values | Comma-separated `value:label` pairs | Only for TYPE=LIST |
| FK Table | Entity name for foreign-key selector | Only for TYPE=FK |
| FK Display Column | Column to display in FK selector | Only for TYPE=FK |
| Is Required | Must have a value | Set as needed |
| Is Fixed | Not user-editable (always uses default) | For system-internal params like `links` |
| Sequence | Display order in the config form | Always recommended |

### 6.2 Dashboard Widget Window

**Menu path:** Workspace > Dashboard Widget  
**Purpose:** Configure default dashboard layouts by layer (SYSTEM or CLIENT). USER-layer records are managed by end users through the dashboard UI.

**Tab (Dashboard Widget):**

| Field | Description | When to use |
|---|---|---|
| Widget Class | FK to the widget class definition | Always required |
| Layer | SYSTEM or CLIENT | Always required |
| Column Position | Grid column (0-based) | Set layout position |
| Row Position | Grid row (0-based) | Set layout position |
| Width | Grid columns occupied | Override class default |
| Height | Grid rows occupied | Override class default |
| Visible | Show/hide toggle | Default Y |
| Sequence | Priority when positions overlap | Recommended |
| Parameters JSON | JSON with parameter overrides | Override class defaults |

---

## 7. How to Define a New Widget

### 7.1 Using an Existing Type (No Java)

For QUERY_LIST or KPI widgets, no Java code is needed:

1. **Create the Widget Class** in the Widget Class window:
   - Set Name (e.g., `top-customers`)
   - Set Type = `QUERY_LIST`
   - Set Title (e.g., "Top Customers")
   - Write the HQL Query using named parameters for filters
   - Set Default Width/Height and Refresh Interval

2. **Add Parameters** in the Widget Params tab:
   - Add filter params that match `:paramName` in HQL (e.g., `customerName`)
   - Add a `links` param (TYPE=TEXT, IS_FIXED=Y) with the navigation JSON

3. **Add to Default Dashboard** in the Dashboard Widget window:
   - Create a SYSTEM-layer instance pointing to your widget class
   - Set position and size

4. **Run `update.database`** or `smartbuild` to export the new AD records to XML.

### 7.2 Using a Custom Resolver (Java Required)

For widget types that need custom data fetching logic:

1. Create a Java class implementing `WidgetDataResolver`:
   ```java
   public class MyCustomResolver implements WidgetDataResolver {
       @Override
       public String getType() { return "MY_CUSTOM_TYPE"; }

       @Override
       public JSONObject resolve(WidgetDataContext ctx) throws Exception {
           // ctx.getWidgetClass()  — the ETMETA_WIDGET_CLASS entity
           // ctx.getInstanceId()   — the dashboard widget instance ID
           // ctx.getParameters()   — merged params (defaults + overrides)
           JSONObject data = new JSONObject();
           // ... fetch and build data ...
           return data;
       }
   }
   ```

2. Register the resolver as a CDI bean (add `@javax.inject.Named` or ensure it's discovered by Weld).

3. Add the new TYPE value to the `ETMETA_WCL_TYPE_CHECK` constraint in `ETMETA_WIDGET_CLASS.xml`.

4. Create the Widget Class record with `RESOLVER_CLASS` pointing to your FQCN.

### 7.3 Using External Data (PROXY Type)

For data from external APIs without writing Java:

1. Create a Widget Class with TYPE = `PROXY`
2. Set `EXTERNAL_DATA_URL` to the HTTPS endpoint
3. The `ProxyResolver` will forward GET requests to that URL, including the Etendo bearer token in the Authorization header
4. The external endpoint must return JSON

---

## 8. Security

### URL Parameter Validation

Widget parameters that contain `:` are validated as URLs:
- Must start with `https://`
- Host must be parseable and not contain `@` or `\` (prevents host confusion attacks)
- Blocks `javascript:`, `data:`, and `http://` schemes

### Proxy Resolver Security

The `PROXY` type forwards the authenticated user's bearer token to the external URL. Only use trusted HTTPS endpoints as `EXTERNAL_DATA_URL`.

### Layer Access Control

- Only users with the **Client Admin** role (`isClientAdmin()`) can modify SYSTEM/CLIENT layer widgets via the PUT layout endpoint
- Regular users can only create/modify USER-layer records
- The Dashboard Widget window itself is role-gated by standard Etendo window access

---

## 9. API Reference (Widget Endpoints)

### GET /meta/dashboard/layout

Returns the resolved widget layout for the current user.

**Response:**
```json
{
  "widgets": [
    {
      "instanceId": "ABC123...",
      "widgetClassId": "DEF456...",
      "name": "best-sellers",
      "type": "QUERY_LIST",
      "title": "Best Sellers",
      "refreshInterval": 0,
      "available": true,
      "col": 0,
      "row": 0,
      "width": 4,
      "height": 2,
      "isVisible": true,
      "seqno": 10,
      "params": "{\"links\":\"{...}\"}"
    }
  ]
}
```

### GET /meta/widget/{instanceId}/data

Fetches data for a specific widget instance. The response shape depends on the widget type.

### GET /meta/widget/classes

Lists all available widget class definitions (for the widget picker UI).

### PUT /meta/dashboard/layout

Batch-update widget positions, sizes, and visibility.

**Request body:**
```json
{
  "widgets": [
    { "instanceId": "ABC123...", "col": 2, "row": 0, "width": 4, "height": 2, "isVisible": true }
  ]
}
```

### POST /meta/dashboard/widget

Add a new widget instance to the dashboard.

**Request body:**
```json
{
  "widgetClassId": "DEF456...",
  "col": 0,
  "row": 4,
  "width": 2,
  "height": 2,
  "parameters": { "src": "https://example.com" }
}
```

### DELETE /meta/dashboard/widget/{instanceId}

Remove a widget. USER-layer records are hard-deleted. SYSTEM/CLIENT records get a USER-layer shadow with `ISVISIBLE=N`.

### PATCH /meta/dashboard/widget/{instanceId}/params

Update widget instance parameters.

**Request body:**
```json
{
  "parameters": { "src": "https://new-url.com" }
}
```

---

## 10. Classic Widget Migration Status

The new system replaces the legacy `org.openbravo.client.myob` widget framework (`OBKMO_WidgetClass` / `OBKMO_WidgetInstance`). Below is the full mapping of classic widgets and their migration status.

### Classic Architecture (for context)

The old system used Java provider classes with superclass inheritance:
- `URLWidgetProvider` (superclass) — rendered an iframe with a configurable URL
- `QueryListWidgetProvider` (superclass) — executed HQL and rendered a table
- `HTMLWidgetProvider` (superclass) — rendered static HTML
- `CalendarWidgetProvider` (superclass) — rendered a calendar embed

Each concrete widget extended one of these superclasses. The new system replaces this with TYPE-based resolver dispatch (no inheritance, no SmartClient rendering).

### Migrated Widgets

These classic widgets were ported to the new system with their HQL queries adapted (stripped `@optional_filters@` macro, adapted parameter binding):

| Classic Name | Classic Module | New Name | New Type | Notes |
|---|---|---|---|---|
| Best Sellers | `org.openbravo.client.widgets` | `best-sellers` | QUERY_LIST | HQL preserved, params adapted |
| Invoices to Collect | `org.openbravo.client.widgets` | `invoices-to-collect` | QUERY_LIST | HQL preserved |
| Invoices to Pay | `org.openbravo.client.widgets` | `invoices-to-pay` | QUERY_LIST | HQL preserved |
| My Current Timesheets | `org.openbravo.client.widgets` | `my-current-timesheets` | QUERY_LIST | HQL preserved |
| Payment In - Awaiting | `org.openbravo.client.widgets` | `payment-in-awaiting` | QUERY_LIST | HQL preserved |
| Payment Out - Awaiting | `org.openbravo.client.widgets` | `payment-out-awaiting` | QUERY_LIST | HQL preserved |
| Pending Goods Receipt | `org.openbravo.client.widgets` | `pending-goods-receipt` | QUERY_LIST | HQL preserved |
| Stock by Warehouse | `org.openbravo.client.widgets` | `stock-by-warehouse` | QUERY_LIST | HQL preserved |
| Simple Stock by Warehouse | `org.openbravo.client.widgets` | `simple-stock-by-warehouse` | QUERY_LIST | HQL preserved |
| Quotations | `etendo-core` | `quotations` | QUERY_LIST | HQL preserved |
| Google Calendar (URL) | `org.openbravo.client.widgets` | `google-calendar` | URL | Converted from URLWidgetProvider to native URL type |

### NOT Migrated — Deprecated / Obsolete

These widgets were intentionally **not** migrated because they reference external services that no longer exist, or content that is irrelevant to Etendo:

| Classic Name | Classic Module | Reason for exclusion |
|---|---|---|
| Openbravo's Twitter | `org.openbravo.client.widgets` | Points to `@openaborvo` Twitter feed. Openbravo-specific branding, not relevant to Etendo. |
| Openbravo's Twitter (Spanish) | `org.openbravo.client.widgets` | Same as above, Spanish variant. |
| Planet (blog feed) | `org.openbravo.client.widgets` | Points to `planet.openbravo.com` RSS feed. Service no longer exists. |
| Openbravo 3.0 Overall Status | `org.openbravo.client.widgets` | Points to a deprecated Openbravo project status page. |
| Insights | `org.openbravo.client.widgets` | Points to `insight.openbravo.com`. Service no longer exists. |
| Sector Summary | `org.openbravo.client.widgets` | Points to a deprecated Openbravo market data page. |
| Motion Chart | `org.openbravo.client.widgets` | Embedded a Google Motion Chart via a deprecated Openbravo URL. Google Motion Charts API was deprecated in 2017. |
| Currency Converter | `org.openbravo.client.widgets` | Embedded an external currency converter iframe. The URL is no longer maintained and many free currency converter embeds have been discontinued. |
| Google Docs | `org.openbravo.client.widgets` | Embedded a Google Docs iframe. Too generic and not useful as a default widget. Users can recreate this using the `URL` type if needed. |

### NOT Migrated — Tutorial/Demo Widgets

These were instructional widgets shipped to help developers learn the classic widget system. They are not needed in the new system since the widget architecture is fundamentally different:

| Classic Name | Classic Module | Reason for exclusion |
|---|---|---|
| How to create simple URL widgets | `org.openbravo.client.widgets` | Tutorial content for the old URLWidgetProvider. Not applicable to the new TYPE-based system. |
| How to create Query and HTML widgets | `org.openbravo.client.htmlwidget` | Tutorial for old QueryList/HTML superclass inheritance pattern. Replaced by this documentation. |

### NOT Migrated — Superclasses (Architecture Change)

The classic superclass definitions are not needed in the new system. The new system uses TYPE-based dispatch instead of Java class inheritance:

| Classic Superclass | Classic Module | New Equivalent |
|---|---|---|
| URLWidgetProvider | `org.openbravo.client.myob` | `URL` type + `URLResolver` |
| CalendarWidgetProvider | `org.openbravo.client.myob` | `CALENDAR` type + `CalendarResolver` |
| QueryListWidgetProvider | `org.openbravo.client.querylist` | `QUERY_LIST` type + `QueryListResolver` |
| HTMLWidgetProvider | `org.openbravo.client.htmlwidget` | `HTML` type + `HTMLResolver` |

### NOT Migrated — Replaced by Better Alternatives

| Classic Name | Classic Module | Replacement |
|---|---|---|
| User defined HTML Widget | `org.openbravo.client.htmlwidget` | Generic `html-content` widget class (TYPE=HTML). Users create instances with custom HTML in the Description field. Same functionality, simpler architecture. |
| Openbravo's Google Calendar (2 variants) | `org.openbravo.client.widgets` | `google-calendar` widget (TYPE=URL) with configurable `src` parameter. Also `fiscal-calendar` (TYPE=CALENDAR) for ERP calendar data. |

### New Widgets (No Classic Equivalent)

These widgets are new to the Etendo UI and did not exist in the classic system:

| Name | Type | Description |
|---|---|---|
| `favorites` | FAVORITES | User-bookmarked records — new concept in the new UI |
| `recent-docs` | RECENT_DOCS | Recently accessed documents — replaces Classic's "recent documents" sidebar |
| `recently-viewed` | RECENTLY_VIEWED | Navigation history — replaces Classic's "recent views" sidebar |
| `notifications` | NOTIFICATION | Alert center — Classic had a separate notification popup, not a widget |
| `stock-alert` | STOCK_ALERT | Minimum stock monitoring — new dedicated widget type |
| `kpi` | KPI | Generic KPI metric — new; Classic had no single-value metric widget |
| `copilot` | COPILOT | AI assistant — new; integrates with Etendo Copilot module |
| `process` | PROCESS | Process execution — new; run AD_Process from the dashboard |
| `proxy` | PROXY | External API proxy — new; fetches JSON from any HTTPS endpoint |
| `fiscal-calendar` | CALENDAR | Fiscal periods and non-business days from the ERP — new |

---

## 11. Known Limitations & Pending Work

| Area | Status | Details |
|---|---|---|
| Navigation log (ETMETA_NAV_LOG) | Deferred | RECENT_DOCS/RECENTLY_VIEWED rely on frontend localStorage signal. Server-side nav tracking not yet implemented. History lost on browser clear. |
| Widget title translations | Not implemented | TRL tables for ETMETA_WIDGET_CLASS.TITLE not set up. |
| FK parameter selector UI | Not implemented | TYPE=FK is defined in schema but frontend config form doesn't support FK selectors yet. Only TEXT, NUMBER, BOOLEAN, DATE, LIST are usable. |
| Instance-level refresh interval | Not supported | Refresh interval is class-level only. All instances of a class share the same interval. |
| Process streaming | Not supported | PROCESS widgets execute synchronously. No progress reporting for long-running processes. |
| Widget class creation from UI only | Partial | New TYPE enum values require a DDL constraint update (CHECK constraint) and a matching Java resolver. Can't add fully new types from the UI alone. |
| User-facing widget config form | Frontend-dependent | Parameter editing UI depends on the frontend implementation in `com.etendoerp.mainui`. |
