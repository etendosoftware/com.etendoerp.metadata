# Widget System — Backend Design

**Date:** 2026-04-17  
**Module:** `com.etendoerp.metadata`  
**Status:** Approved  

---

## Context

The new Etendo UI (Next.js, `com.etendoerp.mainui`) needs a dashboard widget system. Etendo Classic had a widget system (`org.openbravo.client.myob`) built on SmartClient portlets, with `OBKMO_WidgetClass` and `OBKMO_WidgetInstance` as its DB backbone. This design replaces that system with a modern, API-driven equivalent that:

- Delivers widget layout and data through REST endpoints in `com.etendoerp.metadata`
- Supports migration of existing Classic widgets (QueryList, HTML, URL, Calendar)
- Supports new widget types visible in the new UI (Copilot, KPI/metrics, stock alerts, notifications, recent docs, favorites, sales order list)
- Uses a single data-fetch endpoint with internal typed resolvers
- Implements three-layer personalization: SYSTEM → CLIENT → USER

---

## Widget Types

| Type | Classic equivalent | Description |
|---|---|---|
| `FAVORITES` | — | Bookmarked tabs and records for the user |
| `RECENT_DOCS` | — | Recently created/modified documents by the user |
| `RECENTLY_VIEWED` | — | Recently navigated entities in session |
| `NOTIFICATION` | — | System alerts and notes |
| `STOCK_ALERT` | — | Products near or below minimum stock (vs. M_Product minimum stock level) |
| `KPI` | — | Single numeric metric with optional trend and chart |
| `COPILOT` | — | AI-powered recommendations from Copilot API |
| `QUERY_LIST` | QueryList widget | Tabular data from parameterized HQL |
| `HTML` | HTMLWidget | Static HTML content |
| `URL` | URLWidget | Embeds an external URL (iframe) |
| `PROCESS` | — | Executes a process and returns output |

---

## Database Model

### `ETMETA_WIDGET_CLASS` — Widget type registry

| Column | Type | Notes |
|---|---|---|
| `ETMETA_WIDGET_CLASS_ID` | VARCHAR(32) PK | UUID |
| `NAME` | VARCHAR(60) | Internal identifier, e.g. `favorites`, `kpi` |
| `TYPE` | VARCHAR(30) | Enum — see widget types above |
| `TITLE` | VARCHAR(255) | Display name (translatable via `_TRL` table) |
| `DESCRIPTION` | VARCHAR(2000) | |
| `RESOLVER_CLASS` | VARCHAR(255) | FQCN of the Java resolver, e.g. `com.etendoerp.metadata.widgets.resolvers.KPIResolver`. If null, falls back to `ProxyResolver` when `EXTERNAL_DATA_URL` is set. |
| `HQL_QUERY` | CLOB | HQL query string for `QUERY_LIST` and `KPI` resolvers. Supports named parameters matching `ETMETA_WIDGET_PARAM.NAME`. Ignored by other resolver types. |
| `EXTERNAL_DATA_URL` | VARCHAR(1000) | Optional — if set and `RESOLVER_CLASS` is null, `ProxyResolver` forwards calls here with the Etendo bearer token (see Security section). |
| `DEFAULT_WIDTH` | NUMBER(2) | Grid columns occupied (1–4) |
| `DEFAULT_HEIGHT` | NUMBER(2) | Grid rows occupied |
| `REFRESH_INTERVAL` | NUMBER(6) | Seconds between auto-refresh; 0 = disabled. Instance-level override is not supported — this is always a class-level setting. |
| `AD_MODULE_ID` | VARCHAR(32) FK | Module that provides this widget |
| `ISACTIVE` | CHAR(1) | |
| `AD_CLIENT_ID` | VARCHAR(32) FK | |
| `AD_ORG_ID` | VARCHAR(32) FK | |
| `CREATED` | TIMESTAMP | |
| `CREATEDBY` | VARCHAR(32) FK | |
| `UPDATED` | TIMESTAMP | |
| `UPDATEDBY` | VARCHAR(32) FK | |

---

### `ETMETA_WIDGET_PARAM` — Configurable parameters per widget class

