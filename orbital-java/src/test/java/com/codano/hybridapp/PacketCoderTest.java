package com.codano.hybridapp;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.codano.orbital.OrbitalAppData;
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
		assertNull(decoded.getData().getBinary());
		assertNull(decoded.getData().getJson());
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
				new OrbitalAppData(JsonObject.builder()
						.array("arr", Arrays.asList(1, 2, 3)).done()));
		byte[] encoded = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		OrbitalAppData data = decoded.getData();
		assertNull(data.getBinary());
		assertNotNull(data.getJson());
		assertEquals(3, ((List<Object>) data.getJson().getArray("arr")).size());
	}

	@Test
	public void packetWithBinary() {
		OrbitalAppPacket packet = new OrbitalAppPacket(true, null, 0,
				new OrbitalAppData(new byte[] { 1, 2, 3 }));
		byte[] encoded = PacketCoder.encode(packet);
		OrbitalAppPacket decoded = PacketCoder.decode(encoded);

		assertTrue(decoded.isCall());
		assertEquals(0, decoded.getSeqId());
		assertNull(decoded.getEndpoint());
		OrbitalAppData data = decoded.getData();
		assertNotNull(data.getBinary());
		assertEquals(3, data.getBinary().length);
		assertArrayEquals(new byte[] { 1, 2, 3 }, data.getBinary());
		assertNull(data.getJson());
	}
}
