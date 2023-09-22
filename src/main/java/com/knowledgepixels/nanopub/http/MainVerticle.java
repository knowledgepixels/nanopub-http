package com.knowledgepixels.nanopub.http;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Arrays;

import org.apache.http.HttpStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;
import org.nanopub.NanopubUtils;
import org.nanopub.extra.security.MakeKeys;
import org.nanopub.extra.security.SignNanopub;
import org.nanopub.extra.security.SignatureAlgorithm;
import org.nanopub.extra.security.TransformContext;
import org.nanopub.extra.server.PublishNanopub;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import net.trustyuri.TrustyUriUtils;

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
							if (req.getParam("signer") == null) throw new IllegalArgumentException("HTTP request need 'signer' parameter");
							IRI signer = vf.createIRI(req.getParam("signer"));
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
							Nanopub transformedNp = SignNanopub.signAndTransform(np, c);
							System.err.println("TRANSFORMED:\n\n" + NanopubUtils.writeToString(transformedNp, RDFFormat.TRIG));
							PublishNanopub.publish(transformedNp);
							System.err.println("PUBLISHED: " + transformedNp.getUri());
							req.response().setStatusCode(HttpStatus.SC_OK).putHeader("content-type", "text/plain").end(transformedNp.getUri().stringValue() + "\n");
						} catch (Exception ex) {
							req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).setStatusMessage(ex.getMessage()).end();
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
