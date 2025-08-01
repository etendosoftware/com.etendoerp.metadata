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

package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    private void process(HttpServletRequest req, HttpServletResponse res) throws IOException {
        ServiceFactory.getService(req, res).process();
    }

    @Override
    public final void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        process(req, res);
    }

    @Override
    public final void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        process(req, res);
    }

    @Override
    public final void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        process(req, res);
    }

    @Override
    public final void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        process(req, res);
    }
}
