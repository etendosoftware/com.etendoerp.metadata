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
    private static final String JOURNAL_ENTRIES_REPORT_TITLE = "Journal Entries Report";
    private static final String TAB_TITLE = "Title";
    private static final String REPORT_HTML = "/ad_reports/Report.html";
    private static final String COMMAND_DIRECT = "Command=DIRECT";
    private static final String SUBMIT_R_HTML = "submitThisPage('/etendo/ad_reports/R.html');";
    private static final String REPORT_R_HTML = "/ad_reports/R.html";
    private static final String SHARED_TITLE = "Shared Title";

    private LegacyProcessServlet servlet;

    /**
     * Creates a fresh {@link LegacyProcessServlet} instance for each test.
     */
    @Before
    public void setUp() {
        servlet = new LegacyProcessServlet();
    }

    /**
     * Tests that a single quoted submitThisPage call surfaces one report with
     * the host/context stripped and the query split into params.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsSingleReportWhenSubmitThisPageHasQuotedHref() throws Exception {
        String url = "/etendo/ad_reports/ReportGeneralLedgerJournal.html?Command=DIRECT&inpRecord=42";
        String body = newTabParams(JOURNAL_ENTRIES_REPORT_TITLE)
                + "<body onload=\"submitThisPage('" + url + "');\">";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(JOURNAL_ENTRIES_REPORT, reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpRecord=42", reports.get(0).params);
        assertEquals(JOURNAL_ENTRIES_REPORT_TITLE, reports.get(0).tabTitle);
    }

    /**
     * Tests that multiple submitThisPage calls in the body each surface as
     * separate report entries with their own processUrl and params.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsAllReportsWhenMultipleSubmitThisPageCalls() throws Exception {
        String url1 = "/etendo/ad_reports/Report1.html?inpRecord=1";
        String url2 = "/etendo/ad_reports/Report2.html?inpRecord=2";
        String body = newTabParams(TAB_TITLE)
                + "first: submitThisPage('" + url1 + "'); then: submitThisPage('" + url2 + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(2, reports.size());
        assertEquals("/ad_reports/Report1.html", reports.get(0).processUrl);
        assertEquals("inpRecord=1", reports.get(0).params);
        assertEquals("/ad_reports/Report2.html", reports.get(1).processUrl);
        assertEquals("inpRecord=2", reports.get(1).params);
    }

    /**
     * Tests that {@code submitThisPage(null)} does not produce a report entry.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenSubmitThisPageNull() throws Exception {
        String body = "<body onload=\"submitThisPage(null);\" id=\"paramHref\">";

        assertTrue(invokeExtract(body, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    /**
     * Tests that a {@code null} body returns an empty list (defensive guard).
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenBodyIsNull() throws Exception {
        assertTrue(invokeExtract(null, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    /**
     * Tests that ampersands and commas inside the query string do not split
     * the URL into multiple reports.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyHandlesUrlWithEmbeddedAmpersandsAndCommas() throws Exception {
        // Uses a non-journal-entries URL so multi-schema expansion is not triggered;
        // the intent is to verify that commas and ampersands inside query params do
        // not incorrectly split the URL into multiple reports.
        String url = "/etendo/ad_reports/ReportGeneralLedgerJournal.html?Command=DIRECT"
                + "&inpTable=318&inpRecord=ABC&inpAccSchemas=A,B,C";
        String body = newTabParams(JOURNAL_ENTRIES_REPORT_TITLE)
                + "submitThisPage('" + url + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(JOURNAL_ENTRIES_REPORT, reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpTable=318&inpRecord=ABC&inpAccSchemas=A,B,C",
                reports.get(0).params);
    }

    /**
     * Tests that the popup pattern only accepts single-quoted URLs (the
     * classic template emits single quotes; double-quoted args must not match).
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenSubmitThisPageHasDoubleQuotes() throws Exception {
        // Defensive: the popup template emits single-quoted args; double-quoted should NOT match
        // and the regular pipeline should keep handling that body.
        String body = "submitThisPage(\"/etendo/ad_reports/X.html\");";

        assertTrue(invokeExtract(body, DEFAULT_CONTEXT_PATH).isEmpty());
    }

    /**
     * Tests that {@code submitThisPage('SAVE'/'OK'/'EDIT'/'REFRESH')} command
     * tokens are not surfaced as report URLs.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenArgIsActionCode() throws Exception {
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

    /**
     * Tests that when a body mixes action codes and a URL, only the URL-shaped
     * value is surfaced as a report.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyExtractsOnlyUrlShapedValuesWhenMixedWithActionCodes() throws Exception {
        // Defensive: even if a popup body somehow mixes both, only the URL-shaped
        // value should surface as a report URL.
        String url = "/etendo/ad_reports/Report.html?Command=DIRECT";
        String body = newTabParams("T")
                + "submitThisPage('SAVE'); submitThisPage('" + url + "'); submitThisPage('OK');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(REPORT_HTML, reports.get(0).processUrl);
        assertEquals(COMMAND_DIRECT, reports.get(0).params);
    }

    /**
     * Tests that an absolute URL has the host and context path stripped, and
     * the remainder split on the first {@code ?}.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodySplitsAbsoluteUrlIntoProcessUrlAndParams() throws Exception {
        // Realistic Posted flow: backend builds the URL with strDireccion + "/ad_reports/...".
        String url = "http://localhost:8080/etendo/ad_reports/Report.html?Command=DIRECT&inpRecord=42";
        String body = newTabParams(TAB_TITLE) + "submitThisPage('" + url + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(REPORT_HTML, reports.get(0).processUrl);
        assertEquals("Command=DIRECT&inpRecord=42", reports.get(0).params);
    }

    /**
     * Tests that a URL without a query string yields an empty {@code params}
     * field on the resulting report.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyParamsWhenUrlHasNoQuery() throws Exception {
        String body = newTabParams(TAB_TITLE) + SUBMIT_R_HTML;

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(REPORT_R_HTML, reports.get(0).processUrl);
        assertEquals("", reports.get(0).params);
    }

    /**
     * Tests that the {@code tabTitle} field is read from the {@code newTabParams}
     * JSON literal embedded in the popup body.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyExtractsTabTitleFromNewTabParams() throws Exception {
        String body = newTabParams(JOURNAL_ENTRIES_REPORT_TITLE)
                + SUBMIT_R_HTML;

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(JOURNAL_ENTRIES_REPORT_TITLE, reports.get(0).tabTitle);
    }

    /**
     * Tests that a missing {@code newTabParams} literal still surfaces the
     * report with an empty {@code tabTitle}.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyTabTitleWhenNewTabParamsAbsent() throws Exception {
        // Defensive: a popup body without newTabParams should still surface the
        // report (older callers / overload of printPageClosePopUp without title).
        String body = SUBMIT_R_HTML;

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("", reports.get(0).tabTitle);
    }

    /**
     * Tests that JSON-escaped quotes inside the {@code tabTitle} value do not
     * stop the extraction at the first {@code "}.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyExtractsTabTitleWhenJsonHasEscapedQuote() throws Exception {
        // The newTabParams literal is rendered by JSONObject.toString() and may
        // contain JSON-escaped quotes. The regex must not stop at the first \".
        String body = "<script>var newTabParams={\"tabTitle\":\"He said \\\"hi\\\"\",\"addToRecents\":false};</script>"
                + SUBMIT_R_HTML;

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("He said \\\"hi\\\"", reports.get(0).tabTitle);
    }

    /**
     * Tests that when multiple URLs are present, the same {@code tabTitle} is
     * applied to every report (one tabTitle per popup body).
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyAppliesTabTitleToAllReportsWhenMultipleSubmitThisPage() throws Exception {
        String body = newTabParams(SHARED_TITLE)
                + "submitThisPage('/etendo/ad_reports/A.html');"
                + "submitThisPage('/etendo/ad_reports/B.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(2, reports.size());
        assertEquals(SHARED_TITLE, reports.get(0).tabTitle);
        assertEquals(SHARED_TITLE, reports.get(1).tabTitle);
    }

    /**
     * Tests that the context path is only stripped when it actually matches
     * the URL prefix.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyKeepsContextPathWhenContextPathDoesNotMatch() throws Exception {
        String body = newTabParams("T") + "submitThisPage('/other/ad_reports/R.html?x=1');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals("/other/ad_reports/R.html", reports.get(0).processUrl);
        assertEquals("x=1", reports.get(0).params);
    }

    /**
     * Tests that a {@code null} context path leaves the URL unchanged.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyHandlesNullContextPath() throws Exception {
        String body = newTabParams("T") + "submitThisPage('/etendo/ad_reports/R.html?x=1');";

        List<ReportSnapshot> reports = invokeExtract(body, null);

        assertEquals(1, reports.size());
        assertEquals("/etendo/ad_reports/R.html", reports.get(0).processUrl);
        assertEquals("x=1", reports.get(0).params);
    }

    /**
     * Tests that window-refresh URLs (Reactivate-style {@code submitThisPage}
     * on a tab path) do not short-circuit as report URLs.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenUrlIsParentTabRefresh() throws Exception {
        // Reactivate (and other non-Posted action buttons) call printPageClosePopUp
        // with a path obtained from Utility.getTabURL(strTabId, "R", true) — a window
        // URL meant to refresh the parent tab in the classic UI, NOT a report. The
        // new UI must let the regular processOrder/showProcessMessage pipeline handle
        // these, so the forwarder must not short-circuit them.
        String body = "submitThisPage('/SalesInvoice/Header_Relation.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertTrue("Window-refresh URLs must not be treated as report URLs: " + reports, reports.isEmpty());
    }

    /**
     * Tests that an absolute window-refresh URL is also filtered out of the
     * report list.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyReturnsEmptyWhenAbsoluteUrlIsParentTabRefresh() throws Exception {
        // Exact reproduction of the QA repro: Sales Invoice → Reactivate emits the
        // absolute window URL of its own tab.
        String body = "submitThisPage('http://localhost:8080/etendo/SalesInvoice/Header_Relation.html');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertTrue("Absolute window-refresh URLs must not be treated as report URLs: " + reports, reports.isEmpty());
    }

    /**
     * Tests that when a body contains both a window-refresh URL and a report
     * URL, only the report surfaces.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void extractReportsFromBodyFiltersOutWindowUrlsWhenMixedWithReportUrls() throws Exception {
        // Defensive: a popup body that contains both a window-refresh URL and a
        // report URL (e.g. a hypothetical compound action) must surface only the
        // report; the window URL is handled by the regular pipeline.
        String reportUrl = "/etendo/ad_reports/Report.html?Command=DIRECT";
        String body = newTabParams("T")
                + "submitThisPage('/SalesInvoice/Header_Relation.html');"
                + " submitThisPage('" + reportUrl + "');";

        List<ReportSnapshot> reports = invokeExtract(body, DEFAULT_CONTEXT_PATH);

        assertEquals(1, reports.size());
        assertEquals(REPORT_HTML, reports.get(0).processUrl);
        assertEquals(COMMAND_DIRECT, reports.get(0).params);
    }

    /**
     * Tests that the forwarder writes an HTML response carrying the
     * {@code openLegacyReport} payload with {@code reports/processUrl/tabTitle/params}
     * keys and the expected content type/charset/status.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void writeOpenLegacyReportForwarderWritesPayloadAsReportsArray() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        Object report = newReportInfo(REPORT_R_HTML, TAB_TITLE, COMMAND_DIRECT);
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
        // Jettison escapes forward slashes as \/ in JSON strings
        assertTrue("Should contain the report URL", html.contains("\\/ad_reports\\/R.html"));
        assertTrue("Should contain the params value", html.contains(COMMAND_DIRECT));
        assertTrue("Should contain the title value", html.contains(TAB_TITLE));
        assertFalse("Should not embed closeModal", html.contains("'closeModal'"));
    }

    /**
     * Tests that every report in the input list appears in the emitted JSON
     * payload.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void writeOpenLegacyReportForwarderWritesAllReportsWhenMultiple() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        Object r1 = newReportInfo("/ad_reports/A.html", "T", "x=1");
        Object r2 = newReportInfo("/ad_reports/B.html", "T", "x=2");

        invokeWrite(response, Arrays.asList(r1, r2));
        printWriter.flush();

        // Jettison escapes forward slashes as \/ in JSON strings
        String html = stringWriter.toString();
        assertTrue(html.contains("\\/ad_reports\\/A.html"));
        assertTrue(html.contains("\\/ad_reports\\/B.html"));
        assertTrue(html.contains("x=1"));
        assertTrue(html.contains("x=2"));
    }

    /**
     * Tests that quotes embedded in {@code tabTitle} are JSON-escaped in the
     * emitted payload.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void writeOpenLegacyReportForwarderJsonEscapesQuotesInTabTitle() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // Defensive: any embedded quote/backslash must be JSON-escaped via JSONObject/JSONArray.
        Object report = newReportInfo(REPORT_R_HTML, "He said \"hi\"", "x=1");

        invokeWrite(response, Collections.singletonList(report));
        printWriter.flush();

        String html = stringWriter.toString();
        assertTrue("Quote in tabTitle should be JSON-escaped (\\\\\")", html.contains("\\\""));
    }

    // -----------------------------------------------------------------
    //  Multi-schema expansion (Journal Entries Report)
    // -----------------------------------------------------------------

    private static final String JOURNAL_ENTRIES_REPORT = "/ad_reports/ReportGeneralLedgerJournal.html";
    private static final String OTHER_REPORT = "/ad_reports/OtherReport.html";

    /**
     * Default fake resolver: returns "Schema-<id>" so each id maps to a
     * deterministic, unique name without hitting the DAL.
     */
    private static final java.util.function.Function<String, String> FAKE_NAME_RESOLVER =
            id -> "Schema-" + id;
    private static final String JOURNAL_ENTRIES_TITLE_X = "Journal Entries Report - X";
    private static final String JOURNAL_ENTRIES_TITLE_MAIN_USA = "Journal Entries Report - Main USA";
    private static final String PARAMS_SINGLE_SCHEMA_POSTED = "Command=DIRECT&inpAccSchemas=A&posted=Y";
    private static final String PARAMS_TWO_SCHEMAS_POSTED = "Command=DIRECT&inpAccSchemas=A,B&posted=Y";

    /**
     * Tests that a single accounting schema in {@code inpAccSchemas} does not
     * trigger multi-schema expansion.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsDoesNotExpandWhenSingleSchema() throws Exception {
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                PARAMS_SINGLE_SCHEMA_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(1, result.size());
        assertEquals(PARAMS_SINGLE_SCHEMA_POSTED, result.get(0).params);
    }

    /**
     * Tests that the absence of {@code posted=Y} prevents expansion even when
     * multiple schemas are present.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsDoesNotExpandWhenPostedFlagAbsent() throws Exception {
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                "Command=DIRECT&inpAccSchemas=A,B,C");

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(1, result.size());
    }

    /**
     * Tests that expansion only applies to the Journal Entries Report path,
     * not other reports.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsDoesNotExpandWhenReportIsNotJournalEntries() throws Exception {
        Object report = newReportInfo(OTHER_REPORT, "Other - X",
                PARAMS_TWO_SCHEMAS_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(1, result.size());
        assertEquals(OTHER_REPORT, result.get(0).processUrl);
    }

    /**
     * Tests that N schemas combined with {@code posted=Y} yield N report
     * entries: entry 0 keeps the original schema list (with {@code posted=Y}
     * stripped) and entries 1..N-1 use {@code inpParamschemas} with one
     * schema each plus a tab title suffixed with the schema name.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsExpandsIntoNReportsWhenJournalEntriesWithMultipleSchemasAndPosted()
            throws Exception {
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_MAIN_USA,
                "Command=DIRECT&inpTable=318&inpRecord=R&inpOrg=O&inpAccSchemas=A,B,C&posted=Y");

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(3, result.size());
        // Entry 0: clone of the original WITHOUT posted=Y (the popup's
        // openTabWhenPost JS would otherwise spawn an in-popup Smartclient
        // tab for schema B and override this popup's own content).
        assertEquals(JOURNAL_ENTRIES_TITLE_MAIN_USA, result.get(0).tabTitle);
        assertEquals("Command=DIRECT&inpTable=318&inpRecord=R&inpOrg=O&inpAccSchemas=A,B,C",
                result.get(0).params);
        // Entry 1: schema B
        assertEquals(JOURNAL_ENTRIES_REPORT, result.get(1).processUrl);
        assertEquals("Command=DIRECT&inpTable=318&inpRecord=R&inpOrg=O&inpParamschemas=B",
                result.get(1).params);
        assertEquals("Journal Entries Report - Schema-B", result.get(1).tabTitle);
        // Entry 2: schema C
        assertEquals("Command=DIRECT&inpTable=318&inpRecord=R&inpOrg=O&inpParamschemas=C",
                result.get(2).params);
        assertEquals("Journal Entries Report - Schema-C", result.get(2).tabTitle);
    }

    /**
     * Tests that the helper does not emit stray {@code &inpTable=}/{@code &inpRecord=}/{@code &inpOrg=}
     * pairs when those keys are missing from the original query.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsOmitsEmptyFiltersInExpansion() throws Exception {
        // Posted variants without invoice context (defensive — the catalogued
        // Posted always emits inpTable/inpRecord/inpOrg, but the helper must
        // not produce stray "&inpTable=" pairs for missing keys).
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                PARAMS_TWO_SCHEMAS_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(2, result.size());
        assertEquals("Command=DIRECT&inpParamschemas=B", result.get(1).params);
    }

    /**
     * Tests that the schema name appended to the tab title replaces the
     * suffix after the LAST {@code  - } separator in the original title.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsKeepsTitlePrefixWhenOriginalContainsSeparator() throws Exception {
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_MAIN_USA,
                PARAMS_TWO_SCHEMAS_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(2, result.size());
        // Prefix kept = everything before the LAST " - "
        assertEquals("Journal Entries Report - Schema-B", result.get(1).tabTitle);
    }

    /**
     * Tests that when the original title has no {@code  - }, the schema name
     * is appended (with a fresh separator).
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsAppendsToTitleWhenOriginalLacksSeparator() throws Exception {
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, "PlainTitle",
                PARAMS_TWO_SCHEMAS_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(2, result.size());
        // No " - " in the original → prefix = the whole title; suffix appended
        assertEquals("PlainTitle - Schema-B", result.get(1).tabTitle);
    }

    /**
     * Tests that when the resolver returns the raw schema id (lookup miss),
     * the title still carries that id instead of failing.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsFallsBackToSchemaIdWhenResolverReturnsId() throws Exception {
        // Mirrors what the real resolveAcctSchemaName does on lookup failure:
        // it returns the raw schemaId so the popup can still open with the
        // UUID instead of a localised name.
        java.util.function.Function<String, String> idEcho = id -> id;
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, "Journal Entries Report - Main",
                "Command=DIRECT&inpAccSchemas=A,UNKNOWN&posted=Y");

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), idEcho);

        assertEquals(2, result.size());
        assertEquals("Journal Entries Report - UNKNOWN", result.get(1).tabTitle);
    }

    /**
     * Tests that {@code posted=Y} is stripped from entry 0 when expanding so
     * the popup's {@code openTabWhenPost} JS does not spawn extra in-popup
     * Smartclient tabs that would override the visible schema.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsRemovesPostedFlagFromFirstReportWhenExpanding() throws Exception {
        // Once the backend has emitted N entries for the additional schemas,
        // keeping posted=Y on the first entry would make the popup's
        // openTabWhenPost JS open an extra Smartclient tab inside that popup
        // for schema B and override the visible schema A.
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                PARAMS_TWO_SCHEMAS_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(2, result.size());
        assertFalse("First entry must not carry posted=Y once expanded",
                result.get(0).params.contains("posted=Y"));
        assertFalse("First entry must not carry posted= at all",
                result.get(0).params.contains("posted="));
        assertTrue("First entry must keep the rest of the original query",
                result.get(0).params.contains("inpAccSchemas=A,B"));
    }

    /**
     * Tests that {@code posted=Y} is preserved when there is only one schema
     * (no expansion path is taken).
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsKeepsPostedFlagWhenSingleSchema() throws Exception {
        // Single-schema does not expand; keeping posted=Y is harmless because
        // openTabWhenPost iterates over inpParamschemas="" and the empty entry
        // is filtered out before the in-popup tab spawn.
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                PARAMS_SINGLE_SCHEMA_POSTED);

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals(1, result.size());
        assertTrue("Single-schema entry must preserve posted=Y (no expansion path taken)",
                result.get(0).params.contains("posted=Y"));
    }

    /**
     * Tests that a trailing comma in {@code inpAccSchemas} does not produce a
     * phantom empty schema entry.
     *
     * @throws Exception if the underlying reflection call fails
     */
    @Test
    public void expandMultiSchemaReportsSkipsEmptyEntriesWhenCsvHasTrailingComma() throws Exception {
        // The classic Java loop that builds the schemas variable in
        // ReportGeneralLedgerJournal.java appends an extra "," to each id
        // (`schemas + accSchemas[i] + ","`). If that artefact ever leaks into
        // inpAccSchemas, the split must not produce a phantom empty entry.
        Object report = newReportInfo(JOURNAL_ENTRIES_REPORT, JOURNAL_ENTRIES_TITLE_X,
                "Command=DIRECT&inpAccSchemas=A,B,&posted=Y");

        List<ReportSnapshot> result = invokeExpand(Collections.singletonList(report), FAKE_NAME_RESOLVER);

        assertEquals("Trailing-comma should not produce a third report: " + result, 2, result.size());
        assertEquals("Command=DIRECT&inpParamschemas=B", result.get(1).params);
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

    /**
     * Builds the {@code newTabParams} script literal for a given tabTitle.
     */
    private static String newTabParams(String tabTitle) {
        return String.format(NEW_TAB_PARAMS_TEMPLATE, tabTitle);
    }

    /**
     * Invokes {@code extractReportsFromBody(body, contextPath)} via reflection
     * and converts the resulting {@code ReportInfo} list into snapshots.
     *
     * @param body        the popup HTML body to parse
     * @param contextPath the servlet context path used to strip URL prefixes
     * @return list of {@link ReportSnapshot} extracted from the body
     * @throws Exception if the method cannot be found or invoked via reflection
     */
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

    /**
     * Reads the {@code processUrl}/{@code tabTitle}/{@code params} fields from
     * a {@code ReportInfo} via reflection and wraps them in a {@link ReportSnapshot}.
     *
     * @param reportInfo a {@code ReportInfo} instance obtained from the servlet
     * @return a {@link ReportSnapshot} with the three field values
     * @throws Exception if a field cannot be read via reflection
     */
    private ReportSnapshot toSnapshot(Object reportInfo) throws Exception {
        Class<?> klass = reportInfo.getClass();
        return new ReportSnapshot(
                readField(klass, reportInfo, "processUrl"),
                readField(klass, reportInfo, "tabTitle"),
                readField(klass, reportInfo, "params"));
    }

    /**
     * Reads a String field by name via reflection, asserting non-null.
     *
     * @param klass    the class that declares the field
     * @param instance the object to read the field from
     * @param name     the field name
     * @return the field value as a string
     * @throws Exception if the named field cannot be found or accessed via reflection
     */
    private String readField(Class<?> klass, Object instance, String name) throws Exception {
        Field field = klass.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(instance);
        assertNotNull("Field " + name + " must not be null", value);
        return value.toString();
    }

    /**
     * Constructs a {@code ReportInfo} via reflection (the inner class is
     * package-private and not exposed to the test package directly).
     *
     * @param processUrl the report path (e.g. {@code /ad_reports/Report.html})
     * @param tabTitle   the tab title to associate with the report
     * @param params     the query-string parameters (without leading {@code ?})
     * @return a {@code ReportInfo} instance
     * @throws Exception if the {@code ReportInfo} constructor cannot be found or invoked via reflection
     */
    private Object newReportInfo(String processUrl, String tabTitle, String params) throws Exception {
        Class<?> klass = Class.forName("com.etendoerp.metadata.http.LegacyProcessServlet$ReportInfo");
        java.lang.reflect.Constructor<?> ctor =
                klass.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(processUrl, tabTitle, params);
    }

    /**
     * Invokes {@code writeOpenLegacyReportForwarder(response, reports)} via
     * reflection.
     *
     * @param response the mocked {@link HttpServletResponse} to write to
     * @param reports  list of {@code ReportInfo} objects to serialize
     * @throws Exception if the method cannot be found or invoked via reflection
     */
    private void invokeWrite(HttpServletResponse response, List<Object> reports) throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(WRITE_FORWARDER, HttpServletResponse.class,
                List.class);
        method.setAccessible(true);
        method.invoke(servlet, response, reports);
    }

    /**
     * Calls {@code expandMultiSchemaReports(reports, resolver)} via reflection
     * and converts the resulting {@code ReportInfo} list into snapshots. Used
     * by the multi-schema tests to inject a deterministic name resolver
     * instead of hitting DAL.
     *
     * @param reports  list of {@code ReportInfo} objects to expand
     * @param resolver function mapping an accounting-schema ID to its display name
     * @return list of {@link ReportSnapshot} after expansion
     * @throws Exception if the method cannot be found or invoked via reflection
     */
    @SuppressWarnings("unchecked")
    private List<ReportSnapshot> invokeExpand(List<Object> reports,
            java.util.function.Function<String, String> resolver) throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "expandMultiSchemaReports", List.class, java.util.function.Function.class);
        method.setAccessible(true);
        List<Object> raw = (List<Object>) method.invoke(servlet, reports, resolver);
        List<ReportSnapshot> snapshots = new ArrayList<>(raw.size());
        for (Object r : raw) {
            snapshots.add(toSnapshot(r));
        }
        return snapshots;
    }
}
