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

package com.etendoerp.metadata.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.User;
import com.etendoerp.etendorx.data.ETRXTokenUser;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import com.etendoerp.metadata.auth.LoginDefaultsResolver;
import com.etendoerp.metadata.auth.StoreServerAccess;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Single sign-on login on a store server restricted to System Administrator
 * ({@code RestrictErpAccessInStoreServer}).
 */
class SSOServiceStoreServerTest extends SSOServiceTestSupport {

    /**
     * On a store server restricted to System Administrator, the single sign-on login of a user whose default
     * role is another one is refused with the classic message and no token is issued.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @SuppressWarnings({ "rawtypes" })
    @Test
    void callbackRefusedOnARestrictedStoreServer() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class);
             MockedStatic<LoginDefaultsResolver> resolverStatic = mockStatic(LoginDefaultsResolver.class);
             MockedStatic<StoreServerAccess> storeStatic = mockStatic(StoreServerAccess.class)) {
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBCriteria criteria = mockOBDalCriteria(obDalStatic);
            ETRXTokenUser mockTokenUser = mock(ETRXTokenUser.class);
            User mockUser = mock(User.class);
            when(mockUser.getId()).thenReturn(SSO_USER_ID);
            when(mockTokenUser.getUserForToken()).thenReturn(mockUser);
            when(criteria.uniqueResult()).thenReturn(mockTokenUser);
            storeStatic.when(() -> StoreServerAccess.checkLogin(SSO_USER_ID))
                .thenThrow(new UnauthorizedException("Only System Administrator can access the ERP"));

            assertEquals("access_restricted", spyExecuteAndParse().getString(ERROR_KEY));
            utilsStatic.verify(() -> Utils.generateToken(any(), any()), never());
        }
    }
}
