package com.etendoerp.metadata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;
import org.openbravo.model.common.geography.Region;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.service.LocationService.LocationData;

/**
 * Test class for LocationService, which handles the creation of Location objects in Openbravo.
 * This class includes tests for various scenarios including successful creation, error handling,
 * and identifier building.
 */
@RunWith(MockitoJUnitRunner.class)
public class LocationServiceTest extends OBBaseTest {

    @Mock
    private OBProvider obProvider;
    
    @Mock
    private OBDal obDal;
    
    @Mock
    private OBContext obContext;
    
    @Mock
    private Location location;
    
    @Mock
    private Country country;
    
    @Mock
    private Region region;
    
    @Mock
    private User user;
    
    private LocationService locationService;
    private LocationData locationData;

    /**
     * Initializes test environment before each test execution.
     * Creates LocationService instance, sample LocationData with address information,
     * and configures mock objects for OBProvider, OBContext, OBDal, Country, and Region.
     *
     * @throws Exception if parent setUp() fails or mock configuration encounters issues
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        locationService = new LocationService();
        
        locationData = new LocationData(
            "123 Main Street",
            "Apt 4B", 
            "12345",
            "Springfield",
            "country-id",
            "region-id"
        );
        
        when(obProvider.get(Location.class)).thenReturn(location);
        when(obContext.getUser()).thenReturn(user);
        when(obDal.get(Country.class, "country-id")).thenReturn(country);
        when(obDal.get(Region.class, "region-id")).thenReturn(region);
        when(country.getName()).thenReturn("United States");
        when(region.getName()).thenReturn("California");
    }

    /**
     * Test to ensure that the createLocation method successfully creates a Location object
     * and returns the expected result.
     */
    @Test
    public void createLocationShouldReturnSuccessfulResult() {
        String generatedId = "test-location-id";
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(locationData);
            
            assertNotNull(result);
            assertEquals(generatedId, result.get("id"));
            assertEquals("123 Main Street", result.get("address1"));
            assertEquals("Apt 4B", result.get("address2"));
            assertEquals("12345", result.get("postal"));
            assertEquals("Springfield", result.get("city"));
            assertEquals("country-id", result.get("countryId"));
            assertEquals("region-id", result.get("regionId"));
            assertTrue(result.containsKey("_identifier"));
            
            verify(location).setId(generatedId);
            verify(location).setNewOBObject(true);
            verify(location).setActive(true);
            verify(location).setCreatedBy(user);
            verify(location).setUpdatedBy(user);
            verify(location).setCountry(country);
            verify(location).setRegion(region);
            verify(location).setAddressLine1("123 Main Street");
            verify(location).setAddressLine2("Apt 4B");
            verify(location).setPostalCode("12345");
            verify(location).setCityName("Springfield");
            verify(obDal).save(location);
            verify(obDal).flush();
        }
    }

    /**
     * Test to ensure that the createLocation method builds the correct identifier
     * based on the provided location data.
     */
    @Test
    public void createLocationShouldBuildCorrectIdentifier() {
        String generatedId = "test-location-id";
        
        when(location.getAddressLine1()).thenReturn("123 Main Street");
        when(location.getAddressLine2()).thenReturn("Apt 4B");
        when(location.getPostalCode()).thenReturn("12345");
        when(location.getCityName()).thenReturn("Springfield");
        when(location.getRegion()).thenReturn(region);
        when(location.getCountry()).thenReturn(country);
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(locationData);
            
            String identifier = (String) result.get("_identifier");
            assertTrue(identifier.contains("123 Main Street"));
            assertTrue(identifier.contains("Apt 4B"));
            assertTrue(identifier.contains("12345"));
            assertTrue(identifier.contains("Springfield"));
            assertTrue(identifier.contains("California"));
            assertTrue(identifier.contains("United States"));
        }
    }

    /**
     * Test to ensure that the createLocation method handles minimal data correctly,
     * without setting country or region if they are not provided.
     */
    @Test
    public void createLocationWithMinimalDataShouldWork() {
        LocationData minimalData = new LocationData("Main St", null, null, "City", null, null);
        String generatedId = "test-location-id";
        
        when(location.getAddressLine1()).thenReturn("Main St");
        when(location.getCityName()).thenReturn("City");
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(minimalData);
            
            assertNotNull(result);
            assertEquals(generatedId, result.get("id"));
            assertEquals("Main St", result.get("address1"));
            assertNull(result.get("address2"));
            assertNull(result.get("postal"));
            assertEquals("City", result.get("city"));
            assertNull(result.get("countryId"));
            assertNull(result.get("regionId"));
            
            verify(location, never()).setCountry(any());
            verify(location, never()).setRegion(any());
        }
    }

    /**
     * Test to ensure that the createLocation method handles null values correctly,
     * particularly for country and region.
     */
    @Test(expected = OBException.class)
    public void createLocationShouldThrowExceptionWhenCountryNotFound() {
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBMessageUtils> messageUtilsMock = mockStatic(OBMessageUtils.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn("test-id");
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            messageUtilsMock.when(() -> OBMessageUtils.messageBD("ETMETA_CountryNotFound"))
                .thenReturn("Country not found");
            
            when(obDal.get(Country.class, "country-id")).thenReturn(null);
            
            locationService.createLocation(locationData);
        }
    }

    /**
     * Test to ensure that the createLocation method throws an exception when the region is not found.
     */
    @Test(expected = OBException.class)
    public void createLocationShouldThrowExceptionWhenRegionNotFound() {
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBMessageUtils> messageUtilsMock = mockStatic(OBMessageUtils.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn("test-id");
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            messageUtilsMock.when(() -> OBMessageUtils.messageBD("ETMETA_RegionNotFound"))
                .thenReturn("Region not found");
            
            when(obDal.get(Region.class, "region-id")).thenReturn(null);
            
            locationService.createLocation(locationData);
        }
    }

    /**
     * Test to ensure that the createLocation method throws an exception when saving fails.
     */
    @Test(expected = OBException.class)
    public void createLocationShouldThrowExceptionWhenSaveFails() {
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBMessageUtils> messageUtilsMock = mockStatic(OBMessageUtils.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn("test-id");
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            messageUtilsMock.when(() -> OBMessageUtils.messageBD("ETMETA_LocationSaveError"))
                .thenReturn("Location save error");
            
            doThrow(new RuntimeException("Database error")).when(obDal).save(location);
            
            locationService.createLocation(locationData);
        }
    }

    /**
     * Test to ensure that the createLocation method does not set country or region
     * when they are not provided in the LocationData.
     */
    @Test
    public void createLocationWithEmptyStringsShouldNotSetReferences() {
        LocationData dataWithEmptyStrings = new LocationData("Address", "Address2", "12345", "City", "", "");
        String generatedId = "test-location-id";
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(dataWithEmptyStrings);
            
            assertNotNull(result);
            assertEquals("", result.get("countryId"));
            assertEquals("", result.get("regionId"));
            
            verify(location, never()).setCountry(any());
            verify(location, never()).setRegion(any());
            verify(obDal, never()).get(Country.class, "");
            verify(obDal, never()).get(Region.class, "");
        }
    }

    /**
     * Test to ensure that the createLocation method handles null values correctly,
     * particularly for address lines, postal code, and city.
     */
    @Test
    public void identifierBuilderShouldHandleNullValues() {
        LocationData dataWithNulls = new LocationData(null, null, null, "City", "country-id", null);
        String generatedId = "test-location-id";
        
        when(location.getAddressLine1()).thenReturn(null);
        when(location.getAddressLine2()).thenReturn(null);
        when(location.getPostalCode()).thenReturn(null);
        when(location.getCityName()).thenReturn("City");
        when(location.getRegion()).thenReturn(null);
        when(location.getCountry()).thenReturn(country);
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(dataWithNulls);
            
            String identifier = (String) result.get("_identifier");
            assertEquals("City - United States", identifier);
        }
    }

    /**
     * Test to ensure that the identifier builder handles empty strings correctly,
     * particularly for address lines, postal code, and city.
     */
    @Test
    public void identifierBuilderShouldHandleEmptyStrings() {
        LocationData dataWithEmptyStrings = new LocationData("", "", "", "City", "country-id", null);
        String generatedId = "test-location-id";
        
        when(location.getAddressLine1()).thenReturn("");
        when(location.getAddressLine2()).thenReturn("");
        when(location.getPostalCode()).thenReturn("");
        when(location.getCityName()).thenReturn("City");
        when(location.getRegion()).thenReturn(null);
        when(location.getCountry()).thenReturn(country);
        
        try (MockedStatic<SequenceIdData> sequenceIdMock = mockStatic(SequenceIdData.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            
            sequenceIdMock.when(SequenceIdData::getUUID).thenReturn(generatedId);
            providerMock.when(OBProvider::getInstance).thenReturn(obProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            
            Map<String, Object> result = locationService.createLocation(dataWithEmptyStrings);
            
            String identifier = (String) result.get("_identifier");
            assertEquals("City - United States", identifier);
        }
    }

    /**
     * Test to ensure that the identifier builder handles null values correctly,
     * particularly for country and region.
     */
    @Test
    public void locationDataToStringShouldFormatCorrectly() {
        String result = locationData.toString();
        
        assertTrue(result.contains("LocationData{"));
        assertTrue(result.contains("address1='123 Main Street'"));
        assertTrue(result.contains("address2='Apt 4B'"));
        assertTrue(result.contains("postal='12345'"));
        assertTrue(result.contains("city='Springfield'"));
        assertTrue(result.contains("countryId='country-id'"));
        assertTrue(result.contains("regionId='region-id'"));
    }

    /**
     * Test to ensure that the identifier builder handles null values correctly,
     * particularly for address lines, postal code, and city.
     */
    @Test
    public void locationDataDefaultConstructorShouldWork() {
        LocationData data = new LocationData();
        
        assertNull(data.getAddress1());
        assertNull(data.getAddress2());
        assertNull(data.getPostal());
        assertNull(data.getCity());
        assertNull(data.getCountryId());
        assertNull(data.getRegionId());
    }

    /**
     * Test to ensure that the LocationData setters and getters work correctly.
     */
    @Test
    public void locationDataSettersAndGettersShouldWork() {
        LocationData data = new LocationData();
        
        data.setAddress1("Test Address 1");
        data.setAddress2("Test Address 2");
        data.setPostal("54321");
        data.setCity("Test City");
        data.setCountryId("test-country");
        data.setRegionId("test-region");
        
        assertEquals("Test Address 1", data.getAddress1());
        assertEquals("Test Address 2", data.getAddress2());
        assertEquals("54321", data.getPostal());
        assertEquals("Test City", data.getCity());
        assertEquals("test-country", data.getCountryId());
        assertEquals("test-region", data.getRegionId());
    }
}
