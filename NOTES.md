# Notes for Nanopub HTTP

## Update Dependencies

    $ mvn versions:use-latest-versions && mvn versions:update-properties

## Manual POST Request

    $ curl -X POST 'http://localhost:4800/publish?signer=https://orcid.org/0000-0002-1267-0234'  -H "Content-Type: multipart/form-data" -d @scratch/nanopub1.trig
