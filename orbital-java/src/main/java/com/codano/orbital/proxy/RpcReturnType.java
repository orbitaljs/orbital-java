package com.codano.orbital.proxy;

import java.util.List;

public interface RpcReturnType {
	/**
	 * Invokes the given method with the supplied arguments, returning a value
	 * if blocking (or potentially something else like a Future if non-blocking).
	 */
	Object invoke(List<Object> arguments);
}
