package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.AssociationType;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.service.Migration;
import java.util.logging.Level;

import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;


/*
 * Adding a "fulltext key" index to the wikidata text value topics.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 */

public class Migration6 extends Migration {

    private Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void run() {
        // 1) create \"Wikidata Text\"-Type (if non existent)
        Topic textType = dm4.getTopicByUri("org.deepamehta.wikidata.text");
        if (textType == null) {
            try {
                JSONObject textTypeDef = new JSONObject("{" +
                    "\"value\": \"Wikidata Text\"," +
                    "\"uri\": \"org.deepamehta.wikidata.text\"," +
                    "\"data_type_uri\": \"dm4.core.text\"," +
                    "\"index_mode_uris\": [\"dm4.core.fulltext\", \"dm4.core.key\"]," +
                    "\"view_config_topics\": [" +
                    "   {" +
                    "       \"type_uri\": \"dm4.webclient.view_config\"," +
                    "       \"childs\": {" +
                    "           \"dm4.webclient.show_in_create_menu\": false," +
                    "           \"dm4.webclient.searchable_as_unit\": false," +
                    "           \"dm4.webclient.input_field_rows\": 1," +
                    "           \"dm4.webclient.locked\": true" +
                    "   }" +
                    "}]}");
                textType = dm4.createTopicType(mf.newTopicTypeModel(textTypeDef));
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }
        }
        // 2) Add fulltext key index
        dm4.getTopicType("org.deepamehta.wikidata.text").addIndexMode(IndexMode.FULLTEXT_KEY);
        // 3) Add claim edge (if non existent)
        AssociationType claimEdge = dm4.getAssociationType("org.deepamehta.wikidata.claim_edge");
        if (claimEdge == null) {
            try {
                JSONObject claimEdgeTypeDef = new JSONObject("{" +
                "\"value\": \"Wikidata Claim\"," +
                "\"uri\": \"org.deepamehta.wikidata.claim_edge\"," +
                "\"data_type_uri\": \"dm4.core.composite\"," +
                "\"assoc_defs\": [" +
                    "{" +
                    "\"child_type_uri\": \"org.deepamehta.wikidata.property\"," +
                    "\"child_cardinality_uri\": \"dm4.core.one\"," +
                    "\"parent_cardinality_uri\": \"dm4.core.one\"," +
                    "\"assoc_type_uri\": \"dm4.core.aggregation_def\"" +
                    "}" +
                    "]," +
                    "\"view_config_topics\": [" +
                    "{" +
                    "\"type_uri\": \"dm4.webclient.view_config\"," +
                    "\"childs\": {" +
                    "\"dm4.webclient.color\": \"#006699\"" +
                    "}" +
                    "}" +
                    "]" +
                "}");
                dm4.createAssociationType(mf.newAssociationTypeModel(claimEdgeTypeDef));
            } catch (JSONException jex) {
                throw new RuntimeException(jex);
            }
        } else {
            log.info("Adding new Property Type as Child AssocDef to Claim Edge Type");
            try {
                JSONObject assocDef = new JSONObject("{" +
                    "\"child_type_uri\": \"org.deepamehta.wikidata.property\"," +
                    "\"parent_type_uri\": \"org.deepamehta.wikidata.claim_edge\"," +
                    "\"child_cardinality_uri\": \"dm4.core.one\"," +
                    "\"parent_cardinality_uri\": \"dm4.core.one\"," +
                    "\"assoc_type_uri\": \"dm4.core.aggregation_def\"" +
                    "}");
                claimEdge.addAssocDef(mf.newAssociationDefinitionModel(assocDef));
            } catch (JSONException ex) {
                Logger.getLogger(Migration6.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
