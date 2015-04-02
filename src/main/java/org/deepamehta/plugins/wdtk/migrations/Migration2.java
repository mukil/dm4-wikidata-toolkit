package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.ResultList;
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
            log.info("> Created WIKIDATA Workspace ..");
        }

        // 2) connect all types to this workspace (upgrade to # 4.5)
        List<Topic> topics = dms.getTopics("uri", new SimpleValue("org.deepamehta.wikidata.*"));
        for (Topic t : topics) {
            if (t.getTypeUri().equals("dm4.core.assoc_type")) {
                log.info("> Assigning assoc_type \"" + t.getUri() + "\" to \"Wikidata\" workspace");
                wsServices.assignTypeToWorkspace(dms.getAssociationType(t.getUri()), workspace.getId());
            } else if (t.getTypeUri().equals("dm4.core.topic_type")) {
                wsServices.assignTypeToWorkspace(dms.getTopicType(t.getUri()), workspace.getId());
                log.info("> Assigning topic_type \"" + t.getUri() + "\" to \"Wikidata\" workspace");
            }
        }
        
        // 3) connect all other topics of type X to workspace W
        ResultList<RelatedTopic> config_topics = dms.getTopics("org.deepamehta.wikidata.dumpfile_import", 0);
        for (RelatedTopic t : config_topics) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_iso = dms.getTopics("org.deepamehta.wikidata.dumpfile_language_iso", 0);
        for (RelatedTopic t : config_topics_iso) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_sec = dms.getTopics("org.deepamehta.wikidata.dumpfile_sec_parse", 0);
        for (RelatedTopic t : config_topics_sec) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_down = dms.getTopics("org.deepamehta.wikidata.dumpfile_download", 0);
        for (RelatedTopic t : config_topics_down) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_descr = dms.getTopics("org.deepamehta.wikidata.dumpfile_descriptions", 0);
        for (RelatedTopic t : config_topics_descr) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_web = dms.getTopics("org.deepamehta.wikidata.dumpfile_websites", 0);
        for (RelatedTopic t : config_topics_web) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_coords = dms.getTopics("org.deepamehta.wikidata.dumpfile_coordinates", 0);
        for (RelatedTopic t : config_topics_coords) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_cities = dms.getTopics("org.deepamehta.wikidata.dumpfile_cities", 0);
        for (RelatedTopic t : config_topics_cities) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_countries = dms.getTopics("org.deepamehta.wikidata.dumpfile_countries", 0);
        for (RelatedTopic t : config_topics_countries) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_institutions = dms.getTopics("org.deepamehta.wikidata.dumpfile_institutions", 0);
        for (RelatedTopic t : config_topics_institutions) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }
        ResultList<RelatedTopic> config_topics_persons = dms.getTopics("org.deepamehta.wikidata.dumpfile_persons", 0);
        for (RelatedTopic t : config_topics_persons) {
            wsServices.assignToWorkspace(t, workspace.getId());
            log.info("> Assigning topic instance of type \"" + t.getTypeUri() + "\" to \"Wikidata\" workspace");
        }

    }

}
