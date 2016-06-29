package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.accesscontrol.SharingMode;
import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.workspaces.WorkspacesService;
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
        Topic workspace = dm4.getTopicByUri(WS_WIKIDATA_URI);
        if (workspace == null) {
            workspace = wsServices.createWorkspace("Wikidata", WS_WIKIDATA_URI, SharingMode.PUBLIC);
            acServices.setWorkspaceOwner(workspace, AccessControlService.ADMIN_USERNAME);
            log.info("> Created WIKIDATA Workspace ..");
        }

        // 2) connect all types to this workspace (upgrade to # 4.5)
        List<Topic> topics = dm4.getTopicsByValue("uri", new SimpleValue("org.deepamehta.wikidata.*"));
        for (Topic t : topics) {
            if (t.getTypeUri().equals("dm4.core.assoc_type")) {
                log.info("> Assigning assoc_type \"" + t.getUri() + "\" to \"Wikidata\" workspace");
                wsServices.assignTypeToWorkspace(dm4.getAssociationType(t.getUri()), workspace.getId());
            } else if (t.getTypeUri().equals("dm4.core.topic_type")) {
                wsServices.assignTypeToWorkspace(dm4.getTopicType(t.getUri()), workspace.getId());
                log.info("> Assigning topic_type \"" + t.getUri() + "\" to \"Wikidata\" workspace");
            }
        }
        
        // 3) connect all other topics of type X to workspace W
        List<Topic> config_topics = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_import");
        for (Topic t : config_topics) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_iso = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_language_iso");
        for (Topic t : config_topics_iso) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_sec = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_sec_parse");
        for (Topic t : config_topics_sec) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_down = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_download");
        for (Topic t : config_topics_down) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_descr = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_descriptions");
        for (Topic t : config_topics_descr) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_web = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_websites");
        for (Topic t : config_topics_web) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_coords = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_coordinates");
        for (Topic t : config_topics_coords) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_cities = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_cities");
        for (Topic t : config_topics_cities) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_countries = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_countries");
        for (Topic t : config_topics_countries) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_institutions = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_institutions");
        for (Topic t : config_topics_institutions) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        List<Topic> config_topics_persons = dm4.getTopicsByType("org.deepamehta.wikidata.dumpfile_persons");
        for (Topic t : config_topics_persons) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }

    }

}
