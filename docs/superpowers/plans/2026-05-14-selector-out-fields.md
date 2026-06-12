# Selector Out Fields Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose OBUISEL selector out-field mappings in the field metadata JSON so the frontend can generically populate related form fields and callout inputs.

**Architecture:** Add a static `addOutFields` method to `FieldBuilder` that iterates out-fields from the selector and resolves target mappings from the tab's field list. Call it from `FieldBuilderWithColumn.addComboSelectInfo` after building the base selector JSON. Two out-field types: `field` (AD_Field mapping) and `calloutInput` (suffix-only).

**Tech Stack:** Java 17, Mockito 5, JUnit 5, jettison JSON, Etendo DAL (ORM)

**Spec:** `docs/superpowers/specs/2026-05-14-selector-out-fields-design.md`

---

### Task 1: Add `addOutFields` method to FieldBuilder with tests

**Files:**
- Modify: `src/com/etendoerp/metadata/builders/FieldBuilder.java:331` (after `addSelectorInfo`)
- Test: `src-test/src/com/etendoerp/metadata/builders/FieldBuilderTest.java`

- [ ] **Step 1: Write the failing test — type "field" out-field with AD_Field match**

In `FieldBuilderTest.java`, add the following test. It verifies that when a SelectorField has `isOutfield=true` and a tab field references it via `obuiselOutfield`, the `outFields` array contains a `"field"` type entry with the correct mapping.

Note: `getHqlName` is `protected static` and uses `field.getColumn().getTable().getDataOriginType()` internally. When the full chain isn't mocked, it catches the exception and falls back to camelCasing `field.getName()`. We mock `getHqlName` explicitly to avoid relying on that fallback.

```java
@Test
void testAddOutFieldsWithFieldTypeMapping() throws JSONException {
    // Setup selector field marked as out-field
    SelectorField outSelectorField = mock(SelectorField.class);
    when(outSelectorField.isOutfield()).thenReturn(true);
    when(outSelectorField.isActive()).thenReturn(true);
    when(outSelectorField.getProperty()).thenReturn("paymentTerms");
    when(outSelectorField.getSuffix()).thenReturn(null);

    when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSelectorField));

    // Setup tab field that references this out-field
    Field targetField = mock(Field.class);
    Column targetColumn = mock(Column.class);
    when(targetField.getObuiselOutfield()).thenReturn(outSelectorField);
    when(targetField.getColumn()).thenReturn(targetColumn);
    when(targetColumn.getDBColumnName()).thenReturn("C_PaymentTerm_ID");
    when(targetField.getName()).thenReturn("Payment Terms");

    Tab mockTab = mock(Tab.class);
    when(mockTab.getADFieldList()).thenReturn(List.of(targetField));

    JSONObject selectorJson = new JSONObject();

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(outSelectorField))
                .thenReturn("paymentTerms");
        mockedStatic.when(() -> FieldBuilder.getHqlName(targetField))
                .thenReturn("paymentTerms");

        FieldBuilder.addOutFields(selectorJson, selector, mockTab);
    }

    assertTrue(selectorJson.has("outFields"));
    JSONArray outFields = selectorJson.getJSONArray("outFields");
    assertEquals(1, outFields.length());

    JSONObject entry = outFields.getJSONObject(0);
    assertEquals("field", entry.getString("type"));
    assertEquals("paymentTerms", entry.getString("selectorFieldProperty"));
    assertEquals("C_PaymentTerm_ID", entry.getString("targetColumnName"));
    assertEquals("paymentTerms", entry.getString("targetHqlName"));
}
```

Note: `Tab` is already imported in `FieldBuilderTest.java`.

- [ ] **Step 2: Write the failing test — type "calloutInput" with suffix only**

```java
@Test
void testAddOutFieldsWithCalloutInputType() throws JSONException {
    // Selector field with suffix but no tab field references it
    SelectorField outSelectorField = mock(SelectorField.class);
    when(outSelectorField.isOutfield()).thenReturn(true);
    when(outSelectorField.isActive()).thenReturn(true);
    when(outSelectorField.getProperty()).thenReturn("currency");
    when(outSelectorField.getSuffix()).thenReturn("_CURR");

    when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSelectorField));

    // Tab has no field referencing this out-field
    Tab mockTab = mock(Tab.class);
    when(mockTab.getADFieldList()).thenReturn(Collections.emptyList());

    JSONObject selectorJson = new JSONObject();

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(outSelectorField))
                .thenReturn("currency");

        FieldBuilder.addOutFields(selectorJson, selector, mockTab);
    }

    assertTrue(selectorJson.has("outFields"));
    JSONArray outFields = selectorJson.getJSONArray("outFields");
    assertEquals(1, outFields.length());

    JSONObject entry = outFields.getJSONObject(0);
    assertEquals("calloutInput", entry.getString("type"));
    assertEquals("currency", entry.getString("selectorFieldProperty"));
    assertEquals("_CURR", entry.getString("suffix"));
    assertTrue(entry.isNull("targetColumnName"));
    assertTrue(entry.isNull("targetHqlName"));
}
```

