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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.ReportDefinition;

/**
 * Builds the report export flags JSON for an OBUIAPP_Report definition.
 * Mirrors the isPdf/isXls/isHtmlExport flag logic from ParameterWindowComponent
 * in the classic UI so the WorkspaceUI can render the same export buttons.
 */
public class ReportDefinitionBuilder extends Builder {

    private static final String ID = "id";
    private static final String PDF_EXPORT = "pdfExport";
    private static final String XLS_EXPORT = "xlsExport";
    private static final String HTML_EXPORT = "htmlExport";

    private final ReportDefinition report;

    public ReportDefinitionBuilder(ReportDefinition report) {
        this.report = report;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(ID, report.getId());
        json.put(PDF_EXPORT, StringUtils.isNotEmpty(report.getPDFTemplate()));
        json.put(XLS_EXPORT,
                StringUtils.isNotEmpty(report.getXLSTemplate()) || Boolean.TRUE.equals(report.isUsePDFAsXLSTemplate()));
        json.put(HTML_EXPORT,
                StringUtils.isNotEmpty(report.getHTMLTemplate()) || Boolean.TRUE.equals(report.isUsePDFAsHTMLTemplate()));
        return json;
    }
}
