package org.deepamehta.plugins.wdtk.viewmodel;

import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.service.ResultList;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.deepamehta.plugins.wdtk.WikidataEntityMap;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author malte
 */
public class RegionItem implements JSONEnabled {

    DeepaMehtaObject item;
    long regionId;
    String uri;
    static final String UNKNOWN_ID = "UNKNOWN";

    public RegionItem (DeepaMehtaObject item) {
        this.regionId = item.getId();
        this.uri = item.getUri();
        this.item = item;
    }
    
    public long getId() {
        return this.regionId;
    }
    
    public String getOSMRelationId() {
        if (item == null) return UNKNOWN_ID;
        ResultList<RelatedTopic> osmRelationIdValues = item.getRelatedTopics("org.deepamehta.wikidata.osm_relation_id", "dm4.core.parent", 
            "dm4.core.child", "org.deepamehta.wikidata.text", 1);
        return (osmRelationIdValues.getSize() >= 1) ? osmRelationIdValues.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }
    
    public String getNUTSCode() {
        if (item == null) return UNKNOWN_ID;
        ResultList<RelatedTopic> nutsCodeValue = item.getRelatedTopics("org.deepamehta.wikidata.nuts_code", "dm4.core.parent", 
            "dm4.core.child", "org.deepamehta.wikidata.text", 1);
        return (nutsCodeValue.getSize() >= 1) ? nutsCodeValue.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }
    
    public String getUri() {
        return this.uri;
    }

    public JSONObject toJSON() {
        try {
            return new JSONObject()
                .put("default_name", item.getSimpleValue().toString())
                .put("topic_id", getId())
                .put("uri", getUri().replace(WikidataEntityMap.WD_ENTITY_BASE_URI, ""))
                .put("osm_relation_id", getOSMRelationId())
                .put("nuts_code", getNUTSCode());
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

}
