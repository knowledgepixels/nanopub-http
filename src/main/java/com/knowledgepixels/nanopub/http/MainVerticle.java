package com.knowledgepixels.nanopub.http;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import org.apache.http.HttpStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.nanopub.MalformedNanopubException;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.nanopub.extra.security.SignNanopub;
import org.nanopub.extra.security.SignatureAlgorithm;
import org.nanopub.extra.security.TransformContext;
import org.nanopub.extra.server.PublishNanopub;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import net.trustyuri.TrustyUriException;

public class MainVerticle extends AbstractVerticle {

	private boolean serverStarted = false;

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
							Nanopub np = new NanopubImpl(dataString, RDFFormat.TRIG);
							System.err.println("PARAM: " + req.getParam("test"));
							KeyPair keys = SignNanopub.loadKey("~/.nanopub/id_rsa", SignatureAlgorithm.RSA);
							IRI signer = vf.createIRI(req.getParam("signer"));
							TransformContext c = new TransformContext(SignatureAlgorithm.RSA, keys, signer, false, false);
							Nanopub transformedNp = SignNanopub.signAndTransform(np, c);
							System.err.println("TRANSFORMED:\n\n" + NanopubUtils.writeToString(transformedNp, RDFFormat.TRIG));
							PublishNanopub.publish(transformedNp);
							System.err.println("PUBLISHED: " + transformedNp.getUri());
							req.response().setStatusCode(HttpStatus.SC_OK).putHeader("content-type", "text/plain").end(transformedNp.getUri().stringValue());
						} catch (MalformedNanopubException | SignatureException | NoSuchAlgorithmException | InvalidKeySpecException | IOException | InvalidKeyException | TrustyUriException ex) {
							req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST)
								.setStatusMessage(Arrays.toString(ex.getStackTrace()))
								.end();
							ex.printStackTrace();
							return;
						};
					});
				} else {
					throw new RuntimeException("Unknown request path: " + req.path());
				}
			} catch (Exception ex) {
				req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST)
					.setStatusMessage(Arrays.toString(ex.getStackTrace()))
					.end();
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

	private static final ValueFactory vf = SimpleValueFactory.getInstance();

}
