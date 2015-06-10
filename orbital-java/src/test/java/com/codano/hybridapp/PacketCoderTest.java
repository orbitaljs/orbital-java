package com.codano.hybridapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.codano.orbital.OrbitalAppPacket;
import com.codano.orbital.PacketCoder;
import com.grack.nanojson.JsonObject;

public class PacketCoderTest {
	@Test
	public void simplestPacket() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0, null);
		byte[] data = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(data);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		assertEquals(0, decoded.getData().size());
	}

	@Test
	public void sequenceId() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0x12345678,
				null);
		byte[] data = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(data);

		assertEquals(0x12345678, decoded.getSeqId());
	}

	@Test
	public void endpoint() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, "endpoint", 0,
				null);
		byte[] data = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(data);

		assertEquals("endpoint", decoded.getEndpoint());
	}

	@Test
	public void packetWithJson() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0,
				Arrays.asList(JsonObject.builder()
						.array("arr", Arrays.asList(1, 2, 3)).done()));
		byte[] encoded = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		List<Object> data = decoded.getData();
		assertNotNull(data.get(0));
		JsonObject obj = (JsonObject) data.get(0);
		assertEquals(3, obj.getArray("arr").size());
	}

	@Test
	public void packetWithBinary() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0,
				Arrays.asList(new byte[] { 1, 2, 3 }));
		byte[] encoded = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		List<Object> data = decoded.getData();
		assertEquals(3, ((byte[])data.get(0)).length);
		assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[])data.get(0));
	}
	
	@Test
	public void packetWithLotsOfData() {
		List<Object> in = Arrays.asList(
				"hello", 
				null,
				new byte[] { 1, 2, 3 }, 
				1, 
				JsonObject.builder().array("arr", Arrays.asList(1, 2, 3)).done());
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0,
				in);
		byte[] encoded = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		List<Object> data = decoded.getData();
		assertEquals(5, data.size());
		assertEquals("hello", data.get(0));
		assertNull(data.get(1));
		assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[])data.get(2));
		assertEquals(1, data.get(3));
		JsonObject obj = (JsonObject) data.get(4);
		assertEquals(3, obj.getArray("arr").size());
	}
}
