package com.codano.orbital;

import java.util.List;
import java.util.function.Consumer;

public interface OrbitalAppAsyncRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	void handle(String endpoint, List<Object> in, Consumer<Object> cb);
}
