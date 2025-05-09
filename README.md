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

Unless the nanopublication is already signed or trusty, this processing takes place before publication:

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

In particular, you can query them via [SPARQL](https://www.w3.org/TR/sparql11-query/) through one of these endpoints:

- https://query.knowledgepixels.com/repo/full
- https://query.petapico.org/repo/full

These endpoints have the entire nanopublications with all their four graphs loaded, and provide you with the full power of the SPARQL language.
As a starting point, the special graph `npa:graph` provides you with the head graph, the public key, and the publication date, as shown in this example query:

    prefix np: <http://www.nanopub.org/nschema#>
    prefix npx: <http://purl.org/nanopub/x/>
    prefix npa: <http://purl.org/nanopub/admin/>
    prefix dct: <http://purl.org/dc/terms/>
    prefix prov: <http://www.w3.org/ns/prov#>
    
    select ?np ?subj ?pred ?obj ?agent ?date where {
      graph npa:graph {
        ?np npa:hasHeadGraph ?head .
        ?np npa:hasValidSignatureForPublicKey ?pubkey .
        ?np dct:created ?date .
      }
      graph ?head {
        ?np np:hasAssertion ?assertion .
        ?np np:hasProvenance ?prov .
        ?np np:hasPublicationInfo ?pubinfo .
      }
      graph ?assertion {
        ?subj ?pred ?obj .
      }
      graph ?prov {
        ?assertion prov:wasAttributedTo ?agent .
      }
      graph ?pubinfo {
        ?np a npx:ExampleNanopub .
      }
    }
    order by desc(?date)
    limit 100


## Testing

To test the publication of nanopublications without actually publishing them, use the 'server-url' argument:

    $ curl -X POST -d @examples/malaria.trig \
        'http://localhost:4800/publish?signer=http://example.com/example-user&server-url=https://test.registry.knowledgepixels.com/'

This is the recommended test server:

- https://test.registry.knowledgepixels.com/

The nanopublications can then be queried from this test SPARQL endpoint:

- [NOT YET AVAILABLE; COMING SOON...]


## Response

The response to the HTTP request consists of a simple JSON structure, according to this schema:

    {
      "id": "https://w3id.org/np/RA...",
      "url": "https://.../RA..."
    }

The value for "id" is the unique IRI identifier of the nanopublication. This should be used for references, including for pointing to the nanopublication in SPARQL endpoints.

The value for "url" is the place where the nanopublication was published. The nanopublication is available at that location immediately.

Running an HTTP request against the "id" value will forward to a URL where the nanopublication is served, which might be the one of the "url" value or a different one in the network.
This forwarding might not work immediately, but can take a few seconds or a minute.

If a testing server is used, forwarding of the "id" value doesn't work, as the nanopublication is not added to the network, but the "url" value points to the test server and does resolve.
