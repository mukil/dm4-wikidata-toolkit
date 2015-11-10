package org.deepamehta.plugins.wdtk.viewmodel;

import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.deepamehta.plugins.wdtk.WikidataEntityMap;

import java.util.logging.Logger;

/**
 *
 * @author malte
 */
public class WikidataItem implements JSONEnabled {
    
    DeepaMehtaObject item;
    Topic geoCoordinate;
    
    static final Logger log = Logger.getLogger(CountryItem.class.getName());
    
    public WikidataItem (DeepaMehtaObject topic) {
        this.item = topic;
    }
    
    public boolean hasGeoCoordinateObject () {
        item.loadChildTopics("dm4.geomaps.geo_coordinate");
        return item.getChildTopics().has("dm4.geomaps.geo_coordinate");
    }
    
    public JSONObject getGeoCoordinateObject() throws JSONException {
        // 
        item.loadChildTopics();
        if (item.getChildTopics().has("dm4.geomaps.geo_coordinate")) {
            // 
            double lat = 0;
            double lng = 0;
            geoCoordinate = item.getChildTopics().getTopic("dm4.geomaps.geo_coordinate");
            geoCoordinate.loadChildTopics();
            lat = geoCoordinate.getChildTopics().getDouble("dm4.geomaps.latitude");
            lng = geoCoordinate.getChildTopics().getDouble("dm4.geomaps.longitude");
            return new JSONObject().put("latitude", lat).put("longitude", lng);
        } else {
            log.fine("WARNING: Item "+item.getSimpleValue()+" has NO geo-coordinate topic: " + item.getId() + " uri: " + item.getUri());
            return null;
        }
    }

    public JSONObject toJSON() {
        try {
            JSONObject coordinates = getGeoCoordinateObject();
            return new JSONObject()
                .put("default_name", item.getSimpleValue().toString())
                .put("uri", item.getUri().replace(WikidataEntityMap.WD_ENTITY_BASE_URI, ""))
                .put("topic_id", item.getId())
                .put("coordinate", coordinates);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

}
