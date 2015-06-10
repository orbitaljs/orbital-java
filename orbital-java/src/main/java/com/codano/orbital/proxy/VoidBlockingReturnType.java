package com.codano.orbital.proxy;

import java.util.List;

import com.codano.orbital.OrbitalApp;

public class VoidBlockingReturnType implements RpcReturnType {
	private OrbitalApp app;
	private String name;

	public VoidBlockingReturnType(OrbitalApp app, String name) {
		this.app = app;
		this.name = name;
	}
	
	@Override
	public Object invoke(List<Object> arguments) {
		app.callBlocking(name, arguments);
		return null;
	}
}
