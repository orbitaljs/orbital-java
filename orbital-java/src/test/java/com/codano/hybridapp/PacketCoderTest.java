package com.codano.hybridapp;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.grack.nanojson.JsonObject;

public class PacketCoderTest {
	@Test
	public void simplestPacket() {
		HybridAppPacket packet = new HybridAppPacket(true, null, 0, null);
		byte[] data = PacketCoder.encode(packet);
		HybridAppPacket decoded = PacketCoder.decode(data);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		assertNull(decoded.getData().getBinary());
		assertNull(decoded.getData().getJson());
	}

	@Test
	public void sequenceId() {
		HybridAppPacket packet = new HybridAppPacket(true, null, 0x12345678,
				null);
		byte[] data = PacketCoder.encode(packet);
		HybridAppPacket decoded = PacketCoder.decode(data);

		assertEquals(0x12345678, decoded.getSeqId());
	}

	@Test
	public void endpoint() {
		HybridAppPacket packet = new HybridAppPacket(true, "endpoint", 0,
				null);
		byte[] data = PacketCoder.encode(packet);
		HybridAppPacket decoded = PacketCoder.decode(data);

		assertEquals("endpoint", decoded.getEndpoint());
	}

	@Test
	public void packetWithJson() {
		HybridAppPacket packet = new HybridAppPacket(true, null, 0,
				new HybridAppData(JsonObject.builder()
						.array("arr", Arrays.asList(1, 2, 3)).done()));
		byte[] encoded = PacketCoder.encode(packet);
		HybridAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		HybridAppData data = decoded.getData();
		assertNull(data.getBinary());
		assertNotNull(data.getJson());
		assertEquals(3, ((List<Object>) data.getJson().getArray("arr")).size());
	}

	@Test
	public void packetWithBinary() {
		HybridAppPacket packet = new HybridAppPacket(true, null, 0,
				new HybridAppData(new byte[] { 1, 2, 3 }));
		byte[] encoded = PacketCoder.encode(packet);
		HybridAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		HybridAppData data = decoded.getData();
		assertNotNull(data.getBinary());
		assertEquals(3, data.getBinary().length);
		assertArrayEquals(new byte[] { 1, 2, 3 }, data.getBinary());
		assertNull(data.getJson());
	}
}
