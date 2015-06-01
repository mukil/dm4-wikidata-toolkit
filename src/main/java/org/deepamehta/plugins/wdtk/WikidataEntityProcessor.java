package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.Association;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.util.Timer;

/**
 * @author Malte Rei&szlig;ig <malte@mikromedia.de>
 *
 * A simple class that processes EntityDocuments to identify various classes of items in the
 * wikidatawiki. Based on the EntityTimerProcessor from the WDTK examples written by Markus Kroetzsch.
 * Thanks for sharing. Honorable mentions go to jri for telling me about the 
 * ImportPackage bundle notations which helped me to run the WDTK within our OSGi.
 */
public class WikidataEntityProcessor implements EntityDocumentProcessor {
    
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
    private final String DM_NOTE_TITLE          = "dm4.notes.title";
    private final String DM_NOTE_DESCR          = "dm4.notes.text";
    private final String DM_NOTE                = "dm4.notes.note";

    private final String WS_WIKIDATA_URI = "org.deepamehta.workspaces.wikidata";

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
    boolean storeWebsiteAddresses = false;

    // setting: language default value
    String isoLanguageCode = WikidataEntityMap.LANG_EN;

    DeepaMehtaService dms;
    WorkspacesService workspaceService;
    Topic wikidataWorkspace = null;

    public WikidataEntityProcessor (DeepaMehtaService dms, WorkspacesService workspaceService, int timeout,
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
        this.storeWebsiteAddresses = urls;
        this.storeDescription = descriptions;
        if (iso_lang != null) this.isoLanguageCode = iso_lang;
        wikidataWorkspace = workspaceService.getWorkspace(WS_WIKIDATA_URI);
        log.info("Set up to import wikidata topics into workspace \"" + wikidataWorkspace.getSimpleValue() + "\"");
    }

    // globally collect some label for every item processed.. (note: in memory!)
    HashMap<String, String> itemsFirstLabel = new HashMap<String, String>();
    // collecting some more specific text values on each item (note: in memory!)
    HashMap<String, String> itemsDeathDate = new HashMap<String, String>();
    HashMap<String, String> itemsBirthDate = new HashMap<String, String>();
    HashMap<String, String> itemsGivenname = new HashMap<String, String>();
    HashMap<String, String> itemsSurname = new HashMap<String, String>();
    HashMap<String, String> itemsFirstDescription = new HashMap<String, String>();
    // 
    HashMap<String, String> all_persons = new HashMap<String, String>();
    HashMap<String, String> all_institutions = new HashMap<String, String>();
    HashMap<String, String> all_cities = new HashMap<String, String>();
    HashMap<String, String> all_countries = new HashMap<String, String>();
    HashMap<String, String> all_websites = new HashMap<String, String>();
    HashMap<String, double[]> all_coordinates = new HashMap<String, double[]>();
    /** ### HashMap<String, String> all_herbs = new HashMap<String, String>();
    HashMap<String, String> all_vegetables = new HashMap<String, String>();
    HashMap<String, String> all_edible_fruits = new HashMap<String, String>();
    HashMap<String, String> all_edible_fungis = new HashMap<String, String>(); **/

    HashMap<String, HashMap<String, String>> employeeOf = new HashMap<String, HashMap<String, String>>();
    HashMap<String, HashMap<String, String>> citizenOf = new HashMap<String, HashMap<String, String>>();
    HashMap<String, HashMap<String, String>> affiliatedWith = new HashMap<String, HashMap<String, String>>();
    HashMap<String, HashMap<String, String>> studentOf = new HashMap<String, HashMap<String, String>>();
    HashMap<String, HashMap<String, String>> mentorOf = new HashMap<String, HashMap<String, String>>();

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        countEntity();
        
        // 0) Get label and description of current item
        String itemId = itemDocument.getEntityId().getId();
        String label = getItemLabel(itemDocument);
        String description = getItemDescription(itemDocument);
        if (label != null && !label.isEmpty()) itemsFirstLabel.put(itemId, label);
        if (description != null && !description.isEmpty() && storeDescription) {
            itemsFirstDescription.put(itemId, description);
        }

        // 1) Iterate over items statement groups