- [ ] **Step 3: Write the failing test — no out-fields produces no key**

```java
@Test
void testAddOutFieldsEmptySelectorFieldsOmitsKey() throws JSONException {
    when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());

    Tab mockTab = mock(Tab.class);
    when(mockTab.getADFieldList()).thenReturn(Collections.emptyList());

    JSONObject selectorJson = new JSONObject();
    FieldBuilder.addOutFields(selectorJson, selector, mockTab);

    assertFalse(selectorJson.has("outFields"));
}
```

- [ ] **Step 4: Write the failing test — out-field with no match and no suffix is skipped**

```java
@Test
void testAddOutFieldsNoMatchNoSuffixSkipped() throws JSONException {
    SelectorField outSelectorField = mock(SelectorField.class);
    when(outSelectorField.isOutfield()).thenReturn(true);
    when(outSelectorField.isActive()).thenReturn(true);
    when(outSelectorField.getProperty()).thenReturn("someProperty");
    when(outSelectorField.getSuffix()).thenReturn(null);

    when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSelectorField));

    Tab mockTab = mock(Tab.class);
    when(mockTab.getADFieldList()).thenReturn(Collections.emptyList());

    JSONObject selectorJson = new JSONObject();

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(outSelectorField))
                .thenReturn("someProperty");

        FieldBuilder.addOutFields(selectorJson, selector, mockTab);
    }

    assertFalse(selectorJson.has("outFields"));
}
```

- [ ] **Step 5: Write the failing test — field type with suffix populated**

```java
@Test
void testAddOutFieldsFieldTypeWithSuffix() throws JSONException {
    SelectorField outSelectorField = mock(SelectorField.class);
    when(outSelectorField.isOutfield()).thenReturn(true);
    when(outSelectorField.isActive()).thenReturn(true);
    when(outSelectorField.getProperty()).thenReturn("storageBin");
    when(outSelectorField.getSuffix()).thenReturn("_LOC");

    when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSelectorField));

    // Tab field references this out-field
    Field targetField = mock(Field.class);
    Column targetColumn = mock(Column.class);
    when(targetField.getObuiselOutfield()).thenReturn(outSelectorField);
    when(targetField.getColumn()).thenReturn(targetColumn);
    when(targetColumn.getDBColumnName()).thenReturn("M_Locator_ID");
    when(targetField.getName()).thenReturn("Storage Bin");

    Tab mockTab = mock(Tab.class);
    when(mockTab.getADFieldList()).thenReturn(List.of(targetField));

    JSONObject selectorJson = new JSONObject();

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
        mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(outSelectorField))
                .thenReturn("storageBin");
        mockedStatic.when(() -> FieldBuilder.getHqlName(targetField))
                .thenReturn("storageBin");

        FieldBuilder.addOutFields(selectorJson, selector, mockTab);
    }

    JSONArray outFields = selectorJson.getJSONArray("outFields");
    JSONObject entry = outFields.getJSONObject(0);
    assertEquals("field", entry.getString("type"));
    assertEquals("storageBin", entry.getString("selectorFieldProperty"));
    assertEquals("M_Locator_ID", entry.getString("targetColumnName"));
    assertEquals("storageBin", entry.getString("targetHqlName"));
    assertEquals("_LOC", entry.getString("suffix"));
}
```

- [ ] **Step 6: Run all new tests to verify they fail**

Run: `./gradlew test --tests "com.etendoerp.metadata.builders.FieldBuilderTest.testAddOutFields*" -p modules/com.etendoerp.metadata`
Expected: Compilation fails — method `addOutFields` does not exist yet. This is expected.

- [ ] **Step 7: Implement `addOutFields` in FieldBuilder.java**

Add the following method after `addSelectorInfo` (after line 331) in `FieldBuilder.java`:

