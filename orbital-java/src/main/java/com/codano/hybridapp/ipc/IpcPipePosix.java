package com.codano.hybridapp.ipc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

class IpcPipePosix extends IpcPipe {
	IpcPipePosix(String name) throws IOException {
		super(name == null ? generate() : name);
	}

	private static String generate() throws IOException {
		File tmp = File.createTempFile("ipc", "");
		tmp.delete();
		return tmp.getCanonicalPath();
	}

	/**
	 * Attempt to open both FIFOs simultaneously. Note that if we fail to open
	 * both properly the other end may get a spurious EOF and have to re-open
	 * their end as well.
	 */
	@Override
	protected void attemptConnect() throws IOException {
		File fifo = new File(name, "fifo");
		File in = new File(fifo, "i");
		File out = new File(fifo, "o");

		try {
			is = new FileInputStream(in);
			os = new FileOutputStream(out);
		} catch (IOException e) {
			if (is != null) {
				IOUtils.closeQuietly(is);
				is = null;
			}
			if (os != null) {
				IOUtils.closeQuietly(os);
				os = null;
			}
			
			throw e;
		}
	}
	
	public void close() {
		IOUtils.closeQuietly(is);
		IOUtils.closeQuietly(os);
	}
}
