package org.deepamehta.plugins.wdtk.viewmodel;

import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import de.deepamehta.core.RelatedTopic;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.deepamehta.plugins.wdtk.WikidataEntityMap;

import java.util.logging.Logger;

/**
 * A slim data transfer object for a wikidata item topic with just a default label and WGS 84 coordinates.
 *
 * <a href="https://github.com/mukil/dm4-wikidata-toolkit">Source Code Repository</a>
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @version 0.3-SNAPSHOT
 */
public class WikidataItem implements JSONEnabled {
    
    DeepaMehtaObject item;
    Topic geoCoordinate;
    
    static final Logger log = Logger.getLogger(WikidataItem.class.getName());

    public WikidataItem (DeepaMehtaObject topic) {
        this.item = topic;
    }

    public WikidataItem (Topic textTopic, DeepaMehtaService dms) {
        RelatedTopic relatedTopic = textTopic.getRelatedTopic(null, "dm4.core.child", "dm4.core.parent",
                "org.deepamehta.wikidata.item");
        this.item = dms.getTopic(relatedTopic.getId());
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
                .put("uri", item.getUri()) // .replace(WikidataEntityMap.WD_ENTITY_BASE_URI, "")
                .put("topic_id", item.getId())
                .put("coordinate", coordinates);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

}
