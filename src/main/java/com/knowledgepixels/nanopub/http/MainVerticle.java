package com.knowledgepixels.nanopub.http;

import java.util.Arrays;

import org.apache.http.HttpStatus;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.nanopub.MalformedNanopubException;
import org.nanopub.Nanopub;
import org.nanopub.NanopubImpl;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

	private boolean serverStarted = false;

	private boolean allServersStarted() {
		return serverStarted;
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.createHttpServer().requestHandler(req -> {
			try {
				final StringBuilder payload = new StringBuilder();
				req.handler(data -> {
					payload.append(data.toString("UTF-8"));
				});
				req.endHandler(handler -> {
					final String dataString = payload.toString();
					try {
						Nanopub np = new NanopubImpl(dataString, RDFFormat.TRIG);
						// TODO: sign, transform, and publish
						System.err.println("LOADED:\n\n" + dataString);
					} catch (MalformedNanopubException ex) {
						req.response().setStatusCode(HttpStatus.SC_BAD_REQUEST)
							.setStatusMessage(Arrays.toString(ex.getStackTrace()))
							.end();
						ex.printStackTrace();
						return;
					};
					req.response()
						.setStatusCode(HttpStatus.SC_OK)
						.end();
				});
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

}