| Column | Type | Notes |
|---|---|---|
| `ETMETA_WIDGET_PARAM_ID` | VARCHAR(32) PK | |
| `ETMETA_WIDGET_CLASS_ID` | VARCHAR(32) FK | |
| `NAME` | VARCHAR(60) | Parameter key, e.g. `rowsNumber`, `productCategory` |
| `DISPLAY_NAME` | VARCHAR(255) | Translatable |
| `TYPE` | VARCHAR(20) | `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, `LIST`, `FK` |
| `DEFAULT_VALUE` | VARCHAR(255) | |
| `LIST_VALUES` | VARCHAR(2000) | Comma-separated `value:label` pairs for `TYPE=LIST`, e.g. `last_week:Última semana,last_month:Último mes`. Ignored for other types. |
| `FK_TABLE` | VARCHAR(60) | Table name for `TYPE=FK`, e.g. `M_Product_Category`. Used to build the selector in the config form. |
| `FK_DISPLAY_COLUMN` | VARCHAR(60) | Column to display in the FK selector (defaults to the entity identifier if null). |
| `IS_REQUIRED` | CHAR(1) | |
| `IS_FIXED` | CHAR(1) | Fixed value, not user-editable |
| `SEQNO` | NUMBER(10) | Order in the config form |
| `ISACTIVE` | CHAR(1) | |
| `AD_CLIENT_ID` | VARCHAR(32) FK | |
| `AD_ORG_ID` | VARCHAR(32) FK | |
| `CREATED` | TIMESTAMP | |
| `CREATEDBY` | VARCHAR(32) FK | |
| `UPDATED` | TIMESTAMP | |
| `UPDATEDBY` | VARCHAR(32) FK | |

---

### `ETMETA_DASHBOARD_WIDGET` — Dashboard widget instances (layout)

| Column | Type | Notes |
|---|---|---|
| `ETMETA_DASHBOARD_WIDGET_ID` | VARCHAR(32) PK | |
| `ETMETA_WIDGET_CLASS_ID` | VARCHAR(32) FK | |
| `LAYER` | VARCHAR(10) | `SYSTEM`, `CLIENT`, `USER` |
| `AD_CLIENT_ID` | VARCHAR(32) FK | |
| `AD_ORG_ID` | VARCHAR(32) FK | |
| `AD_USER_ID` | VARCHAR(32) FK | Null for SYSTEM and CLIENT layers |
| `AD_ROLE_ID` | VARCHAR(32) FK | Optional — restricts visibility to a role |
| `COL_POSITION` | NUMBER(2) | Grid column (0-based) |
| `ROW_POSITION` | NUMBER(2) | Grid row (0-based) |
| `WIDTH` | NUMBER(2) | Column span (1–4) |
| `HEIGHT` | NUMBER(2) | Row span |
| `ISVISIBLE` | CHAR(1) | |
| `SEQNO` | NUMBER(10) | Order within column |
| `PARAMETERS_JSON` | CLOB | Instance-level parameter values as JSON. ORM mapping: TEXT on PostgreSQL, CLOB on Oracle — use `@Lob` annotation in the DAL entity class. |
| `ISACTIVE` | CHAR(1) | |
| `CREATED` | TIMESTAMP | |
| `CREATEDBY` | VARCHAR(32) FK | |
| `UPDATED` | TIMESTAMP | |
| `UPDATEDBY` | VARCHAR(32) FK | |

**Layer resolution rules:**

1. The API loads all `ETMETA_DASHBOARD_WIDGET` records visible to the current user: SYSTEM records (any client), CLIENT records matching `AD_CLIENT_ID`, and USER records matching `AD_USER_ID`.
2. Records are grouped by `ETMETA_WIDGET_CLASS_ID`. Within each group, USER overrides CLIENT, CLIENT overrides SYSTEM.
3. If no USER or CLIENT record exists for a given `ETMETA_WIDGET_CLASS_ID`, the SYSTEM record is used as-is with its original position and parameters.
4. A SYSTEM widget with `ISVISIBLE=N` at the USER layer hides the widget for that user only (see DELETE behavior below).

**DAL entity class name:** `EtmetaDashboardWidget` (following Etendo DAL naming: camel-case of the table name without underscores, first letter uppercase per segment).

---

## API Endpoints

All endpoints require JWT authentication via `com.etendoerp.metadata.auth.AuthenticationManager`.

### Error response envelope

All error responses follow:
```json
{ "status": 404, "error": "NOT_FOUND", "message": "Widget instance uuid not found" }
```

| HTTP status | Error code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 403 | `FORBIDDEN` | Valid JWT but insufficient role/org access |
| 404 | `NOT_FOUND` | Instance ID does not exist or is not visible to the user |
| 500 | `INTERNAL_ERROR` | Resolver threw an exception; message contains a safe description, not the stack trace |

---

### `GET /meta/dashboard/layout`

Returns the resolved dashboard layout for the current user after applying layer inheritance.

**Response:**
```json
{
  "widgets": [
    {
      "instanceId": "uuid",
      "widgetClassId": "uuid",
      "type": "KPI",
      "name": "productivity_index",
      "title": "Índice de productividad",
      "position": { "col": 3, "row": 1, "width": 1, "height": 2 },
      "refreshInterval": 300,
      "parameters": { "period": "last_week" },
      "layer": "SYSTEM"
    }
  ]
}
```

`refreshInterval` is always sourced from `ETMETA_WIDGET_CLASS.REFRESH_INTERVAL` and cannot be overridden per instance.

---

### `GET /meta/widget/{instanceId}/data`

The central data endpoint. Metadata identifies the widget class, selects the resolver, executes it, and always returns the same envelope. The frontend never needs to know which resolver ran.

The `data` field shape varies by type — see the **Data shapes by type** section below.

**Response envelope:**
```json
{
  "widgetInstanceId": "uuid",
  "type": "KPI",
  "data": { },
  "meta": {
    "lastUpdate": "2024-05-22T14:00:00Z",
    "totalRows": null,
    "hasMore": false
  }
}
```

If `RESOLVER_CLASS` is null and `EXTERNAL_DATA_URL` is set, `ProxyResolver` is used (see Security section).

---

### `GET /meta/widget/classes`

Returns all active `ETMETA_WIDGET_CLASS` records with their parameters. Used by the admin UI to browse available widget types.

**Response:**
```json
{
  "classes": [
    {
      "widgetClassId": "uuid",
      "name": "kpi",
      "type": "KPI",
      "title": "Índice de productividad",
      "description": "...",
      "defaultWidth": 1,
      "defaultHeight": 2,
      "refreshInterval": 300,
      "params": [
        {
          "name": "period",
          "displayName": "Período",
          "type": "LIST",
          "required": true,
          "fixed": false,
          "defaultValue": "last_week",
          "listValues": [{ "value": "last_week", "label": "Última semana" }, { "value": "last_month", "label": "Último mes" }]
        }
      ]
    }
  ]
}
```

---

### `PUT /meta/dashboard/layout`

Saves layout changes. Layer determination: if the current user has the `Dashboard Admin` permission (checked via `OBContext` role), the backend writes `LAYER=CLIENT` records; otherwise `LAYER=USER`. Only position, size, and visibility fields are updated — parameters require `POST /meta/dashboard/widget`.

**Request body:**
```json
{
  "widgets": [
    { "instanceId": "uuid", "col": 2, "row": 0, "width": 2, "height": 1, "isVisible": true }
  ]
}
```

---

### `POST /meta/dashboard/widget`

Adds a new widget instance to the current user's or client's dashboard. Layer is determined by the same `Dashboard Admin` permission rule as `PUT`.

**Request body:**
```json
{
  "widgetClassId": "uuid",
  "col": 0,
  "row": 3,
  "width": 2,
  "height": 1,
  "parameters": { "rowsNumber": 5 }
}
```

---

### `DELETE /meta/dashboard/widget/{instanceId}`

Behavior depends on the layer of the target record:

- **USER record:** deleted from the DB.
- **CLIENT or SYSTEM record:** a new `LAYER=USER` shadow record is inserted for the current user with `ISVISIBLE=N`. The original shared record is never mutated. This preserves the system/client default for all other users.

---

## Resolver Architecture

### Java contract

```java
public interface WidgetDataResolver {
    String getType(); // matches ETMETA_WIDGET_CLASS.TYPE
    JSONObject resolve(WidgetDataContext context) throws Exception;
}

