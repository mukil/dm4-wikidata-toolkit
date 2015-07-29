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
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.workspaces.service.WorkspacesService;
import java.util.HashMap;
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
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        countEntity();
        
        // 0) Get label and description of current item
        String itemId = itemDocument.getEntityId().getId();
        String label = getItemLabel(itemDocument);
        String description = getItemDescription(itemDocument);

        // 1) Iterate over items statement groups

        if (itemDocument.getStatementGroups().size() > 0) {
            
            for (StatementGroup sg : itemDocument.getStatementGroups()) {
                                
                // -- Inspect with which type of StatementGroup (resp. Property) we deal here 
                
                boolean isInstanceOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_INSTANCE_OF);
                boolean isSubclassOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SUBCLASS_OF);
                // ?principiality?
                boolean isCoordinateOf = sg.getProperty().getId().equals(WikidataEntityMap.GEO_COORDINATES);
                boolean isISOThreeLetterCode = sg.getProperty().getId().equals(WikidataEntityMap.IS_ISO_THREE_LETTER_CODE);
                boolean isCapital = sg.getProperty().getId().equals(WikidataEntityMap.IS_CAPITAL);
                boolean isCountry = sg.getProperty().getId().equals(WikidataEntityMap.IS_COUNTRY);
                boolean containsAdministrativeEntity = sg.getProperty().getId().equals(WikidataEntityMap.CONTAINS_ADMIN_T_ENTITY);
                boolean isGivenNameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_GIVEN_NAME_OF);
                boolean isSurnameOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_SURNAME_OF);
                boolean isBirthDateOf = sg.getProperty().getId().equals(WikidataEntityMap.WAS_BORN_ON);
                boolean isDeathDateOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_DEAD_SINCE);
                // 
                boolean isEmployeeOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_EMPLOYEE_OF);
                /** boolean isPartyMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_PARTY_MEMBER_OF);
                boolean isOfficiallyResidingAt = sg.getProperty().getId().equals(WikidataEntityMap.IS_OFFICIALLY_RESIDING_AT);
                boolean isMemberOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_MEMBER_OF);
                boolean isCitizenOf = sg.getProperty().getId().equals(WikidataEntityMap.IS_CITIZEN_OF);
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

                // -- StatementGroup of propertyType is instance | subclass of

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
                                        // if (doPersons) createPerson();

                                    // 2.2 current wikidata item is direct instanceOf|subclassOf "university", "company" or "organisation"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COMPANY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.UNIVERSITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.ORGANISATION_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.COLLEGIATE_UNIVERSITY_ITEM)) { // = often subclass of "university" items
                                        if (doInstitutions) //
                                        log.info(".. doInstitution");
                                        updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);

                                    // 2.3 current wikidata item is direct instanceOf|subclassOf "city", "metro" or "capital"
                                    } else if (referencedItemId.equals(WikidataEntityMap.CITY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.METROPOLIS_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.CAPITAL_CITY_ITEM)) {
                                        if (doCities) //
                                        log.info(".. doCity");
                                        updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);

                                    // 2.4 current wikidata item is direct instanceOf|subclassOf "country" or "sovereing state"
                                    } else if (referencedItemId.equals(WikidataEntityMap.COUNTRY_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.SOVEREIGN_STATE_ITEM)
                                        || referencedItemId.equals(WikidataEntityMap.STATE_ITEM)) {
                                        if (doCountries) // && !all_countries.containsKey(itemId)) all_countries.put(itemId, label);
                                        log.info(".. doCountry");
                                        updateOrCreateWikidataItem(itemId, label, null, description, this.isoLanguageCode);

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
                                log.info("### Parsing geo-coordinates " + coordinateValues.getLatitude()
                                        + ", " + coordinateValues.getLongitude() + " of item " + label + "(itemId=" + itemId + ")");
                                if (longitude != -1 && latitude != -1) {
                                    double coordinates[] = {longitude, latitude};
                                    // do Coordinates
                                    attachGeoCoordinates(coordinates, itemId);
                                }
                            }
                        }
                    }

                } else if (isISOThreeLetterCode) {
                    for (Statement s : sg.getStatements()) {
                        if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value mainSnakValue = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                            if (mainSnakValue instanceof StringValue) {
                                StringValue dateValue = (StringValue) mainSnakValue; // ### respect calendermodel
                                log.fine("### NEW: ISO Three Letter Code: " + dateValue);
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
                
                // 1.2) Record various relations of current item to other items

                } else if (isEmployeeOf) {
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
                    } **/
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
  
    private void addWebbrowserURLAsChildTopic(ChildTopicsModel composite, String itemId) {
        /** if (all_websites.containsKey(itemId)) {
             composite.add(DM_WEBBROWSER_URL,
                new TopicModel(DM_WEBBROWSER_URL, new SimpleValue(all_websites.get(itemId))));
        } */
    }
    
    private void updateOrCreateWikidataItem(String itemId, String name, String alias, String description, String language) {
        // check precondition
        if (language == null) {
            log.warning("Invalid Argument - values can just be stored with an accompanying iso language code!");
            throw new WebApplicationException(500);
        }
        String language_code = language.trim().toLowerCase();
        Topic languageCode = getLanguageIsoCodeTopicByValue(language_code);
        //        
        Topic item = getWikidataItemByEntityId(itemId);
        if (item == null) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                TopicModel wikidataItemTopicModel = null;
                ChildTopicsModel wikidataEntityModel = new ChildTopicsModel();
                ChildTopicsModel languagedValueModel = new ChildTopicsModel();
                if (languageCode != null) {
                    languagedValueModel.putRef("org.deepamehta.wikidata.language_code", languageCode.getId());
                } else {
                    languagedValueModel.put("org.deepamehta.wikidata.language_code", language_code);   
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
                tx.success();
            } catch (Exception re) {
                tx.failure();
                log.log(Level.SEVERE, "ERROR: Could not create wikidata item topic", re);
            } finally {
                tx.finish();
            }   
        } else if (item != null) {
            log.info(" UPDATING wikidata item which already exists ... ");
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                // ### if values for language code already exist, updates those otherwise
                // create and append values in new language code..
                ChildTopicsModel languagedValueModel = new ChildTopicsModel();
                // 
                if (languageCode != null) {
                    languagedValueModel.putRef("org.deepamehta.wikidata.language_code", languageCode.getId());
                } else {
                    languagedValueModel.put("org.deepamehta.wikidata.language_code", language_code);   
                }
                // ### experimental..
                // item.loadChildTopics();
                if (item.getChildTopics().getModel().has("org.deepamehta.wikidata.entity_value")) {
                    List<TopicModel> existingValues = item.getModel().getChildTopicsModel().getTopics("org.deepamehta.wikidata.entity_value");
                    boolean existing = false;
                    for (TopicModel el : existingValues) {
                        String existingLanguageValueCode = el.getChildTopicsModel().getString("org.deepamehta.wikidata.language_code");
                        if (existingLanguageValueCode.equals(language_code)) {
                            existing = true;
                            log.info("Updating existing values for language " + language_code + " and item " + itemId);
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
                                break;
                            }
                        }
                    }
                    if (!existing) {
                        log.info("Creating and appending values for language " + language_code + " and item " + itemId);
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
                            TopicModel existingItem = item.getModel();
                            existingItem.getChildTopicsModel().add("org.deepamehta.wikidata.entity_value",
                                    new TopicModel("org.deepamehta.wikidata.entity_value", languagedValueModel));
                            dms.updateTopic(existingItem);
                            tx.success();
                        }
                    }   
                }
            } catch (Exception re) {
                tx.failure();
                log.log(Level.SEVERE, "ERROR: Could not create wikidata item topic", re);
            } finally {
                tx.finish();
            }
        }
    }

    private void attachGeoCoordinates(double[] coordinates, String itemId) {
        Topic item = getWikidataItemByEntityId(itemId);
        if (item != null) { // Item is already in DB
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                // ### fixme does not work, may be there is an error in the storage layer impl
                //     (during comparison of double values after wrapping them in SimpleValues?)
                Topic coordinatesTopic = getGeoCoordinateTopicByValue(coordinates);
                if (coordinatesTopic != null) { // geo coordinates for this item are already in DB (currently never)
                    log.info(" > Adding ASSIGNMENT existing Geo Coordinates to item " + itemId + " to " + coordinates);
                    TopicModel updatedItem = item.getModel();
                    updatedItem.getChildTopicsModel().addRef("dm4.geomaps.geo_coordinate", coordinatesTopic.getId());
                    dms.updateTopic(updatedItem);
                    tx.success();
                } else {
                    log.info(" > Creating NEW Geo Coordinates for item " + itemId + " at " + coordinates);
                    ChildTopicsModel geoCoordinates = new ChildTopicsModel();
                    geoCoordinates.put("dm4.geomaps.latitude", coordinates[1]);
                    geoCoordinates.put("dm4.geomaps.longitude", coordinates[0]);
                    TopicModel geoCoordinatesModel = new TopicModel("dm4.geomaps.geo_coordinate", geoCoordinates);
                    TopicModel updatedItem = item.getModel();
                    updatedItem.getChildTopicsModel().add("dm4.geomaps.geo_coordinate", geoCoordinatesModel);
                    dms.updateTopic(updatedItem);
                    tx.success();
                }
            } catch (Exception error) {
                log.log(Level.SEVERE, "could not attach coordinates to item " + itemId, error);
                tx.failure();
            } finally {
                tx.finish();
            }
        } else { // no wikidata item found
            log.warning("No wikidata item " + itemId + " found for assigning geo-coordinates");
        }
    }
        
    private Topic getGeoCoordinateTopicByValue(double[] coordinates) {
        Topic latitudeExist = dms.getTopic("dm4.geomaps.latitude", new SimpleValue(coordinates[1]));
        Topic longitudeExist = dms.getTopic("dm4.geomaps.longitude", new SimpleValue(coordinates[0]));
        if (latitudeExist != null && longitudeExist != null) {
            log.info(" > Latitude & Longitude values already exist in DB ... ");
            RelatedTopic latitudeParent = latitudeExist.getRelatedTopic("dm4.core.composition", "dm4.core.child", 
                "dm4.core.parent", "dm4.geomaps.geo_coordinate");
            RelatedTopic longitudeParent = longitudeExist.getRelatedTopic("dm4.core.composition", "dm4.core.child", 
                "dm4.core.parent", "dm4.geomaps.geo_coordinate");
            if (latitudeParent.getId() == longitudeParent.getId()) {
                return latitudeParent;
            }
        }
        log.info(" > Latitude & Longitude values DO NOT currently exist in DB ... " + coordinates[1] + ", " + coordinates[0]);
        return null;
    }
    
    private Topic getLanguageIsoCodeTopicByValue(String iso_code) {
        return dms.getTopic("org.deepamehta.wikidata.language_code", new SimpleValue(iso_code));
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