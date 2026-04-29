/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DateDomainType;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the parameters needed to launch a legacy (HTML-iframe) process from
 * the new UI.
 * <p>
 * A "legacy process" is any Button column (reference 28) whose execution is handled
 * by a Classic HTML template / Java servlet rather than the modern Process Definition
 * API. This includes:
 * <ul>
 *   <li>Section 2.B.1-2.B.5: columns detected by their {@code columnname}
 *       (DocAction, Posted, CreateFrom, ChangeProjectStatus, PaymentRule)</li>
 *   <li>Section 2.B.6: columns whose linked {@code AD_Process} has
 *       {@code uipattern = 'M'} (Manual)</li>
 *   <li>Standard button columns ({@code uipattern = 'S'}) that are tab-bound
 *       (the Classic framework routes them through the tab servlet)</li>
 * </ul>
 */
public class LegacyProcessResolver {

    private static final Logger logger = LogManager.getLogger(LegacyProcessResolver.class);

    // --- Special column name constants (Section 2.B.1-2.B.5) ---
    static final String COLUMN_DOC_ACTION = "DocAction";
    static final String COLUMN_POSTED = "Posted";
    static final String COLUMN_CREATE_FROM = "CreateFrom";
    static final String COLUMN_CHANGE_PROJECT_STATUS = "ChangeProjectStatus";
    static final String COLUMN_PAYMENT_RULE = "PaymentRule";

    private static final Set<String> SPECIAL_COLUMN_NAMES = Set.of(
            COLUMN_DOC_ACTION,
            COLUMN_POSTED,
            COLUMN_CREATE_FROM,
            COLUMN_CHANGE_PROJECT_STATUS,
            COLUMN_PAYMENT_RULE
    );

    // --- Command/URL building constants ---
    private static final String COMMAND_PREFIX = "BUTTON";
    private static final String COMMAND_DEFAULT = "DEFAULT";
    private static final String UIPATTERN_MANUAL = "M";
    private static final String TEMPLATE_EXTENSION = ".html";

    // --- AD_MODEL_OBJECT.Action values of interest ---
    private static final String ACTION_PROCESS = "P";
    private static final String ACTION_REPORT = "R";

    // --- AD_Reference IDs excluded from the additional-parameters snapshot.
    //     Classic submits every hidden input (including Button-typed columns whose value is
    //     stored in DB, e.g. EM_Aprm_Processed=P), so we mirror that and exclude only the
    //     references that have no transmittable value.
    private static final String REFERENCE_PASSWORD = "24";
    private static final String REFERENCE_IMAGE = "32";
    private static final Set<String> EXCLUDED_REFERENCE_IDS =
            Set.of(REFERENCE_PASSWORD, REFERENCE_IMAGE);

    // --- Placeholder grammar consumed by the client ---
    private static final String RECORD_PLACEHOLDER_PREFIX = "$record.";
    private static final String COERCION_SEPARATOR = "!";
    private static final String COERCION_YN = COERCION_SEPARATOR + "yn";
    private static final String COERCION_DATE = COERCION_SEPARATOR + "date";
    /** SmartClient datasource always exposes the primary key under the `id` JSON property. */
    private static final String RECORD_ID_PLACEHOLDER = RECORD_PLACEHOLDER_PREFIX + "id";

    // --- Classic input naming convention (mirrors columnNameToInpKey on the client) ---
    private static final String INP_PREFIX = "inp";
    private static final String COLUMN_NAME_SEPARATOR = "_";

    // Sorts Boolean.TRUE entries first, tolerates null. Useful for picking the "default" row.
    private static final Comparator<Boolean> DEFAULT_FIRST =
            Comparator.comparing(b -> !Boolean.TRUE.equals(b));

    /**
     * Private constructor — this is a stateless utility class.
     * All entry points are static; instantiation is not meaningful.
     */
    private LegacyProcessResolver() {
    }

