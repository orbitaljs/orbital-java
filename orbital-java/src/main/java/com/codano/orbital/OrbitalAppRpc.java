package com.codano.orbital;

import java.util.List;

public interface OrbitalAppRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	Object handle(String endpoint, List<Object> in);
}
