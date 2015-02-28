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
import java.util.HashMap;
import java.util.logging.Logger;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
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
    WikidataToolkitPlugin wdSearch;

    public WikidataEntityProcessor (DeepaMehtaService dms, WikidataToolkitPlugin wd, int timeout,
        boolean persons, boolean institutions, boolean cities, boolean countries, boolean descriptions, 
        boolean urls, boolean coordinates, String iso_lang) {
        this.timeout = timeout;
        this.dms = dms;
        this.wdSearch = wd;
        this.doCountries = countries;
        this.doCities = cities;
        this.doInstitutions = institutions;
        this.doPersons = persons;
        this.storeGeoCoordinates = coordinates;
        this.storeWebsiteAddresses = urls;
        this.storeDescription = descriptions;
        if (iso_lang != null) this.isoLanguageCode = iso_lang;
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
    HashMap<String, String> all_herbs = new HashMap<String, String>();
    HashMap<String, String> all_vegetables = new HashMap<String, String>();
    HashMap<String, String> all_edible_fruits = new HashMap<String, String>();
    HashMap<String, String> all_edible_fungis = new HashMap<String, String>();
    HashMap<String, double[]> all_coordinates = new HashMap<String, double[]>();

    HashMap<String, String> worksAt = new HashMap<String, String>();
    HashMap<String, String> livesAt = new HashMap<String, String>();
    HashMap<String, String> affiliatedWith = new HashMap<String, String>();
    HashMap<String, String> studentOf = new HashMap<String, String>();
    HashMap<String, String> mentorOf = new HashMap<String, String>();

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        countEntity();
        
        // 0) Get label and description of current item
        String itemId = itemDocument.getEntityId().getId();
        String label = getFirstLabel(itemDocument);
        String description = getFirstDescription(itemDocument);
        // .. so we memorize any non empty label or description for all (potential) items
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
                boolean isCoordinateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_COORDINATE_LOCATION);
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
                                        || referencedItemId.equals(WikidataEntityMap.SOVEREIGN_STATE_ITEM)) {
                                        if (doCountries && !all_countries.containsKey(itemId)) all_countries.put(itemId, label);

                                    // 2.5 current wikidata item is direct instanceOf|subclassOf "vegetable"
                                    } else if (referencedItemId.equals(WikidataEntityMap.VEGETABLE)) {
                                        if (!all_vegetables.containsKey(itemId)) all_vegetables.put(itemId, label);

                                    // 2.6 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.HERB)) {
                                        if (!all_herbs.containsKey(itemId)) all_herbs.put(itemId, label);

                                    // 2.7 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.FOOD_FRUIT)) {
                                        if (!all_edible_fruits.containsKey(itemId)) all_edible_fruits.put(itemId, label);

                                    // 2.8 current wikidata item is direct instanceOf|subclassOf "herb"
                                    } else if (referencedItemId.equals(WikidataEntityMap.FUNGI)) {
                                        if (!all_edible_fungis.containsKey(itemId)) all_edible_fungis.put(itemId, label);
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
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    worksAt.put(itemDocument.getItemId().getId(), "T" + entity.getId());
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
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    affiliatedWith.put(itemDocument.getItemId().getId(), "T" + entity.getId());
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
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    livesAt.put(itemDocument.getItemId().getId(), "T" + entity.getId());
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
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    studentOf.put(itemDocument.getItemId().getId(), "T" + entity.getId());
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
                                // check on all already imported wikidata items
                                Topic entity = getWikidataItemByEntityId(nameId);
                                if (entity != null) {
                                    mentorOf.put(itemDocument.getItemId().getId(), "T" + entity.getId());
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

    private String getItemLabelByEntityId (EntityIdValue id) {
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
    }

    private String getFirstLabel(ItemDocument itemDocument) {
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
    
    private String getFirstDescription(ItemDocument itemDocument) {
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
        }
        return person;
    }
    
    private Topic createInstitutionTopic(String name, String itemId) {
        Topic institution = null;
        if (!alreadyExists(itemId)) {
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
            TopicModel cityModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_CITY, new SimpleValue(name));
            // ### set GeoCoordinate Facet via values in all_coordinates
            city = dms.createTopic(cityModel);
        }
        return city;
    }
    
    private Topic createNoteTopic(String name, String itemId) {
        Topic city = null;
        if (!alreadyExists(itemId)) {
            ChildTopicsModel institutionComposite = new ChildTopicsModel();
            institutionComposite.put(DM_NOTE_TITLE, name);
            String descr = "";
            if (itemsFirstDescription.get(itemId) != null) {
                descr = "<p>"+itemsFirstDescription.get(itemId)+"</p>";
                institutionComposite.put(DM_NOTE_DESCR, descr);
            }
            TopicModel cityModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_NOTE, institutionComposite);
            // ### set GeoCoordinate Facet via values in all_coordinates
            city = dms.createTopic(cityModel);
        }
        return city;
    }
    
    private Topic createCountryTopic(String name, String itemId) {
        Topic country = null;
        if (!alreadyExists(itemId)) {
            TopicModel countryModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_COUNTRY, new SimpleValue(name));
            // ### set GeoCoordinate Facet via values in all_coordinates
            country = dms.createTopic(countryModel);
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
    
    private void createRelatedURLTopic(Topic topic, String itemId) {
        if (all_websites.containsKey(itemId)) {
            TopicModel url = new TopicModel(DM_WEBBROWSER_URL, 
                new SimpleValue(all_websites.get(itemId)));
            Topic website = dms.createTopic(url);
            if (website != null && topic != null) {
                dms.createAssociation(new AssociationModel("dm4.core.association",
                    new TopicRoleModel(topic.getId(), "dm4.core.default"), 
                    new TopicRoleModel(website.getId(), "dm4.core.default")));
            }
        }
    }
    
    private boolean alreadyExists (String wikidataItemId) {
        String uri = WikidataEntityMap.WD_ENTITY_BASE_URI + wikidataItemId;
        Topic entity = dms.getTopic("uri", new SimpleValue(uri));
        if (entity == null) return false;
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
        
        log.info("Start creating Topics ...");
        
        // Matching the entities to the types of dm4-standard distro ..

        log.info(" ... " + all_cities.size() + " cities");
        for (String itemId : all_cities.keySet()) {
            String cityName = itemsFirstLabel.get(itemId);
            Topic city = null;
            if (cityName != null) {
                city = createCityTopic(cityName, itemId);
                createRelatedURLTopic(city, itemId);
                wdSearch.assignToWikidataWorkspace(city);
            }
        }

        log.info(" ... " + all_countries.size() + " countries");
        for (String itemId : all_countries.keySet()) {
            String countryName = itemsFirstLabel.get(itemId);
            Topic country;
            if (countryName != null) {
                country = createCountryTopic(countryName, itemId);
                createRelatedURLTopic(country, itemId);
                wdSearch.assignToWikidataWorkspace(country);
            }
        }
        
        log.info(" ... " + all_institutions.size() + " institutions");
        for (String itemId : all_institutions.keySet()) {
            String instName = itemsFirstLabel.get(itemId);
            Topic institution;
            if (instName != null) {
                institution = createInstitutionTopic(instName, itemId);
                // ### wdSearch.assignToWikidataWorkspace(institution);
            }
        }
        
        log.info(" ... " + all_persons.size() + " persons");
        for (String itemId : all_persons.keySet()) { // this might work but only after having read in the complete dump
            String fullName = itemsFirstLabel.get(itemId); // ### use all_institutions
            Topic person;
            if (fullName != null) {
                String firstName = fullName.split(" ")[0]; // ### import full name
                String lastName = fullName.split(" ")[fullName.split(" ").length-1];
                person = createPersonTopic(firstName, lastName, itemId);
                // ### wdSearch.assignToWikidataWorkspace(person);
                if (person == null) log.warning("Person Topic ("+itemId+") NOT created - reason unknown.");
            } else {
                log.warning("Person Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        // --- Import relations between entities

        if (worksAt.isEmpty() && livesAt.isEmpty()) {
            log.info("Skipping the import of relations, this works just among already imported items.");
        } else {
            log.info(" ... " + worksAt.size() + " worksAt associations");
            for (String itemId : worksAt.keySet()) {
                String reference = worksAt.get(itemId);
                Association employeeOf = null;
                Topic playerTwo = null, playerOne = null;
                if (reference.startsWith("T")) {
                    playerTwo = dms.getTopic(Long.parseLong(reference.substring(1)));
                    playerOne = dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId));
                }
                if (playerOne != null && playerTwo != null) {
                    employeeOf = dms.createAssociation(new AssociationModel("dm4.core.association",
                            new TopicRoleModel(playerOne.getId(), "dm4.core.default"),
                            new TopicRoleModel(playerTwo.getId(), "dm4.core.default")));
                    log.info("Created new works-at relationship for " + itemId + " to " + playerTwo.getId() + "(" + playerTwo.getSimpleValue() + ")");
                }
                if (employeeOf != null) {
                    // ### assign assocs to ws: wdSearch.assignToWikidataWorkspace(employeeOf);
                    employeeOf.setSimpleValue("works at");
                }
            }

            log.info(" ... " + livesAt.size() + " livesAt associations");
            for (String itemId : livesAt.keySet()) {
                String reference = livesAt.get(itemId);
                Association citizen = null;
                Topic playerTwo = null, playerOne = null;
                if (reference.startsWith("T")) {
                    log.info(" ... fetching .. " + reference);
                    playerTwo = dms.getTopic(Long.parseLong(reference.substring(1)));
                    playerOne = dms.getTopic("uri", new SimpleValue(WikidataEntityMap.WD_ENTITY_BASE_URI + itemId));
                }
                if (playerOne != null && playerTwo != null) {
                    citizen = dms.createAssociation(new AssociationModel("dm4.core.association",
                            new TopicRoleModel(playerOne.getId(), "dm4.core.default"),
                            new TopicRoleModel(playerTwo.getId(), "dm4.core.default")));
                    log.info("Created new lives-at relationship for " + itemId + " to " + playerTwo.getId() + "(" + playerTwo.getSimpleValue() + ")");
                }
                if (citizen != null) {
                    // ### assign assocs to ws: wdSearch.assignToWikidataWorkspace(citizen);
                    citizen.setSimpleValue("lives at");
                }
            }

        }

        // --- Froods

        log.info(" ... " + all_herbs.size() + " herbs");
        for (String itemId : all_herbs.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId);
            Topic herbs;
            if (name != null) {
                herbs = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(herbs);
            } else {
                log.warning("Herb Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_vegetables.size() + " vegetables");
        for (String itemId : all_vegetables.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId);
            Topic vegetables;
            if (name != null) {
                vegetables = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(vegetables);
            } else {
                log.warning("Vegetable Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_edible_fruits.size() + " fruits");
        for (String itemId : all_edible_fruits.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId);
            Topic fruits;
            if (name != null) {
                fruits = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(fruits);
            } else {
                log.warning("Fruit Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_edible_fungis.size() + " fungis");
        for (String itemId : all_edible_fungis.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId);
            Topic fungis;
            if (name != null) {
                fungis = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(fungis);
            } else {
                log.warning("Fungi Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }

        ResultList<RelatedTopic> personas = dms.getTopics("dm4.contacts.person", 0);
        ResultList<RelatedTopic> institutions = dms.getTopics("dm4.contacts.institution", 0);
        ResultList<RelatedTopic> cities = dms.getTopics("dm4.contacts.city", 0);
        ResultList<RelatedTopic> countries = dms.getTopics("dm4.contacts.country", 0);
        ResultList<RelatedTopic> notes = dms.getTopics("dm4.notes.note", 0);
        if (personas != null && institutions != null && cities != null && countries != null) {
            log.info("DeepaMehta now recognizes " + this.all_persons.size() + " human beings"
                + ", " + this.all_institutions.size() + " institutions, "
                + this.all_cities.size() + " cities, " 
                + this.all_countries.size() + " countries and "+ notes.getTotalCount()+ " food items by name.");
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