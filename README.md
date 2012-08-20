SOLR Analytics
==============

Apache Lucene/Solr

lucene/ is a search engine library
solr/ is a search engine server that uses lucene

To compile the sources run 'ant compile'
To run all the tests run 'ant test'
To setup your ide run 'ant idea' or 'ant eclipse'
For Maven info, see dev-tools/maven/README.maven.

For more information on how to contribute see:
http://wiki.apache.org/lucene-java/HowToContribute
http://wiki.apache.org/solr/HowToContribute

Objective/Goal
==============
The goal of solr analytics is to provide a platform
that provides best of breed analytical functions to
people who would otherwise have to purchase SAS or
some other statistical engine.

Solr's unique value proposition is in it's speed over
data that does not change, it offers both exploratory
analysis of data that can scale horizontally and in 
the cloud. Solr's value also comes from it's schema-ish
design where one can load any type of data from PDF, to
a word document sitting right next to "enterprise" data
like an account, contract, or financial instrument.

Combine this with big data analysis via HyperLogLog,
MurMurHashing, cryptographic hashing and you have
the ability to scan and run statistics against large
datasets (into the many billions) on commodity PC 
hardware.

With SOLR also comes management tools, and standard API
interfaces that reduce the amount of work development
has to do in order to expose and explore data. The REST
based API that supports both JSON,JSONP,XML and JAVA/binary
supports any number of integration scenarios.

Patches Applied to Create SOLR Analytics
==============
* Apache VFS support
  Any user can now provide a VFS location when loading content
  into solr via updatecsv. With this specifically setup, 
  the use-case covered was importing multi-million/billion rows via
  the updatecsv handler was consuming a large amount of 
  memory and crashing. By using VFS we could consume
  gzip compressed csv files which reduced memory and actually
  increased performance of indexing from raw files.
  
  Further by using VFS we could inject credentials to load
  from FTP/SSH/HTTP/ and other VFS supported endpoints without
  having to worry about connection logic. This was especially
  important in cloud-based deployments where the CSV files 
  could be hosted on Amazon S3 (for example).
  
* RatiosComponent:
  The ratios component was created to allow for querying
  solr and returning documents who match two queries and
  the "distance" between these two queries would yield
  the document score.
  
  Use Case - Suppose you have an index of order line items
  from a online shopping cart. It contains a flattened view
  of your products + location + shopper information [username].
  
  Now you wanted to find all purchases with a high correlation
  between peoples purchases and the items they purchased. For example
  you wanted to find out how many shoppers that when they buy ENERGY DRINKS
  they buy most of the time RED BULL and you want to score them based
  upon their purchase price.