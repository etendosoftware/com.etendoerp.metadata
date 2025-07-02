package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.LabelsBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;

public class LabelsService extends MetadataService {
    public LabelsService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            write(new LabelsBuilder().toJSON());
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
