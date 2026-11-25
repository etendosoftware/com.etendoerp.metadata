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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.GET;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.data.SavedView;

/**
 * Unit tests for the window-level lookup of default views ({@code GET ?window=<id>&isdefault=true}):
 * one query returns the effective default view of every tab of the window, keeping the most specific
 * scope per tab (USER &gt; ROLE &gt; ORGANIZATION &gt; CLIENT &gt; SYSTEM).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SavedViewServiceWindowDefaultsTest extends AbstractSavedViewServiceTest {

    private static final String WINDOW_ID = "window-143";
    private static final String WINDOW_PARAM = "window";
    private static final String TAB_PARAM = "tab";
    private static final String TRUE = "true";
    private static final String OTHER_TAB_ID = "tab-child-789";
    private static final String ROLE_VIEW_ID = "view-role-1";
    private static final String CHILD_VIEW_ID = "view-child-1";
    private static final String SECOND_USER_VIEW_ID = "view-user-2";
    private static final String ID = "id";

    /** Stubs a GET list request with the given window and isdefault parameters and no tab. */
    private void givenWindowRequest(String isDefault) {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(null);
        when(mockRequest.getParameter(WINDOW_PARAM)).thenReturn(WINDOW_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn(isDefault);
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
    }

    /** Builds a ROLE-scoped view (no user, the session role) of the given tab. */
    private SavedView roleView(String id, Tab tab) {
        SavedView view = mock(SavedView.class);
        when(view.getId()).thenReturn(id);
        when(view.getTab()).thenReturn(tab);
        when(view.getUser()).thenReturn(null);
        when(view.getRole()).thenReturn(mockRole);
        return view;
    }

    /** Builds a USER-scoped view (owned by the session user) of the given tab. */
    private SavedView userView(String id, Tab tab) {
        SavedView view = mock(SavedView.class);
        when(view.getId()).thenReturn(id);
        when(view.getTab()).thenReturn(tab);
        when(view.getUser()).thenReturn(mockUser);
        return view;
    }

    /** Builds a tab mock reporting the given id. */
    private Tab tab(String id) {
        Tab tab = mock(Tab.class);
        when(tab.getId()).thenReturn(id);
        return tab;
    }

    /**
     * Runs the request and returns the ids of the views of the response, in order.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    private List<String> processAndReadViewIds() throws IOException, JSONException {
        service.process();
        JSONArray data = new JSONObject(responseWriter.toString()).getJSONObject("response").getJSONArray("data");
        String[] ids = new String[data.length()];
        for (int i = 0; i < data.length(); i++) {
            ids[i] = data.getJSONObject(i).getString(ID);
        }
        return Arrays.asList(ids);
    }

    /**
     * A single query answers every tab: the user view of the header tab prevails over its role view,
     * whichever comes first, and the child tab keeps its own default.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    @Test
    public void testWindowDefaultsKeepTheMostSpecificViewOfEachTab() throws IOException, JSONException {
        givenWindowRequest(TRUE);
        SavedView headerRoleView = roleView(ROLE_VIEW_ID, mockTab);
        SavedView childRoleView = roleView(CHILD_VIEW_ID, tab(OTHER_TAB_ID));
        when(mockCriteria.list()).thenReturn(Arrays.asList(headerRoleView, mockView, childRoleView));

        assertEquals(Arrays.asList(VIEW_ID, CHILD_VIEW_ID), processAndReadViewIds());
        verify(mockCriteria, times(1)).list();
        verify(mockCriteria).createAlias(anyString(), anyString());
    }

    /**
     * A less specific view found after the effective one never replaces it.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    @Test
    public void testWindowDefaultsIgnoreALessSpecificViewFoundLater() throws IOException, JSONException {
        givenWindowRequest(TRUE);
        SavedView laterRoleView = roleView(ROLE_VIEW_ID, mockTab);
        when(mockCriteria.list()).thenReturn(Arrays.asList(mockView, laterRoleView));

        assertEquals(Collections.singletonList(VIEW_ID), processAndReadViewIds());
    }

    /**
     * Two views of the same scope and tab keep the first one, as the per-tab lookup does.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    @Test
    public void testWindowDefaultsKeepTheFirstViewOnATie() throws IOException, JSONException {
        givenWindowRequest(TRUE);
        SavedView secondUserView = userView(SECOND_USER_VIEW_ID, mockTab);
        when(mockCriteria.list()).thenReturn(Arrays.asList(mockView, secondUserView));

        assertEquals(Collections.singletonList(VIEW_ID), processAndReadViewIds());
    }

    /**
     * A window without default views answers an empty list.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    @Test
    public void testWindowWithoutDefaultsReturnsEmptyList() throws IOException, JSONException {
        givenWindowRequest(TRUE);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), processAndReadViewIds());
    }

    /**
     * Without {@code isdefault=true} the window parameter is ignored: the generic list is returned,
     * so existing callers keep their behavior.
     *
     * @throws IOException   if an I/O error occurs during processing
     * @throws JSONException if the response is not the expected JSON
     */
    @Test
    public void testWindowParameterIgnoredWithoutIsDefault() throws IOException, JSONException {
        givenWindowRequest(null);
        SavedView headerRoleView = roleView(ROLE_VIEW_ID, mockTab);
        when(mockCriteria.list()).thenReturn(Arrays.asList(mockView, headerRoleView));

        assertEquals(Arrays.asList(VIEW_ID, ROLE_VIEW_ID), processAndReadViewIds());
        verify(mockCriteria, never()).createAlias(anyString(), anyString());
    }
}
