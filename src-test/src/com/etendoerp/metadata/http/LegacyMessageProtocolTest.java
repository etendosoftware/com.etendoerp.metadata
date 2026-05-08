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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Verifies that every {@link LegacyMessageProtocol} constant keeps its
 * documented string value.
 *
 * <p>These constants are part of a <strong>client-server contract</strong>:
 * the JavaScript side in {@code legacyMessageProtocol.ts} hard-codes the same
 * strings and must stay in sync. A failing test here means the TypeScript
 * counterpart also needs updating.
 */
public class LegacyMessageProtocolTest {

    /**
     * Verifies that {@link LegacyMessageProtocol#MESSAGE_TYPE} equals {@code "fromForm"},
     * the discriminator key the JavaScript side uses to identify messages originating
     * from a legacy iframe form.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void messageTypeIsFromForm() {
        assertEquals("fromForm", LegacyMessageProtocol.MESSAGE_TYPE);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_CLOSE_MODAL} equals
     * {@code "closeModal"}, the action token that instructs the parent window to
     * dismiss the currently open modal dialog.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionCloseModalValue() {
        assertEquals("closeModal", LegacyMessageProtocol.ACTION_CLOSE_MODAL);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_PROCESS_ORDER} equals
     * {@code "processOrder"}, the action token sent when a legacy process button
     * triggers an order-processing flow.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionProcessOrderValue() {
        assertEquals("processOrder", LegacyMessageProtocol.ACTION_PROCESS_ORDER);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_SHOW_PROCESS_MESSAGE} equals
     * {@code "showProcessMessage"}, the action token used to surface a process-result
     * message in the parent window after a legacy operation completes.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionShowProcessMessageValue() {
        assertEquals("showProcessMessage", LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_IFRAME_UNLOADED} equals
     * {@code "iframeUnloaded"}, the action token posted by the legacy iframe just
     * before it navigates away or is destroyed.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionIframeUnloadedValue() {
        assertEquals("iframeUnloaded", LegacyMessageProtocol.ACTION_IFRAME_UNLOADED);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_REQUEST_FAILED} equals
     * {@code "requestFailed"}, the action token sent when an HTTP request made from
     * inside the legacy iframe returns an error status.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionRequestFailedValue() {
        assertEquals("requestFailed", LegacyMessageProtocol.ACTION_REQUEST_FAILED);
    }

    /**
     * Verifies that {@link LegacyMessageProtocol#ACTION_OPEN_LEGACY_REPORT} equals
     * {@code "openLegacyReport"}, the action token that tells the parent window to
     * open a legacy report in a new tab or dialog.
     *
     * @throws AssertionError if the constant value has changed
     */
    @Test
    public void actionOpenLegacyReportValue() {
        assertEquals("openLegacyReport", LegacyMessageProtocol.ACTION_OPEN_LEGACY_REPORT);
    }

    /**
     * Verifies that none of the {@link LegacyMessageProtocol} constants are {@code null},
     * ensuring every token is safe to use in string comparisons without a null-check.
     *
     * @throws AssertionError if any constant is {@code null}
     */
    @Test
    public void allConstantsAreNonNull() {
        assertNotNull(LegacyMessageProtocol.MESSAGE_TYPE);
        assertNotNull(LegacyMessageProtocol.ACTION_CLOSE_MODAL);
        assertNotNull(LegacyMessageProtocol.ACTION_PROCESS_ORDER);
        assertNotNull(LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE);
        assertNotNull(LegacyMessageProtocol.ACTION_IFRAME_UNLOADED);
        assertNotNull(LegacyMessageProtocol.ACTION_REQUEST_FAILED);
        assertNotNull(LegacyMessageProtocol.ACTION_OPEN_LEGACY_REPORT);
    }
}
