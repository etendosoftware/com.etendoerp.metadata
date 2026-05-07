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
package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code openLegacyReport} forwarder additions to
 * {@link LegacyProcessServlet}: report extraction (URL split + tabTitle) from
 * the captured {@code PopUp_Response.html} body and the corresponding
 * short-circuit forwarder writer that emits the
 * {@code {reports: [{processUrl, tabTitle, params}, ...]}} payload.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LegacyProcessServletReportForwarderTest {

    private static final String EXTRACT_REPORTS = "extractReportsFromBody";
    private static final String WRITE_FORWARDER = "writeOpenLegacyReportForwarder";
    private static final String HTML_UTF8_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String UTF8_CHARSET = "UTF-8";
    private static final String OPEN_LEGACY_REPORT_ACTION_SNIPPET = "action:'openLegacyReport'";
    private static final String DEFAULT_CONTEXT_PATH = "/etendo";
    private static final String NEW_TAB_PARAMS_TEMPLATE =
            "<script id=\"newTabParams\">var newTabParams={\"tabTitle\":\"%s\",\"addToRecents\":false};</script>";

    private LegacyProcessServlet servlet;

    @Before
    public void setUp() {
        servlet = new LegacyProcessServlet();
    }

    @Test
    public void extractReportsFromBody_returnsSingleReport_whenSubmitThisPageHasQuotedHref() throws Exception {
        String url = "/etendo/ad_reports/ReportGeneralLedgerJournal.html?Command=DIRECT&inpRecord=42";
        String body = newTabParams("Journal Entries Report")
                + "<body onload=\"submitThisPage('" + url + "');\">";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/ReportGeneralLedgerJournal.html", reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpRecord=42", reports.get(0).params);
        assertEquals("Journal Entries Report", reports.get(0).tabTitle);
    }

    @Test
    public void extractReportsFromBody_returnsAllReports_whenMultipleSubmitThisPageCalls() throws Exception {
        String url1 = "/etendo/ad_reports/Report1.html?inpRecord=1";
        String url2 = "/etendo/ad_reports/Report2.html?inpRecord=2";
        String body = newTabParams("Title")
                + "first: submitThisPage('" + url1 + "'); then: submitThisPage('" + url2 + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(2, reports.size());
        assertEquals("/ad_reports/Report1.html", reports.get(0).processUrl);
        assertEquals("inpRecord=1", reports.get(0).params);
        assertEquals("/ad_reports/Report2.html", reports.get(1).processUrl);
        assertEquals("inpRecord=2", reports.get(1).params);
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenSubmitThisPageNull() throws Exception {
        String body = "<body onload=\"submitThisPage(null);\" id=\"paramHref\">";

        assertTrue(invokeExtract(body, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenBodyIsNull() throws Exception {
        assertTrue(invokeExtract(null, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    @Test
    public void extractReportsFromBody_handlesUrlWithEmbeddedAmpersandsAndCommas() throws Exception {
        String url = "/etendo/ad_reports/ReportGeneralLedgerJournal.html?Command=DIRECT"
                + "&inpTable=318&inpRecord=ABC&inpAccSchemas=A,B,C&posted=Y";
        String body = newTabParams("Journal Entries Report")
                + "submitThisPage('" + url + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/ReportGeneralLedgerJournal.html", reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpTable=318&inpRecord=ABC&inpAccSchemas=A,B,C&posted=Y",
                reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenSubmitThisPageHasDoubleQuotes() throws Exception {
        // Defensive: the popup template emits single-quoted args; double-quoted should NOT match
        // and the regular pipeline should keep handling that body.
        String body = "submitThisPage(\"/etendo/ad_reports/X.html\");";

        assertTrue(invokeExtract(body, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenArgIsActionCode() throws Exception {
        // The form-render HTML of an action button (the GET that prepares the
        // confirmation page, before the user submits) wires its OK button to
        // submitThisPage('SAVE') / 'OK' / 'EDIT' / etc. These are command tokens
        // for the JS helper, not URLs, and must NOT short-circuit the pipeline.
        String body = "<button onclick=\"submitThisPage('SAVE');\">OK</button>"
                + "<a onclick=\"submitThisPage('OK');\">x</a>"
                + "<a onclick=\"submitThisPage('EDIT');\">x</a>"
                + "<a onclick=\"submitThisPage('REFRESH');\">x</a>";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertTrue("Action codes must not be treated as URLs: " + reports, reports.isEmpty());
    }

    @Test
    public void extractReportsFromBody_extractsOnlyUrlShapedValues_whenMixedWithActionCodes() throws Exception {
        // Defensive: even if a popup body somehow mixes both, only the URL-shaped
        // value should surface as a report URL.
        String url = "/etendo/ad_reports/Report.html?Command=DIRECT";
        String body = newTabParams("T")
                + "submitThisPage('SAVE'); submitThisPage('" + url + "'); submitThisPage('OK');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/Report.html", reports.get(0).processUrl);
        assertEquals("Command=DIRECT", reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_splitsAbsoluteUrlIntoProcessUrlAndParams() throws Exception {
        // Realistic Posted flow: backend builds the URL with strDireccion + "/ad_reports/...".
        String url = "http://localhost:8080/etendo/ad_reports/Report.html?Command=DIRECT&inpRecord=42";
        String body = newTabParams("Title") + "submitThisPage('" + url + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/Report.html", reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpRecord=42", reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_returnsEmptyParams_whenUrlHasNoQuery() throws Exception {
        String body = newTabParams("Title") + "submitThisPage('/etendo/ad_reports/R.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/R.html", reports.get(0).processUrl);
        assertEquals("", reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_extractsTabTitleFromNewTabParams() throws Exception {
        String body = newTabParams("Journal Entries Report")
                + "submitThisPage('/etendo/ad_reports/R.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("Journal Entries Report", reports.get(0).tabTitle);
    }

    @Test
    public void extractReportsFromBody_returnsEmptyTabTitle_whenNewTabParamsAbsent() throws Exception {
        // Defensive: a popup body without newTabParams should still surface the
        // report (older callers / overload of printPageClosePopUp without title).
        String body = "submitThisPage('/etendo/ad_reports/R.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("", reports.get(0).tabTitle);
    }

    @Test
    public void extractReportsFromBody_extractsTabTitle_whenJsonHasEscapedQuote() throws Exception {
        // The newTabParams literal is rendered by JSONObject.toString() and may
        // contain JSON-escaped quotes. The regex must not stop at the first \".
        String body = "<script>var newTabParams={\"tabTitle\":\"He said \\\"hi\\\"\",\"addToRecents\":false};</script>"
                + "submitThisPage('/etendo/ad_reports/R.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("He said \\\"hi\\\"", reports.get(0).tabTitle);
    }

    @Test
    public void extractReportsFromBody_appliesTabTitleToAllReports_whenMultipleSubmitThisPage() throws Exception {
        String body = newTabParams("Shared Title")
                + "submitThisPage('/etendo/ad_reports/A.html');"
                + "submitThisPage('/etendo/ad_reports/B.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(2, reports.size());
        assertEquals("Shared Title", reports.get(0).tabTitle);
        assertEquals("Shared Title", reports.get(1).tabTitle);
    }

    @Test
    public void extractReportsFromBody_keepsContextPath_whenContextPathDoesNotMatch() throws Exception {
        String body = newTabParams("T") + "submitThisPage('/other/ad_reports/R.html?x=1');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/other/ad_reports/R.html", reports.get(0).processUrl);
        assertEquals("x=1", reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_handlesNullContextPath() throws Exception {
        String body = newTabParams("T") + "submitThisPage('/etendo/ad_reports/R.html?x=1');";

        List<ReportSnapshot> reports = invokeExtract(body, null);

        assertEquals(1, reports.size());
        assertEquals("/etendo/ad_reports/R.html", reports.get(0).processUrl);
        assertEquals("x=1", reports.get(0).params);
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenUrlIsParentTabRefresh() throws Exception {
        // Reactivate (and other non-Posted action buttons) call printPageClosePopUp
        // with a path obtained from Utility.getTabURL(strTabId, "R", true) — a window
        // URL meant to refresh the parent tab in the classic UI, NOT a report. The
        // new UI must let the regular processOrder/showProcessMessage pipeline handle
        // these, so the forwarder must not short-circuit them.
        String body = "submitThisPage('/SalesInvoice/Header_Relation.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertTrue("Window-refresh URLs must not be treated as report URLs: " + reports, reports.isEmpty());
    }

    @Test
    public void extractReportsFromBody_returnsEmpty_whenAbsoluteUrlIsParentTabRefresh() throws Exception {
        // Exact reproduction of the QA repro: Sales Invoice → Reactivate emits the
        // absolute window URL of its own tab.
        String body = "submitThisPage('http://localhost:8080/etendo/SalesInvoice/Header_Relation.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertTrue("Absolute window-refresh URLs must not be treated as report URLs: " + reports, reports.isEmpty());
    }

    @Test
    public void extractReportsFromBody_filtersOutWindowUrls_whenMixedWithReportUrls() throws Exception {
        // Defensive: a popup body that contains both a window-refresh URL and a
        // report URL (e.g. a hypothetical compound action) must surface only the
        // report; the window URL is handled by the regular pipeline.
        String reportUrl = "/etendo/ad_reports/Report.html?Command=DIRECT";
        String body = newTabParams("T")
                + "submitThisPage('/SalesInvoice/Header_Relation.html');"
                + " submitThisPage('" + reportUrl + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/ad_reports/Report.html", reports.get(0).processUrl);
        assertEquals("Command=DIRECT", reports.get(0).params);
    }

    @Test
    public void writeOpenLegacyReportForwarder_writesPayloadAsReportsArray() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        Object report = newReportInfo("/ad_reports/R.html", "Title", "Command=DIRECT");
        invokeWrite(response, Collections.singletonList(report));
        printWriter.flush();

        verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
        verify(response).setCharacterEncoding(UTF8_CHARSET);
        verify(response).setStatus(eq(HttpServletResponse.SC_OK));

        String html = stringWriter.toString();
        assertTrue("Should contain openLegacyReport action", html.contains(OPEN_LEGACY_REPORT_ACTION_SNIPPET));
        assertTrue("Should contain reports array key", html.contains("\"reports\""));
        assertTrue("Should contain processUrl", html.contains("\"processUrl\""));
        assertTrue("Should contain tabTitle", html.contains("\"tabTitle\""));
        assertTrue("Should contain params", html.contains("\"params\""));
        assertTrue("Should contain the report URL", html.contains("/ad_reports/R.html"));
        assertTrue("Should contain the params value", html.contains("Command=DIRECT"));
        assertTrue("Should contain the title value", html.contains("Title"));
        assertFalse("Should not embed closeModal", html.contains("'closeModal'"));
    }

    @Test
    public void writeOpenLegacyReportForwarder_writesAllReports_whenMultiple() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        Object r1 = newReportInfo("/ad_reports/A.html", "T", "x=1");
        Object r2 = newReportInfo("/ad_reports/B.html", "T", "x=2");

        invokeWrite(response, Arrays.asList(r1, r2));
        printWriter.flush();

        String html = stringWriter.toString();
        assertTrue(html.contains("/ad_reports/A.html"));
        assertTrue(html.contains("/ad_reports/B.html"));
        assertTrue(html.contains("x=1"));
        assertTrue(html.contains("x=2"));
    }

    @Test
    public void writeOpenLegacyReportForwarder_jsonEscapesQuotesInTabTitle() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // Defensive: any embedded quote/backslash must be JSON-escaped via JSONObject/JSONArray.
        Object report = newReportInfo("/ad_reports/R.html", "He said \"hi\"", "x=1");

        invokeWrite(response, Collections.singletonList(report));
        printWriter.flush();

        String html = stringWriter.toString();
        assertTrue("Quote in tabTitle should be JSON-escaped (\\\\\")", html.contains("\\\""));
    }

    /**
     * Snapshot of a {@code ReportInfo} used to drive assertions without leaking
     * the inner-class shape into the test surface.
     */
    private static final class ReportSnapshot {
        final String processUrl;
        final String tabTitle;
        final String params;

        ReportSnapshot(String processUrl, String tabTitle, String params) {
            this.processUrl = processUrl;
            this.tabTitle = tabTitle;
            this.params = params;
        }

        @Override
        public String toString() {
            return "{processUrl=" + processUrl + ", tabTitle=" + tabTitle + ", params=" + params + "}";
        }
    }

    private static String newTabParams(String tabTitle) {
        return String.format(NEW_TAB_PARAMS_TEMPLATE, tabTitle);
    }

    @SuppressWarnings("unchecked")
    private List<ReportSnapshot> invokeExtract(String body, String contextPath) throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(EXTRACT_REPORTS, String.class, String.class);
        method.setAccessible(true);
        List<Object> raw = (List<Object>) method.invoke(servlet, body, contextPath);
        List<ReportSnapshot> snapshots = new ArrayList<>(raw.size());
        for (Object r : raw) {
            snapshots.add(toSnapshot(r));
        }
        return snapshots;
    }

    private ReportSnapshot toSnapshot(Object reportInfo) throws Exception {
        Class<?> klass = reportInfo.getClass();
        return new ReportSnapshot(
                readField(klass, reportInfo, "processUrl"),
                readField(klass, reportInfo, "tabTitle"),
                readField(klass, reportInfo, "params"));
    }

    private String readField(Class<?> klass, Object instance, String name) throws Exception {
        Field field = klass.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(instance);
        assertNotNull("Field " + name + " must not be null", value);
        return value.toString();
    }

    private Object newReportInfo(String processUrl, String tabTitle, String params) throws Exception {
        Class<?> klass = Class.forName("com.etendoerp.metadata.http.LegacyProcessServlet$ReportInfo");
        java.lang.reflect.Constructor<?> ctor =
                klass.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(processUrl, tabTitle, params);
    }

    private void invokeWrite(HttpServletResponse response, List<Object> reports) throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(WRITE_FORWARDER, HttpServletResponse.class,
                List.class);
        method.setAccessible(true);
        method.invoke(servlet, response, reports);
    }
}
