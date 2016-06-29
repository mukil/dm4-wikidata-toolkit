package org.deepamehta.plugins.wdtk.viewmodel;

import de.deepamehta.core.Association;
import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.deepamehta.plugins.wdtk.WikidataEntityMap;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A data transfer object for a wikidata item topic with a default label, ISO Three Letter Code, an OSM Relation ID
 * and a list of child items claiming this item as their "Country". The countries childs are serialized as a list of
 * elements of type <code>WikidataItem</code>.
 *
 * <a href="https://github.com/mukil/dm4-wikidata-toolkit">Source Code Repository</a>
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @version 0.3-SNAPSHOT
 */
public class CountryItem implements JSONEnabled {
    
    public Topic item;
    public Topic code;
    
    static final Logger log = Logger.getLogger(CountryItem.class.getName());
    static final String UNKNOWN_ID = "UNKNOWN";

    /** public HashMap<String, String> label; // key: language code, value: label-value
    public HashMap<String, String> description; // key: language code, value: description-value
    public HashMap<String, String> alias; // key: language code, value: alias-value **/

    public CountryItem (Topic topic) {
        this.code = topic;
        this.item = topic.getRelatedTopic("org.deepamehta.wikidata.iso_country_code", "dm4.core.child",
            "dm4.core.parent", "org.deepamehta.wikidata.item");
    }

    public String getIsoCountryCode() {
        if (code == null) return UNKNOWN_ID;
        return code.getSimpleValue().toString();
    }
    
    public String getOSMRelationId() {
        if (item == null) return UNKNOWN_ID;
        List<RelatedTopic> osmRelationIdValues = item.getRelatedTopics("org.deepamehta.wikidata.osm_relation_id", "dm4.core.parent", 
            "dm4.core.child", "org.deepamehta.wikidata.text");
        return (osmRelationIdValues.size() >= 1) ? osmRelationIdValues.get(0).getSimpleValue().toString() : UNKNOWN_ID;
    }
    
    public boolean isCountry() {
        return (this.item != null);
    }
    
    public ArrayList<RegionItem> listSubregions() {
        ArrayList<RegionItem> regions = new ArrayList<RegionItem>();
        List<Association> claims = this.item.getAssociations();
        for (Association claim : claims) {
            if (claim.getTypeUri().equals("org.deepamehta.wikidata.claim_edge")) {
                Topic propertyTopic = claim.getChildTopics().getTopicOrNull("org.deepamehta.wikidata.property");
                if (propertyTopic != null) {
                    if (propertyTopic.getUri().contains(WikidataEntityMap.CONTAINS_ADMIN_T_ENTITY)) {
                        /** log.info("> wikidataClaim available about: \"" + claim.getPlayer1().getSimpleValue().toString() 
                                + "\" being a territory contained in " + claim.getPlayer2().getSimpleValue().toString() + " or vice-versa"); **/
                        if (claim.getPlayer1().getId() == this.item.getId()) {
                            regions.add(new RegionItem(claim.getPlayer2()));
                        } else {
                            regions.add(new RegionItem(claim.getPlayer1()));
                        }
                    } else if (propertyTopic.getUri().contains(WikidataEntityMap.IS_LOCATED_IN_ADMIN_T)) {
                        log.info("> wikidataClaim available about: " + claim.getPlayer1().getSimpleValue().toString() 
                                + " being a territory located in " + claim.getPlayer2().getSimpleValue().toString() + " or vice-versa");
                    }
                }
            }
        }
        if (claims.size() == 0) log.info("> no claims found for " + this.item.getId() + " as parent");
        return regions;
    }
    
    public ArrayList<WikidataItem> getItemsInCountry() {
        ArrayList<WikidataItem> cities = new ArrayList<WikidataItem>();
        List<Association> claims = this.item.getAssociations();
        for (Association claim : claims) {
            if (claim.getTypeUri().equals("org.deepamehta.wikidata.claim_edge")) {
                Topic propertyTopic = claim.getChildTopics().getTopicOrNull("org.deepamehta.wikidata.property");
                if (propertyTopic != null) {
                    if (propertyTopic.getUri().contains(WikidataEntityMap.IS_COUNTRY)) { // IS_CAPITAL
                        if (!claim.getPlayer1().getUri().equals(this.item.getUri())) {
                            cities.add(new WikidataItem(claim.getPlayer1()));
                        } else if (!claim.getPlayer2().getUri().equals(this.item.getUri())) {
                            cities.add(new WikidataItem(claim.getPlayer2()));
                        }
                    }
                }
            }
        }
        if (claims.size() == 0) log.info("> no claims found for " + this.item.getId() + " as parent");
        return cities;
    }

    public JSONObject toJSON() {
        try {
            // assemble regions
            JSONArray regions = new JSONArray();
            ArrayList<RegionItem> subregions = listSubregions();
            for (RegionItem region : subregions) {
                regions.put(region.toJSON());
            }
            // assemble cities
            JSONArray items = new JSONArray();
            ArrayList<WikidataItem> itemsInCountry = getItemsInCountry();
            for (WikidataItem wikidataItem : itemsInCountry) {
                if (wikidataItem.hasGeoCoordinateObject()) items.put(wikidataItem.toJSON());
            }
            return new JSONObject()
                .put("default_name", item.getSimpleValue())
                .put("topic_id", item.getId())
                .put("uri", item.getUri()) // .replace(WikidataEntityMap.WD_ENTITY_BASE_URI, "")
                .put("iso_code", getIsoCountryCode())
                .put("osm_relation_id", getOSMRelationId())
                // .put("regions", regions)
                .put("items", items);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
