# Nanopub HTTP

This is a container that provides features of the [nanopub library](https://github.com/Nanopublication/nanopub-java) over HTTP.

The focus is currently on the signing and publishing features. The coverage of other features from the nanopub library is planned for the future.


## Setup

Clone this repository and then make a local copy of the 'override' file:

    $ cp docker-compose.override.yml.template docker-compose.override.yml

Edit `docker-compose.override.yml` to adjust the settings (don't edit `docker-compose.yml`, as it can lead to problems when updating).


## Update

To update, pull the latest version from GitHub first:

    $ git pull

Then update and restart the Docker containers (might need `sudo`):

    $ docker compose pull
    $ docker compose down && docker compose up -d

You can also find earlier versions on [Docker Hub](https://hub.docker.com/r/nanopub/http/tags).


## Nanopublications

To be used with this service, the nanopublications need to comply with the [Nanopublication Guidelines](https://nanopub.net/guidelines/working_draft/).
They are based on [RDF 1.1](https://www.w3.org/TR/rdf11-concepts/) and can be expressed in the RDF notations that support graphs/context,
such as [TriG](https://www.w3.org/TR/trig/).

See the [general template](examples/general-template.trig) and the other [examples](examples/) for more detailed information.

Nanopublications are immutable and their publication cannot be undone. See the part about "superseding" at the bottom of
the [general template](examples/general-template.trig) and the [retractopm template](examples/retraction-template.trig) on how to declare new versions and
retractions.


## Signing and Publishing

Once the Nanopub HTTP service is running, nanopublications can be signed and published with POST requests like this one:

    $ curl -X POST -d @examples/malaria.trig \
        'http://localhost:4800/publish?signer=http://example.com/example-user'

If the nanopublication is already signed (or is unsigned but has a [Trusty URI](https://trustyuri.net/)), the nanopublication is published as is.

Otherwise, the nanopublication is signed and assigned a Trusty URI and only then published.
If the `signer` argument is not present, the environment variable `NANOPUB-DEFAULT-SIGNER` is read instead, as specified in the docker-compose file.
The `signer` value is a URI specifying the creator and signer of the nanopublication, which can be a software tool or a person.

Ideally, every user is identified individually (e.g. with an ORCID identifier) and this identifier is used as `signer` argument.
Like that, each user gets its own key pairs and the resulting nanopublications can easily be filtered by user later on.

Unless the nanopublication is already signed or trusty, this processing takes place:

- A timestamp is added (via `dct:created`) if none is present in the input nanopublication
- The specified signer is added as a creator (via `dct:creator`) if not already present
- Temporary nanopublication URIs `http://purl.org/nanopub/temp/...` are transformed into standard ones that will resolve to the nanopublication network: `https://w3id.org/np/...`
- If the given signer doesn't have a local key pair yet, a new one is created in the local folder
- The nanopublication content is signed with the key pair for the given signer


## Local Key Pair Files

The cryptographic key pairs used for signing the nanopublications are stored in the local folder `local`.
For each signer URI, a subfolder in this local folder is created, named with a hash generated from the URI.
This subfolder contains the public (`id_rsa.pub`) and private (`id_rsa`) keys, and the corresponding signer URI (`uri.txt`).

The `local` folder should be treated with great care.
It contains the private keys that, if leaked, can be used to publish fake nanopublications in the names of the respective users.
If these private keys are lost, on the other hand, it makes it harder to publish trusted retractions and new versions of the existing nanopublications signed with the lost keys.
Key leakage or loss are not fatal, as the nanopublication ecosystem is designed to cope with these scenarios, but should be prevented as much as possible nevertheleess.


## Formats

Nanopublications are by default assumed to be in TriG format, but other formats can be specified with a "Content Type" header:

    $ curl -X POST -H "Content-Type: application/jsonld" -d @examples/malaria.jsonld \
        'http://localhost:4800/publish?signer=http://example.com/example-user'

The supported RDF format with corresponding Content Types are:

- TriG (`application/trig`)
- TriX (`application/trix`)
- JSON-LD (`application/ld+json`)


## Querying

You can query published nanopublications through the services in the nanopublication network, which you can find through these monitors:

- https://monitor.knowledgepixels.com/
- https://monitor.np.trustyuri.net/
- https://monitor.petapico.org/

In particular, you can query them via [SPARQL](https://www.w3.org/TR/sparql11-query/) through one of these endpoints:

- https://virtuoso.nps.knowledgepixels.com/sparql
- https://virtuoso.services.np.trustyuri.net/sparql
- https://virtuoso.nps.petapico.org/sparql


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
