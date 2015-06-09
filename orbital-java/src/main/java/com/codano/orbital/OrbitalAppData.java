package com.codano.orbital;

import com.grack.nanojson.JsonObject;

public class OrbitalAppData {
	private JsonObject json;
	private byte[] binary;
	
	public OrbitalAppData() {
	}

	public OrbitalAppData(JsonObject json) {
		this.json = json;
	}

	public OrbitalAppData(byte[] binary) {
		this.binary = binary;
	}

	public OrbitalAppData(JsonObject json, byte[] binary) {
		this.json = json;
		this.binary = binary;
	}
	
	public byte[] getBinary() {
		return binary;
	}
	
	public JsonObject getJson() {
		return json;
	}
}
