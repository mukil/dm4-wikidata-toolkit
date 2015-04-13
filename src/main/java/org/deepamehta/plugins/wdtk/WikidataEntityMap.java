package org.deepamehta.plugins.wdtk;

/**
 *
 * @author bob
 */
public class WikidataEntityMap {
    
    
    
    // --- Wikidata Statics & Data Types
    
    final static String WD_TYPE_ITEM                 = "item";
    final static String WD_TYPE_PROPERTY             = "property";
    final static String WD_TYPE_GLOBE_COORDINATES    = "globe-coordinates";
    final static String WD_ENTITY_BASE_URI           = "org.wikidata.entity.";
    final static String URI_SEPERATOR                = ":";
    
    
    
    // --- Wikidata Language Codes
    
    final static String LANG_EN                      = "en";
    
    
    // --- General Wikidata Items
    
    // ---- Person
    final static String HUMAN_ITEM                   = "Q5";
    final static String PERSON_ITEM                  = "Q215627";
    final static String GENDER_IDENTITY_ITEM         = "Q48264";
    final static String GENDER_ITEM                  = "Q48277";
    final static String MALE_ITEM                    = "Q6581097";
    final static String FEMALE_ITEM                  = "Q6581072";
    final static String GENDER_QUEER_ITEM            = "Q48270";
    final static String INTERSEX_ITEM                = "Q1097630";
    final static String TRANSGENDER_FEMALE_ITEM      = "Q1052281";
    final static String TRANSGENDER_MALE_ITEM        = "Q2449503";
    final static String MAYOR_ITEM                   = "Q30185";
    // ---- Organisation
    final static String UNIVERSITY_ITEM              = "Q3918";
    final static String ORGANISATION_ITEM            = "Q43229";
    final static String COMPANY_ITEM                 = "Q783794";
    final static String COLLEGIATE_UNIVERSITY_ITEM   = "Q3354859";
    // ---- City
    final static String CITY_ITEM                    = "Q515";
    final static String METROPOLIS_ITEM              = "Q200250";
    final static String CAPITAL_CITY_ITEM            = "Q36";
    final static String SOVEREIGN_STATE_ITEM         = "Q3624078";
    final static String STATE_ITEM                   = "Q7275";
    final static String COUNTRY_ITEM                 = "Q6256";
    
    
    // --- Food related items
    
    final static String VEGETABLE                    = "Q11004";
    final static String NUT                          = "Q11009";
    final static String HERB                         = "Q207123";
    final static String FUNGI                        = "Q764";
    final static String MUSHROOM                     = "Q83093";
    final static String EDIBLE_FUNGI                 = "Q654236";
    final static String FOOD_FRUIT                   = "Q3314483";
    
    
    // --- Generic Wikidata Properties
    
    final static String IS_INSTANCE_OF               = "P31";
    final static String IS_SUBCLASS_OF               = "P279";
    final static String STARTED_AT                   = "P580";
    final static String ENDED_AT                     = "P582";
    final static String GEO_COORDINATES              = "P625";
    final static String IS_COUNTRY                   = "P17";
    
    
    
    // --- Persona Related Properties

    final static String IS_HEAD_OF_GOVERNMENT_OF     = "P6";
    final static String IS_SISTER_OF                 = "P9";
    final static String IS_PLACE_OF_BIRTH            = "P19";
    final static String IS_OF_GENDER                 = "P21";
    final static String IS_FATHER_OF                 = "P22";
    final static String IS_MOTHER_OF                 = "P25";
    final static String IS_SPOUSE_OF                 = "P26";
    final static String IS_CITIZEN_OF                = "P27";
    final static String IS_CHILD_OF                  = "P40";
    final static String IS_ALMA_MATER_OF             = "P69";
    final static String IS_PARTY_MEMBER_OF           = "P102";
    final static String IS_EMPLOYEE_OF               = "P108";
    final static String IS_FOUNDER_OF                = "P112";
    final static String IS_DOCTORAL_ADVISOR_OF       = "P184";
    final static String IS_DOCTORAL_STUDENT_OF       = "P185";
    final static String IS_OFFICIALLY_RESIDING_AT    = "P263";
    final static String IS_MEMBER_OF                 = "P463";
    final static String IS_OPEN_RESEARCH_ID_OF       = "P496";
    final static String CAUSE_OF_DEATH               = "P509";
    final static String WAS_BORN_ON                  = "P569";
    final static String IS_DEAD_SINCE                = "P570";
    final static String IS_COORDINATE_LOCATION       = "P625";
    final static String IS_PARTICIPANT_OF            = "P710";
    final static String IS_SURNAME_OF                = "P734";
    final static String IS_GIVEN_NAME_OF             = "P735";
    final static String IS_PSEUDONYM_OF              = "P742";
    final static String IS_NOTABLE_WORK_OF           = "P800";
    final static String IS_OFFICIAL_WEBSITE_OF       = "P856";
    final static String IS_STUDENT_OF_PERSON         = "P1066"; // doublecheck
    final static String IS_AFFILIATED_WITH           = "P1416";
    final static String IS_POPULATION_OF             = "P1082";
 
    // --- Institution related properties
    
    final static String LOCATION_OF_FORMATION        = "P740";
    final static String LOCATION_OF_HEADQUARTER      = "P159";
    
    
    // --- Food related properties
    
    final static String IS_INGREDIENT                = "Q10675206";
    final static String IS_EDIBLE_MUSHROOM           = "Q654236";

}
