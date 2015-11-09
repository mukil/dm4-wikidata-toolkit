package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.Association;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.WebApplicationException;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.datamodel.json.jackson.datavalues.JacksonValueTime;
import org.wikidata.wdtk.util.Timer;

/**
 * @author Malte Rei&szlig;ig <malte@mikromedia.de>
 *
 * A simple class that processes EntityDocuments to identify various classes of items in the
 * wikidatawiki. Based on the EntityTimerProcessor from the WDTK examples written by Markus Kroetzsch.
 * Thanks for sharing. Honorable mentions go to jri for telling me about the 
 * ImportPackage bundle notations which helped me to run the WDTK within our OSGi.
 */
public class WikidataGeodataProcessor implements EntityDocumentProcessor {
    
    private Logger log = Logger.getLogger(getClass().getName());
    
    private final String DM_PERSON              = "dm4.contacts.person";
    private final String DM_PERSON_NAME         = "dm4.contacts.person_name";
    private final String DM_PERSON_FIRST_NAME   = "dm4.contacts.first_name";
    private final String DM_PERSON_LAST_NAME    = "dm4.contacts.last_name";
    private final String DM_INSTITUTION         = "dm4.contacts.institution";
    private final String DM_INSTITUTION_NAME    = "dm4.contacts.institution_name";
    private final String DM_CITY                = "dm4.contacts.city";
    private final String DM_COUNTRY             = "dm4.contacts.country";
    private final String DM_WEBBROWSER_URL      = "dm4.webbrowser.url";
    private final String DM_CONTACT_NOTE        = "dm4.contacts.notes";

    private final String WS_WIKIDATA_URI = "org.deepamehta.workspaces.wikidata";

    // custom association types and properties
    private final String ASSOCTYPE_ISO_COUNTRY_CODE = "org.deepamehta.wikidata.iso_country_code";
    private final String ASSOCTYPE_NUTS_CODE        = "org.deepamehta.wikidata.nuts_code";
    private final String ASSOCTYPE_OSM_RELATION_ID  = "org.deepamehta.wikidata.osm_relation_id";

    private final String WIKIDATA_START_TIME_PROP   = "org.deepamehta.start_time";
    private final String WIKIDATA_END_TIME_PROP     = "org.deepamehta.end_time";

    // setting: overall seconds and timer to parse the dumpfile
    final Timer timer = Timer.getNamedTimer("WikidataEntityProcessor");
    int lastSeconds = 0, entityCount = 0;
    int timeout;

    // flags: create or not create topics of type ..
    boolean doCountries = false;
    boolean doCities = false;
    boolean doInstitutions = false;
    boolean doPersons = false;

    // flags: to store additional text values for items
    boolean storeDescription = false;
    boolean storeGeoCoordinates = false;

    // setting: language default value
    String isoLanguageCode = WikidataEntityMap.LANG_EN;

    DeepaMehtaService dms;
    WorkspacesService workspaceService;
    Topic wikidataWorkspace = null;

    DeepaMehtaTransaction tx = null;
    Date importStartedAt = null;

