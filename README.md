
# DeepaMehta 4 Wikidata Toolkit

This plugin  provides users the functionality to process and import entities from a wikidata JSON dump file.

Note: This plugin is experimental, since DeepaMehta 4 does not officially supports Java 1.7 but the WikidataToolkit requires Java 1.7 or higher.

## Requirements

DeepaMehta 4 is a platform for collaboration and knowledge management.
https://github.com/jri/deepamehta

## Download & Installation

You can find a bundle file for installation at [http://download.deepamehta.de](http://download.deepamehta.de).

Copy the downloaded `dm44-wikidata-toolkit-0.0.x.jar` file into your DeepaMehta bundle repository and re-start your [DeepaMehta installation](https://github.com/jri/deepamehta#requirements).

## Usage

Find, edit and use the commands provided by any topic of type "Wikidata Dump Importer Settings" to start processing entities of a complete Wikidata JSON Dump.

## Research & Documentation

You can find more infos on this project in the DeepaMehta Community Trac at https://trac.deepamehta.de/wiki/WikidataSearchPlugin%20

# GNU Public License

This software is released under the terms of the GNU General Public License in Version 3.0, 2007. You can find a copy of that in the root directory of this repository or read it [here](http://www.gnu.org/licenses/gpl).

# Version History

0.0.1, Upcoming
- Importing _Persons_, _Institutions_, _Cities_ and _Countries_ (using the _instance of_ property)
  (with english label, english description and a webpage URL if provided as "official website")
- Compatible with DM 4.4

--------------------------
Author: Malte Reißig, 2014

