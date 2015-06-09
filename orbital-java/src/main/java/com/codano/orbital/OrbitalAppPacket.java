package com.codano.orbital;

public class OrbitalAppPacket {
	private boolean call;
	private int seqId;
	private OrbitalAppData data;
	private String endpoint;
	
	public OrbitalAppPacket(boolean call, String endpoint, int seqId, OrbitalAppData data) {
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
	
	public OrbitalAppData getData() {
		return data;
	}
	
	public int getSeqId() {
		return seqId;
	}
}