    public WikidataGeodataProcessor (DeepaMehtaService dms, WorkspacesService workspaceService, int timeout,
        boolean persons, boolean institutions, boolean cities, boolean countries, boolean descriptions,
        boolean urls, boolean coordinates, String iso_lang) {
        this.timeout = timeout;
        this.dms = dms;
        this.workspaceService = workspaceService;
        this.doCountries = countries;
        this.doCities = cities;
        this.doInstitutions = institutions;
        this.doPersons = persons;
        this.storeGeoCoordinates = coordinates;
        // this.storeWebsiteAddresses = urls;
        this.storeDescription = descriptions;
        if (iso_lang != null) this.isoLanguageCode = iso_lang;
        wikidataWorkspace = workspaceService.getWorkspace(WS_WIKIDATA_URI);
        log.info("Set up to import wikidata topics into workspace \"" + wikidataWorkspace.getSimpleValue()
                + "\" with language Code " + this.isoLanguageCode );
        importStartedAt = new Date();
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        countEntity();

        // Finish and create a new transaction for every 1000 items
        if (tx == null) tx = dms.beginTx();
        if (this.entityCount % 1000 == 0) {
            tx.success();
            tx.finish();
            tx = dms.beginTx();
        }
        // 0) Get label and description of current item
        String itemId = itemDocument.getEntityId().getId();
        String label = getItemLabel(itemDocument);
        String description = getItemDescription(itemDocument);

        /** Optimization: pass on current wikidata item topic instead of fetching it all the time */
        Topic wikidataItem = null;

        // 1) Iterate over items statement groups
        if (itemDocument.getStatementGroups().size() > 0) {
            
            for (StatementGroup sg : itemDocument.getStatementGroups()) {
                                
                // -- Inspect with which type of StatementGroup (resp. Property) we deal here 
                
                boolean isInstanceOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_INSTANCE_OF);
                boolean isSubclassOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SUBCLASS_OF);
                // ?principiality?
                boolean isCoordinateOf = sg.getProperty().getId().equals(WikidataEntityMap.GEO_COORDINATES);
                boolean isISOThreeLetterCode = sg.getProperty().getId().equals(WikidataEntityMap.IS_ISO_THREE_LETTER_CODE);
                // boolean isISOSubregionCode = sg.getProperty().getId().equals(WikidataEntityMap.IS_ISO_SUB_REGION_CODE);
                boolean isNUTSCode = sg.getProperty().getId().equals(WikidataEntityMap.IS_NUTS_CODE);
                boolean isOSMRelationIdentifier = sg.getProperty().getId().equals(WikidataEntityMap.OSM_RELATION_ID);
                //
                boolean isCapital = sg.getProperty().getId().equals(WikidataEntityMap.IS_CAPITAL);
                boolean isCountry = sg.getProperty().getId().equals(WikidataEntityMap.IS_COUNTRY);
                //
                /** boolean isLocatedHQ = sg.getProperty().getId().equals(WikidataEntityMap.LOCATION_OF_HEADQUARTER);
                boolean isLocatedFO = sg.getProperty().getId().equals(WikidataEntityMap.LOCATION_OF_FORMATION); **/
                //
                boolean isCitizenOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_CITIZEN_OF);
                boolean isResidenceOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_RESIDENCE_OF);
                //
                boolean containsAdministrativeEntity = sg.getProperty().getId().equals(WikidataEntityMap.CONTAINS_ADMIN_T_ENTITY); // down the hierarchy
                boolean locatedInAdministrativeEntity = sg.getProperty().getId().equals(WikidataEntityMap.IS_LOCATED_IN_ADMIN_T);  // up the hierarchy
                //
                /** boolean isGivenNameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_GIVEN_NAME_OF);
                boolean isSurnameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SURNAME_OF);
                boolean isBirthDateOf = sg.getProperty().getId().equals(WikidataEntityMap.WAS_BORN_ON);
                boolean isDeathDateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DEAD_SINCE); **/
                //
                /** boolean isEmployeeOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_EMPLOYEE_OF);
                boolean isPartyMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PARTY_MEMBER_OF);
                boolean isOfficiallyResidingAt = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIALLY_RESIDING_AT);
                boolean isMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_MEMBER_OF);
                // boolean isAlmaMaterOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_ALMA_MATER_OF);
                boolean isStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_STUDENT_OF_PERSON);
                boolean isDoctoralAdvisorOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_ADVISOR_OF);
                boolean isDoctoralStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_STUDENT_OF);
                boolean isAffiliatedWith = sg.getProperty().getId().equals(WikidataEntityMap.IS_AFFILIATED_WITH); **/
                /** boolean isPseudonymOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PSEUDONYM_OF);
                boolean isOpenResearchIdOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_OPEN_RESEARCH_ID_OF);
                boolean isNotableWorkOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_NOTABLE_WORK_OF); **/
                /** if (isSubclassOf && classRecord == null) {
                    // logger.info("Has SubclassOf Relationship.. and NO classRecord");
                    classRecord = getClassRecord(itemDocument, itemDocument.getItemId());
                } **/
                
                // 1.1) Record various "attributes" of the current item

                // -- StatementGroup of propertyType is instance | subclass of - the four basic items we **directly** create ###

                if (isInstanceOf || isSubclassOf) {
                    for (Statement s : sg.getStatements()) {
                        // ### simply using the main snak value of this statement
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            String referencedItemId = "";
                            
                            // --- Statement involving other ITEMS
                            if (mainSnakValue instanceof EntityIdValue) {
                                
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;

                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    
                                    referencedItemId = itemIdValue.getId();

                                    // 2.1 current wikidata item is direct instanceOf|subclassOf "human" or "person"
                                    if (referencedItemId.equals(WikidataEntityMap.HUMAN_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.PERSON_ITEM)) {
                                        if (doPersons) {
                                            updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                        }

                                    // 2.2 current wikidata item is direct instanceOf|subclassOf "university", "company" or "organisation"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COMPANY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.UNIVERSITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.ORGANISATION_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.COLLEGIATE_UNIVERSITY_ITEM)) { // = often subclass of "university" items
                                        if (doInstitutions) {
                                            updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                        }

                                    // 2.3 current wikidata item is direct instanceOf|subclassOf "city", "metro" or "capital"
                                    } else if (referencedItemId.equals(WikidataEntityMap.CITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.METROPOLIS_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.CAPITAL_CITY_ITEM)) {
                                        if (doCities) {
                                            updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                        }

                                    // 2.4 current wikidata item is direct instanceOf|subclassOf "country" or "sovereing state"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COUNTRY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.SOVEREIGN_STATE_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.STATE_ITEM)) {
                                        if (doCountries) { // && !all_countries.containsKey(itemId)) all_countries.put(itemId, label);
                                            updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                        }

                                    }

                                }
                            }
                        }
                    }

                // 1.2) introducing everything which has a geo-coordinate, too
                } else if (isCoordinateOf && this.storeGeoCoordinates) {
                    for (Statement s : sg.getStatements()) {
                        // ### simply using the main snak value of this statement
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof GlobeCoordinatesValue) {
                                GlobeCoordinatesValue coordinateValues = (GlobeCoordinatesValue) mainSnakValue;
                                double longitude = coordinateValues.getLatitude();
                                double latitude = coordinateValues.getLongitude();
                                // note: coordinateValues.getGlobe().contains("Q2) == Planet Earth
                                // every item with a coordinate gets a label and description UPDATE
                                updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                if (longitude != -1 && latitude != -1) {
                                    double coordinates[] = {latitude, longitude};
                                    // do Coordinates
                                    attachGeoCoordinates(coordinates, itemId);
                                }
                            }
                        }
                    }

                // 1.3) Storing simple, but related text values from the geo-domain/vocabulary
                // ### isRegion (Administrative Subregion) via hasCode?

                } else if (isISOThreeLetterCode) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof StringValue) {
                                StringValue codeValue = (StringValue) mainSnakValue;
                                updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                createWikidataTextClaim(codeValue.toString(), ASSOCTYPE_ISO_COUNTRY_CODE, itemId, s.getStatementId());
                            }
                        }
                    }

                } else if (isNUTSCode) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof StringValue) {
                                StringValue codeValue = (StringValue) mainSnakValue;
                                updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                createWikidataTextClaim(codeValue.toString(), ASSOCTYPE_NUTS_CODE, itemId, s.getStatementId());
                            }
                        }
                    }

                } else if (isOSMRelationIdentifier) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof StringValue) {
                                StringValue codeValue = (StringValue) mainSnakValue;
                                // log.info("### NEW: OSM Relation ID: " + codeValue);
                                // every item with a osm relation id gets a label and description UPDATE
                                updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                createWikidataTextClaim(codeValue.toString(), ASSOCTYPE_OSM_RELATION_ID, itemId, s.getStatementId());
                            }
                        }
                    }

                // .. Starting to qualify claims.. but ### store References too!

                } else if (isCountry) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                    // ### Needs issue #805 solved (for hierarchical/directed claim edges).
                                    claimEdge = createWikidataClaimEdge(referencedItemId, itemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                } else if (isCapital) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            // value
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS (country or subregion at the "other" side of this statement)
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    // ### Needs issue #805 solved (for hierarchical/directed claim edges).
                                    claimEdge = createWikidataClaimEdge(itemId, referencedItemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            // qualifiers
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                } else if (locatedInAdministrativeEntity) { // institutions in cities or cities in regions and regions in countries
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS (country or subregion at the "other" side of this statement)
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    log.fine("### NEW: item is located in Administrative unit: " + referencedItemId); // ### add this item // upward relation
                                    updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);
                                    // ### Needs issue #805 solved (for hierarchical/directed claim edges).
                                    claimEdge = createWikidataClaimEdge(referencedItemId, itemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                } else if (containsAdministrativeEntity) { // regions in countries
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    // ## need label
                                    updateOrCreateWikidataItem(referencedItemId, null, null, null, this.isoLanguageCode);
                                    // ### Needs issue #805 solved (for hierarchical/directed claim edges).
                                    claimEdge = createWikidataClaimEdge(itemId, referencedItemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                } /** else if (isCitizenOf) { // persons in countries
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    log.fine("### NEW: person/human is citizen of : " + referencedItemId);
                                    // ### updateOrCreateWikidataItem(itemId, label, label, description, label);
                                    claimEdge = createWikidataClaimEdge(itemId, referencedItemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                } else if (isResidenceOf) { // persons in cities
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            // --- Statement involving other ITEMS
                            Association claimEdge = null;
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue itemIdValue = (EntityIdValue) mainSnakValue;
                                if (itemIdValue.getEntityType().equals(EntityIdValue.ET_ITEM)) {
                                    String referencedItemId = itemIdValue.getId();
                                    log.fine("### NEW: person/human has residence in : " + referencedItemId);
                                    claimEdge = createWikidataClaimEdge(itemId, referencedItemId, s.getStatementId(), sg.getProperty());
                                }
                            }
                            if (claimEdge != null) storeQualifyingTimeProperties(claimEdge, s);
                        }
                    }

                // #### IS_PLACE_OF_BIRTH // person at city
                // #### IS_PLACE_OF_DEATH // person at city

                /** } else if (isBirthDateOf || isDeathDateOf) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof TimeValue) {
                                TimeValue dateValue = (TimeValue) mainSnakValue; // ### respect calendermodel
                                log.fine("### NEW: Parsed time value: " + dateValue.getDay() + "."
                                        + dateValue.getMonth() + " " + dateValue.getYear());
                            }
                        }
                    }

                } else if (isGivenNameOf || isSurnameOf) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof EntityIdValue) { // ### is item id!
                                EntityIdValue nameId = (EntityIdValue) mainSnakValue;
                                log.fine("### NEW: Parsed name is item with ID : " + nameId);
                            }
                        }
                    } **/
                
                // 1.2) Record various relations of current item to other items

                /** } else if (isEmployeeOf) {
                    // #### wasEducatedAt // person at institution
                    // some professional person to organisation relationship
                    /*** for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof EntityIdValue) {
                                EntityIdValue nameId = (EntityIdValue) mainSnakValue;
                                PropertyIdValue propertyId = s.getClaim().getMainSnak().getPropertyId();
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    HashMap<String, String> claim = new HashMap<String, String>();
                                    claim.put("T" + entity.getId(), s.getStatementId() + WikidataEntityMap.URI_SEPERATOR + propertyId.getId());
                                    // employeeOf.put(itemDocument.getItemId().getId(), claim);
                                } else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) worksAt.put(itemDocument.getItemId().getId(), relatedLabel);
                                }
                            }
                        }
                    }
                } **/

            }
        }

        // Print a report every 10000 items:
        if (this.entityCount % 10000 == 0) {
            printProcessingStatus();
        }
    }

    private Topic getWikidataItemByEntityId (EntityIdValue id) {
        return dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + id.getId()));
    }
   
    private Topic getWikidataItemByPropertyId (String propertyId) {
        return dms.getTopic("uri", new SimpleValue(propertyId));
    }
    
    private Topic getWikidataItemByEntityId (String id) {
        return dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + id));
    }

    private String getItemLabel(ItemDocument itemDocument) {
        MonolingualTextValue value = null;
        if (itemDocument.getLabels().size() > 1) {
            if (itemDocument.getLabels().containsKey(this.isoLanguageCode)) {
                value = itemDocument.getLabels().get(this.isoLanguageCode);
            } else { // if no label (in configured language) is available, we take first available
                for (String key : itemDocument.getLabels().keySet()) {
                    value = itemDocument.getLabels().get(key);
                    break;
                }
            }
        }
        if (value == null) {
            log.fine("Could not find ANY label for item (" 
                + itemDocument.getEntityId().getId() + ", label=NULL)!");
            return null;
        }
        return value.getText();
    }
    
    private String getItemDescription(ItemDocument itemDocument) {
        MonolingualTextValue value = null;
        if (itemDocument.getLabels().size() > 1) {
            if (itemDocument.getDescriptions().containsKey(this.isoLanguageCode)) {
                value = itemDocument.getDescriptions().get(this.isoLanguageCode);
            } else { // if no descr (in configured language) is available, we take first available
                for (String key : itemDocument.getDescriptions().keySet()) {
                    value = itemDocument.getDescriptions().get(key);
                    break;
                }
            }
        }
        if (value == null) {
            log.fine("Could not find ANY description for item (" 
               + itemDocument.getEntityId().getId() + ", label=NULL)! --- Skippin Description");
            return null;
        }
        return value.getText();
    }
    
    /**
     * Creates a wikidata item placeholder topic through just setting the uri, which is 
     * needed to relate claims too this item before it occured in the dump.
     */
    private Topic createMinimalWikidataItem(String itemId) {
        // Topic item = getWikidataItemByEntityId(itemId); // should be just called if no item exists
        Topic item = null;
        try {
            TopicModel wikidataItemTopicModel = null;
            wikidataItemTopicModel = new TopicModel(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId,
                "org.deepamehta.wikidata.item");
            item = dms.createTopic(wikidataItemTopicModel);
            if (item != null) {
                // OK
                workspaceService.assignToWorkspace(item, wikidataWorkspace.getId());
                // log.info("CREATED minimal Wikidata Topic for item " + itemId + ":" + item.getSimpleValue());
            }
        } catch (Exception re) {
            log.log(Level.SEVERE, "ERROR: Could not create wikidata item topic", re);
        }
        return item;
    }

    private void updateOrCreateWikidataItem(String itemId, String name, String alias, String description, String language) {
        // check precondition
        if (language == null) {
            log.warning("Invalid Argument - values can just be stored with an accompanying iso language code!");
            throw new WebApplicationException(500);
        }
        String languageCodeValue = language.trim().toLowerCase();
        Topic languageCode = getLanguageIsoCodeTopicByValue(languageCodeValue);
        Topic item = getWikidataItemByEntityId(itemId);
        if (item == null) {
            createWikidataItem(itemId, name, alias, description, languageCode);
        } else {
            // ### if values for language code already exist, updates those otherwise
            // create and append values in new language code..
            ChildTopicsModel languagedValueModel = new ChildTopicsModel();
            //
            if (languageCode != null) {
                languagedValueModel.putRef("org.deepamehta.wikidata.language_code", languageCode.getId());
            } else {
                languagedValueModel.put("org.deepamehta.wikidata.language_code", languageCodeValue);
            }
            // ### experimental.
            updateWikidataItem(item, name, alias, description, languagedValueModel, languageCodeValue);
        }
    }


    private void createWikidataItem(String itemId, String name, String alias, String description, Topic lang) {
        try {
            TopicModel wikidataItemTopicModel = null;
            ChildTopicsModel wikidataEntityModel = new ChildTopicsModel();
            ChildTopicsModel languagedValueModel = new ChildTopicsModel();
            if (lang != null) {
                languagedValueModel.putRef("org.deepamehta.wikidata.language_code", lang.getId());
            } else {
                languagedValueModel.put("org.deepamehta.wikidata.language_code", lang);
            }
            if (name != null) {
                languagedValueModel.put("org.deepamehta.wikidata.label_value", name);
            }
            if (alias != null) {
                languagedValueModel.put("org.deepamehta.wikidata.alias_value", alias);
            }
            if (description != null) {
                languagedValueModel.put("org.deepamehta.wikidata.description_value", description);
            }
            if (name != null || alias != null || description != null) {
                wikidataEntityModel.add("org.deepamehta.wikidata.entity_value",
                        new TopicModel("org.deepamehta.wikidata.entity_value", languagedValueModel));
                wikidataItemTopicModel = new TopicModel(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId,
                        "org.deepamehta.wikidata.item", wikidataEntityModel);
            } else {
                wikidataItemTopicModel = new TopicModel(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId,
                        "org.deepamehta.wikidata.item");
            }
            Topic wikidataTopic = dms.createTopic(wikidataItemTopicModel);
            if (wikidataTopic != null) {
                // OK
                workspaceService.assignToWorkspace(wikidataTopic, wikidataWorkspace.getId());
            } else {
                log.warning(" Could not create Wikidata Topic for item " + itemId);
            }
        } catch (Exception re) {
            throw new RuntimeException("ERROR: Could not create wikidata item topic", re);
        }
    }

    private void updateWikidataItem (Topic item, String name, String alias, String description, ChildTopicsModel
            languageModel, String languageCode) {
        if (item.getChildTopics().getModel().has("org.deepamehta.wikidata.entity_value")) {
            List<RelatedTopicModel> existingValues;
            existingValues = item.getModel().getChildTopicsModel().getTopics("org.deepamehta.wikidata.entity_value");
            boolean existing = false;
            for (TopicModel el : existingValues) {
                String existingLanguageValueCode = el.getChildTopicsModel().getString("org.deepamehta.wikidata.language_code");
                if (existingLanguageValueCode.equals(languageCode)) {
                    existing = true;
                    // ## skip updating values during development
                    /** DeepaMehtaTransaction tx = dms.beginTx();
                     // log.info("Updating existing values for language " + language_code + " and item " + itemId);
                     if (name != null) {
                     el.getChildTopicsModel().put("org.deepamehta.wikidata.label_value", name);
                     }
                     if (alias != null) {
                     el.getChildTopicsModel().put("org.deepamehta.wikidata.alias_value", alias);
                     }
                     if (description != null) {
                     el.getChildTopicsModel().put("org.deepamehta.wikidata.description_value", description);
                     }
                     if (name != null || alias != null || description != null) {
                     TopicModel existingItem = item.getModel();
                     existingItem.getChildTopicsModel().add("org.deepamehta.wikidata.entity_value",
                     new TopicModel("org.deepamehta.wikidata.entity_value", el.getChildTopicsModel()));
                     dms.updateTopic(existingItem);
                     tx.success();
                     tx.finish();
                     break;
                     }**/
                }
            }
            if (!existing) {
                // log.info("Creating and appending values for language " + language_code + " and item " + itemId);
                if (name != null) {
                    languageModel.put("org.deepamehta.wikidata.label_value", name);
                }
                if (alias != null) {
                    languageModel.put("org.deepamehta.wikidata.alias_value", alias);
                }
                if (description != null) {
                    languageModel.put("org.deepamehta.wikidata.description_value", description);
                }
                if (name != null || alias != null || description != null) {
                    TopicModel existingItem = item.getModel();
                    existingItem.getChildTopicsModel().add("org.deepamehta.wikidata.entity_value",
                            new TopicModel("org.deepamehta.wikidata.entity_value", languageModel));
                    dms.updateTopic(existingItem);
                }
            }
        }
    }

    private void attachGeoCoordinates(double[] coordinates, String itemId) {
        Topic item = getWikidataItemByEntityId(itemId);
        if (item != null) { // Item is already in DB
                // ### fixme does not work, may be there is an error in the storage layer impl
                //     (during comparison of double values after wrapping them in SimpleValues?)
                // Topic coordinatesTopic = getGeoCoordinateTopicByValue(coordinates);
                // if (coordinatesTopic != null) { // geo coordinates for this item are already in DB (currently never)
                    // ### if so, lets leave them and not add otherones during development
                    // log.info(" > Adding ASSIGNMENT existing Geo Coordinates to item " + itemId + " to " + coordinates);
                    /** TopicModel updatedItem = item.getModel();
                    updatedItem.getChildTopicsModel().addRef("dm4.geomaps.geo_coordinate", coordinatesTopic.getId());
                    dms.updateTopic(updatedItem); **/
                    // tx.success();
                // } else {
                if (!item.getChildTopics().has("dm4.geomaps.geo_coordinate")) { // during dev we stick to one coordinate per item
                    try {
                        // log.info(" > Creating NEW Geo Coordinates for item " + itemId + " at " + coordinates);
                        ChildTopicsModel geoCoordinates = new ChildTopicsModel();
                        /** Topic el = getLatitudeTopicByValue(coordinates[1]);
                        if (el != null) {
                            geoCoordinates.putRef("dm4.geomaps.latitude", el.getId());
                        } else { **/
                            geoCoordinates.put("dm4.geomaps.latitude", coordinates[1]);
                        /** }
                        Topic elng = getLongitudeTopicByValue(coordinates[0]);
                        if (el != null) {
                            geoCoordinates.putRef("dm4.geomaps.longitude", elng.getId());
                        } else { **/
                            geoCoordinates.put("dm4.geomaps.longitude", coordinates[0]);
                        // }
                        TopicModel geoCoordinatesModel = new TopicModel("dm4.geomaps.geo_coordinate", geoCoordinates);
                        TopicModel updatedItem = item.getModel();
                        updatedItem.getChildTopicsModel().put("dm4.geomaps.geo_coordinate", geoCoordinatesModel);
                        dms.updateTopic(updatedItem);
                    } catch (Exception error) {
                        log.log(Level.SEVERE, "could not attach coordinates to item " + itemId, error);
                    }
                }
        } else { // no wikidata item found
            log.warning("No wikidata item " + itemId + " found for assigning geo-coordinates");
        }
    }

    // currently defused due to model change (geo-coordinates are one and composites of an item)
    /** private Topic getGeoCoordinateTopicByValue(double[] coordinates) {
        Topic latitudeTopic = dms.getTopic("dm4.geomaps.latitude", new SimpleValue(coordinates[1]));
        Topic longitudeTopic = dms.getTopic("dm4.geomaps.longitude", new SimpleValue(coordinates[0]));
        if (latitudeTopic != null && longitudeTopic != null) {
            RelatedTopic latitudeParent = latitudeTopic.getRelatedTopic("dm4.core.composition", "dm4.core.child",
                "dm4.core.parent", "dm4.geomaps.geo_coordinate");
            RelatedTopic longitudeParent = longitudeTopic.getRelatedTopic("dm4.core.composition", "dm4.core.child",
                "dm4.core.parent", "dm4.geomaps.geo_coordinate");
            if (latitudeParent.getId() == longitudeParent.getId()) {
                // if both values share the same parent, return the geo-coordinate topic value
                return latitudeParent;
            }
        }
        return null;
    } **/

    /**
     * Store timestamps for an edge qualifying it's lifetime in our DB.
     */
    private void storeQualifyingTimeProperties(Association claim, Statement s) {
        // 0) fetch claim edge via uri (yet not possible with dm4-core)
        /** String statementGUID = s.getStatementId();
        log.info("Try to fetch assoc by statementUID " + statementGUID + " .... " );
        Topic actuallyAssoc = dms.getTopic("uri", new Simp  leValue(statementGUID)); */
        // 1) store properties to claim edge
        List<SnakGroup> qualifierGroups = s.getClaim().getQualifiers();
        if (qualifierGroups.size() > 0) log.info("> Claim to qualify is " + claim.getUri() + " id: " + claim.getId());
        for (SnakGroup statement : qualifierGroups) {
            if (statement.getProperty().getId().contains(WikidataEntityMap.STARTED_AT)) {
                for (Snak snak : statement.getSnaks()) {
                    try {
                        Value snakValue = ((ValueSnak) snak).getValue(); // JacksonNoValueSnack or NoValueSnack will throw an exception
                        if (snakValue instanceof TimeValue || snakValue instanceof JacksonValueTime) {
                                TimeValue value = (TimeValue) snakValue;
                                Calendar calendar = new GregorianCalendar();
                                int year = (int) value.getYear();
                                int month = value.getMonth();
                                calendar.set(year, month, value.getDay(), value.getHour(), value.getMinute(), value.getSecond());
                                Date date = calendar.getTime();
                                if (date != null) {
                                    log.info(">> Statement ("+statement.getProperty().getId()+") has a qualified START_DATE: " + date.getTime() + " (Year: " + value.getYear() + ")");
                                    claim.setProperty(WIKIDATA_START_TIME_PROP, date.getTime(), true);
                                }
                        }
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Could not parse and convert TimeValue into a Date, due to a ", e);
                    }
                }
            } else if (statement.getProperty().getId().contains(WikidataEntityMap.ENDED_AT)) {
                for (Snak snak : statement.getSnaks()) {
                    try {
                        Value snakValue = ((ValueSnak) snak).getValue(); // JacksonNoValueSnack or NoValueSnack will throw an exception
                        if (snakValue instanceof TimeValue || snakValue instanceof JacksonValueTime) {
                            TimeValue value = (TimeValue) snakValue;
                            Calendar calendar = new GregorianCalendar();
                            int year = (int) value.getYear();
                            int month = value.getMonth();
                            calendar.set(year, month, value.getDay(), value.getHour(), value.getMinute(), value.getSecond());
                            Date date = calendar.getTime();
                            if (date != null) {
                                log.info(">> Statement ("+statement.getProperty().getId()+") has a qualified END_DATE: " + date.getTime() + " (Year: " + value.getYear() + ")");
                                claim.setProperty(WIKIDATA_END_TIME_PROP, date.getTime(), true);
                            }
                        }
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Could not parse and convert TimeValue into a Date, due to a ", e);
                    }
                }
            }
        }
    }

    /** ### see Ticket 804 private Topic getLatitudeTopicByValue(double value) {
        return dms.getTopic("dm4.geomaps.latitude", new SimpleValue(value));
    }

    private Topic getLongitudeTopicByValue(double value) {
        return dms.getTopic("dm4.geomaps.longitude", new SimpleValue(value));
    } **/

    private Topic getLanguageIsoCodeTopicByValue(String iso_code) {
        return dms.getTopic("org.deepamehta.wikidata.language_code", new SimpleValue(iso_code));
    }

    private Topic getWikidataTextTopic(String text_value) {
        if (text_value.contains("\"")) text_value = text_value.replaceAll("\"", "");
        return dms.getTopic("org.deepamehta.wikidata.text", new SimpleValue(text_value));
    }

    private Topic createWikidataTextTopic(String text_value) {
        if (text_value.contains("\"")) text_value = text_value.replaceAll("\"", "");
        Topic countryCode = dms.createTopic(new TopicModel("org.deepamehta.wikidata.text", new SimpleValue(text_value)));
        return countryCode;
    }

    /** private void addWebbrowserURLAsChildTopic(ChildTopicsModel composite, String itemId) {
        if (all_websites.containsKey(itemId)) {
             composite.add(DM_WEBBROWSER_URL,
                new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(all_websites.get(itemId))));
        }
    } **/
    
    /** private void createRelatedURLTopic(Topic topic, String url) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            TopicModel urlmodel = new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(url));
            Topic website = dms.createTopic(urlmodel);
            if (website != null && topic != null) {
                dms.createAssociation(new AssociationModel("dm4.core.association",
                    new TopicRoleModel(topic.getId(), "dm4.core.parent"),
                    new TopicRoleModel(website.getId(), "dm4.core.child")));
                workspaceService.assignToWorkspace(website, wikidataWorkspace.getId());
            }
            tx.success();
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            tx.failure();
        } finally {
            tx.finish();
        }
    } */
    
    /** Creates a (non-hierarchical) wikidata claim edge (default), due to timestamps bubbling up **parents**. */
    private Association createWikidataClaimEdge (String fromItemId, String toItemId, String statementGUID, PropertyIdValue propertyEntityId) {
        Association relation = null;
        Topic fromPlayer = getWikidataItemByEntityId(fromItemId);
        String relationType = "org.deepamehta.wikidata.claim_edge";
        if (fromPlayer == null) {
            log.fine("Could not find Wikidata Item topic to related country code too! - creating minimal wikidata item");
            fromPlayer = createMinimalWikidataItem(fromItemId);
        }
        // get or do create (minimal) wikidata item topic
        Topic wikidataItemTopic = getWikidataItemByEntityId(toItemId);
        if (wikidataItemTopic == null) {
            wikidataItemTopic = createMinimalWikidataItem(toItemId);
        }
        if (!associationAlreadyExists(fromPlayer.getId(), wikidataItemTopic.getId(), relationType)) {
            Topic propertyEntityTopic = getWikidataItemByPropertyId(propertyEntityId.getIri());
            ChildTopicsModel assocModel = null;
            if (propertyEntityTopic == null) { // do create new property entity topic
                assocModel = new ChildTopicsModel()
                    .put("org.deepamehta.wikidata.property", new TopicModel(
                            propertyEntityId.getIri(), "org.deepamehta.wikidata.property"));
            } else {
                assocModel = new ChildTopicsModel()
                    .putRef("org.deepamehta.wikidata.property", propertyEntityTopic.getId());
            }
            try {
                relation = dms.createAssociation(new AssociationModel(relationType,
                        new TopicRoleModel(fromPlayer.getId(), "dm4.core.default"),
                        new TopicRoleModel(wikidataItemTopic.getId(), "dm4.core.default"), assocModel));
                if (relation != null) {
                    relation.setUri(statementGUID);
                    log.fine("Created new \""+relationType+"\" relationship for " + fromPlayer.getUri()+
                            " to " + wikidataItemTopic.getUri()+ " (" + wikidataItemTopic.getSimpleValue() + ") with Prop: "+propertyEntityId+" GUID: \""
                            + relation.getUri() + "\"");
                    workspaceService.assignToWorkspace(relation, wikidataWorkspace.getId());
                    // relation.setSimpleValue(relationName);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
            }
        } // wikidata claim edge relation (with default, default) ### already exists
        return relation;
    }

    private void createWikidataTextClaim (String textValue, String relationType, String forItemId, String statementGUID) {
        Association relation = null;
        Topic fromPlayer = getWikidataItemByEntityId(forItemId);
        if (fromPlayer == null) {
            log.warning("Could not find Wikidata Item topic to related country code too! - creating minimal wikidata item");
            fromPlayer = createMinimalWikidataItem(forItemId);
        }
        // 
        Topic textTopic = getWikidataTextTopic(textValue);
        if (textTopic == null) { // do create new country code topic
            textTopic = createWikidataTextTopic(textValue);
        }
        if (!hierarchicalAssociationAlreadyExists(fromPlayer.getId(), textTopic.getId(), relationType)) {
            try {
                relation = dms.createAssociation(new AssociationModel(relationType,
                        new TopicRoleModel(fromPlayer.getId(), "dm4.core.parent"),
                        new TopicRoleModel(textTopic.getId(), "dm4.core.child")));
                if (relation != null) {
                    relation.setUri(statementGUID);
                    log.fine("Created new \""+relationType+"\" relationship for " + fromPlayer.getUri()+
                            " to " + textTopic.getSimpleValue() + ") with \"" + relationType + "\" - GUID: \""
                            + relation.getUri() + "\"");
                    workspaceService.assignToWorkspace(relation, wikidataWorkspace.getId());
                    // relation.setSimpleValue(relationName);
                }

            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
            }
        } // wikidata text claim (with text as child forItemId arleady exists)
    }

    private boolean associationAlreadyExists (long playerOne, long playerTwo, String assocTypeUri) {
        Association assoc = dms.getAssociation(assocTypeUri, playerOne, playerTwo, "dm4.core.default", "dm4.core.default");
        if (assoc == null) return false;
        return true;
    }
    
    private boolean hierarchicalAssociationAlreadyExists (long parentId, long childId, String assocTypeUri) {
        Association assoc = dms.getAssociation(assocTypeUri, parentId, childId, "dm4.core.parent", "dm4.core.child");
        if (assoc == null) return false;
        return true;
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        // count and check time 
        countEntity();
    }

    /**
     * Prints a report about the statistics gathered so far.
     */
    private void printProcessingStatus() {
        log.info("Processed " + this.entityCount + " items from the wikidata json-dump.");
    }

    /**
     * Counts one entity. Every once in a while, the current time is checked so
     * as to print an intermediate report roughly every ten seconds.
     */
    private void countEntity() {
       if (!this.timer.isRunning()) {
           startTimer();
       }
       this.entityCount++;
       if (this.entityCount % 100 == 0) {
           timer.stop();
           int seconds = (int) (timer.getTotalWallTime() / 1000000000);
           if (seconds >= this.lastSeconds + 10) {
               this.lastSeconds = seconds;
               printProcessingStatus();
               if (this.timeout > 0 && seconds > this.timeout) {
                   log.info("Timeout. Aborting processing.");
                   throw new TimeoutException();
               }
           }
           timer.start();
       }
    }

    /**
     * Stops the processing and prints the final time.
     */
    public void stop() {
        if (tx != null) {
            tx.success();
            tx.finish();
        }
        printProcessingStatus();
        log.info("Wikidata Timestamps Start: "+importStartedAt.toGMTString()+" Stop:" + new Date().toGMTString());
        log.info("Finished importing.");
        this.timer.stop();
        this.lastSeconds = (int) (timer.getTotalWallTime() / 1000000000);
    }

    private void startTimer() {
        log.info("Starting processing ("+timeout+" sec) wikidata JSON dump.");
        this.timer.start();
    }

    public class TimeoutException extends RuntimeException {

        private static final long serialVersionUID = -1083533602730765194L;

    }

}