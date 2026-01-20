package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.data.Toolbar;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import com.etendoerp.metadata.data.ToolbarWindow;
import org.openbravo.model.ad.ui.Window;

import java.util.List;

/**
 * Builder class for generating JSON representations of Toolbars.
 * This class extends the Builder class and provides methods to convert Toolbar objects
 * into JSON format, as well as to generate a JSON representation of the menu structure.
 */
public class ToolbarBuilder extends Builder {
    private JSONArray toolbarWindowsToJSON(Toolbar toolbar) throws JSONException {
        JSONArray windowsArray = new JSONArray();

        for (ToolbarWindow tw : toolbar.getEtmetaToolbarWindowList()) {
            if (!Boolean.TRUE.equals(tw.isActive())) {
                continue;
            }

            Window window = tw.getWindow();

            JSONObject windowJson = new JSONObject()
                    .put("id", window.getId())
                    .put("name", window.getName());

            windowsArray.put(windowJson);
        }

        return windowsArray;
    }



    /**
   * Constructor that initializes the MenuManager.
   * This ensures that the menu is loaded and ready for processing.
   */
  private JSONObject toolbarToJSON(Toolbar t) throws JSONException {
    return new JSONObject().put("id", t.getId())
        .put("client", t.getClient().getId())
        .put("organization", t.getOrganization().getId())
        .put("active", t.isActive())
        .put("creationDate", t.getCreationDate().toString())
        .put("createdBy", t.getCreatedBy().getId())
        .put("updated", t.getUpdated().toString())
        .put("updatedBy", t.getUpdatedBy().getId())
        .put("identifier", t.getIdentifier())
        .put("entityName", t.getEntityName())
        .put("name", t.getName())
        .put("icon", t.getIcon())
        .put("seqno", t.getSeqno())
        .put("description", t.getDescription())
        .put("etmetaActionHandler", t.getEtmetaActionHandler())
        .put("nameKey", t.getNameKey())
        .put("action", t.getAction())
        .put("buttonType", t.getButtontype())
        .put("section", t.getSection())
        .put("module", t.getModule() != null ? t.getModule().getId() : null)
        .put("windows", toolbarWindowsToJSON(t));
  }

  /**
   * Converts the current ToolbarBuilder instance into a JSON object.
   * This method retrieves the menu structure and converts it into a JSON representation.
   *
   * @return JSONObject representing the menu structure.
   * @throws JSONException if there is an error during JSON creation.
   */
  @Override
  public JSONObject toJSON() throws JSONException {
    JSONObject output = new JSONObject();
    try {
      List<Toolbar> list = OBDal.getInstance().createCriteria(Toolbar.class).list();
      JSONArray data = new JSONArray();
      for (Toolbar t : list) {
        data.put(toolbarToJSON(t));
      }
      JSONObject response = new JSONObject().put("data", data)
          .put("startRow", 0)
          .put("endRow", data.length() - 1)
          .put("totalRows", data.length())
          .put("status", 0);
      output.put("response", response);
    } catch (JSONException e) {
      logger.error(e.getMessage(), e);
    }
    return output;
  }
}
