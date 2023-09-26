# Nanopub HTTP

This is a container that provides features of the [nanopub library](https://github.com/Nanopublication/nanopub-java) over HTTP.

The focus is currently on the signing and publishing features. The coverage of other features from the nanopub library is planned for the future.


## Setup

Clone this repository and then make a local copy of the `override` file:

    $ cp docker-compose.override.yml.template docker-compose.override.yml

Edit `docker-compose.override.yml` to adjust the settings (don't edit `docker-compose.yml`, as it can lead to problems when updating).


## Nanopubliations

The nanopublications need to comply with the [Nanopublication Guidelines](https://nanopub.net/guidelines/working_draft/).
They are based on [RDF 1.1](https://www.w3.org/TR/rdf11-concepts/) and can be expressed in the RDF notations that support graphs/context,
such as [TriG](https://www.w3.org/TR/trig/). See the [examples](examples/) for further information.


## Signing and Publishing

Once the Nanopub HTTP service is running, nanopublications can be signed and published with POST requests like this one:

    $ curl -X POST -d @examples/malaria.trig \
        'http://localhost:4800/publish?signer=http://example.com/example-user'


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

    $ curl -X POST -d @examples/malaria.trig \
        'http://localhost:4800/publish?signer=http://example.com/example-user&server-url=https://np.test.knowledgepixels.com/'

These are the available test servers:

- https://np.test.knowledgepixels.com/
- https://np.test.petapico.org/

The nanopublications can then be queried from these respective test SPARQL endpoints:

- https://virtuoso.test.nps.petapico.org/sparql
- https://virtuoso.test.nps.knowledgepixels.com/sparql
