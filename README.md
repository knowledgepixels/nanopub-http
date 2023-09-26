# Nanopub HTTP

This is a container that provides features of the [nanopub library](https://github.com/Nanopublication/nanopub-java) over HTTP.


## Signing and Publishing Nanopublications

Once the Nanopub HTTP service is running, nanopublications can be signed and published with POST requests like this one:

    $ curl -X POST -d @examples/malaria.trig 'http://localhost:4800/publish?signer=http://example.com/example-user'


## Formats

Nanopublications are by default assumed to be in TriG format, but other formats can be specified with a "Content Type" header:

    $ curl -X POST -H "Content-Type: application/jsonld" -d @examples/malaria.jsonld \
          'http://localhost:4800/publish?signer=http://example.com/example-user'

The supported RDF format with corresponding Content Types are:

- TriG (`application/trig`)
- TriX (`application/trix`)
- JSON-LD (`application/ld+json`)


## Testing

To test the publication of nanopublications without actually publishing them, use the 'server-url' argument:

    $ curl -X POST -H "Content-Type: application/trig" -d @examples/malaria.trig \
          'http://localhost:4800/publish?signer=http://example.com/example-user&server-url=https://np.test.knowledgepixels.com/'

These are the available test servers:

- https://np.test.knowledgepixels.com/
- https://np.test.petapico.org/

The nanopublications can then be queried from these respective test SPARQL endpoints:

- https://virtuoso.test.nps.petapico.org/sparql
- https://virtuoso.test.nps.knowledgepixels.com/sparql
