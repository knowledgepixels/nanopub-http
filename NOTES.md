# Notes for Nanopub HTTP

## Update Dependencies

    $ mvn versions:use-latest-versions
    $ mvn versions:update-properties

## Manual POST Request

    $ curl -X POST http://localhost:4800  -H "Content-Type: multipart/form-data" -d @scratch/nanopub.trig
