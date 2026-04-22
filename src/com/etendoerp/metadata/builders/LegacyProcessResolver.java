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
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;

import java.util.Comparator;
import java.util.List;
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

    // Sorts Boolean.TRUE entries first, tolerates null. Useful for picking the "default" row.
    private static final Comparator<Boolean> DEFAULT_FIRST =
            Comparator.comparing(b -> !Boolean.TRUE.equals(b));

    private LegacyProcessResolver() {
    }

    /**
     * Returns {@code true} when the field should be launched as a legacy iframe process.
     *
     * @param field the AD_Field to evaluate
     * @return true if this field maps to a legacy process
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
     * Resolves the {@link LegacyProcessParams} for a legacy field.
     *
     * @param field the AD_Field to resolve
     * @return an {@link Optional} containing the params, or empty if not legacy or resolution fails
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

            return Optional.of(new LegacyProcessParams(url, command, keyColumn, keyColumn));
        } catch (Exception e) {
            logger.warn("Error resolving legacy process params for field {}: {}", field.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // isLegacy helpers
    // -------------------------------------------------------------------------

    private static boolean isSpecialColumn(Field field) {
        String columnName = field.getColumn().getDBColumnName();
        return columnName != null && SPECIAL_COLUMN_NAMES.contains(columnName);
    }

    private static boolean hasManualProcess(Field field) {
        Process process = field.getColumn().getProcess();
        return process != null && UIPATTERN_MANUAL.equals(process.getUIPattern());
    }

    private static boolean hasStandardButtonProcess(Field field) {
        return field.getColumn().getProcess() != null;
    }

    // -------------------------------------------------------------------------
    // URL resolution
    // -------------------------------------------------------------------------

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
     * Resolves the URL for a manual ({@code uipattern='M'}) process using the Openbravo
     * dispatcher registry as primary source and the package-derived path as fallback.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>{@code AD_MODEL_OBJECT_MAPPING} — the canonical URL registry consulted by the
     *       Classic dispatcher. Covers processes with remapped URLs (e.g.
     *       {@code /ad_process/RescheduleProcess.html}).</li>
     *   <li>{@link #javaClassToUrl(String)} applied to the FQCN taken from
     *       {@code AD_Process.Classname} or from the default {@code AD_MODEL_OBJECT}
     *       implementation — legacy fallback for processes whose package coincides with
     *       the servlet mount point.</li>
     * </ol>
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
     * Walks {@code process → ModelImplementation (action P/R) → ModelImplementationMapping}
     * preferring rows flagged as default, and returns the first non-blank mapping name.
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
     * Returns the FQCN of the Java implementation for this process. Reads
     * {@code AD_Process.Classname} first; falls back to the default row in
     * {@code AD_MODEL_OBJECT} (subtab "Process Class") when the inline value is missing.
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

    private static boolean isProcessOrReportImpl(ModelImplementation mi) {
        String action = mi.getAction();
        return ACTION_PROCESS.equals(action) || ACTION_REPORT.equals(action);
    }

    /**
     * Converts a fully-qualified Java class name to a Classic action-button URL path.
     * <p>
     * Example: {@code "org.openbravo.advpaymentmngt.ad_actionbutton.ProcessInvoice"}
     * → {@code "/org.openbravo.advpaymentmngt.ad_actionbutton/ProcessInvoice.html"}
     * </p>
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

    private static String resolveKeyColumnName(Field field) {
        List<Column> columns = field.getTab().getTable().getADColumnList();
        return columns.stream()
                .filter(Column::isKeyColumn)
                .findFirst()
                .map(Column::getDBColumnName)
                .orElse(null);
    }
}
