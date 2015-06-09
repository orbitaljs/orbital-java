package com.codano.hybridapp;

import java.util.function.Consumer;

public interface HybridAppAsyncRpc {
	/**
	 * Handles a request may optionally include json and/or data. May return
	 * either JSON or binary data.
	 */
	void handle(String endpoint, HybridAppData in, Consumer<HybridAppData> cb);
}
