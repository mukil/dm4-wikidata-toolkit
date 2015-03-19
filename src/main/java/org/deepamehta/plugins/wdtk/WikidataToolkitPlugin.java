
package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.*;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.deepamehta.plugins.wdtk.service.WikidataToolkitService;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;



/**
 * A very basic plugin to use the wikidata toolkit as DeepaMehta 4 plugin.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.0.1-SNAPSHOT
 */

@Path("/wdtk")
@Consumes("application/json")
@Produces("application/json")
public class WikidataToolkitPlugin extends PluginActivator implements WikidataToolkitService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- Wikidata DeepaMehta URIs

    private final String WS_WIKIDATA_URI = "org.deepamehta.workspaces.wikidata";
    // private final String WD_ENTITY_CLAIM_EDGE = "org.deepamehta.wikidata.claim_edge";
    private final String WD_IMPORT_LANG = "org.deepamehta.wikidata.dumpfile_language_iso";
    private final String WD_IMPORT_COUNTRIES = "org.deepamehta.wikidata.dumpfile_countries";
    private final String WD_IMPORT_CITIES = "org.deepamehta.wikidata.dumpfile_cities";
    private final String WD_IMPORT_INSTITUTIONS = "org.deepamehta.wikidata.dumpfile_institutions";
    private final String WD_IMPORT_PERSONS = "org.deepamehta.wikidata.dumpfile_persons";
    private final String WD_IMPORT_SECONDS = "org.deepamehta.wikidata.dumpfile_sec_parse";
    private final String WD_IMPORT_DOWNLOAD = "org.deepamehta.wikidata.dumpfile_download";
    private final String WD_IMPORT_DESCRIPTIONS = "org.deepamehta.wikidata.dumpfile_descriptions";
    private final String WD_IMPORT_WEBSITES = "org.deepamehta.wikidata.dumpfile_websites";
    private final String WD_IMPORT_COORDINATES = "org.deepamehta.wikidata.dumpfile_coordinates";
    
    // private final String WIKIDATA_PROPERTY_ENTITY_URL_PREFIX = "Property:";
    
    // --- Instance Variables
    
    // private String dumpFilePath = ""; // ### make dumpfile location configurable
    // prevents corrupting the db because of parallel imports/transactions
    private boolean isCurrentlyImporting = false;
    
    @Inject
    private AccessControlService acService = null;
    
    @Inject
    private WorkspacesService wsServices;

    
    // --
    // --- Public REST API Endpoints
    // --
    
    @GET
    @Path("/import/entities/{importerId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Topic importEntitiesFromWikidataDump(@PathParam("importerId") long settingsTopicId) {
        Topic settings = dms.getTopic(settingsTopicId);
        importWikidataEntities(settings);
        return settings;
    }
    
    @GET
    @Path("/delete/topics/{importerId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    @Transactional
    public Topic deleteAllWikidataTopics(@PathParam("importerId") long settingsTopicId) {
        Topic importerSettings = dms.getTopic(settingsTopicId);
        ChildTopics settings = importerSettings.getChildTopics();
        boolean persons = settings.getBoolean(WD_IMPORT_PERSONS);
        boolean institutions = settings.getBoolean(WD_IMPORT_INSTITUTIONS);
        boolean cities = settings.getBoolean(WD_IMPORT_CITIES);
        boolean countries = settings.getBoolean(WD_IMPORT_COUNTRIES);
        // 
        if (persons) { // Delete all "Person" Topics
            for (RelatedTopic person : dms.getTopics("dm4.contacts.person", 0)){
                if (person.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                    dms.deleteTopic(person.getId());
                }
            }
        }
        if (institutions) { // Delete all "Institution"-Topics
            for (RelatedTopic institution : dms.getTopics("dm4.contacts.institution", 0)){
                if (institution.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                    dms.deleteTopic(institution.getId());
                }
            }            
        }
        if (cities) { // Delete all "City"-Topics
            for (RelatedTopic city : dms.getTopics("dm4.contacts.city", 0)){
                if (city.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                    dms.deleteTopic(city.getId());
                }
            }
        }
        if (countries) { // Delete all "Country"-Topics
            for (RelatedTopic country : dms.getTopics("dm4.contacts.country", 0)){
                if (country.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                    dms.deleteTopic(country.getId());
                }
            }            
        }
        return importerSettings;
    }

    // --
    // --- Methods to process and import topics based on a complete (daily) wikidatawiki (json) dump.
    // --
        
    private void importWikidataEntities(Topic importerSettings) {
        // ### read in settings stored as child topics
        ChildTopics childs = importerSettings.getChildTopics();
        int timeOut = Integer.parseInt(childs.getString(WD_IMPORT_SECONDS));
        String isoLanguageCode = childs.getString(WD_IMPORT_LANG);
        boolean persons = childs.getBoolean(WD_IMPORT_PERSONS);
        boolean institutions = childs.getBoolean(WD_IMPORT_INSTITUTIONS);
        boolean cities = childs.getBoolean(WD_IMPORT_CITIES);
        boolean countries = childs.getBoolean(WD_IMPORT_COUNTRIES);
        boolean noDownload = !childs.getBoolean(WD_IMPORT_DOWNLOAD);
        boolean descriptions = childs.getBoolean(WD_IMPORT_DESCRIPTIONS);
        boolean websites = childs.getBoolean(WD_IMPORT_WEBSITES);
        boolean geoCoordinates = childs.getBoolean(WD_IMPORT_COORDINATES);
        WikidataEntityProcessor wikidataEntityProcessor = new WikidataEntityProcessor(dms, this, timeOut, 
                persons, institutions, cities, countries, descriptions, websites, geoCoordinates, isoLanguageCode);
        WikidataToolkitPlugin.this.startProcesssingWikidataDumpfile(wikidataEntityProcessor, noDownload);
    }
    
    /**
     * Processes all entities in a Wikidata dump using the given entity
     * processor. By default, the most recent JSON dump will be used. In offline
     * mode, only the most recent previously downloaded file is considered.
     *
     * @param   entityProcessor the object to use for processing entities
     * @param   noDownload     if set to true only dumpfiles already stored on disk are considered for import
     * in this dump
     */
    private void startProcesssingWikidataDumpfile(WikidataEntityProcessor entityProcessor, boolean noDownload) {
        
        if (isCurrentlyImporting == true) {
            log.warning("One WDTK DumpFileProcessor is already running, please try again later.");
            return;
        }
        // Controller object for processing dumps:
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(noDownload);
        try {
            String path = findDumpDirectoryPath();
            log.info("Searching for a wikidata (json) dump on your hard disk under " + path);
            dumpProcessingController.setDownloadDirectory(path);
            /** List<MwDumpFile> availableJSONDumps = dumpProcessingController
                .getWmfDumpFileManager().findAllDumps(DumpContentType.JSON);
            if (availableJSONDumps == null) throw new RuntimeException("No JSON dumps "
                + "to process availabe. Place a <date>.json.gz dumpfile in your filerepo "
                + "under /dumpfiles/wikidatawiki/json-2014XXYY"); **/
        } catch (IOException ex) {
            log.warning("IOException: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
        dumpProcessingController.registerEntityDocumentProcessor(entityProcessor, null, false);
        try {
            isCurrentlyImporting = true;
            dumpProcessingController.processMostRecentJsonDump();
        } catch (Exception e) {
            isCurrentlyImporting = false;
            log.warning("Exception of type " + e.getClass() + " caught silent!");
            // The timer caused a time out or we could not find a dumpfile.
        }
        entityProcessor.stop();
    }
    
    // --
    // --- DeepaMehta 4 Plugin Related Private Methods
    // --

    @Override
    public void assignToWikidataWorkspace(Topic topic) {
        DeepaMehtaTransaction tx = dms.beginTx();
        if (topic == null) return;
        Topic wikidataWorkspace = dms.getTopic("uri", new SimpleValue(WS_WIKIDATA_URI));
        if (!associationExists("dm4.core.aggregation", topic, wikidataWorkspace)) {
            dms.createAssociation(new AssociationModel("dm4.core.aggregation",
                new TopicRoleModel(topic.getId(), "dm4.core.parent"),
                new TopicRoleModel(wikidataWorkspace.getId(), "dm4.core.child")
            ));
            tx.success();
            tx.finish();
        }
    }
    
    private boolean associationExists(String edge_type, Topic item, Topic user) {
        List<Association> results = dms.getAssociations(item.getId(), user.getId(), edge_type);
        return (results != null) ? ((results.size() > 0) ? true : false) : false;
    }
    
    private String findDumpDirectoryPath() {
        // ### use Sysetm.getenv() for the best OS independent solution
        // see http://docs.oracle.com/javase/6/docs/api/java/lang/System.html
        String filerepo = System.getProperty("dm4.filerepo.path");
        if (filerepo != null && !filerepo.isEmpty()) {
            return filerepo + "/";
        }
        String userhome = System.getProperty("user.home");
        if (userhome != null) {
            return userhome + "/";
        }
        return "";
    }
    
}
