
package org.deepamehta.plugins.wdtk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.deepamehta.plugins.wdtk.viewmodel.CountryItem;
import org.deepamehta.plugins.wdtk.viewmodel.WikidataItem;
import org.deepamehta.plugins.wdtk.viewmodel.RegionItem;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationType;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.workspaces.WorkspacesService;
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;



/**
 * A plugin to use the WikidataToolkit by Markus Krötzsch et al as DeepaMehta 4 plugin.
 *
 * <a href="https://github.com/mukil/dm4-wikidata-toolkit">Source Code Repository</a>
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @version 0.3-SNAPSHOT
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
        Topic settings = dm4.getTopic(settingsTopicId);
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
        Topic importerSettings = dm4.getTopic(settingsTopicId);
        ChildTopics settings = importerSettings.getChildTopics();
        boolean persons = settings.getBoolean(WD_IMPORT_PERSONS);
        boolean institutions = settings.getBoolean(WD_IMPORT_INSTITUTIONS);
        boolean cities = settings.getBoolean(WD_IMPORT_CITIES);
        boolean countries = settings.getBoolean(WD_IMPORT_COUNTRIES);
        try {
            log.info("Start to remove all wikidata topics ... ");
            if (persons) { // Delete all "Person" Topics
                for (Topic person : dm4.getTopicsByType("dm4.contacts.person")){
                    if (person.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Persons ... ");
                        dm4.deleteTopic(person.getId());
                    }
                }
            }
            if (institutions) { // Delete all "Institution"-Topics
                for (Topic institution : dm4.getTopicsByType("dm4.contacts.institution")){
                    if (institution.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Institutions ... ");
                        dm4.deleteTopic(institution.getId());
                    }
                }
            }
            if (cities) { // Delete all "City"-Topics
                for (Topic city : dm4.getTopicsByType("dm4.contacts.city")){
                    if (city.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Cities ... ");
                        dm4.deleteTopic(city.getId());
                    }
                }
            }
            if (countries) { // Delete all "Country"-Topics
                for (Topic country : dm4.getTopicsByType("dm4.contacts.country")){
                    if (country.getUri().startsWith(WikidataEntityMap.WD_ENTITY_BASE_URI)) {
                        log.info("Countries ... ");
                        dm4.deleteTopic(country.getId());
                    }
                }
            }
            log.info("Deleted all previously imported wikidata topics!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "removal of wikidata topics failed", e);
        }
        return importerSettings;
    }

    // --- TODO: Create a search GUI
    // --- TODO: Integrate a connector tor the OSM API
    // https://wiki.openstreetmap.org/wiki/API_v0.6
    // e.g. see a XML document returned for the OSM Relation describing "Japan"
    // http://api.openstreetmap.org/api/0.6/relation/382313/full
    // see also http://dev.openlayers.org/apidocs/files/OpenLayers/Format/OSM-js.html
    /**
     * <script src='https://api.mapbox.com/mapbox.js/plugins/leaflet-osm/v0.1.0/leaflet-osm.js'></script>
     * $.ajax({
        url: "https://www.openstreetmap.org/api/0.6/way/260501602/full",
        dataType: "xml",
        success: function (xml) {
            var layer = new L.OSM.DataLayer(xml).addTo(map);
            map.fitBounds(layer.getBounds());
        }
     });
     */

    // --- Specific "Listing" Endpoints

    @GET
    @Path("/search/{query}")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<WikidataItem> getWikidataItemsByValue(@PathParam("query") String value) {
        //
        ArrayList<WikidataItem> results = new ArrayList<WikidataItem>();
        log.info("Searching wikidata text topics with " + value);
        List<Topic> all = dm4.searchTopics(value, "org.deepamehta.wikidata.text");
        for (Topic textValue : all) {
            WikidataItem item = new WikidataItem(textValue, dm4);
            results.add(item);
        }
        log.info("Fetched " + results.size() + " wikidata items");
        return results;
    }

    @GET
    @Path("/list/countries")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<CountryItem> getAllCountryItems() {
        //
        ArrayList<CountryItem> results = new ArrayList<CountryItem>();
        List<Topic> all = dm4.getTopicsByType("org.deepamehta.wikidata.text");
        log.info("Requested all countries (identified via iso-code), fetched " + all.size());
        for (Topic textValue : all) {
            CountryItem item = new CountryItem(textValue);
            if (item.isCountry()) results.add(item);
        }
        log.info("Fetched " + results.size() + " countries via ISO Code");
        return results;
    }

    @GET
    @Path("/list/items/iso-coded")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<Topic> getAllItemsWithIsoCodes() {
        log.info("Requested all wikidata items with an \"ISO Three Letter Code\"");
        ArrayList<Topic> results = new ArrayList<Topic>();
        List<Topic> textValues = dm4.getTopicsByType("org.deepamehta.wikidata.text");
        // all results
        for (Topic textValue : textValues) {
            List<Association> assocs = textValue.getAssociations();
            for (Association assoc : assocs) {
                if (assoc.getTypeUri().equals("org.deepamehta.wikidata.iso_country_code")) {
                    // OK, lets' add the parent wikidata item of this iso code to our resultset
                    if (assoc.getPlayer1().getId() == textValue.getId()) {
                        results.add(dm4.getTopic(assoc.getPlayer2().getId()));
                    } else {
                        results.add(dm4.getTopic(assoc.getPlayer1().getId()));
                    }
                }
            }
        }
        log.info("> Fetched " + results.size() + " iso coded wikidata items");
        return results;
    }

    @GET
    @Path("/list/items/osm-relations")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<Topic> getAllItemsWithOSMRelations() {
        log.info("Requested all wikidata items with an \"OSM Relation ID\"");
        ArrayList<Topic> results = new ArrayList<Topic>();
        List<Topic> textValues = dm4.getTopicsByType("org.deepamehta.wikidata.text");
        // all results
        for (Topic textValue : textValues) {
            List<Association> assocs = textValue.getAssociations();
            for (Association assoc : assocs) {
                if (assoc.getTypeUri().equals("org.deepamehta.wikidata.osm_relation_id")) {
                    // OK, lets' add the parent wikidata item of this iso code to our resultset
                    if (assoc.getPlayer1().getId() == textValue.getId()) {
                        results.add(dm4.getTopic(assoc.getPlayer2().getId()));
                    } else {
                        results.add(dm4.getTopic(assoc.getPlayer1().getId()));
                    }
                }
            }
        }
        log.info("> Fetched " + results.size() + " wikidata items with an OSM Relation ID");
        return results;
    }

    @GET
    @Path("/list/items/nuts-coded")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<RegionItem> getAllItemsWithNutsCodes() {
        log.info("Requested all wikidata items with a \"NUTS Code\"");
        ArrayList<RegionItem> results = new ArrayList<RegionItem>();
        List<Topic> textValues = dm4.getTopicsByType("org.deepamehta.wikidata.text");
        // all results
        for (Topic textValue : textValues) {
            List<Association> assocs = textValue.getAssociations();
            for (Association assoc : assocs) {
                if (assoc.getTypeUri().equals("org.deepamehta.wikidata.nuts_code")) {
                    // OK, lets' add the parent wikidata item of this iso code to our resultset
                    if (assoc.getPlayer1().getId() == textValue.getId()) {
                        results.add(new RegionItem(dm4.getTopic(assoc.getPlayer2().getId())));
                    } else {
                        results.add(new RegionItem(dm4.getTopic(assoc.getPlayer1().getId())));
                    }
                }
            }
        }
        log.info("> Fetched " + results.size() + " nuts coded wikidata items");
        return results;
    }

    // --- The dm4-wdtk "Query" Endpoints supporting simple hops

    /**
     * For example: Firing a GET to `/wdtk/list/P108/Q9531` will respond with
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
    public List<RelatedTopic> getRelatedTopics(@PathParam("propertyId") String propertyId,
            @PathParam("itemId") String itemId) {
        Topic item = getWikidataItemByEntityId(itemId);
        Topic propertyTopic = getWikidataItemByEntityId(propertyId.trim());
        if (propertyTopic != null) {
            log.fine("### Query Related Topics for Property \"" + propertyTopic.getUri() + "\"");
            List<RelatedTopic> associatedAssocTypes = propertyTopic.getRelatedTopics("dm4.core.aggregation", "dm4.core.child",
                    "dm4.core.parent", "dm4.core.assoc_type");
            if (associatedAssocTypes.size() == 1) {
                AssociationType propertyType = dm4.getAssociationType(associatedAssocTypes.get(0).getUri());
                if (item != null) {
                    List<RelatedTopic> results = item.getRelatedTopics(propertyType.getUri());
                    String resultShowCaseInfo = "Nothing we know of.";
                    if (results.size() >= 1) {
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
                    + associatedAssocTypes.size() + " - SKIPPING QUERY due to misconfiguration");
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
    public List<RelatedAssociation> getRelatedAssociations(@PathParam("propertyId") String propertyId) {
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
        List<RelatedAssociation> claims = getRelatedAssociations(propertyId);
        for (RelatedAssociation claim : claims) {
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
        List<RelatedTopic> firstHopResults = getRelatedTopics(propertyId, itemId);
        ArrayList<RelatedTopic> collection = new ArrayList<RelatedTopic>();
        if (firstHopResults.size() > 0) {
            Topic propertyTwoTopic = getWikidataItemByEntityId(propertyTwoId.trim());
            for (RelatedTopic result : firstHopResults) {
                if (propertyTwoTopic != null) {
                    log.fine("### Query Super Related Topic for Property \"" + propertyTwoTopic.getSimpleValue()+ "\"");
                    List<RelatedTopic> associatedAssocTypes = propertyTwoTopic.getRelatedTopics("dm4.core.aggregation", "dm4.core.child",
                            "dm4.core.parent", "dm4.core.assoc_type");
                    if (associatedAssocTypes.size() == 1) {
                        AssociationType propertyTypeTwo = dm4.getAssociationType(associatedAssocTypes.get(0).getUri());
                        if (itemTwoId != null && !itemTwoId.equals("NOQ")) {
                            log.info("### Query: What is super related to " + "\"" + itemTwoId+ "\" via \""
                                    + propertyTypeTwo.getSimpleValue() + "\" >>> ");
                            List<RelatedTopic> results = result.getRelatedTopics(propertyTypeTwo.getUri());
                            if (results.size() >= 1) {
                                for (RelatedTopic end : results) {
                                    if (end.getUri().equals(WikidataEntityMap.WD_ENTITY_BASE_URI + itemTwoId)) {
                                        collection.add(result);
                                        log.info(" .... at least " + end.getSimpleValue() + " ("+end.getUri() + ") .. at least.");
                                    }
                                }
                                return collection;
                            }
                        } else {
                            log.warning("### Query: Super Related Topic "+result.getSimpleValue()+" had NO ItemId via \"" + propertyTypeTwo.getSimpleValue() + "\"");
                            // List<RelatedTopic> results = result.getRelatedTopics(propertyTypeTwo.getUri());
                        }
                    } else {
                        log.warning("### Query: could not fetch ONE assocType for given " + propertyId + " BUT => "
                            + associatedAssocTypes.size() + " - SKIPPING QUERY due to misconfiguration");
                    }
                } else {
                    log.warning("### Query: Property with ID: " + propertyId + " NOT FOUND in DB!");
                }
            }
        }
        return null;
    }

    // --
    // --- Methods to process and import topics based on a complete (daily) wikidatawiki (json) dump.
    // --

    private void checkAuthorization () {
        if (acService.getUsername() == null || acService.getUsername().isEmpty()) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }
    }

    // ### remove copy in WikidataEntityProcessor
    private Topic getWikidataItemByEntityId (String id) {
        return dm4.getTopicByUri(WikidataEntityMap.WD_ENTITY_BASE_URI + id);
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
        WikidataGeodataProcessor wikidataEntityProcessor = new WikidataGeodataProcessor(dm4, mf, wsService, timeOut,
                persons, institutions, cities, countries, descriptions, websites, geoCoordinates, isoLanguageCode);
        WikidataToolkitPlugin.this.startProcessingWikidataDumpfile(wikidataEntityProcessor, noDownload);
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
    private void startProcessingWikidataDumpfile(WikidataGeodataProcessor entityProcessor, boolean noDownload) {
        if (isCurrentlyImporting == true) {
            log.warning("One WDTK DumpFileProcessor is already running, please try again later.");
            return;
        }
        // Controller object for processing dumps:
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(noDownload);
        List<MwDumpFile> availableJSONDumps = null;
        String path = findDumpDirectoryPath();
        try {
            log.info("Searching for a wikidata (json) dump on your hard disk under " + path);
            dumpProcessingController.setDownloadDirectory(path);
            availableJSONDumps = dumpProcessingController
                .getWmfDumpFileManager().findAllDumps(DumpContentType.JSON);
            if (availableJSONDumps.isEmpty()) {
                log.warning("No JSON dumps to process availabe. Place a <date>.json.gz dumpfile in your filerepo "
                + "directory under /dumpfiles/wikidatawiki/2014XXYY.json.gz");
            }
        } catch (IOException ex) {
            log.warning("IOException: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
        dumpProcessingController.registerEntityDocumentProcessor(entityProcessor, null, false);
        try {
            isCurrentlyImporting = true;
            if (availableJSONDumps.isEmpty()) {
                MwDumpFile jsonDumpFile = new MwLocalDumpFile(path + "/dumpfiles/wikidatawiki/20160425.json.gz");
                dumpProcessingController.processDump(jsonDumpFile);
            } else {
                dumpProcessingController.processMostRecentJsonDump();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Let's see, if this is not a TimeoutException, what was catched then?", e);
            // The timer caused a time out or we could not find a dumpfile.
        }
        isCurrentlyImporting = false;
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
