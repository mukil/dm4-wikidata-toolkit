package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.service.Migration;
import java.util.logging.Logger;


/*
 * A basic plugin for turning wikidata entities into topics and associations.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 */

public class Migration4 extends Migration {

    private Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void run() {

        dm4.getTopicType("dm4.geomaps.latitude").addIndexMode(IndexMode.KEY);
        dm4.getTopicType("dm4.geomaps.longitude").addIndexMode(IndexMode.KEY);

    }

}
