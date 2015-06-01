
package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.AssociationType;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import org.deepamehta.plugins.wdtk.service.WikidataToolkitService;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;



/**
 * A very basic plugin to use the wikidata toolkit as DeepaMehta 4 plugin.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.2
 */

@Path("/wdtk")
@Consumes("application/json")
@Produces("application/json")
public class WikidataToolkitPlugin extends PluginActivator implements WikidataToolkitService {

    private Logger log = Logger.getLogger(getClass().getName());

    // --- Wikidata DeepaMehta URIs

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
    private WorkspacesService wsService = null;

    
    // --
    // --- Public REST API Endpoints
    // --
    
    @GET
    @Path("/import/entities/{importerId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Topic importEntitiesFromWikidataDump(@PathParam("importerId") long settingsTopicId) {
        //
        checkAuthorization();
        //
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
        //
        checkAuthorization();
        //
        Topic importerSettings = dms.getTopic(settingsTopicId);
        ChildTopics settings = importerSettings.getChildTopics();
        boolean persons = settings.getBoolean(WD_IMPORT_PERSONS);
        boolean institutions = settings.getBoolean(WD_IMPORT_INSTITUTIONS);
        boolean cities = settings.getBoolean(WD_IMPORT_CITIES);
        boolean countries = settings.getBoolean(WD_IMPORT_COUNTRIES);
        try {
            log.info("Start to remove all wikidata topics ... ");
            if (persons) { // Delete all "Person" Topics
                for (RelatedTopic person : dms.getTopics("dm4.contacts.person", 0)){
                    if (person.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Persons ... ");
                        dms.deleteTopic(person.getId());
                    }
                }
            }
            if (institutions) { // Delete all "Institution"-Topics
                for (RelatedTopic institution : dms.getTopics("dm4.contacts.institution", 0)){
                    if (institution.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Institutions ... ");
                        dms.deleteTopic(institution.getId());
                    }
                }
            }
            if (cities) { // Delete all "City"-Topics
                for (RelatedTopic city : dms.getTopics("dm4.contacts.city", 0)){
                    if (city.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Cities ... ");
                        dms.deleteTopic(city.getId());
                    }
                }
            }
            if (countries) { // Delete all "Country"-Topics
                for (RelatedTopic country : dms.getTopics("dm4.contacts.country", 0)){
                    if (country.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Countries ... ");
                        dms.deleteTopic(country.getId());
                    }
                }
            }
            log.info("Deleted all previously imported wikidata topics!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "removal of wikidata topics failed", e);
        }
        return importerSettings;
    }

    /**
     * For example: Firing a GET to `/wdtk/query/P108/Q9531` will respond with
     * a list of persons imported from wikidata formerly|currently employed by BBC.
     *
     * @param propertyId    String valid Wikidata Propery ID (e.g "P108")
     * @param itemId        String valid Wikidata Item ID (e.g "Q42")
     * @return              ResultList of DeepaMehta 4 Topics which are relate to the
     *                      given item via the given property.
     */
    @GET
    @Path("/list/{propertyId}/{itemId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public ResultList<RelatedTopic> getRelatedTopics(@PathParam("propertyId") String propertyId,
            @PathParam("itemId") String itemId) {
        Topic item = getWikidataItemByEntityId(itemId);
        Topic propertyTopic = getWikidataItemByEntityId(propertyId.trim());
        if (propertyTopic != null) {
            log.fine("### Query Related Topics for Property \"" + propertyTopic.getUri() + "\"");
            ResultList<RelatedTopic> associatedAssocTypes = propertyTopic.getRelatedTopics("dm4.core.aggregation", "dm4.core.child",
                    "dm4.core.parent", "dm4.core.assoc_type", 0);
            if (associatedAssocTypes.getSize() == 1) {
                AssociationType propertyType = dms.getAssociationType(associatedAssocTypes.get(0).getUri());
                if (item != null) {
                    ResultList<RelatedTopic> results = item.getRelatedTopics(propertyType.getUri(), 0);
                    String resultShowCaseInfo = "Nothing we know of.";
                    if (results.getSize() >= 1) {
                        Topic firstItem = results.get(0);
                        resultShowCaseInfo = firstItem.getSimpleValue() + " ("+firstItem.getUri() + ") .. at least.";
                    }
                    log.info("### Query: What is related to " + "\"" + item.getSimpleValue() + "\" via \""
                            + propertyType.getSimpleValue() + "\" >>> " + resultShowCaseInfo);
                    return results;
                } else {
                    log.severe("### Query: Item with ID: " + itemId + " NOT FOUND in DB! - SKIPPING QUERY");
                }
            } else {
                log.severe("### Query: could not fetch ONE assocType for given " + propertyId + " BUT => "
                    + associatedAssocTypes.getSize() + " - SKIPPING QUERY due to misconfiguration");
            }
        } else {
            log.severe("### Query: Property with ID: " + propertyId + " NOT FOUND in DB!");
        }
        return null;
    }

    /**
     * Will simply just return the list of all imported claims involving the given property.
     * @param propertyId    String valid Wikidata Propery ID (e.g "P27")
     * @return              List of all imported claims (along with naming both players involved)
     *                      for the given property ID.
     */
    @GET
    @Path("/list/claims/{propertyId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public ResultList<RelatedAssociation> getRelatedAssociations(@PathParam("propertyId") String propertyId) {
        Topic propertyTopic = getWikidataItemByEntityId(propertyId.trim());
        if (propertyTopic != null) {
            log.info("### Query Wikidata Claims by property \"" + propertyTopic.getUri() + "\"");
            return propertyTopic.getRelatedAssociations("dm4.core.aggregation", null, null, null);
        } else {
            log.severe("### Query: Property with ID: " + propertyId + " NOT FOUND in DB!");
        }
        return null;
    }

    /**
     * Simply joining the list of all imported claims relating somehow (=on both ends)
     * to the given wikidata Item ID.
     * @param propertyId        String valid Wikidata Propery ID (e.g "P27")
     * @param itemId            String valid Wikidata Item ID (e.g "Q42")
     * @return
     */
    @GET
    @Path("/list/claims/{propertyId}/{itemId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public ArrayList<RelatedAssociation> getRelatedAssociationsForItem(@PathParam("propertyId") String propertyId,
            @PathParam("itemId") String itemId) {
        Topic item = getWikidataItemByEntityId(itemId.trim());
        ArrayList<RelatedAssociation> collection = new ArrayList<RelatedAssociation>();
        ResultList<RelatedAssociation> claims = getRelatedAssociations(propertyId);
        for (RelatedAssociation claim : claims.getItems()) {
            if (claim.getPlayer1().getId() == item.getId() ||
                claim.getPlayer2().getId() == item.getId()) collection.add(claim);
        }
        return collection;
    }

    /**
     *
     * @param propertyId
     * @param itemId
     * @param propertyTwoId
     * @param itemTwoId
     * @return                  A list of topics which relate to both items via the respective property ID.
     */
    @GET
    @Path("/list/{propertyId}/{itemId}/with/{propertyTwoId}/{itemTwoId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public ArrayList<RelatedTopic> getSuperRelatedTopics(@PathParam("propertyId") String propertyId,
            @PathParam("itemId") String itemId, @PathParam("propertyTwoId") String propertyTwoId,
            @PathParam("itemTwoId") String itemTwoId) {
        ResultList<RelatedTopic> firstHopResults = getRelatedTopics(propertyId, itemId);
        ArrayList<RelatedTopic> collection = new ArrayList<RelatedTopic>();
        if (firstHopResults.getSize() > 0) {
            Topic propertyTwoTopic = getWikidataItemByEntityId(propertyTwoId.trim());
            for (RelatedTopic result : firstHopResults.getItems()) {
                if (propertyTwoTopic != null) {
                    log.fine("### Query Super Related Topic for Property \"" + propertyTwoTopic.getSimpleValue()+ "\"");
                    ResultList<RelatedTopic> associatedAssocTypes = propertyTwoTopic.getRelatedTopics("dm4.core.aggregation", "dm4.core.child",
                            "dm4.core.parent", "dm4.core.assoc_type", 0);
                    if (associatedAssocTypes.getSize() == 1) {
                        AssociationType propertyTypeTwo = dms.getAssociationType(associatedAssocTypes.get(0).getUri());
                        if (itemTwoId != null && !itemTwoId.equals("NOQ")) {
                            log.info("### Query: What is super related to " + "\"" + itemTwoId+ "\" via \""
                                    + propertyTypeTwo.getSimpleValue() + "\" >>> ");
                            ResultList<RelatedTopic> results = result.getRelatedTopics(propertyTypeTwo.getUri(), 0);
                            if (results.getSize() >= 1) {
                                for (RelatedTopic end : results.getItems()) {
                                    if (end.getUri().equals(WikidataEntityMap.WD_ENTITY_BASE_URI + itemTwoId)) {
                                        collection.add(result);
                                        log.info(" .... at least " + end.getSimpleValue() + " ("+end.getUri() + ") .. at least.");
                                    }
                                }
                                return collection;
                            }
                        } else {
                            log.severe("### Query: Super Related Topic "+result.getSimpleValue()+" had NO ItemId via \"" + propertyTypeTwo.getSimpleValue() + "\"");
                            ResultList<RelatedTopic> results = result.getRelatedTopics(propertyTypeTwo.getUri(), 0);
                            log.info("  > " + results.toJSON().toString());
                        }
                    } else {
                        log.severe("### Query: could not fetch ONE assocType for given " + propertyId + " BUT => "
                            + associatedAssocTypes.getSize() + " - SKIPPING QUERY due to misconfiguration");
                    }
                } else {
                    log.severe("### Query: Property with ID: " + propertyId + " NOT FOUND in DB!");
                }

            }
        }
        return null;
    }

    // --
    // --- Methods to process and import topics based on a complete (daily) wikidatawiki (json) dump.
    // --

    private void checkAuthorization () {
        if (!acService.getUsername().equals("admin")) throw new WebApplicationException(Status.UNAUTHORIZED);
    }

    // ### remove copy in WikidataEntityProcessor
    private Topic getWikidataItemByEntityId (String id) {
        return dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + id));
    }
        
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
        WikidataGeodataProcessor wikidataEntityProcessor = new WikidataGeodataProcessor(dms, wsService, timeOut, 
                persons, institutions, cities, countries, descriptions, websites, geoCoordinates, isoLanguageCode);
        WikidataToolkitPlugin.this.startProcesssingWikidataDumpfile(wikidataEntityProcessor, noDownload);
        wikidataEntityProcessor = null;
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
    private void startProcesssingWikidataDumpfile(WikidataGeodataProcessor entityProcessor, boolean noDownload) {
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