        if (itemDocument.getStatementGroups().size() > 0) {
            
            for (StatementGroup sg : itemDocument.getStatementGroups()) {
                                
                // -- Inspect with which type of StatementGroup (resp. Property) we deal here 
                
                boolean isInstanceOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_INSTANCE_OF);
                boolean isSubclassOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SUBCLASS_OF);
                boolean isCoordinateOf = sg.getProperty().getId().equals(WikidataEntityMap.GEO_COORDINATES);
                boolean isGivenNameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_GIVEN_NAME_OF);
                boolean isSurnameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SURNAME_OF);
                boolean isBirthDateOf = sg.getProperty().getId().equals(WikidataEntityMap.WAS_BORN_ON);
                boolean isDeathDateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DEAD_SINCE);
                boolean isWebsiteOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIAL_WEBSITE_OF);
                
                // 
                boolean isMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_MEMBER_OF);
                boolean isCitizenOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_CITIZEN_OF);
                boolean isEmployeeOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_EMPLOYEE_OF);
                boolean isPartyMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PARTY_MEMBER_OF);
                boolean isOfficiallyResidingAt = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIALLY_RESIDING_AT);
                // boolean isAlmaMaterOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_ALMA_MATER_OF);
                boolean isStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_STUDENT_OF_PERSON);
                boolean isDoctoralAdvisorOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_ADVISOR_OF);
                boolean isDoctoralStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_STUDENT_OF);
                boolean isAffiliatedWith = sg.getProperty().getId().equals(WikidataEntityMap.IS_AFFILIATED_WITH);

                /** boolean isPseudonymOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PSEUDONYM_OF);
                boolean isOpenResearchIdOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_OPEN_RESEARCH_ID_OF);
                boolean isNotableWorkOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_NOTABLE_WORK_OF); **/
                /** if (isSubclassOf && classRecord == null) {
                    // logger.info("Has SubclassOf Relationship.. and NO classRecord");
                    classRecord = getClassRecord(itemDocument, itemDocument.getItemId());
                } **/
                
                // 1.1) Record various "attributes" of the current item

                // -- is instance | subclass of

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
                                        if (doPersons && !all_persons.containsKey(itemId)) all_persons.put(itemId, label);

                                    // 2.2 current wikidata item is direct instanceOf|subclassOf "university", "company" or "organisation"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COMPANY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.UNIVERSITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.ORGANISATION_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.COLLEGIATE_UNIVERSITY_ITEM)) { // = often subclass of "university" items
                                        if (doInstitutions && !all_institutions.containsKey(itemId)) all_institutions.put(itemId, label);

                                    // 2.3 current wikidata item is direct instanceOf|subclassOf "city", "metro" or "capital"
                                    } else if (referencedItemId.equals(WikidataEntityMap.CITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.METROPOLIS_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.CAPITAL_CITY_ITEM)) {
                                        if (doCities && !all_cities.containsKey(itemId)) all_cities.put(itemId, label);

                                    // 2.4 current wikidata item is direct instanceOf|subclassOf "country" or "sovereing state"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COUNTRY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.SOVEREIGN_STATE_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.STATE_ITEM)) {
                                        if (doCountries && !all_countries.containsKey(itemId)) all_countries.put(itemId, label);

                                    // 2.5 current wikidata item is direct instanceOf|subclassOf "vegetable"
                                    /*** } else if (referencedItemId.equals(WikidataEntityMap.VEGETABLE)) {
                                        if (!all_vegetables.containsKey(itemId)) all_vegetables.put(itemId, label);

                                    // 2.6 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.HERB)) {
                                        if (!all_herbs.containsKey(itemId)) all_herbs.put(itemId, label);

                                    // 2.7 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.FOOD_FRUIT)) {
                                        if (!all_edible_fruits.containsKey(itemId)) all_edible_fruits.put(itemId, label);

                                    // 2.8 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.FUNGI)) {
                                        if (!all_edible_fungis.containsKey(itemId)) all_edible_fungis.put(itemId, label); **/
                                    }

                                }
                            }

                        }
                    }

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
                                log.fine("### NEW: Parsed geo-coordinates " + coordinateValues.getLatitude()
                                        + ", " + coordinateValues.getLongitude());
                                if (longitude != -1 && latitude != -1) {
                                    double coordinates[] = {longitude, latitude};
                                    if (!all_coordinates.containsKey(itemId)) {
                                        all_coordinates.put(itemId, coordinates);
                                    }
                                }
                            }
                        }
                    }

                } else if (isBirthDateOf || isDeathDateOf) {
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
                    }
                
                } else if (isWebsiteOf && this.storeWebsiteAddresses) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof StringValue) {
                                StringValue urlValue = (StringValue) mainSnakValue;
                                if ((urlValue.getString() != null && !urlValue.getString().isEmpty())) {
                                    all_websites.put(itemId, urlValue.getString());
                                }
                            }
                        }
                    }

                // 1.2) Record various relations of current item to other items

                } else if (isEmployeeOf) {
                    // some professional person to organisation relationship
                    for (Statement s : sg.getStatements()) {
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
                                    employeeOf.put(itemDocument.getItemId().getId(), claim);
                                } /** else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) worksAt.put(itemDocument.getItemId().getId(), relatedLabel);
                                } **/
                            }
                        }
                    }

                } else if (isMemberOf || isPartyMemberOf || isAffiliatedWith) {
                    // some person to institution / person? relationship
                    for (Statement s : sg.getStatements()) {
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
                                    affiliatedWith.put(itemDocument.getItemId().getId(), claim);
                                } /* else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) affiliatedWith.put(itemDocument.getItemId().getId(), relatedLabel);
                                } **/
                            }
                        }
                    }

                } else if (isCitizenOf || isOfficiallyResidingAt) {
                    // some person to city/country relationship
                    for (Statement s : sg.getStatements()) {
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
                                    citizenOf.put(itemDocument.getItemId().getId(), claim);
                                } /** else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) livesAt.put(itemDocument.getItemId().getId(), relatedLabel);
                                } */
                            }
                        }
                    }

                } else if (isDoctoralStudentOf || isStudentOf) {
                    // some personal relationship
                    for (Statement s : sg.getStatements()) {
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
                                    studentOf.put(itemDocument.getItemId().getId(), claim);
                                } /** else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) studentOf.put(itemDocument.getItemId().getId(), relatedLabel);
                                } */
                            }
                        }
                    }

                } else if (isDoctoralAdvisorOf) {
                    // some personal relationship
                    for (Statement s : sg.getStatements()) {
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
                                    mentorOf.put(itemDocument.getItemId().getId(), claim);
                                }/**  else {
                                    // check on all currently to be imported wikidata items (in memory)
                                    String relatedLabel = getItemLabelByEntityId(nameId);
                                    if (relatedLabel != null) mentorOf.put(itemDocument.getItemId().getId(), relatedLabel);
                                } **/
                            }
                        }
                    }
                }

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

    private Topic getWikidataItemByEntityId (String id) {
        return dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + id));
    }

    /** private String getItemLabelByEntityId (EntityIdValue id) {
        if (all_persons.containsKey(id.getId())) {
            return all_persons.get(id.getId());
        }
        if (all_institutions.containsKey(id.getId())) {
            return all_institutions.get(id.getId());
        }
        if (all_cities.containsKey(id.getId())) {
            return all_persons.get(id.getId());
        }
        if (all_countries.containsKey(id.getId())) {
            return all_persons.get(id.getId());
        }
        return null;
    } **/

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
    
    private Topic createPersonTopic(String firstName, String lastName, String itemId) {
        Topic person = null;
        if (!alreadyExists(itemId)) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                ChildTopicsModel personComposite = new ChildTopicsModel().put(DM_PERSON_NAME, new ChildTopicsModel()
                    .put(DM_PERSON_FIRST_NAME, firstName).put(DM_PERSON_LAST_NAME, lastName)
                );
                // add "official website" to person if available
                addWebbrowserURLAsChildTopic(personComposite, itemId);
                // add item description to person
                String description = itemsFirstDescription.get(itemId);
                description = (description != null) ? "<p>"+description+"</p>" : "";
                addWikidataItemDescription(itemId, description, personComposite);
                // build up model
                TopicModel personModel = new TopicModel(
                    WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_PERSON, personComposite);
                person = dms.createTopic(personModel);
                workspaceService.assignToWorkspace(person, wikidataWorkspace.getId());
                tx.success();
            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                tx.failure();
            } finally {
                tx.finish();
            }
        }
        return person;
    }
    
    private Topic createInstitutionTopic(String name, String itemId) {
        Topic institution = null;
        if (!alreadyExists(itemId)) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                ChildTopicsModel institutionComposite = new ChildTopicsModel();
                institutionComposite.put(DM_INSTITUTION_NAME, name);
                // add "official website" to institution if available
                addWebbrowserURLAsChildTopic(institutionComposite, itemId);
                // add item description to institution
                String description = itemsFirstDescription.get(itemId);
                description = (description != null) ? "<p>"+description+"</p>" : "";
                addWikidataItemDescription(itemId, description, institutionComposite);
                // build up model
                TopicModel institutionModel = new TopicModel(
                    WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_INSTITUTION, institutionComposite);
                // ### set GeoCoordinate Facet via values in all_coordinates
                institution = dms.createTopic(institutionModel);
                workspaceService.assignToWorkspace(institution, wikidataWorkspace.getId());
                tx.success();
            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
                tx.failure();
            } finally {
                tx.finish();
            }
        }
        return institution;
    }
    
    private void addWebbrowserURLAsChildTopic(ChildTopicsModel composite, String itemId) {
        if (all_websites.containsKey(itemId)) {
            composite.add(DM_WEBBROWSER_URL,
                new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(all_websites.get(itemId))));
        }
    }

    private Topic createCityTopic(String name, String itemId) {
        Topic city = null;
        if (!alreadyExists(itemId)) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                TopicModel cityModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_CITY, new SimpleValue(name));
                // ### set GeoCoordinate Facet via values in all_coordinates
                city = dms.createTopic(cityModel);
                workspaceService.assignToWorkspace(city, wikidataWorkspace.getId());
                tx.success();
            } catch (Exception re) {
                tx.failure();
                log.log(Level.SEVERE, "could not create city topic", re);
            } finally {
                tx.finish();
            }
        }
        return city;
    }
    
    private Topic createCountryTopic(String name, String itemId) {
        Topic country = null;
        if (!alreadyExists(itemId)) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                TopicModel countryModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_COUNTRY, new SimpleValue(name));
                // ### set GeoCoordinate Facet via values in all_coordinates
                country = dms.createTopic(countryModel);
                workspaceService.assignToWorkspace(country, wikidataWorkspace.getId());
                tx.success();
            } catch (Exception re) {
                tx.failure();
                log.log(Level.SEVERE, "could not create city topic", re);
            } finally {
                tx.finish();
            }
        }
        return country;
    }
    
    private ChildTopicsModel addWikidataItemDescription(String itemId, String desc, ChildTopicsModel comp) {
        comp.put(DM_CONTACT_NOTE, desc + "<p class=\"wd-item-footer\">"
                + "For more infos visit this items "
                + "<a href=\"http://www.wikidata.org./entity/" + itemId 
                + "\" target=\"_blank\" title=\"Wikidata page for this item\">"
            + "page</a> on wikidata.org.</p>");
        return comp;
    }
    
    private void createRelatedURLTopic(Topic topic, String url) {
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
    }

    private void createItemRelations (HashMap<String, HashMap<String, String>> relations, String relationName, String relationType) {
        log.info(" ... " + relations.size() + " " +relationName+ " associations");
        // Key: Wikidata Entity/Item ID, Value: Set<TopicId, StatementUID>
        for (String itemId : relations.keySet()) {
            HashMap<String, String> referenceMap = relations.get(itemId);
            Association relation = null;
            Topic toPlayer = null, fromPlayer = null;
            // Key: Topic ID, Value: Statemend-UID
            for (String relatedEntityId : referenceMap.keySet()) {
                String statementValueCode = referenceMap.get(relatedEntityId);
                String statementGUID = statementValueCode.split(WikidataEntityMap.URI_SEPERATOR)[0];
                String propertyEntityId = statementValueCode.split(WikidataEntityMap.URI_SEPERATOR)[1];
                if (relatedEntityId.startsWith("T")) {
                    toPlayer = dms.getTopic(Long.parseLong(relatedEntityId.substring(1)));
                    fromPlayer = dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId));
                }
                if (fromPlayer != null && toPlayer != null &&
                    !associationAlreadyExists(fromPlayer.getId(), toPlayer.getId(), relationType)) {
                    Topic propertyEntity = getWikidataItemByEntityId(propertyEntityId);
                    ChildTopicsModel assocModel = null;
                    if (propertyEntity == null) { // do create new property entity topic
                        assocModel = new ChildTopicsModel()
                            .add("org.deepamehta.wikidata.property", new TopicModel(
                                    WikidataEntityMap.WD_ENTITY_BASE_URI + propertyEntityId,
                                    "org.deepamehta.wikidata.property"));
                    } else {
                        assocModel = new ChildTopicsModel()
                            .addRef("org.deepamehta.wikidata.property", propertyEntity.getId());
                    }
                    DeepaMehtaTransaction tx = dms.beginTx();
                    try {
                        relation = dms.createAssociation(new AssociationModel(relationType,
                                new TopicRoleModel(fromPlayer.getId(), "dm4.core.parent"),
                                new TopicRoleModel(toPlayer.getId(), "dm4.core.child"), assocModel));
                        relation.setUri(statementGUID);
                        if (relation != null) {
                            log.info("Created new \""+relationType+"\" relationship for " + itemId +
                                    " to " + toPlayer.getId() + " (" + toPlayer.getSimpleValue() + ") with GUID: \""
                                    + relation.getUri() + "\" and propertyEntityID: \"" + propertyEntityId +"\"");
                            workspaceService.assignToWorkspace(relation, wikidataWorkspace.getId());
                        }
                        // ### relation.setSimpleValue(relationName);
                        tx.success();
                    } catch (Exception e) {
                        log.log(Level.SEVERE, e.getMessage(), e);
                        tx.failure();
                    } finally {
                        tx.finish();
                    }
                }
            }
        }
    }
    
    private boolean alreadyExists (String wikidataItemId) {
        String uri = WikidataEntityMap.WD_ENTITY_BASE_URI + wikidataItemId;
        Topic entity = dms.getTopic("uri", new SimpleValue(uri));
        if (entity == null) return false;
        return true;
    }

    private boolean associationAlreadyExists (long playerOne, long playerTwo, String assocTypeUri) {
        Association assoc = dms.getAssociation(assocTypeUri, playerOne, playerTwo, "dm4.core.default", "dm4.core.default");
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
        log.info("Identified " + this.all_persons.size() + " human beings"
            + ", " + this.all_institutions.size() + " institutions, "
            + this.all_cities.size() + " cities and " 
            + this.all_countries.size() + " countries.");
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
        
        printProcessingStatus();
        
        log.info("### Start creating Topics ...");
        
        // Matching the entities to the types of dm4-standard distro ..

        log.info(" ... " + all_cities.size() + " cities");
        for (String itemId : all_cities.keySet()) {
            String cityName = itemsFirstLabel.get(itemId);
            Topic city = null;
            if (cityName != null) {
                city = createCityTopic(cityName, itemId);
                if (all_websites.containsKey(itemId)) {
                    createRelatedURLTopic(city, all_websites.get(itemId));
                }
            }
        }

        log.info(" ... " + all_countries.size() + " countries");
        for (String itemId : all_countries.keySet()) {
            String countryName = itemsFirstLabel.get(itemId);
            Topic country;
            if (countryName != null) {
                country = createCountryTopic(countryName, itemId);
                if (all_websites.containsKey(itemId)) {
                    createRelatedURLTopic(country, all_websites.get(itemId));
                }
            }
        }
        
        log.info(" ... " + all_institutions.size() + " institutions");
        for (String itemId : all_institutions.keySet()) {
            String instName = itemsFirstLabel.get(itemId);
            if (instName != null) {
                createInstitutionTopic(instName, itemId);
            }
        }
        
        log.info(" ... " + all_persons.size() + " persons");
        for (String itemId : all_persons.keySet()) { // this might work but only after having read in the complete dump
            String fullName = itemsFirstLabel.get(itemId); // ### use all_institutions
            if (fullName != null) {
                String firstName = fullName.split(" ")[0]; // ### import full name
                String lastName = fullName.split(" ")[fullName.split(" ").length-1];
                createPersonTopic(firstName, lastName, itemId);
            } else {
                log.warning("Person Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        // --- Import relations between entities

        if (employeeOf.isEmpty() && citizenOf.isEmpty() && affiliatedWith.isEmpty()
                && studentOf.isEmpty() && mentorOf.isEmpty()) {
            log.info("Skipping the import of relations, this works just with (and among) already imported items.");
        } else {
            log.info("### Start creating associations.");
            createItemRelations(employeeOf, "employee of", "org.deepamehta.wikidata.employee_of");
            createItemRelations(citizenOf, "citizen of", "org.deepamehta.wikidata.citizen_of");
            createItemRelations(affiliatedWith, "affiliated with", "org.deepamehta.wikidata.affiliated_with");
            createItemRelations(studentOf, "student of", "org.deepamehta.wikidata.student_of");
            createItemRelations(mentorOf, "mentor of", "org.deepamehta.wikidata.mentor_of");
        }

        ResultList<RelatedTopic> personas = dms.getTopics("dm4.contacts.person", 0);
        ResultList<RelatedTopic> institutions = dms.getTopics("dm4.contacts.institution", 0);
        ResultList<RelatedTopic> cities = dms.getTopics("dm4.contacts.city", 0);
        ResultList<RelatedTopic> countries = dms.getTopics("dm4.contacts.country", 0);
        int numberOfAssocs = employeeOf.size() + citizenOf.size() + affiliatedWith.size() + studentOf.size() + mentorOf.size();
        if (personas != null && institutions != null && cities != null && countries != null) {
            log.info("DeepaMehta now recognizes " + this.all_persons.size() + " human beings"
                + ", " + this.all_institutions.size() + " institutions, "
                + this.all_cities.size() + " cities, " 
                + this.all_countries.size() + " countries by name.\n"
                + "Additionally DeepaMehta recorded " + numberOfAssocs + " associations among these items.");
        }
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