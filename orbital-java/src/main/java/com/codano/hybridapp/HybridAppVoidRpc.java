package com.codano.hybridapp;

public interface HybridAppVoidRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	void handle(String endpoint, HybridAppData in);
}
