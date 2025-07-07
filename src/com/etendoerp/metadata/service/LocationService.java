package com.etendoerp.metadata.service;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;
import org.openbravo.model.common.geography.Region;

/**
 * Service to replicate original Location class logic
 * to create UUID address
 *
 * @author Santiago Alaniz
 */
@ApplicationScoped
public class LocationService {

    private static final Logger logger = LogManager.getLogger(LocationService.class);

    /**
     * Create a new address
     *
     * @param locationData Data from the frontend
     * @return Map with the generated ID and formatted identifier
     * @throws OBException if there's an error creating the location
     */
    public Map<String, Object> createLocation(LocationData locationData) {
        try {
            String locationId = SequenceIdData.getUUID();

            Location location = createLocationEntity(locationId);
            setLocationReferences(location, locationData);
            setLocationFields(location, locationData);

            saveLocation(location);

            return buildResult(locationId, location, locationData);

        } catch (Exception e) {
            logger.error("Error creating location with data: {}", locationData, e);
            throw new OBException("Error creating location: " + e.getMessage(), e);
        }
    }

    /**
     * Create and initialize Location entity
     */
    private Location createLocationEntity(String locationId) {
        Location location = OBProvider.getInstance().get(Location.class);
        location.setId(locationId);
        location.setNewOBObject(true);

        OBContext context = OBContext.getOBContext();
        location.setClient(context.getCurrentClient());
        location.setOrganization(context.getCurrentOrganization());
        location.setActive(true);
        location.setCreatedBy(context.getUser());
        location.setUpdatedBy(context.getUser());

        return location;
    }

    /**
     * Set foreign key references for Country and Region
     */
    private void setLocationReferences(Location location, LocationData locationData) {
        if (locationData.getCountryId() != null) {
            Country country = OBDal.getInstance().get(Country.class, locationData.getCountryId());
            if (country == null) {
                throw new OBException("Country not found with ID: " + locationData.getCountryId());
            }
            location.setCountry(country);
        }

        if (locationData.getRegionId() != null) {
            Region region = OBDal.getInstance().get(Region.class, locationData.getRegionId());
            if (region == null) {
                throw new OBException("Region not found with ID: " + locationData.getRegionId());
            }
            location.setRegion(region);
        }
    }

    /**
     * Set location address fields
     */
    private void setLocationFields(Location location, LocationData locationData) {
        location.setAddressLine1(locationData.getAddress1());
        location.setAddressLine2(locationData.getAddress2());
        location.setPostalCode(locationData.getPostal());
        location.setCityName(locationData.getCity());
    }

    /**
     * Save location to database
     */
    private void saveLocation(Location location) {
        try {
            OBDal dal = OBDal.getInstance();
            dal.save(location);
            dal.flush();
        } catch (Exception e) {
            logger.error("Error saving location to database", e);
            throw new OBException("Error persisting location: " + e.getMessage(), e);
        }
    }

    /**
     * Build response result map
     */
    private Map<String, Object> buildResult(String locationId, Location location, LocationData locationData) {
        String identifier = buildLocationIdentifier(location);

        Map<String, Object> result = new HashMap<>();
        result.put("id", locationId);
        result.put("_identifier", identifier);
        result.put("address1", locationData.getAddress1());
        result.put("address2", locationData.getAddress2());
        result.put("postal", locationData.getPostal());
        result.put("city", locationData.getCity());
        result.put("countryId", locationData.getCountryId());
        result.put("regionId", locationData.getRegionId());

        return result;
    }

    /**
     * Build an identifier for the address with expected format
     * Replicates the logic from LocationSearchData.locationAddress
     */
    private String buildLocationIdentifier(Location location) {
        StringBuilder identifier = new StringBuilder();

        appendIfNotEmpty(identifier, location.getAddressLine1());
        appendIfNotEmpty(identifier, location.getAddressLine2());
        appendIfNotEmpty(identifier, location.getPostalCode());
        appendIfNotEmpty(identifier, location.getCityName());

        if (location.getRegion() != null) {
            appendIfNotEmpty(identifier, location.getRegion().getName());
        }

        if (location.getCountry() != null) {
            appendIfNotEmpty(identifier, location.getCountry().getName());
        }

        return identifier.toString();
    }

    /**
     * Append value to identifier if not null or empty
     */
    private void appendIfNotEmpty(StringBuilder identifier, String value) {
        if (value != null && !value.trim().isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(" - ");
            }
            identifier.append(value);
        }
    }

    /**
     * Internal class to receive data from the frontend
     */
    public static class LocationData {
        private String address1;
        private String address2;
        private String postal;
        private String city;
        private String countryId;
        private String regionId;

        public LocationData() {}

        public String getAddress1() { return address1; }
        public void setAddress1(String address1) { this.address1 = address1; }

        public String getAddress2() { return address2; }
        public void setAddress2(String address2) { this.address2 = address2; }

        public String getPostal() { return postal; }
        public void setPostal(String postal) { this.postal = postal; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getCountryId() { return countryId; }
        public void setCountryId(String countryId) { this.countryId = countryId; }

        public String getRegionId() { return regionId; }
        public void setRegionId(String regionId) { this.regionId = regionId; }

        @Override
        public String toString() {
            return String.format("LocationData{address1='%s', address2='%s', postal='%s', city='%s', countryId='%s', regionId='%s'}",
                    address1, address2, postal, city, countryId, regionId);
        }
    }
}