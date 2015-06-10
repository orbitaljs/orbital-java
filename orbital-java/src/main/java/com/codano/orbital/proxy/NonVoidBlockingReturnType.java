package com.codano.orbital.proxy;

import java.util.List;

import com.codano.orbital.OrbitalApp;

public class NonVoidBlockingReturnType implements RpcReturnType {
	private OrbitalApp app;
	private String name;

	public NonVoidBlockingReturnType(OrbitalApp app, String name) {
		this.app = app;
		this.name = name;
	}

	@Override
	public Object invoke(List<Object> arguments) {
		return app.callBlocking(name, arguments);
	}
}
