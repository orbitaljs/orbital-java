package com.codano.hybridapp;

public interface HybridAppRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	HybridAppData handle(String endpoint, HybridAppData in);
}
