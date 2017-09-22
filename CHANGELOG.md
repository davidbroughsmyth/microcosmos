# Change Log

## 0.2.0
### Changes
* Pass logger generator to components
* When logging exceptions, format is a little different: will always log in two keys:
`:exception` and `:backtrace`, both strings (fix bugs with ElasticSearch, conforms to
a simple schema)

### Bugfix
* Adds serializer to Throwable, not Exceptions
* Adds a multimethod on microscope.io - `serialize-type` - that allows to serialize
  specific types, falling back to just generating a string with `str`. Fixes
  serializes message

## 0.1.4
### Changes
* Change log output format from vector of vectors to string because Elasticsearch
does not support Arrays with a mixture of datatypes. Ex: [ 10, "some string" ]

## 0.1.3
### Added
* IO instances will now receive an additional parameter `:meta`, with the original
message's metadata

## 0.1.2
### Changes
* Better log displaying
