package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.LOCATION_PATH;
import static com.etendoerp.metadata.utils.Utils.getHttpStatusFor;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonUtils;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Service to handle location operations
 *
 * @author Santiago Alaniz
 */
public class LocationMetadataService extends MetadataService {

    private LocationService locationService;

    /**
     * Constructor to initialize the LocationMetadataService with HTTP request and response objects.
     *
     * @param req the HTTP servlet request
     * @param res the HTTP servlet response
     */
    public LocationMetadataService(HttpServletRequest req, HttpServletResponse res) {
        super(req, res);
        this.locationService = new LocationService();
    }

    @Override
    public void process() throws IOException {
        try {
            String pathInfo = getRequest().getPathInfo();

            if (pathInfo.startsWith(LOCATION_PATH + "create")) {
                handleCreateLocation();
            } else {
                throw new NotFoundException("Location endpoint not found");
            }

        } catch (Exception e) {
            logger.error("Error processing location request", e);
            handleError(e);
        }
    }

    /**
     * Handle creation of an address
     */
    private void handleCreateLocation() throws IOException {
        try {
            OBContext.setAdminMode(true);

            String requestBody = readRequestBody(getRequest());
            JSONObject jsonRequest = new JSONObject(requestBody);

            LocationService.LocationData locationData = buildLocationData(jsonRequest);
            validateLocationData(locationData);

            Map<String, Object> result = locationService.createLocation(locationData);

            JSONObject responseJson = new JSONObject();
            responseJson.put("success", true);
            responseJson.put("data", new JSONObject(result));

            write(responseJson);

        } catch (JSONException e) {
            logger.error("Invalid JSON format", e);
            throw new UnprocessableContentException("Invalid JSON format: " + e.getMessage());
        } catch (OBException e) {
            logger.error("Business error creating location", e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating location", e);
            throw new OBException("Unexpected error: " + e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Build LocationData from JSON request
     */
    private LocationService.LocationData buildLocationData(JSONObject jsonRequest) {
        LocationService.LocationData locationData = new LocationService.LocationData();
        locationData.setAddress1(jsonRequest.optString("address1", ""));
        locationData.setAddress2(jsonRequest.optString("address2", ""));
        locationData.setPostal(jsonRequest.optString("postal", ""));
        locationData.setCity(jsonRequest.optString("city", ""));
        locationData.setCountryId(jsonRequest.optString("countryId", null));
        locationData.setRegionId(jsonRequest.optString("regionId", null));
        return locationData;
    }

    /**
     * Validate location data according to business rules.
     * Aggregates all validation errors and returns them together for better user experience.
     *
     * @param locationData the location data to validate
     * @throws UnprocessableContentException if validation fails with all error messages
     */
    private void validateLocationData(LocationService.LocationData locationData) {
        StringBuilder errors = new StringBuilder();

        if (StringUtils.isBlank(locationData.getAddress1())) {
            errors.append("Address line 1 is required. ");
        }

        if (StringUtils.isBlank(locationData.getCity())) {
            errors.append("City is required. ");
        }

        if (StringUtils.isBlank(locationData.getCountryId())) {
            errors.append("Country is required. ");
        }

        if (errors.length() > 0) {
            throw new UnprocessableContentException(errors.toString().trim());
        }
    }

    /**
     * Handle error response using module conventions
     */
    private void handleError(Exception e) throws IOException {
        try {
            HttpServletResponse response = getResponse();
            response.setStatus(getHttpStatusFor(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
        } catch (Exception ex) {
            logger.error("Error writing error response", ex);
            throw new IOException("Error processing request", ex);
        }
    }

    /**
     * Read HTTP request body and return it as a string.
     *
     * @param request the HTTP servlet request
     * @return the request body as a string
     * @throws IOException if there's an error reading the request body
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        String line;
        try (java.io.BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
        }
        return buffer.toString();
    }
}
