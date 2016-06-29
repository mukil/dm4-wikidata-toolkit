package org.deepamehta.plugins.wdtk.viewmodel;

import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.RelatedTopic;
import java.util.List;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A data transfer object for a wikidata item topic with a default label and additionally carrying
 * * a NUTS Code,<br/>
 * * an OSM Relation ID and,<br/>
 * * an ISO Three Letter Code<br/>
 * if *known*. If values are unknown, the values are initialized with the String value "undefined".
 *
 * <a href="https://github.com/mukil/dm4-wikidata-toolkit">Source Code Repository</a>
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @version 0.3-SNAPSHOT
 */
public class RegionItem implements JSONEnabled {

    DeepaMehtaObject item;
    long regionId;
    String uri;
    static final String UNKNOWN_ID = "undefined";

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
        List<RelatedTopic> osmRelationIdValues = item.getRelatedTopics("org.deepamehta.wikidata.osm_relation_id", "dm4.core.parent",
            "dm4.core.child", "org.deepamehta.wikidata.text");
        return (osmRelationIdValues.size() >= 1) ? osmRelationIdValues.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }
    
    public String getNUTSCode() {
        if (item == null) return UNKNOWN_ID;
        List<RelatedTopic> nutsCodeValue = item.getRelatedTopics("org.deepamehta.wikidata.nuts_code", "dm4.core.parent",
            "dm4.core.child", "org.deepamehta.wikidata.text");
        return (nutsCodeValue.size() >= 1) ? nutsCodeValue.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }

    public String getISOCode() {
        if (item == null) return UNKNOWN_ID;
        List<RelatedTopic> nutsCodeValue = item.getRelatedTopics("org.deepamehta.wikidata.iso_country_code",
                "dm4.core.parent", "dm4.core.child", "org.deepamehta.wikidata.text");
        return (nutsCodeValue.size() >= 1) ? nutsCodeValue.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }
    
    public String getUri() {
        return this.uri;
    }

    public JSONObject toJSON() {
        try {
            return new JSONObject()
                .put("default_name", item.getSimpleValue().toString())
                .put("topic_id", getId())
                .put("uri", getUri()) // .replace(WikidataEntityMap.WD_ENTITY_BASE_URI, "")
                .put("iso_code", getISOCode())
                .put("nuts_code", getNUTSCode())
                .put("osm_relation_id", getOSMRelationId());
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

}
