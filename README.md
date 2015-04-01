
# DeepaMehta 4 Wikidata Toolkit

This plugin  provides users the functionality to process and import entities from a wikidata JSON dump file.

Note: This plugin makes use of the WikidataToolkit and therefore requires Java 1.7 or higher.

## Requirements

DeepaMehta 4 is a platform for collaboration and knowledge management.
https://github.com/jri/deepamehta

## Download & Installation

You can find a bundle file for installation at [http://download.deepamehta.de](http://download.deepamehta.de).

Copy the downloaded `dm45-wikidata-toolkit-0.2.jar` file into your DeepaMehta bundle repository and re-start your [DeepaMehta installation](https://github.com/jri/deepamehta#requirements).

## Usage

Find, edit and use the commands provided by any topic of type "Wikidata Dump Import" to
* start importing or removing certain entities and/or
* control aspects of the "to-be-imported" data-values (like language) and relations (like websites)
of a complete Wikidata JSON Dump.

## REST API

You can do GET requests like the following and return a simple list of JSON topics/assocations:

- `/wdtk/query/P108/Q9531/`
- `/wdtk/query/P108/Q9531/P27/Q183/`
- `/wdtk/claims/P27`
- `/wdtk/claims/P27/Q183`

No optimizations done yet, just operating by deepamehta 4 standard means.

## Research & Documentation

You can find some background infos (but outdated details) on the project page in the DeepaMehta Community Trac at https://trac.deepamehta.de/wiki/WikidataSearchPlugin%20

# GNU Public License

This software is released under the terms of the GNU General Public License in Version 3.0, 2007. You can find a copy of that in the root directory of this repository or read it [here](http://www.gnu.org/licenses/gpl).

# Version History

0.2, Upcoming
- Importing _Persons_, _Institutions_, _Cities_ and _Countries_ (using the _instance of_ property)
  with a label, description and a webpage URL (if provided as "official website")
- Importing of five custom relation-types between persons, institutions, cities and countries:<\br>
  namely: **citizen of**, **student of**, **employee of**, **mentor of**, **affiliated with**
- Compatible with DM 4.5

-----------------------------
Author: Malte Reißig, 2014-15

