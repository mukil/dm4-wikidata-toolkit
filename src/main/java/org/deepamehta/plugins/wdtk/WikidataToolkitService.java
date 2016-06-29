
package org.deepamehta.plugins.wdtk;

import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import java.util.List;

/**
 * A very basic plugin to use the WDTK as a DeepaMehta 4 plugin and navigate topics by wikidata entity ids.
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.2
 */
public interface WikidataToolkitService {

    Topic importEntitiesFromWikidataDump (long settingsTopicId);
    
    Topic deleteAllWikidataTopics (long settingsTopicId);

    List<RelatedTopic> getRelatedTopics(String wikidataPropertyId, String wikidataItemId);

    List<RelatedTopic> getSuperRelatedTopics(String wikidataPropertyId, String wikidataItemId,
            String wikidataPropertyTwoId, String wikidataItemTwoId);

    List<RelatedAssociation> getRelatedAssociations(String wikidataPropertyId);

    List<RelatedAssociation> getRelatedAssociationsForItem(String wikidataPropertyId, String wikidataItemId);
    
}
