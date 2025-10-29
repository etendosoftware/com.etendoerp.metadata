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

package com.etendoerp.metadata.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import static org.junit.Assert.*;

/**
 * Test class for AuthData.
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthDataTest {

    @Mock
    private User mockUser;
    
    @Mock
    private Role mockRole;
    
    @Mock
    private Organization mockOrg;
    
    @Mock
    private Warehouse mockWarehouse;
    
    @Mock
    private Client mockClient;

    private AuthData authData;

    @Before
    public void setUp() {
        authData = new AuthData(mockUser, mockRole, mockOrg, mockWarehouse, mockClient);
    }

    @Test
    public void testConstructor() {
        assertNotNull("AuthData should not be null", authData);
        assertEquals("User should match", mockUser, authData.getUser());
        assertEquals("Role should match", mockRole, authData.getRole());
        assertEquals("Organization should match", mockOrg, authData.getOrg());
        assertEquals("Warehouse should match", mockWarehouse, authData.getWarehouse());
        assertEquals("Client should match", mockClient, authData.getClient());
    }

    @Test
    public void testGetUser() {
        assertEquals("User should match", mockUser, authData.getUser());
    }

    @Test
    public void testGetAndSetRole() {
        assertEquals("Initial role should match", mockRole, authData.getRole());
        
        Role newRole = org.mockito.Mockito.mock(Role.class);
        authData.setRole(newRole);
        
        assertEquals("Updated role should match", newRole, authData.getRole());
    }

    @Test
    public void testGetAndSetOrg() {
        assertEquals("Initial org should match", mockOrg, authData.getOrg());
        
        Organization newOrg = org.mockito.Mockito.mock(Organization.class);
        authData.setOrg(newOrg);
        
        assertEquals("Updated org should match", newOrg, authData.getOrg());
    }

    @Test
    public void testGetAndSetWarehouse() {
        assertEquals("Initial warehouse should match", mockWarehouse, authData.getWarehouse());
        
        Warehouse newWarehouse = org.mockito.Mockito.mock(Warehouse.class);
        authData.setWarehouse(newWarehouse);
        
        assertEquals("Updated warehouse should match", newWarehouse, authData.getWarehouse());
    }

    @Test
    public void testGetAndSetClient() {
        assertEquals("Initial client should match", mockClient, authData.getClient());
        
        Client newClient = org.mockito.Mockito.mock(Client.class);
        authData.setClient(newClient);
        
        assertEquals("Updated client should match", newClient, authData.getClient());
    }

    @Test
    public void testConstructorWithNullValues() {
        AuthData authDataWithNulls = new AuthData(null, null, null, null, null);
        
        assertNotNull("AuthData should not be null", authDataWithNulls);
        assertNull("User should be null", authDataWithNulls.getUser());
        assertNull("Role should be null", authDataWithNulls.getRole());
        assertNull("Organization should be null", authDataWithNulls.getOrg());
        assertNull("Warehouse should be null", authDataWithNulls.getWarehouse());
        assertNull("Client should be null", authDataWithNulls.getClient());
    }

    @Test
    public void testSettersWithNull() {
        authData.setRole(null);
        authData.setOrg(null);
        authData.setWarehouse(null);
        authData.setClient(null);
        
        assertNull("Role should be null", authData.getRole());
        assertNull("Organization should be null", authData.getOrg());
        assertNull("Warehouse should be null", authData.getWarehouse());
        assertNull("Client should be null", authData.getClient());
        
        // User should still be the original since it's final
        assertEquals("User should remain unchanged", mockUser, authData.getUser());
    }
}