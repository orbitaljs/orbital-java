package com.codano.orbital;

import java.util.function.Consumer;

public interface OrbitalAppAsyncRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	void handle(String endpoint, OrbitalAppData in, Consumer<OrbitalAppData> cb);
}