```java
/**
 * Adds out-field mappings to the selector JSON for a custom OBUISEL selector.
 * Iterates selector fields marked as out-fields and resolves target mappings
 * from the tab's field list.
 *
 * Two types of out-field entries are produced:
 * <ul>
 *   <li>{@code "field"} — the selector field is referenced by an AD_Field in the tab
 *       via {@code obuiselOutfield}. The frontend uses this to set a form field value directly.</li>
 *   <li>{@code "calloutInput"} — the selector field has a suffix but no AD_Field references it.
 *       The frontend uses this to populate the callout payload.</li>
 * </ul>
 *
 * If no out-field entries are produced, the {@code "outFields"} key is omitted from the JSON.
 *
 * @param selectorJson The selector JSON object to augment with out-fields
 * @param selector     The OBUISEL selector entity
 * @param tab          The tab containing the field that uses this selector
 * @throws JSONException if there's an error updating the JSON structure
 */
public static void addOutFields(JSONObject selectorJson, Selector selector, Tab tab)
        throws JSONException {
    List<SelectorField> outFields = selector.getOBUISELSelectorFieldList().stream()
            .filter(sf -> Boolean.TRUE.equals(sf.isOutfield()) && Boolean.TRUE.equals(sf.isActive()))
            .collect(Collectors.toList());

    if (outFields.isEmpty()) {
        return;
    }

    List<Field> tabFields = tab.getADFieldList();
    JSONArray outFieldsArray = new JSONArray();

    for (SelectorField sf : outFields) {
        String selectorFieldProperty = getPropertyOrDataSourceField(sf);
        List<Field> matchedFields = tabFields.stream()
                .filter(f -> sf.equals(f.getObuiselOutfield()))
                .collect(Collectors.toList());

        if (!matchedFields.isEmpty()) {
            for (Field matchedField : matchedFields) {
                outFieldsArray.put(buildOutFieldEntry(
                        "field", selectorFieldProperty, matchedField, sf.getSuffix()));
            }
        } else if (sf.getSuffix() != null && !sf.getSuffix().isEmpty()) {
            outFieldsArray.put(buildCalloutInputEntry(selectorFieldProperty, sf.getSuffix()));
        }
    }

    if (outFieldsArray.length() > 0) {
        selectorJson.put("outFields", outFieldsArray);
    }
}

private static JSONObject buildOutFieldEntry(String type, String selectorFieldProperty,
        Field targetField, String suffix) throws JSONException {
    JSONObject entry = new JSONObject();
    entry.put("type", type);
    entry.put("selectorFieldProperty", selectorFieldProperty);
    entry.put("targetColumnName", targetField.getColumn().getDBColumnName());
    entry.put("targetHqlName", getHqlName(targetField));
    entry.put("suffix", suffix);
    return entry;
}

private static JSONObject buildCalloutInputEntry(String selectorFieldProperty, String suffix)
        throws JSONException {
    JSONObject entry = new JSONObject();
    entry.put("type", "calloutInput");
    entry.put("selectorFieldProperty", selectorFieldProperty);
    entry.put("targetColumnName", (Object) null);
    entry.put("targetHqlName", (Object) null);
    entry.put("suffix", suffix);
    return entry;
}
```

Add the `Tab` import at the top of `FieldBuilder.java` if not already present:
```java
import org.openbravo.model.ad.ui.Tab;
```

- [ ] **Step 8: Run all new tests to verify they pass**

Run: `./gradlew test --tests "com.etendoerp.metadata.builders.FieldBuilderTest.testAddOutFields*" -p modules/com.etendoerp.metadata`
Expected: All 5 tests PASS

- [ ] **Step 9: Run full FieldBuilderTest suite to check no regressions**

Run: `./gradlew test --tests "com.etendoerp.metadata.builders.FieldBuilderTest" -p modules/com.etendoerp.metadata`
Expected: All existing tests PASS

- [ ] **Step 10: Commit**

```bash
git add src/com/etendoerp/metadata/builders/FieldBuilder.java \
        src-test/src/com/etendoerp/metadata/builders/FieldBuilderTest.java
git commit -m "Feature ETP-3757: Add addOutFields method to FieldBuilder with tests"
```

---

### Task 2: Wire `addOutFields` into FieldBuilderWithColumn

**Files:**
- Modify: `src/com/etendoerp/metadata/builders/FieldBuilderWithColumn.java:352-361`
- Test: `src-test/src/com/etendoerp/metadata/builders/FieldBuilderWithColumnTest.java`

- [ ] **Step 1: Write the failing test — addComboSelectInfo calls addOutFields for OBUISEL selectors**

In `FieldBuilderWithColumnTest.java`, add a test that uses the existing `executeToJSON` helper pattern (which sets up `OBContext`, `KernelUtils`, `DataSourceUtils`, and `DataToJsonConverter` mocks). The test verifies that when a field uses an OBUISEL selector reference, the resulting JSON has `selector.outFields`.

