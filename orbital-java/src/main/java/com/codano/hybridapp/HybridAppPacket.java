package com.codano.hybridapp;

public class HybridAppPacket {
	private boolean call;
	private int seqId;
	private HybridAppData data;
	private String endpoint;
	
	public HybridAppPacket(boolean call, String endpoint, int seqId, HybridAppData data) {
		this.call = call;
		this.endpoint = endpoint;
		this.seqId = seqId;
		this.data = data;
	}
	
	public String getEndpoint() {
		return endpoint;
	}
	
	public boolean isCall() {
		return call;
	}
	
	public HybridAppData getData() {
		return data;
	}
	
	public int getSeqId() {
		return seqId;
	}
}
