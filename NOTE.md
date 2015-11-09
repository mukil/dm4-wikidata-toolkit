
### Geofronts Wikidata Parser Identificiation Algorithm

#### Create Wikidata Items

Items who match the following criteria are created:

instanceOf | subclass

	DOPERSONS: - HUMAN | PERSON
	DOINSTITS: - COMPANY | UNIVERSITY | ORGANISATION | COLLEGIATE_UNIVERSITY
	DOCITIES : - CITY | METROPOL | CAPITAL
	DOCOUNTRS: - COUNTRY | STATE | SOUVERIGN STATE

isCoordinateOf && DOCOORDINATES
	


#### Create Wikidata Text Values

Text claims are created if we pass one item which has such
(### this must trigger of wikidata item creation, too)

isNUTSCode
isOSMRelationID
isISOThreeLetterCode


#### Creating (qualified) Claims

Claims to other items are created if we pass one item which has such
(### this must trigger of wikidata item creation, too)

isCountry
isCapital
locatedInAdministrativeTerritory
containsAdministrativeEntity
isCitizenOf
isResidenceOf

All these type of claims are post-processed by a "storeQualifyingTimeProperties" implementation.