public class WidgetDataContext {
    private final EtmetaDashboardWidget instance;
    private final Map<String, Object> params;  // merged: class defaults + instance PARAMETERS_JSON
    private final OBContext obContext;
}
```

`WidgetResolverRegistry` is a Weld `@ApplicationScoped` singleton that collects all `WidgetDataResolver` CDI beans on startup and indexes them by type. The `/meta/widget/{id}/data` endpoint delegates to it.

### Resolvers

| Resolver | Type | Data source |
|---|---|---|
| `FavoritesResolver` | `FAVORITES` | User favorites table |
| `RecentDocsResolver` | `RECENT_DOCS` | Recent document access log |
| `RecentlyViewedResolver` | `RECENTLY_VIEWED` | Session navigation log |
| `NotificationResolver` | `NOTIFICATION` | `AD_Note` / alert tables |
| `StockAlertResolver` | `STOCK_ALERT` | `M_Storage` — current stock vs. `M_Product.minimumstock` |
| `KPIResolver` | `KPI` | HQL from `ETMETA_WIDGET_CLASS.HQL_QUERY` with named params |
| `QueryListResolver` | `QUERY_LIST` | HQL from `ETMETA_WIDGET_CLASS.HQL_QUERY` with named params |
| `CopilotResolver` | `COPILOT` | Proxy to Copilot API with user context |
| `HTMLResolver` | `HTML` | Static HTML stored in `ETMETA_WIDGET_CLASS.DESCRIPTION` or a dedicated column |
| `URLResolver` | `URL` | Returns `EXTERNAL_DATA_URL` for frontend iframe rendering — does not proxy, just returns the URL |
| `ProcessResolver` | `PROCESS` | Executes an Etendo process and returns output |
| `ProxyResolver` | fallback | HTTP proxy to `EXTERNAL_DATA_URL` with bearer token; used when `RESOLVER_CLASS` is null |

**Note:** `URLResolver` and `ProxyResolver` both use `EXTERNAL_DATA_URL` but serve different purposes. `URLResolver` (type=`URL`) returns the URL to the frontend for iframe rendering. `ProxyResolver` (fallback when `RESOLVER_CLASS=null` and type is anything else) fetches the URL server-side and returns the response body as `data`.

---

## Data shapes by type

All are the `data` field inside the standard response envelope.

**FAVORITES**
```json
{ "items": [{ "label": "Pedido de venta", "icon": "sales-order", "windowId": "...", "tabId": "..." }] }
```

**RECENT_DOCS**
```json
{ "items": [{ "label": "F&B International Group", "type": "BusinessPartner", "recordId": "...", "windowId": "..." }] }
```

**RECENTLY_VIEWED**
```json
{ "items": [{ "label": "Pedido de venta", "icon": "...", "windowId": "...", "recordId": "..." }] }
```

**NOTIFICATION**
```json
{
  "items": [
    { "text": "Se ha calculado los costes para el 30 de Julio de 2024.", "priority": "normal", "time": "2024-07-30T13:59:00Z" },
    { "text": "Se ha detectado un error en la base de datos.", "priority": "high", "time": "2024-07-30T12:00:00Z" }
  ],
  "totalCount": 7
}
```

Priority values: `normal`, `high`, `success`.

**STOCK_ALERT**

`estimatedStock` is `M_Product.minimumstock` — the configured minimum stock level for the product, not a projection. `currentStock` is the sum of `M_StorageDetail.quantityonhand` for the product and organization.

```json
{
  "items": [
    {
      "productName": "Crema De Leche La Paulina 350 Cc",
      "imageUrl": "...",
      "currentStock": 34,
      "estimatedStock": 100,
      "unit": "Unidades"
    }
  ]
}
```

**KPI**
```json
{ "value": 95, "unit": "%", "label": "Facturas de ventas completadas", "trend": "up", "chartType": "donut" }
```

`trend` values: `up`, `down`, `neutral`, `null` (when trend calculation is not applicable).

**QUERY_LIST**
```json
{
  "columns": [{ "name": "order", "label": "Pedido" }, { "name": "org", "label": "Organización" }],
  "rows": [{ "order": "123456", "org": "F&B España", "total": 133005.00, "deliveryDate": "2011-09-15" }],
  "totalRows": 42
}
```

**COPILOT**

`actions[].type` values:
- `copilot_action`: the action is delegated back to Copilot (e.g. "Copilot se encarga"). The frontend POSTs to the Copilot conversation endpoint with the `actionId` payload. No additional fields required.
- `navigate`: the frontend opens the window/record. Requires `windowId` and optionally `recordId`.
- `external_url`: opens a URL in a new tab. Requires `url`.

```json
{
  "messages": [
    {
      "text": "El producto 'Arroz Largo Fino Crucero 1K' se está quedando sin stock.",
      "actions": [
        { "label": "Copilot se encarga", "type": "copilot_action", "actionId": "restock-suggestion-xyz" },
        { "label": "Hacerlo yo", "type": "navigate", "windowId": "...", "recordId": "..." }
      ]
    }
  ]
}
```

**HTML**
```json
{ "content": "<p>...</p>" }
```

**URL**
```json
{ "url": "https://...", "sandbox": true }
```

`sandbox: true` instructs the frontend to apply the `sandbox` attribute to the iframe for isolation.

**PROCESS**
```json
{
  "status": "success",
  "message": "Process completed successfully.",
  "result": { }
}
```

`status` values: `success`, `error`, `warning`. `result` is process-specific and can be an empty object.

---

## Security — ProxyResolver credential forwarding

When `ProxyResolver` forwards a request to `EXTERNAL_DATA_URL`, it adds the following header:

```
Authorization: Bearer <etendo-jwt-token>
```

The token is the same bearer JWT used to authenticate the original `/meta/widget/{id}/data` request, extracted from the incoming `Authorization` header and forwarded as-is. The target URL must be a trusted internal endpoint (e.g. an EtendoRX service or Copilot API). Forwarding to arbitrary external URLs is a configuration responsibility — `ProxyResolver` does not validate the target domain.

---

## Migration from Classic widgets

`ClassicWidgetMigrationProcess` (runnable from the Etendo menu):

1. Reads active `OBKMO_WidgetClass` records.
2. Maps each to an `ETMETA_WIDGET_CLASS` record:
   - `OBQueryListWidget` → `QUERY_LIST` — copies the HQL query to `HQL_QUERY`
   - `OBHTMLWidget` → `HTML`
   - `OBURLWidget` → `URL` — copies the URL to `EXTERNAL_DATA_URL`
   - `OBCalendarWidget` → `URL` — copies the calendar URL to `EXTERNAL_DATA_URL` (rendered as iframe, not proxied)
   - Others → logged for manual review; not auto-migrated
3. Reads `OBKMO_WidgetInstance` per user, creates `ETMETA_DASHBOARD_WIDGET` records with `LAYER=USER`.
4. Copies parameter values to `PARAMETERS_JSON`.
5. Produces a migration report listing all unmapped widget classes.

**Note on Calendar migration:** `OBCalendarWidget` maps to type `URL` with `URLResolver`, which returns the URL to the frontend for iframe rendering. It does **not** use `ProxyResolver`, even though both use `EXTERNAL_DATA_URL`. The distinction is the `TYPE` value: `URL` → `URLResolver`; anything else with `RESOLVER_CLASS=null` → `ProxyResolver`.

---

## Out of scope (deferred)

- **Live drag-and-drop layout editing from the UI:** `PUT /meta/dashboard/layout` already supports it, but the frontend implementation is deferred. When ready, the front sends position updates and the endpoint persists them.
- **Per-user widget visibility toggles in the UI:** the data model supports it via `ISVISIBLE` and the DELETE shadow-record mechanism, but the frontend toggle is deferred.
- **Widget marketplace / install from registry:** future milestone.
