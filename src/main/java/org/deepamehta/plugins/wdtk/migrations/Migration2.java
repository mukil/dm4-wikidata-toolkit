package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.Migration;
import java.util.logging.Logger;


/*
 * A basic plugin for turning wikidata entities into topics and associations.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 */

public class Migration2 extends Migration {

    private Logger log = Logger.getLogger(getClass().getName());

    private final String WS_WIKIDATA_URI = "org.deepamehta.workspaces.wikidata";

    @Override
    public void run() {

        // 1) create \"Wikidata\"-Workspace (if non existent)
        Topic existingWorkspace = dms.getTopic("uri", new SimpleValue(WS_WIKIDATA_URI));
        if (existingWorkspace == null) {
            TopicModel workspace = new TopicModel(WS_WIKIDATA_URI, "dm4.workspaces.workspace");
            Topic ws = dms.createTopic(workspace);
            ws.setSimpleValue("Wikidata");
            log.info("1) Created WIKIDATA Workspace ..");
        }

    }

    // === Workspace ===

    private void assignWorkspace(Topic topic) {
        Topic defaultWorkspace = dms.getTopic("uri", new SimpleValue(WS_WIKIDATA_URI));
        dms.createAssociation(new AssociationModel("dm4.core.aggregation",
            new TopicRoleModel(topic.getId(), "dm4.core.parent"),
            new TopicRoleModel(defaultWorkspace.getId(), "dm4.core.child")
        ));
    }

}
