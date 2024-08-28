package com.etendo.metadata;

import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.smf.securewebservices.SWSConfig;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.jfree.chart.HashUtilities;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.utils.FormatUtilities;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;

/**
 * 
 * @author androettop
 */
public class MetadataServlet extends HttpBaseServlet {
	private static final String APPLICATION_JSON = "application/json";
	private static final long serialVersionUID = 1L;
	static final long ONE_MINUTE_IN_MILLIS = 60000;
	private static final Logger log = LogManager.getLogger();

	public void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		JSONObject result = new JSONObject();
		AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
		OBContext.setAdminMode(true);
		response.setContentType(APPLICATION_JSON);
		Writer out = response.getWriter();
		out.write(result.toString());
		out.close();
	}
}