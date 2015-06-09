package com.codano.orbital;

public interface OrbitalAppRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	OrbitalAppData handle(String endpoint, OrbitalAppData in);
}
