
package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.ResultList;
import java.util.ArrayList;

/**
 * A very basic plugin to use the WDTK as a DeepaMehta 4 plugin and navigate topics by wikidata entity ids.
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.2
 */
public interface WikidataToolkitService {

    Topic importEntitiesFromWikidataDump (long settingsTopicId);
    
    Topic deleteAllWikidataTopics (long settingsTopicId);

    ResultList<RelatedTopic> getRelatedTopics(String wikidataPropertyId, String wikidataItemId);

    ArrayList<RelatedTopic> getSuperRelatedTopics(String wikidataPropertyId, String wikidataItemId,
            String wikidataPropertyTwoId, String wikidataItemTwoId);

    ResultList<RelatedAssociation> getRelatedAssociations(String wikidataPropertyId);

    ArrayList<RelatedAssociation> getRelatedAssociationsForItem(String wikidataPropertyId, String wikidataItemId);
    
}
