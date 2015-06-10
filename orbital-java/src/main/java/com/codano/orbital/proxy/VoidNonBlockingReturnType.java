package com.codano.orbital.proxy;

import java.util.List;

import com.codano.orbital.OrbitalApp;

public class VoidNonBlockingReturnType implements RpcReturnType {
	private OrbitalApp app;
	private String name;

	public VoidNonBlockingReturnType(OrbitalApp app, String name) {
		this.app = app;
		this.name = name;
	}

	@Override
	public Object invoke(List<Object> arguments) {
		app.callNoReturn(name, arguments);
		return null;
	}
}
