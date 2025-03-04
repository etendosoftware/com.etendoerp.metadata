package com.etendoerp.metadata.requests;

import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WindowRequest extends Request {
    public WindowRequest(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    public void process() {
        String id = request.getPathInfo().substring(8);
        JSONObject data = new WindowBuilder(id).toJSON();

        try {
            response.getWriter().write(data.toString());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }
}
