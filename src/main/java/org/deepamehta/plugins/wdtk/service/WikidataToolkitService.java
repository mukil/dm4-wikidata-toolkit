
package org.deepamehta.plugins.wdtk.service;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.PluginService;

/**
 * A very basic plugin to use the WDTK as DeepaMehta 4 plugin.
 
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.0.1-SNAPSHOT
 */
public interface WikidataToolkitService extends PluginService {

    Topic importEntitiesFromWikidataDump (long settingsTopicId);
    
    Topic deleteAllWikidataTopics (long settingsTopicId);
    
    void assignToWikidataWorkspace (Topic topic);

}