Add necessary imports at the top if not already present:
```java
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;
import com.etendoerp.metadata.data.ReferenceSelectors;
```

```java
@Test
void testToJSONIncludesOutFieldsForObuiselSelector() throws JSONException {
    // Setup: OBUISEL selector reference
    Selector mockSelector = mock(Selector.class);

    SelectorField outSf = mock(SelectorField.class);
    when(outSf.isOutfield()).thenReturn(true);
    when(outSf.isActive()).thenReturn(true);
    when(outSf.getProperty()).thenReturn("paymentTerms");
    when(outSf.getSuffix()).thenReturn(null);
    when(mockSelector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSf));

    // Target tab field that references the out-field
    Field targetField = mock(Field.class);
    Column targetCol = mock(Column.class);
    when(targetField.getObuiselOutfield()).thenReturn(outSf);
    when(targetField.getColumn()).thenReturn(targetCol);
    when(targetCol.getDBColumnName()).thenReturn("C_PaymentTerm_ID");
    when(targetField.getName()).thenReturn("Payment Terms");

    // Wire the tab to return the target field
    when(tab.getADFieldList()).thenReturn(List.of(targetField));

    // Set reference to a selector type
    when(reference.getId()).thenReturn("95E2A8B50A254B2AAE6774B8C2F28120");

    JSONObject json = executeToJSON(() -> {
        // No extra mocks needed beyond what executeToJSON provides
    });

    // The test validates that addComboSelectInfo wires through to addOutFields.
    // Since getSelectorInfo and getReferenceSelectors use real static calls,
    // we need to mock them. Use the FieldBuilder static mock approach:
    // If this doesn't work cleanly with executeToJSON, fall back to testing
    // addComboSelectInfo via reflection with invokePrivate.
    // See alternative approach below.
}
```

**Alternative (recommended): test via `invokePrivate` on `addComboSelectInfo` directly.**

This avoids fighting with `toJSON()`'s complex mock setup. The test creates a `FieldBuilderWithColumn`, sets its internal `json` field, and invokes the private `addComboSelectInfo`:

```java
@Test
void testAddComboSelectInfoIncludesOutFieldsForObuiselSelector() throws Exception {
    // Setup: OBUISEL selector
    Selector mockSelector = mock(Selector.class);

    SelectorField outSf = mock(SelectorField.class);
    when(outSf.isOutfield()).thenReturn(true);
    when(outSf.isActive()).thenReturn(true);
    when(outSf.getProperty()).thenReturn("paymentTerms");
    when(outSf.getSuffix()).thenReturn(null);
    when(mockSelector.getOBUISELSelectorFieldList()).thenReturn(List.of(outSf));

    // Target tab field
    Field targetField = mock(Field.class);
    Column targetCol = mock(Column.class);
    when(targetField.getObuiselOutfield()).thenReturn(outSf);
    when(targetField.getColumn()).thenReturn(targetCol);
    when(targetCol.getDBColumnName()).thenReturn("C_PaymentTerm_ID");
    when(targetField.getName()).thenReturn("Payment Terms");

    when(tab.getADFieldList()).thenReturn(List.of(targetField));

    // Set reference to OBUISEL selector type
    when(reference.getId()).thenReturn("95E2A8B50A254B2AAE6774B8C2F28120");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
         MockedConstruction<DataToJsonConverter> ignored = mockDataToJsonConverter()) {

        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

        // Mock getSelectorInfo to return a base JSON
        mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(anyString(), any()))
                .thenReturn(new JSONObject());
        mockedFieldBuilder.when(() -> FieldBuilder.getReferenceSelectors(any()))
                .thenReturn(new ReferenceSelectors(mockSelector, null));
        mockedFieldBuilder.when(() -> FieldBuilder.getPropertyOrDataSourceField(outSf))
                .thenReturn("paymentTerms");
        mockedFieldBuilder.when(() -> FieldBuilder.getHqlName(targetField))
                .thenReturn("paymentTerms");

        fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

        // Invoke private addComboSelectInfo
        invokePrivate(fieldBuilder, "addComboSelectInfo",
                new Class<?>[] { Field.class }, field);

        // Get the json field via reflection
        java.lang.reflect.Field jsonField = FieldBuilder.class.getDeclaredField("json");
        jsonField.setAccessible(true);
        JSONObject json = (JSONObject) jsonField.get(fieldBuilder);

        assertTrue(json.has("selector"));
        JSONObject selectorJson = json.getJSONObject("selector");
        assertTrue(selectorJson.has("outFields"));

        JSONArray outFields = selectorJson.getJSONArray("outFields");
        assertEquals(1, outFields.length());
        assertEquals("field", outFields.getJSONObject(0).getString("type"));
        assertEquals("paymentTerms", outFields.getJSONObject(0).getString("selectorFieldProperty"));
    }
}
```