    /**
     * Returns {@code true} when the given field should be launched as a legacy iframe process
     * rather than through the modern Process Definition API.
     *
     * <p>A field is considered legacy when any of the following is true:
     * <ol>
     *   <li>Its DB column name is one of the well-known special names ({@code DocAction},
     *       {@code Posted}, {@code CreateFrom}, {@code ChangeProjectStatus},
     *       {@code PaymentRule}).</li>
     *   <li>Its linked {@code AD_Process} has {@code uipattern = 'M'} (Manual).</li>
     *   <li>Its linked {@code AD_Process} uses the Standard ({@code 'S'}) UI pattern — these
     *       are routed through the tab's Edition servlet.</li>
     * </ol>
     *
     * @param field the {@code AD_Field} to evaluate; {@code null} or a field without a column
     *              always returns {@code false}
     * @return {@code true} if this field maps to a legacy iframe process; {@code false} otherwise
     */
    public static boolean isLegacy(Field field) {
        if (field == null || field.getColumn() == null) {
            return false;
        }
        if (isSpecialColumn(field)) {
            return true;
        }
        if (hasManualProcess(field)) {
            return true;
        }
        return hasStandardButtonProcess(field);
    }

    /**
     * Resolves the full set of {@link LegacyProcessParams} required to build the Classic iframe
     * URL for the given field.
     *
     * <p>This is the main entry point for consumers. It delegates to the private helpers to
     * determine the URL, the WAD {@code Command} value, the primary-key column name, and the
     * snapshot of per-column {@code inp*} parameters that mirror the hidden inputs of the
     * Classic Edition form.
     *
     * <p>Returns {@link Optional#empty()} in three cases:
     * <ul>
     *   <li>{@link #isLegacy(Field)} returns {@code false} for this field.</li>
     *   <li>URL, command, or key-column name cannot be resolved (logged at {@code WARN}).</li>
     *   <li>An unexpected exception is thrown during resolution (logged at {@code WARN}).</li>
     * </ul>
     *
     * @param field the {@code AD_Field} to resolve; must be non-{@code null}
     * @return an {@link Optional} containing the resolved params, or empty if the field is not
     *         legacy or if resolution fails
     */
    public static Optional<LegacyProcessParams> resolve(Field field) {
        if (!isLegacy(field)) {
            return Optional.empty();
        }
        try {
            String url = resolveUrl(field);
            String command = resolveCommand(field);
            String keyColumn = resolveKeyColumnName(field);

            if (url == null || command == null || keyColumn == null) {
                logger.warn("Could not fully resolve legacy params for field {}", field.getId());
                return Optional.empty();
            }

            Map<String, String> additionalParams = resolveAdditionalParameters(field);
            return Optional.of(new LegacyProcessParams(url, command, keyColumn, keyColumn, additionalParams));
        } catch (Exception e) {
            logger.warn("Error resolving legacy process params for field {}: {}", field.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // isLegacy helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the field's DB column name is one of the five well-known
     * special column names that have dedicated Classic action-button pages
     * ({@code DocAction}, {@code Posted}, {@code CreateFrom}, {@code ChangeProjectStatus},
     * {@code PaymentRule}).
     *
     * @param field a non-{@code null} field with a non-{@code null} column
     * @return {@code true} if the column name matches one of the special names
     */
    private static boolean isSpecialColumn(Field field) {
        String columnName = field.getColumn().getDBColumnName();
        return columnName != null && SPECIAL_COLUMN_NAMES.contains(columnName);
    }

    /**
     * Returns {@code true} when the field's column is linked to an {@code AD_Process} whose
     * {@code uipattern} is {@code 'M'} (Manual).
     *
     * <p>Manual processes have their own HTML template served directly by the Java servlet
     * named after the process class (e.g. {@code /ad_process/RescheduleProcess.html}).
     * They do not go through the tab's Edition servlet.
     *
     * @param field a non-{@code null} field with a non-{@code null} column
     * @return {@code true} if the column has a Manual-pattern process attached
     */
    private static boolean hasManualProcess(Field field) {
        Process process = field.getColumn().getProcess();
        return process != null && UIPATTERN_MANUAL.equals(process.getUIPattern());
    }

    /**
     * Returns {@code true} when the field's column has any {@code AD_Process} attached,
     * regardless of its UI pattern.
     *
     * <p>This catch-all covers Standard ({@code 'S'}) button columns that route through
     * the tab's Edition servlet. It is evaluated last so that Manual processes are already
     * handled by {@link #hasManualProcess(Field)}.
     *
     * @param field a non-{@code null} field with a non-{@code null} column
     * @return {@code true} if the column references any {@code AD_Process}
     */
    private static boolean hasStandardButtonProcess(Field field) {
        return field.getColumn().getProcess() != null;
    }

    // -------------------------------------------------------------------------
    // URL resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the Classic HTML page URL for the given legacy field.
     *
     * <p>Resolution strategy:
     * <ul>
     *   <li>If the column has a Manual-pattern process, delegates to
     *       {@link #resolveManualProcessUrl(Process)} which uses the
     *       {@code AD_MODEL_OBJECT_MAPPING} registry or the Java class name.</li>
     *   <li>For all other legacy columns (special names and Standard-button columns),
     *       returns the tab's Edition servlet URL via {@link #resolveTabUrl(Field)}.
     *       These columns share the tab URL because the Classic framework uses
     *       session variables — set during the first request — to route the action.</li>
     * </ul>
     *
     * @param field a non-{@code null} legacy field
     * @return the Classic HTML page URL, or {@code null} if it cannot be resolved
     */
    private static String resolveUrl(Field field) {
        Process process = field.getColumn().getProcess();

        if (process != null && UIPATTERN_MANUAL.equals(process.getUIPattern())) {
            return resolveManualProcessUrl(process);
        }

        // All special columns (DocAction, Posted, CreateFrom, ChangeProjectStatus, PaymentRule)
        // and Standard-button columns route through the tab's Edition servlet so that
        // session variables are set correctly before the action-button page renders.
        return resolveTabUrl(field);
    }

    /**
     * Resolves the URL for a Manual ({@code uipattern='M'}) process using the Openbravo
     * dispatcher registry as primary source and a class-name-derived path as fallback.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code AD_MODEL_OBJECT_MAPPING} via {@link #resolveUrlFromMappings(Process)} — the
     *       canonical URL registry consulted by the Classic dispatcher at runtime. Covers
     *       processes with explicitly remapped URLs (e.g.
     *       {@code /ad_process/RescheduleProcess.html}).</li>
     *   <li>{@link #javaClassToUrl(String)} applied to the FQCN obtained from
     *       {@link #resolveJavaClassName(Process)} — legacy fallback for processes whose
     *       package path coincides with the Classic servlet mount point.</li>
     * </ol>
     *
     * <p>If neither source yields a URL, logs a warning and returns {@code null}.
     *
     * @param process a non-{@code null} process with {@code uipattern = 'M'}
     * @return the resolved Classic HTML page URL, or {@code null} if neither source resolves it
     */
    private static String resolveManualProcessUrl(Process process) {
        String mapped = resolveUrlFromMappings(process);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        String javaClassName = resolveJavaClassName(process);
        if (javaClassName != null && !javaClassName.isBlank()) {
            return javaClassToUrl(javaClassName);
        }
        logger.warn("Process {} has uipattern=M but no resolvable URL (no mapping, no classname)", process.getId());
        return null;
    }

    /**
     * Walks the {@code AD_MODEL_OBJECT → AD_MODEL_OBJECT_MAPPING} hierarchy for the given
     * process, preferring implementations and mappings flagged as default, and returns the
     * first non-blank mapping name found.
     *
     * <p>Only implementations with {@code action = 'P'} (Process) or {@code 'R'} (Report)
     * are considered. Within each matching implementation the mapping rows are also sorted
     * so that the default row is tried first.
     *
     * @param process a non-{@code null} {@code AD_Process} instance
     * @return the first non-blank {@code MappingName}, or {@code null} if none is found
     */
    private static String resolveUrlFromMappings(Process process) {
        return process.getADModelImplementationList().stream()
                .filter(LegacyProcessResolver::isProcessOrReportImpl)
                .sorted(Comparator.comparing(ModelImplementation::isDefault, DEFAULT_FIRST))
                .flatMap(mi -> mi.getADModelImplementationMappingList().stream())
                .sorted(Comparator.comparing(ModelImplementationMapping::isDefault, DEFAULT_FIRST))
                .map(ModelImplementationMapping::getMappingName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the fully-qualified Java class name for the given process, consulting two
     * sources in order:
     * <ol>
     *   <li>{@code AD_Process.Classname} — the inline value stored directly on the process
     *       record; populated for the majority of module-level processes.</li>
     *   <li>The default {@code AD_MODEL_OBJECT} implementation (action {@code 'P'} or
     *       {@code 'R'}) — used when the process was registered only through the
     *       object-implementation subtab without filling the inline field.</li>
     * </ol>
     *
     * @param process a non-{@code null} {@code AD_Process} instance
     * @return the FQCN of the Java implementation, or {@code null} if neither source provides
     *         a non-blank value
     */
    private static String resolveJavaClassName(Process process) {
        String inline = process.getJavaClassName();
        if (inline != null && !inline.isBlank()) {
            return inline;
        }
        return process.getADModelImplementationList().stream()
                .filter(LegacyProcessResolver::isProcessOrReportImpl)
                .sorted(Comparator.comparing(ModelImplementation::isDefault, DEFAULT_FIRST))
                .map(ModelImplementation::getJavaClassName)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@code true} when the {@code AD_MODEL_OBJECT} row represents a Process
     * ({@code action = 'P'}) or Report ({@code action = 'R'}) implementation.
     *
     * <p>Other action values (e.g. Form, Workbench) are skipped because they do not map
     * to executable HTML pages in the Classic iframe flow.
     *
     * @param mi a non-{@code null} {@code ModelImplementation} row
     * @return {@code true} if the action is {@code 'P'} or {@code 'R'}
     */
    private static boolean isProcessOrReportImpl(ModelImplementation mi) {
        String action = mi.getAction();
        return ACTION_PROCESS.equals(action) || ACTION_REPORT.equals(action);
    }

    /**
     * Converts a fully-qualified Java class name to the Classic action-button URL path used
     * by the Openbravo WAD dispatcher.
     *
     * <p>The Classic URL convention is: {@code /<package>/<ClassName>.html}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "org.openbravo.advpaymentmngt.ad_actionbutton.ProcessInvoice"}
     *       → {@code "/org.openbravo.advpaymentmngt.ad_actionbutton/ProcessInvoice.html"}</li>
     *   <li>{@code "SimpleClass"} (no package) → {@code "/SimpleClass.html"}</li>
     * </ul>
     *
     * @param javaClassName the fully-qualified class name; must be non-{@code null}
     * @return the URL path for the Classic servlet dispatcher
     */
    static String javaClassToUrl(String javaClassName) {
        int lastDot = javaClassName.lastIndexOf('.');
        if (lastDot < 0) {
            return "/" + javaClassName + TEMPLATE_EXTENSION;
        }
        String packagePath = javaClassName.substring(0, lastDot);
        String className = javaClassName.substring(lastDot + 1);
        return "/" + packagePath + "/" + className + TEMPLATE_EXTENSION;
    }

    /**
     * Returns the Edition servlet URL for the tab that contains the given field, using
     * {@link Utility#getTabURL(org.openbravo.model.ad.ui.Tab, String, boolean)}.
     *
     * <p>Special column types (DocAction, Posted, CreateFrom, ChangeProjectStatus, PaymentRule)
     * and Standard-button processes share the tab URL because the Classic framework stores
     * intermediate state in session variables during the first HTTP request and then re-reads
     * it on the second request triggered by the FRAMESET response.
     *
     * @param field a non-{@code null} field whose tab has a resolvable URL
     * @return the Edition servlet URL for the tab, or {@code null} if {@link Utility#getTabURL}
     *         throws an exception
     */
    private static String resolveTabUrl(Field field) {
        try {
            return Utility.getTabURL(field.getTab(), null, false);
        } catch (Exception e) {
            logger.warn("Could not resolve tab URL for field {}: {}", field.getId(), e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Command resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the Classic {@code Command} parameter value for the given legacy field.
     *
     * <p>The Classic WAD servlet dispatches to the correct handler based on this value.
     * Resolution rules:
     * <ul>
     *   <li>No linked process (e.g. bare {@code Posted} or {@code CreateFrom} columns):
     *       {@code "BUTTON" + dbColumnName}.</li>
     *   <li>Manual-pattern process ({@code uipattern = 'M'}): {@code "DEFAULT"} — Manual
     *       processes use a fixed command and are dispatched by URL alone.</li>
     *   <li>Special or Standard-button columns with a linked process:
     *       {@code "BUTTON" + dbColumnName + processId} — Classic uses the combined key
     *       so that multiple buttons on the same tab can coexist without command collisions.</li>
     * </ul>
     *
     * @param field a non-{@code null} legacy field whose column has a non-{@code null}
     *              DB column name
     * @return the resolved WAD {@code Command} parameter value; never {@code null}
     */
    private static String resolveCommand(Field field) {
        String columnName = field.getColumn().getDBColumnName();
        Process process = field.getColumn().getProcess();

        if (process == null) {
            // Posted and CreateFrom without AD_Process — command is BUTTON + columnName
            return COMMAND_PREFIX + columnName;
        }

        if (UIPATTERN_MANUAL.equals(process.getUIPattern())) {
            return COMMAND_DEFAULT;
        }

        // Special columns (DocAction, ChangeProjectStatus) and Standard-button columns
        // use BUTTON + columnName + processId
        return COMMAND_PREFIX + columnName + process.getId();
    }

    // -------------------------------------------------------------------------
    // Key column resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the DB column name of the primary-key column for the tab's table.
     *
     * <p>The Classic iframe URL requires both {@code keyColumnName} and
     * {@code inpkeyColumnId} to contain the PK column name so that the servlet can read
     * the record from the database. This method finds the first column flagged as a key
     * column ({@link Column#isKeyColumn()}) in the table's column list.
     *
     * @param field a non-{@code null} field whose tab has a table with at least one key column
     * @return the DB column name of the primary-key column, or {@code null} if none is found
     */
    private static String resolveKeyColumnName(Field field) {
        List<Column> columns = field.getTab().getTable().getADColumnList();
        return columns.stream()
                .filter(Column::isKeyColumn)
                .findFirst()
                .map(Column::getDBColumnName)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Additional parameters resolution
    //
    // For each active column of the tab's table whose reference is NOT Password/Image,
    // emit { "inp<camelCase(DBColumnName)>": "$record.<entityProperty>[!coercion]" }.
    // The client resolves the placeholders against the current record at click time,
    // mirroring the hidden inputs that the Classic *_Edition.html form would have
    // submitted (already pre-formatted by the XSQL template).
    //
    // Property names come from ModelProvider — same JPA model the SmartClient datasource
    // uses to serialize records. The PK is always emitted as $record.id because the
    // datasource exposes it under that JSON key regardless of the column name.
    // -------------------------------------------------------------------------

    /**
     * Builds the {@code additionalParameters} map that the frontend will use to populate
     * the Classic iframe URL at click time.
     *
     * <p>For each active, includable column of the tab's table, this method emits one entry:
     * <pre>
     *   "inp{camelCase(DBColumnName)}" → "$record.{jpaPropertyName}[!coercion]"
     * </pre>
     * The client resolves the {@code $record.*} placeholders against the SmartClient record
     * JSON at the moment the user clicks the button. This mirrors the hidden inputs that the
     * Classic {@code *_Edition.html} form pre-formats via XSQL at render time.
     *
     * <p>JPA property names come from {@link ModelProvider} — the same model the SmartClient
     * datasource uses to serialize records — so name mapping is exact. The PK is always
     * emitted as {@code $record.id} because the datasource exposes the primary key under the
     * {@code "id"} JSON key regardless of the actual column name.
     *
     * <p>If no JPA entity exists for the table, or if an unexpected exception is thrown, the
     * method logs a warning and returns an empty map so that legacy resolution can still
     * proceed without additional parameters.
     *
     * @param field a non-{@code null} legacy field whose tab and table are accessible
     * @return an ordered {@link Map} of {@code inp*} keys to {@code $record.*} placeholder
     *         strings; never {@code null}, may be empty
     */
    private static Map<String, String> resolveAdditionalParameters(Field field) {
        try {
            Table table = field.getTab().getTable();
            Entity entity = ModelProvider.getInstance().getEntityByTableName(table.getDBTableName());
            if (entity == null) {
                logger.warn("No JPA entity for table {}", table.getDBTableName());
                return Collections.emptyMap();
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (Column col : table.getADColumnList()) {
                if (shouldIncludeColumn(col)) {
                    Property property = safePropertyFor(entity, col);
                    if (property != null) {
                        String inpKey = toInpKey(col.getDBColumnName());
                        params.put(inpKey, buildPlaceholder(property));
                    }
                }
            }
            return params;
        } catch (Exception e) {
            logger.warn("Could not build additional parameters for field {}: {}",
                    field.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Returns {@code true} when the given column should contribute an {@code inp*} entry to
     * the additional-parameters snapshot.
     *
     * <p>Inclusion criteria:
     * <ul>
     *   <li>The column must be active and have a non-blank DB column name.</li>
     *   <li>Its {@code AD_Reference} must not be Password ({@code 24}) or Image ({@code 32}).
     *       These references either contain sensitive data that must never be sent over HTTP
     *       or store binary content that has no URL-safe representation.</li>
     *   <li>All other references — including Button ({@code 28}) — are included to match the
     *       set of hidden inputs submitted by the Classic form (e.g. {@code EM_Aprm_Processed=P}
     *       is a Button column whose stored value the WAD servlet reads from the request).</li>
     * </ul>
     *
     * @param col the {@code AD_Column} to evaluate; {@code null} columns return {@code false}
     * @return {@code true} if this column should appear in the additional-parameters map
     */
    private static boolean shouldIncludeColumn(Column col) {
        if (col == null || !col.isActive()) {
            return false;
        }
        String dbName = col.getDBColumnName();
        if (dbName == null || dbName.isBlank()) {
            return false;
        }
        Reference ref = col.getReference();
        if (ref == null) {
            return true;
        }
        return !EXCLUDED_REFERENCE_IDS.contains(ref.getId());
    }

    /**
     * Looks up the JPA {@link Property} for the given column in the entity, returning
     * {@code null} instead of propagating exceptions.
     *
     * <p>{@link Entity#getPropertyByColumnName(String)} throws {@link IllegalArgumentException}
     * when no property matches the DB column name (e.g. columns that exist in the DB schema
     * but have no corresponding JPA property — typically audit or computed columns). Catching
     * here allows the loop in {@link #resolveAdditionalParameters(Field)} to skip such
     * orphan columns without aborting the entire snapshot.
     *
     * @param entity     the JPA entity for the column's table; must be non-{@code null}
     * @param col        the {@code AD_Column} whose property should be resolved; must be
     *                   non-{@code null} with a non-{@code null} DB column name
     * @return the matching {@link Property}, or {@code null} if no property exists for the
     *         column name or if any exception is thrown during lookup
     */
    private static Property safePropertyFor(Entity entity, Column col) {
        try {
            return entity.getPropertyByColumnName(col.getDBColumnName());
        } catch (Exception e) {
            logger.debug("No JPA property for column {} in entity {}", col.getDBColumnName(), entity.getName());
            return null;
        }
    }

    /**
     * Builds the {@code $record.…} placeholder string for a single JPA property.
     *
     * <p>Placeholder grammar: {@code $record.<propertyName>[!coercion]}
     *
     * <p>Mapping rules:
     * <ul>
     *   <li><strong>Primary key</strong> ({@link Property#isId()} is {@code true}):
     *       always returns {@code $record.id}. The SmartClient datasource exposes the PK
     *       under the fixed JSON key {@code "id"} regardless of the column name.</li>
     *   <li><strong>Boolean / Yes-No column</strong> (domain type is {@link PrimitiveDomainType}
     *       with primitive class {@code Boolean} or {@code boolean}):
     *       returns {@code $record.<prop>!yn}. The client coerces {@code true → "Y"} and
     *       {@code false → "N"} so that the WAD servlet receives the expected string.</li>
     *   <li><strong>Pure date column</strong> (domain type is {@link DateDomainType}):
     *       returns {@code $record.<prop>!date}. The client converts the ISO-8601 string
     *       from SmartClient ({@code YYYY-MM-DD[T...]}) to the {@code dd-mm-yyyy} format
     *       expected by Classic.</li>
     *   <li><strong>All other types</strong> (string, numeric, FK, datetime/timestamp):
     *       returns {@code $record.<prop>} — pass-through via {@code String(value)}.</li>
     * </ul>
     *
     * <p><strong>Important:</strong> {@link Property#getPrimitiveType()} casts the domain type
     * to {@link PrimitiveDomainType} unconditionally. Calling it on FK domain types
     * ({@code TableDirDomainType}, {@code SearchDomainType}, {@code TableDomainType}) throws
     * a {@link ClassCastException}. This method avoids the issue by inspecting
     * {@code property.getDomainType()} directly and casting only after an
     * {@code instanceof PrimitiveDomainType} guard.
     *
     * @param property a non-{@code null} JPA property; its domain type may be {@code null}
     *                 for synthetic or unmapped properties
     * @return the {@code $record.*} placeholder string; never {@code null}
     */
    static String buildPlaceholder(Property property) {
        if (property.isId()) {
            return RECORD_ID_PLACEHOLDER;
        }
        String base = RECORD_PLACEHOLDER_PREFIX + property.getName();
        DomainType domainType = property.getDomainType();
        if (domainType instanceof PrimitiveDomainType) {
            Class<?> primitive = ((PrimitiveDomainType) domainType).getPrimitiveType();
            if (Boolean.class == primitive || boolean.class == primitive) {
                return base + COERCION_YN;
            }
            if (domainType instanceof DateDomainType) {
                return base + COERCION_DATE;
            }
        }
        return base;
    }

    /**
     * Converts an Openbravo DB column name to the Classic WAD {@code inp*} HTTP parameter name.
     *
     * <p>Algorithm: split on {@code "_"}, lowercase every segment, capitalize the first letter
     * of each segment after the first (lowerCamelCase), then prepend {@code "inp"}.
     * This mirrors {@code columnNameToInpKey} in the client
     * ({@code utils/processes/manual/utils.ts}) so that both sides generate identical keys.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Fin_Finacc_Transaction_ID} → {@code inpfinFinaccTransactionId}</li>
     *   <li>{@code C_Order_ID}                → {@code inpcOrderId}</li>
     *   <li>{@code AD_Org_ID}                 → {@code inpadOrgId}</li>
     *   <li>{@code Processed}                 → {@code inpprocessed}</li>
     * </ul>
     *
     * @param dbColumnName the raw DB column name as stored in {@code AD_Column.ColumnName};
     *                     must be non-{@code null} and non-blank
     * @return the derived {@code inp*} parameter name; never {@code null}
     */
    static String toInpKey(String dbColumnName) {
        String[] segments = dbColumnName.split(COLUMN_NAME_SEPARATOR);
        StringBuilder sb = new StringBuilder(INP_PREFIX);
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i].toLowerCase(Locale.ROOT);
            if (seg.isEmpty()) {
                continue;
            }
            if (i == 0) {
                sb.append(seg);
            } else {
                sb.append(Character.toUpperCase(seg.charAt(0))).append(seg.substring(1));
            }
        }
        return sb.toString();
    }
}
