package com.codano.hybridapp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.io.Charsets;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

public class PacketCoder {
	public static byte[] encode(HybridAppPacket packet) {
		HybridAppData data = packet.getData();
		byte[] binary = data == null ? null : data.getBinary();
		
		byte[] json = null;
		if (data != null && data.getJson() != null) {
			String jsonString = JsonWriter.string(data.getJson());
			json = jsonString.getBytes(Charsets.UTF_8);
		}

		byte[] endpoint = null;
		if (packet.getEndpoint() != null) {
			endpoint = packet.getEndpoint().getBytes(Charsets.UTF_8);
		}
		
		int capacity = 1; // flags
		capacity += 4; // seqId
		if (endpoint != null)
			capacity += 4 + endpoint.length;
		if (binary != null)
			capacity += 4 + binary.length;
		if (json != null)
			capacity += 4 + json.length;
		
		ByteBuffer buffer = ByteBuffer.allocate(capacity);
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		int flags = (packet.isCall() ? 1 : 0) 
				| (endpoint != null ? 1 << 1 : 0) 
				| (binary != null ? 1 << 2 : 0) 
				| (json != null ? 1 << 3 : 0);

		buffer.put((byte)flags);
		buffer.putInt(packet.getSeqId());
		
		if (endpoint != null) {
			buffer.putInt(endpoint.length);
			buffer.put(endpoint);
		}
		if (binary != null) {
			buffer.putInt(binary.length);
			buffer.put(binary);
		}
		if (json != null) {
			buffer.putInt(json.length);
			buffer.put(json);
		}
		
		return buffer.array();
	}

	public static HybridAppPacket decode(byte[] bytes) throws PacketCoderException {
		int flags = bytes[0];
		boolean call = (flags & 1) != 0;
		boolean hasEndpoint = (flags & (1 << 1)) != 0;
		boolean hasBinary = (flags & (1 << 2)) != 0;
		boolean hasJson = (flags & (1 << 3)) != 0;
		
		int offset = 1;
		int seqId = readInt(bytes, offset);
		offset += 4;
		
		String endpoint = null;
		if (hasEndpoint) {
			int len = readInt(bytes, offset);
			offset += 4;
			endpoint = new String(bytes, offset, len, Charsets.UTF_8);
			offset += len;
		}

		byte[] binary = null;
		if (hasBinary) {
			int len = readInt(bytes, offset);
			offset += 4;
			binary = new byte[len];
			System.arraycopy(bytes, offset, binary, 0, len);
			offset += len;
		}

		JsonObject json = null;
		if (hasJson) {
			int len = readInt(bytes, offset);
			offset += 4;
			String jsonString = new String(bytes, offset, len, Charsets.UTF_8);
			try {
				json = JsonParser.object().from(jsonString);
			} catch (JsonParserException e) {
				throw new PacketCoderException(e);
			}
			offset += len;
		}

		HybridAppData data = new HybridAppData(json, binary);
		return new HybridAppPacket(call, endpoint, seqId, data);
	}
	
	private static int readInt(byte[] data, int offset) {
		return (data[offset + 3] & 0xff) | ((data[offset + 2] & 0xff) << 8)
				| ((data[offset + 1] & 0xff) << 16) | ((data[offset + 0] & 0xff) << 24);
	}
}
