package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.AssociationType;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.accesscontrol.SharingMode;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.List;
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

    @Inject
    private WorkspacesService wsServices;

    @Inject
    private AccessControlService acServices;

    @Override
    public void run() {

        // 1) create \"Wikidata\"-Workspace (if non existent)
        Topic workspace = dms.getTopic("uri", new SimpleValue(WS_WIKIDATA_URI));
        if (workspace == null) {
            workspace = wsServices.createWorkspace("Wikidata", WS_WIKIDATA_URI, SharingMode.PUBLIC);
            acServices.setWorkspaceOwner(workspace, "admin");
            log.info("1) Created WIKIDATA Workspace ..");
        }

        // 2) connect all types to this workspace (upgrade to # 4.5)
        List<Topic> topics = dms.getTopics("uri", new SimpleValue("org.deepamehta.wikidata.*"));
        for (Topic t : topics) {
            if (t.getTypeUri().equals("dm4.core.assoc_type")) {
                wsServices.assignTypeToWorkspace(dms.getAssociationType(t.getUri()), workspace.getId());
            } else if (t.getTypeUri().equals("dm4.core.topic_type")) {
                wsServices.assignTypeToWorkspace(dms.getTopicType(t.getUri()), workspace.getId());
            } else if (t.getUri().equals("org.deepamehta.wikidata.importer_default_config")) {
                wsServices.assignToWorkspace(t, workspace.getId());
            }
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
