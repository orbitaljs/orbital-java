package com.codano.hybridapp;

import com.grack.nanojson.JsonObject;

public class HybridAppData {
	private JsonObject json;
	private byte[] binary;
	
	public HybridAppData() {
	}

	public HybridAppData(JsonObject json) {
		this.json = json;
	}

	public HybridAppData(byte[] binary) {
		this.binary = binary;
	}

	public HybridAppData(JsonObject json, byte[] binary) {
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
