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

/**
 * Centralizes the postMessage envelope type and action names exchanged between the
 * legacy iframe and the parent window in the new UI.
 *
 * <p>Mirrored on the client by
 * {@code client/packages/MainUI/components/ProcessModal/legacyMessageProtocol.ts}.
 * Both files MUST stay in sync — any new action requires updating both.
 */
public final class LegacyMessageProtocol {

    /** Common envelope type used by every action emitted by the legacy iframe. */
    public static final String MESSAGE_TYPE = "fromForm";

    /** Tells the parent to close the iframe modal. */
    public static final String ACTION_CLOSE_MODAL = "closeModal";

    /** Signals that the user clicked the OK button on a Classic process popup. */
    public static final String ACTION_PROCESS_ORDER = "processOrder";

    /** Carries an in-page process message ({@code {type, title, text}} payload). */
    public static final String ACTION_SHOW_PROCESS_MESSAGE = "showProcessMessage";

    /** Notifies the parent that the iframe document is being unloaded. */
    public static final String ACTION_IFRAME_UNLOADED = "iframeUnloaded";

    /** Signals that the legacy request itself failed (surface generic error overlay). */
    public static final String ACTION_REQUEST_FAILED = "requestFailed";

    private LegacyMessageProtocol() {
    }
}
