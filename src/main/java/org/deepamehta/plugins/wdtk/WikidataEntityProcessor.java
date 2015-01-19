package org.deepamehta.plugins.wdtk;

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
import org.json.JSONObject;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.datamodel.json.ValueJsonConverter;
import org.wikidata.wdtk.util.Timer;

/**
 * @author bob
 *
 * A simple class that processes EntityDocuments to identify personas in the wikidatawiki,
 * based on the EntityTimerProcessor of the WDTK written by Markus Kroetzsch. 
 * Thanks for sharing. Honorable mentions go to jri for telling me about the 
 * ImportPackage notations which helped me to run the WDTK within OSGi.
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

    final Timer timer = Timer.getNamedTimer("WikidataEntityProcessor");
    int lastSeconds = 0, entityCount = 0;
    // seconds to parse the dumpfile
    int timeout;
    // create or not create topics of type ..
    boolean doCountries = false;
    boolean doCities = false;
    boolean doInstitutions = false;
    boolean doPersons = false;
    // store additional text values for items
    boolean storeDescription = false;
    boolean storeGeoCoordinates = false;
    boolean storeWebsiteAddresses = false;
    // ### language value
    String isoLanguageCode = "en";
    
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

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        countEntity();
        
        // 0) Get english label of current item
        String itemId = itemDocument.getEntityId().getId();
        String label = getFirstLabel(itemDocument);
        String description = getFirstDescription(itemDocument);
        // .. Memorizing any items english label in instance-variable
        if (label != null && !label.isEmpty()) itemsFirstLabel.put(itemId, label);
        if (description != null && !description.isEmpty() && storeDescription) {
            itemsFirstDescription.put(itemId, description);
        }

        // 1) Iterate over statement groups 
        if (itemDocument.getStatementGroups().size() > 0) {
            
            for (StatementGroup sg : itemDocument.getStatementGroups()) {
                                
                // -- Inspect with which type of StatementGroup (Property) we deal here 
                
                boolean isInstanceOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_INSTANCE_OF);
                boolean isSubclassOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SUBCLASS_OF);
                boolean isCoordinateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_COORDINATE_LOCATION);
                boolean isGivenNameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_GIVEN_NAME_OF);
                boolean isSurnameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SURNAME_OF);
                boolean isBirthDateOf = sg.getProperty().getId().equals(WikidataEntityMap.WAS_BORN_ON);
                boolean isDeathDateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DEAD_SINCE);
                boolean isWebsiteOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIAL_WEBSITE_OF);
                
                /** boolean isPseudonymOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PSEUDONYM_OF);
                boolean isAlmaMaterOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_ALMA_MATER_OF);
                boolean isStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_STUDENT_OF_PERSON);
                boolean isDoctoralAdvisorOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_ADVISOR_OF);
                boolean isDoctoralStudentOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DOCTORAL_STUDENT_OF); 
                boolean isMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_MEMBER_OF);
                boolean isCitizenOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_CITIZEN_OF);
                boolean isEmployeeOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_EMPLOYEE_OF);
                boolean isPartyMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PARTY_MEMBER_OF);
                boolean isOfficiallyResidingAt = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIALLY_RESIDING_AT);
                boolean isAffiliatedWith = sg.getProperty().getId().equals(WikidataEntityMap.IS_AFFILIATED_WITH);
                boolean isOpenResearchIdOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_OPEN_RESEARCH_ID_OF);
                boolean isNotableWorkOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_NOTABLE_WORK_OF); **/
                /** if (isSubclassOf && classRecord == null) {
                    // logger.info("Has SubclassOf Relationship.. and NO classRecord");
                    classRecord = getClassRecord(itemDocument, itemDocument.getItemId());
                } **/
                
                // 2) Deal with the various specifics of this StatementGroup (Property)..
                
                // -- is instance | subclass of
                
                if (isInstanceOf || isSubclassOf) {
                    
                    for (Statement s : sg.getStatements()) {
                        
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            String mainSnakValueType = valueObject.getString("entity-type");
                            
                            // --- Statement involving other ITEMS
                            if (mainSnakValueType.equals(WikidataEntityMap.WD_TYPE_ITEM)) {
                                String referencedItemId = valueObject.getString("numeric-id");
                                
                                // 2.1 current wikidata item is direct instanceOf|subclassOf "human" or "person"
                                if (referencedItemId.equals(WikidataEntityMap.HUMAN_ITEM) 
                                    || referencedItemId.equals(WikidataEntityMap.PERSON_ITEM)) {

                                    if (doPersons && !all_persons.containsKey(itemId)) { // no persona available
                                        // .. keep a reference to person items for counting (the one which has an english label)
                                        all_persons.put(itemId, label);
                                    }
                                    
                                // 2.2 current wikidata item is direct instanceOf|subclassOf "university", "company" or "organisation"
                                } else if (referencedItemId.equals(WikidataEntityMap.COMPANY_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.UNIVERSITY_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.ORGANISATION_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.COLLEGIATE_UNIVERSITY_ITEM)) { // = often subclass of "university" items
                                    
                                    // ### store item as being of some sort of "organisation"
                                    if (doInstitutions && !all_institutions.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to institution items for counting (the one which has an english label)
                                        all_institutions.put(itemId, label);
                                    }

                                // 2.3 current wikidata item is direct instanceOf|subclassOf "city", "metro" or "capital"
                                } else if (referencedItemId.equals(WikidataEntityMap.CITY_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.METROPOLIS_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.CAPITAL_CITY_ITEM)) {
                                    
                                    // ### store item as being of some sort of "city"
                                    if (doCities && !all_cities.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to city items for counting (the one which has an english label)
                                        all_cities.put(itemId, label);
                                    }

                                // 2.4 current wikidata item is direct instanceOf|subclassOf "country" or "sovereing state"
                                } else if (referencedItemId.equals(WikidataEntityMap.COUNTRY_ITEM)
                                    || referencedItemId.equals(WikidataEntityMap.SOVEREIGN_STATE_ITEM)) {
                                    
                                    // ### store item as being of some sort of "country"
                                    if (doCountries && !all_countries.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to country items for counting (the one which has an english label)
                                        all_countries.put(itemId, label);
                                    }

                                // 2.5 current wikidata item is direct instanceOf|subclassOf "vegetable"
                                } else if (referencedItemId.equals(WikidataEntityMap.VEGETABLE)) {
                                    
                                    // ### store item as being of some sort of "country"
                                    if (!all_vegetables.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to vegetable item for counting (the one which has an english label)
                                        all_vegetables.put(itemId, label);
                                    }
                            
                                // 2.6 current wikidata item is direct instanceOf|subclassOf "herb"
                                } else if (referencedItemId.equals(WikidataEntityMap.HERB)) {
                                    
                                    // ### store item as being of some sort of "country"
                                    if (!all_herbs.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to herb items for counting (the one which has an english label)
                                        all_herbs.put(itemId, label);
                                    }

                                // 2.7 current wikidata item is direct instanceOf|subclassOf "herb"
                                } else if (referencedItemId.equals(WikidataEntityMap.FOOD_FRUIT)) {
                                    
                                    // ### store item as being of some sort of "country"
                                    if (!all_edible_fruits.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to fruit items for counting (the one which has an english label)
                                        all_edible_fruits.put(itemId, label);
                                    }
                                    
                                // 2.8 current wikidata item is direct instanceOf|subclassOf "herb"
                                } else if (referencedItemId.equals(WikidataEntityMap.FUNGI)) {
                                    
                                    // ### store item as being of some sort of "country"
                                    if (!all_edible_fungis.containsKey(itemId)) { // no organisation available
                                        // .. keep a reference to fungi items for counting (the one which has an english label)
                                        all_edible_fungis.put(itemId, label);
                                    }
                                }

                            }
                        }
                    }

                } else  if (isCoordinateOf && this.storeGeoCoordinates) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            double longitude = valueObject.getDouble("longitude");
                            double latitude = valueObject.getDouble("latitude");
                            // --- Statement involving globe-coordinates
                            if (longitude != -1 && latitude != -1) {
                                double coordinates[] = {longitude, latitude}; 
                                // String string_value = valueObject.getString("numeric-id");
                                if (!all_coordinates.containsKey(itemId)) { // no persona available
                                    // .. keep a reference to person items for counting (the one which has an english label)
                                    all_coordinates.put(itemId, coordinates);
                                }
                            }
                        }
                    }

                } else  if (isBirthDateOf) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            String date = valueObject.getString("time"); // ### respect calendermodel
                            // log.info("Item is dead since: " + date); // Access DataValueModel..
                        }
                    }

                } else  if (isDeathDateOf) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            String date = valueObject.getString("time"); // ### respect calendermodel
                            // log.info("Item is dead since: " + date);
                        }
                    }
                
                } else  if (isGivenNameOf) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            // ## ARE of type ITEM! log.info("Items given name is: " + valueObject);
                        }
                    }
                
                } else  if (isSurnameOf) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            JSONObject valueObject = mainSnakValue.accept(new ValueJsonConverter()).getJSONObject("value");
                            // ## ARE of type ITEM! log.info("Items surname is : " + valueObject);
                        }
                    }
                
                } else  if (isWebsiteOf && this.storeWebsiteAddresses) {
                    
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue.accept(new ValueJsonConverter()).has("value")) {
                                String url = mainSnakValue.accept(new ValueJsonConverter()).getString("value");
                                if ((url != null && !url.isEmpty())) {
                                    all_websites.put(itemId, url);
                                }
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
    
    private String getFirstLabel(ItemDocument itemDocument) {
        MonolingualTextValue value = null;
        if (itemDocument.getLabels().size() > 1) {
            if (itemDocument.getLabels().containsKey(WikidataEntityMap.LANG_EN)) {
                value = itemDocument.getLabels().get(WikidataEntityMap.LANG_EN);
            } else { // if no english label avaiable, take first label available
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
            if (itemDocument.getDescriptions().containsKey(WikidataEntityMap.LANG_EN)) {
                value = itemDocument.getDescriptions().get(WikidataEntityMap.LANG_EN);
            } else { // if no english label avaiable, take first label available
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
            ChildTopicsModel personComposite = new ChildTopicsModel();
            personComposite.put(DM_PERSON_NAME, new ChildTopicsModel()
                .put(DM_PERSON_FIRST_NAME, firstName)
                .put(DM_PERSON_LAST_NAME, lastName)
            );
            // add "official" website to institution if available
            if (all_websites.containsKey(itemId)) {
                personComposite.add(DM_WEBBROWSER_URL, 
                    new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(all_websites.get(itemId))));
            }
            String desc = "";
            if (itemsFirstDescription.get(itemId) != null) {
                desc = "<p>"+itemsFirstDescription.get(itemId)+"</p>";
            }
            addWikidataItemDescription(itemId, desc, personComposite);
            TopicModel personModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_PERSON, personComposite);
            person = dms.createTopic(personModel);
            wdSearch.assignToWikidataWorkspace(person);
            // log.info("> Created person Topic: " + firstName + " " + lastName);
        }
        return person;
    }
    
    private Topic createInstitutionTopic(String name, String itemId) {
        Topic institution = null;
        if (!alreadyExists(itemId)) {
            ChildTopicsModel institutionComposite = new ChildTopicsModel();
            institutionComposite.put(DM_INSTITUTION_NAME, name);
            if (all_websites.containsKey(itemId)) {
                institutionComposite.add(DM_WEBBROWSER_URL, 
                    new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(all_websites.get(itemId))));
            }
            // add "official" website to institution if available
            String desc = "";
            if (itemsFirstDescription.get(itemId) != null) {
                desc = "<p>"+itemsFirstDescription.get(itemId)+"</p>";
            }
            addWikidataItemDescription(itemId, desc, institutionComposite);
            TopicModel institutionModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_INSTITUTION, institutionComposite);
            // ### set GeoCoordinate Facet via values in all_coordinates
            institution = dms.createTopic(institutionModel);
            wdSearch.assignToWikidataWorkspace(institution);
        }
        return institution;
    }
    
    private Topic createCityTopic(String name, String itemId) {
        Topic city = null;
        if (!alreadyExists(itemId)) {
            TopicModel cityModel = new TopicModel(
                WikidataEntityMap.WD_ENTITY_BASE_URI + itemId, DM_CITY, new SimpleValue(name));
            // ### set GeoCoordinate Facet via values in all_coordinates
            city = dms.createTopic(cityModel);
            wdSearch.assignToWikidataWorkspace(city);
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
            wdSearch.assignToWikidataWorkspace(city);
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
            wdSearch.assignToWikidataWorkspace(country);
        }
        return country;
    }
    
    private ChildTopicsModel addWikidataItemDescription(String itemId, String desc, 
            ChildTopicsModel comp) {
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
        
        log.info(" ... " + all_cities.size() + " cities");
        for (String itemId : all_cities.keySet()) {
            String cityName = itemsFirstLabel.get(itemId); // ### all_cities
            Topic city = null;
            if (cityName != null) {
                city = createCityTopic(cityName, itemId);
                createRelatedURLTopic(city, itemId);
                wdSearch.assignToWikidataWorkspace(city);
            }
        }

        log.info(" ... " + all_countries.size() + " countries");
        for (String itemId : all_countries.keySet()) {
            String countryName = itemsFirstLabel.get(itemId); // ### all_countries
            Topic country;
            if (countryName != null) {
                country = createCountryTopic(countryName, itemId);
                createRelatedURLTopic(country, itemId);
                wdSearch.assignToWikidataWorkspace(country);
            }
        }
        
        log.info(" ... " + all_institutions.size() + " institutions");
        for (String itemId : all_institutions.keySet()) {
            String instName = itemsFirstLabel.get(itemId); // ### all_institutions
            Topic institution;
            if (instName != null) {
                institution = createInstitutionTopic(instName, itemId);
                wdSearch.assignToWikidataWorkspace(institution);
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
                wdSearch.assignToWikidataWorkspace(person);
            } else {
                log.warning("Person Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_herbs.size() + " herbs");
        for (String itemId : all_herbs.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId); // ### use all_institutions
            Topic person;
            if (name != null) {
                person = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(person);
            } else {
                log.warning("Herb Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_vegetables.size() + " vegetables");
        for (String itemId : all_vegetables.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId); // ### use all_institutions
            Topic person;
            if (name != null) {
                person = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(person);
            } else {
                log.warning("Vegetable Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_edible_fruits.size() + " fruits");
        for (String itemId : all_edible_fruits.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId); // ### use all_institutions
            Topic person;
            if (name != null) {
                person = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(person);
            } else {
                log.warning("Frui Topic ("+itemId+") NOT created (no label value found!) --- Skippin Entry");
            }
        }
        
        log.info(" ... " + all_edible_fungis.size() + " fungis");
        for (String itemId : all_edible_fungis.keySet()) { // this might work but only after having read in the complete dump
            String name = itemsFirstLabel.get(itemId); // ### use all_institutions
            Topic person;
            if (name != null) {
                person = createNoteTopic(name, itemId);
                wdSearch.assignToWikidataWorkspace(person);
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