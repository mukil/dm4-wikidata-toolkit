
/*
 *
 * DeepaMehta 4 Webclient Wikidata Toolkit Plugin
 * @author Malte Reißig (<malte@mikromedia.de>)
 * @website https://github.com/mukil/dm4-wikidata-toolkit
 * @version 0.0.1-SNAPSHOT
 *
 */

(function ($, dm4c) {

    dm4c.add_plugin('org.deepamehta.wikidata-toolkit', function () {

        // === Webclient Listeners ===

        dm4c.add_listener('topic_commands', function (topic) {
            // check if user is authenticated
            if (!dm4c.has_create_permission_for_topic_type('org.deepamehta.wikidata.dumpfile_import')) {
                return
            }
            var commands = []
            if (topic.type_uri === 'org.deepamehta.wikidata.dumpfile_import') {
                commands.push({is_separator: true, context: 'context-menu'})
                commands.push({
                    label: 'Create topics',
                    handler: importWikidataEntities,
                    context: ['context-menu', 'detail-panel-show']
                })
                commands.push({is_separator: true, context: 'context-menu'})
                commands.push({
                    label: 'Remove topics',
                    handler: deleteWikidataEntities,
                    context: ['context-menu', 'detail-panel-show']
                })
            }
            return commands
        })

        function importWikidataEntities () {

            var requestUri = '/wdtk/import/entities/' + dm4c.selected_object.id
            var response_data_type = response_data_type || "json"
            //
            $.ajax({
                type: "GET", url: requestUri,
                dataType: response_data_type, processData: false,
                async: true,
                success: function(data, text_status, jq_xhr) {
                    dm4c.do_select_topic(data.id, true)
                },
                error: function(jq_xhr, text_status, error_thrown) {
                    $('#page-content').html('<div class="field-label wikidata-search started">'
                        + 'An error occured: ' +error_thrown+ ' </div>')
                    throw "RESTClientError: GET request failed (" + text_status + ": " + error_thrown + ")"
                },
                complete: function(jq_xhr, text_status) {
                    var status = text_status
                }
            })

            $('#page-content').html('<div class="field-label wikidata-search started">'
                + 'Start importing entities of your wikidata dump (file, json) </div>')

            $('#page-content').append('<div class="field-item wikidata-search-spinner">'
                + '<img src="/org.deepamehta.wikidata-toolkit/images/ajax-loader.gif" '
                    + 'title="Processing the wikidata dump (file, json)"></div>')
        }
        
        function deleteWikidataEntities () {

            var requestUri = '/wdtk/delete/topics/' + dm4c.selected_object.id
            var response_data_type = response_data_type || "json"
            //
            $.ajax({
                type: "GET", url: requestUri,
                dataType: response_data_type, processData: false,
                async: true,
                success: function(data, text_status, jq_xhr) {
                    dm4c.do_select_topic(data.id, true)
                },
                error: function(jq_xhr, text_status, error_thrown) {
                    $('#page-content').html('<div class="field-label wikidata-search started">'
                        + 'An error occured: ' +error_thrown+ ' </div>')
                    throw "RESTClientError: GET request failed (" + text_status + ": " + error_thrown + ")"
                },
                complete: function(jq_xhr, text_status) {
                    var status = text_status
                }
            })

            $('#page-content').html('<div class="field-label wikidata-search started">'
                + 'Start deleting all topics created via a wikidata dump (file, json).</div>')

            $('#page-content').append('<div class="field-item wikidata-search-spinner">'
                + '<img src="/org.deepamehta.wikidata-toolkit/images/ajax-loader.gif" '
                    + 'title="Deleting all topics created via a wikidata dump (file, json)."></div>')

        }

    })

}(jQuery, dm4c))
