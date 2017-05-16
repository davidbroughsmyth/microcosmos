# Change Log

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
