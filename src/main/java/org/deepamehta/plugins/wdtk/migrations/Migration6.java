package org.deepamehta.plugins.wdtk.migrations;

import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.service.Migration;

import java.util.logging.Logger;


/*
 * Adding a "fulltext key" index to the wikidata text value topics.
 *
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 */

public class Migration6 extends Migration {

    private Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void run() {

        dm4.getTopicType("org.deepamehta.wikidata.text").addIndexMode(IndexMode.FULLTEXT_KEY);

    }

}
