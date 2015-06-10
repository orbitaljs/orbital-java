package com.codano.orbital;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.Charsets;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

public class PacketCoder {
	private static final int TYPE_NULL = 0;
	private static final int TYPE_JSON = 1;
	private static final int TYPE_BINARY = 2;
	
	public static byte[] encode(OrbitalAppPacket packet) {
		List<Object> data = packet.getData();
		if (data == null)
			data = Collections.emptyList();
		
		byte[] endpoint = null;
		if (packet.getEndpoint() != null) {
			endpoint = packet.getEndpoint().getBytes(Charsets.UTF_8);
		}
		
		int capacity = 1; // flags
		capacity += 4; // seqId
		if (endpoint != null)
			capacity += 4 + endpoint.length;
		
		byte[] types = new byte[data.size()];
		byte[][] encodings = new byte[data.size()][];
		for (int i = 0; i < data.size(); i++) {
			Object obj = data.get(i);
			if (obj == null) {
				encodings[i] = null;
				types[i] = TYPE_NULL;
				capacity++;
			} else if (obj instanceof byte[]) {
				encodings[i] = (byte[]) obj;
				types[i] = TYPE_BINARY;
				capacity += 5 + encodings[i].length;
			} else {
				encodings[i] = JsonWriter.string(obj).getBytes(Charsets.UTF_8);
				types[i] = TYPE_JSON;
				capacity += 5 + encodings[i].length;
			}
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(capacity);
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		int flags = (packet.isCall() ? 1 : 0) 
				| (endpoint != null ? 1 << 1 : 0);

		buffer.put((byte)flags);
		buffer.putInt(packet.getSeqId());
		
		if (endpoint != null) {
			buffer.putInt(endpoint.length);
			buffer.put(endpoint);
		}
		
		for (int i = 0; i < encodings.length; i++) {
			buffer.put(types[i]);
			if (types[i] == TYPE_NULL)
				continue;
			
			byte[] encoding = encodings[i];
			buffer.putInt(encoding.length);
			buffer.put(encoding);
		}
		
		return buffer.array();
	}

	public static OrbitalAppPacket decode(byte[] bytes) throws PacketCoderException {
		int flags = bytes[0];
		boolean call = (flags & 1) != 0;
		boolean hasEndpoint = (flags & (1 << 1)) != 0;
		
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
		
		List<Object> data = new ArrayList<>();
		for (int i = offset; i < bytes.length; i++) {
			switch (bytes[i]) {
			case TYPE_NULL:
				data.add(null);
				break;
			case TYPE_BINARY: {
				int length = readInt(bytes, i + 1);
				data.add(Arrays.copyOfRange(bytes, i + 5, i + 5 + length));
				i += 4 + length;
				break;
			}
			case TYPE_JSON: {
				int length = readInt(bytes, i + 1);
				String s = new String(bytes, i + 5, length, Charsets.UTF_8);
				try {
					data.add(JsonParser.any().from(s));
				} catch (JsonParserException e) {
					throw new PacketCoderException(e);
				}
				i += 4 + length;
				break;
			}
			}
		}

		return new OrbitalAppPacket(call, endpoint, seqId, data);
	}
	
	private static int readInt(byte[] data, int offset) {
		return (data[offset + 3] & 0xff) | ((data[offset + 2] & 0xff) << 8)
				| ((data[offset + 1] & 0xff) << 16) | ((data[offset + 0] & 0xff) << 24);
	}
}
