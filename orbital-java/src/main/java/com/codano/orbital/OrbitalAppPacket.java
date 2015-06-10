package com.codano.orbital;

import java.util.List;

public class OrbitalAppPacket {
	private boolean call;
	private int seqId;
	private List<Object> data;
	private String endpoint;
	
	public OrbitalAppPacket(boolean call, String endpoint, int seqId, List<Object> data) {
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
	
	public List<Object> getData() {
		return data;
	}
	
	public int getSeqId() {
		return seqId;
	}
}
