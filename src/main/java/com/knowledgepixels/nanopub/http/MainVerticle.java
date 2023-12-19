package com.knowledgepixels.nanopub.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.nanopub.MalformedNanopubException;
import org.nanopub.Nanopub;
import org.nanopub.NanopubCreator;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.nanopub.SimpleTimestampPattern;
import org.nanopub.extra.security.MakeKeys;
import org.nanopub.extra.security.MalformedCryptoElementException;
import org.nanopub.extra.security.SignNanopub;
import org.nanopub.extra.security.SignatureAlgorithm;
import org.nanopub.extra.security.SignatureUtils;
import org.nanopub.extra.security.TransformContext;
import org.nanopub.extra.server.PublishNanopub;
import org.nanopub.trusty.TrustyNanopubUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import net.trustyuri.TrustyUriUtils;

public class MainVerticle extends AbstractVerticle {

	private boolean serverStarted = false;
	private IRI defaultSigner;

	private boolean allServersStarted() {
		return serverStarted;
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.createHttpServer().requestHandler(req -> {
			try {
				System.err.println(req.uri());
				if (req.path().matches("/publish")) {
					final StringBuilder payload = new StringBuilder();
					req.handler(data -> {
						payload.append(data.toString("UTF-8"));
					});
					req.endHandler(handler -> {
						final String dataString = payload.toString();
						try {
							RDFFormat format = Rio.getParserFormatForMIMEType(req.getHeader("Content-Type")).orElse(RDFFormat.TRIG);
							Nanopub np = new NanopubImpl(dataString, format);

							boolean isTrusty = TrustyNanopubUtils.isValidTrustyNanopub(np);
							boolean autoSign = !isTrusty && !SignatureUtils.seemsToHaveSignature(np);
							if (hasInvalidSignature(np)) {
								throw new MalformedNanopubException("nanopublication has invalid signature");
							}
							if (autoSign) {
								NanopubCreator nc = getNanopubCreator(np);
								if (SimpleTimestampPattern.getCreationTime(np) == null) nc.addTimestampNow();
								IRI signer;
								if (req.getParam("signer") == null) {
									if (getDefaultSigner() == null) throw new IllegalArgumentException("Neither NANOPUB-DEFAULT-SIGNER environment variable nor 'signer' HTTP parameter found");
									signer = getDefaultSigner();
								} else {
									signer = vf.createIRI(req.getParam("signer"));
								}
								nc.addPubinfoStatement(DCTERMS.CREATOR, signer);
								String signerHash = TrustyUriUtils.getBase64Hash(signer.stringValue());
								Path keyFile = Paths.get("/root/local/" + signerHash + "/id_rsa");
								if (!Files.exists(keyFile)) {
									MakeKeys.make("/root/local/" + signerHash + "/id", SignatureAlgorithm.RSA);
									PrintWriter w = new PrintWriter("/root/local/" + signerHash + "/uri.txt");
									w.println(signer.stringValue());
									w.close();
								}
								KeyPair keys = SignNanopub.loadKey(keyFile.toString(), SignatureAlgorithm.RSA);
								TransformContext c = new TransformContext(SignatureAlgorithm.RSA, keys, signer, false, false);
								np = nc.finalizeNanopub();
								np = SignNanopub.signAndTransform(np, c);
							}

							String npId;
							if (req.getParam("server-url") == null) {
								npId = PublishNanopub.publish(np);
							} else {
								String serverUrl = req.getParam("server-url");
								if (!serverUrl.endsWith("/")) serverUrl += "/";
								publishToServer(np, serverUrl);
								npId = serverUrl + TrustyUriUtils.getArtifactCode(np.getUri().stringValue()); 
							}
							System.err.println("PUBLISHED: " + np.getUri());
							String responseJson = "{\n  \"id\": \"" + np.getUri().stringValue() + "\",\n  \"url\": \"" + npId + "\"\n}\n";
							req.response().setStatusCode(HttpStatus.SC_OK).putHeader("content-type", "application/json").end(responseJson);
						} catch (Exception ex) {
							req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).setStatusMessage(ex.getMessage()).end("Error: " + ex.getMessage() + "\n");
							ex.printStackTrace();
						};
					});
				} else {
					throw new RuntimeException("Unknown request path: " + req.path());
				}
			} catch (Exception ex) {
				req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).setStatusMessage(ex.getMessage()).end("Error: " + ex.getMessage() + "\n");
				ex.printStackTrace();
			}
		}).listen(4800, http -> {
			if (http.succeeded()) {
				serverStarted = true;
				if (allServersStarted()) startPromise.complete();
				System.out.println("HTTP server started on port 9300");
			} else {
				startPromise.fail(http.cause());
			}
		});
	}

	private IRI getDefaultSigner() {
		if (defaultSigner == null && System.getenv("NANOPUB-DEFAULT-SIGNER") != null) {
			defaultSigner = vf.createIRI(System.getenv("NANOPUB-DEFAULT-SIGNER"));
		}
		return defaultSigner;
	}

	// TODO Move this to NanopubCreator class:
	private static NanopubCreator getNanopubCreator(Nanopub np) {
		NanopubCreator nc = new NanopubCreator(np.getUri());
		nc.setAssertionUri(np.getAssertionUri());
		nc.setProvenanceUri(np.getProvenanceUri());
		nc.setPubinfoUri(np.getPubinfoUri());
		for (Statement st : np.getAssertion()) nc.addAssertionStatements(st);
		for (Statement st : np.getProvenance()) nc.addProvenanceStatement(st.getSubject(), st.getPredicate(), st.getObject());
		for (Statement st : np.getPubinfo()) nc.addPubinfoStatement(st.getSubject(), st.getPredicate(), st.getObject());
		return nc;
	}

	// TODO Move this to PublishNanopub class:
	private static void publishToServer(Nanopub nanopub, String serverUrl) throws IOException {
		HttpPost post = new HttpPost(serverUrl);
		String nanopubString = NanopubUtils.writeToString(nanopub, RDFFormat.TRIG);
		post.setEntity(new StringEntity(nanopubString, "UTF-8"));
		post.setHeader("Content-Type", RDFFormat.TRIG.getDefaultMIMEType());
		RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
		HttpResponse response = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build().execute(post);
		int code = response.getStatusLine().getStatusCode();
		if (code < 200 || code >= 300) {
			throw new HttpException(code, response.getStatusLine().getReasonPhrase());
		}
	}

	// TODO Move this to SignatureUtils class:
	private static boolean hasInvalidSignature(Nanopub np) {
		if (!SignatureUtils.seemsToHaveSignature(np)) return false;
		try {
			return !SignatureUtils.hasValidSignature(SignatureUtils.getSignatureElement(np));
		} catch (GeneralSecurityException | MalformedCryptoElementException ex) {
			ex.printStackTrace();
			return true;
		}
	}

	private static final ValueFactory vf = SimpleValueFactory.getInstance();

}