- [ ] **Step 2: Write the failing test — tree selector does NOT produce outFields**

Uses the same `invokePrivate` pattern:

```java
@Test
void testAddComboSelectInfoTreeSelectorDoesNotIncludeOutFields() throws Exception {
    // Set reference to a tree selector type
    when(reference.getId()).thenReturn("8C57A4A2E05F4261A1FADF47C30398AD");

    ReferencedTree mockTree = mock(ReferencedTree.class);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
         MockedConstruction<DataToJsonConverter> ignored = mockDataToJsonConverter()) {

        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

        mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(anyString(), any()))
                .thenReturn(new JSONObject());
        mockedFieldBuilder.when(() -> FieldBuilder.getReferenceSelectors(any()))
                .thenReturn(new ReferenceSelectors(null, mockTree));

        fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

        invokePrivate(fieldBuilder, "addComboSelectInfo",
                new Class<?>[] { Field.class }, field);

        java.lang.reflect.Field jsonField = FieldBuilder.class.getDeclaredField("json");
        jsonField.setAccessible(true);
        JSONObject json = (JSONObject) jsonField.get(fieldBuilder);

        if (json.has("selector")) {
            JSONObject selectorJson = json.getJSONObject("selector");
            assertFalse(selectorJson.has("outFields"));
        }
    }
}
```

- [ ] **Step 3: Run new tests to verify they fail**

Run: `./gradlew test --tests "com.etendoerp.metadata.builders.FieldBuilderWithColumnTest.testAddComboSelectInfo*" -p modules/com.etendoerp.metadata`
Expected: Compilation fails (the wiring doesn't call `addOutFields` yet, but the test will fail because `getReferenceSelectors` is not called in the current code path)

- [ ] **Step 4: Implement the wiring in `addComboSelectInfo`**

Modify `FieldBuilderWithColumn.java` method `addComboSelectInfo` (lines 352-361):

Replace:
```java
private void addComboSelectInfo(Field field) throws JSONException {
    if (isSelectorField(field)) {
        try {
            json.put(SELECTOR, getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
        } catch (Exception e) {
            logger.error("Error retrieving selector info for field: {} ({}). Skipping selector configuration.",
                    field.getId(), field.getName(), e);
        }
    }
}
```

With:
```java
private void addComboSelectInfo(Field field) throws JSONException {
    if (isSelectorField(field)) {
        try {
            var ref = field.getColumn().getReferenceSearchKey();
            JSONObject selectorJson = getSelectorInfo(field.getId(), ref);

            ReferenceSelectors refSelectors = getReferenceSelectors(ref);
            if (refSelectors.selector != null) {
                addOutFields(selectorJson, refSelectors.selector, field.getTab());
            }

            json.put(SELECTOR, selectorJson);
        } catch (Exception e) {
            logger.error("Error retrieving selector info for field: {} ({}). Skipping selector configuration.",
                    field.getId(), field.getName(), e);
        }
    }
}
```

Add the `ReferenceSelectors` import at the top if not already present:
```java
import com.etendoerp.metadata.data.ReferenceSelectors;
```

- [ ] **Step 5: Run the new tests to verify they pass**

Run: `./gradlew test --tests "com.etendoerp.metadata.builders.FieldBuilderWithColumnTest.testAddComboSelectInfo*" -p modules/com.etendoerp.metadata`
Expected: PASS

- [ ] **Step 6: Run full test suite to check no regressions**

Run: `./gradlew test -p modules/com.etendoerp.metadata`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/com/etendoerp/metadata/builders/FieldBuilderWithColumn.java \
        src-test/src/com/etendoerp/metadata/builders/FieldBuilderWithColumnTest.java
git commit -m "Feature ETP-3757: Wire addOutFields into FieldBuilderWithColumn"
```

---

### Task 3: Run full build and verify

- [ ] **Step 1: Run full module test suite**

Run: `./gradlew test -p modules/com.etendoerp.metadata`
Expected: All tests PASS, no regressions

- [ ] **Step 2: Run compilation check**

Run: `./gradlew compileJava -p modules/com.etendoerp.metadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify JSON output manually (optional integration check)**

If a local Etendo instance is available, hit the metadata endpoint for a window that has a selector with out-fields (e.g., Sales Order line → Product selector):

```
GET /meta/window/{salesOrderWindowId}
```

Find the Product field in the response and verify it contains `selector.outFields` with entries for the known out-fields (currency, UOM, prices, etc.).
