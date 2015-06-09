package com.codano.hybridapp.ipc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

class IpcPipeWindows extends IpcPipe {
	private RandomAccessFile file;

	public IpcPipeWindows(String name) {
		super(name == null ? generate() : name);
	}
	
	private static String generate() {
		return "\\\\.\\pipe\\ipc-" + UUID.randomUUID();
	}

	protected void attemptConnect() throws IOException {
		file = new RandomAccessFile(name, "rw");
		
		is = new InputStream() {
			@Override
			public int read() throws IOException {
				return file.read();
			}
			
			@Override
			public int read(byte[] b) throws IOException {
				return file.read(b);
			}
			
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return file.read(b, off, len);
			}
		};
		
		os = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				file.write(b);
			}
			
			@Override
			public void write(byte[] b) throws IOException {
				file.write(b);
			}
			
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				file.write(b, off, len);
			}
		};
	}
	
	public void close() {
		IOUtils.closeQuietly(file);
		file = null;
		is = null;
		os = null;
	}
}
