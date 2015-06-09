package com.codano.orbital;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.commons.io.Charsets;

public class OrbitalAppHttpResponse {
	private String mimeType;
	private byte[] data;

	public OrbitalAppHttpResponse(String mimeType, byte[] data) {
		this.mimeType = mimeType;
		this.data = data;
	}

	public OrbitalAppHttpResponse(String mimeType, String data) {
		this.mimeType = mimeType;
		this.data = data.getBytes(Charsets.UTF_8);
	}

	public OrbitalAppHttpResponse(RenderedImage data) {
		this.mimeType = "image/png";
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			ImageIO.write(data, "png", output);
		} catch (IOException e) {
			// Should not be possible
			throw new RuntimeException(e);
		}
		this.data = output.toByteArray();
	}
	
	public byte[] getData() {
		return data;
	}
	
	public String getMimeType() {
		return mimeType;
	}
}
