package com.codano.hybridapp.ipc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An {@link IpcPipe} is a named pipe in Windows and a pair of FIFOs in POSIX
 * environments (everything else).
 * 
 * When connecting to an {@link IpcPipe}, specify the full named pipe name in
 * Windows (ie: \\.\pipe\foo) and a directory in unix that will contain one fifo
 * named "i" and another named "o".
 * 
 * Note that Java cannot create either named pipes or FIFOs, so it will spin and
 * wait for the other side to create them.
 */
public abstract class IpcPipe {
	protected String name;
	protected InputStream is;
	protected OutputStream os;

	protected IpcPipe(String name) {
		this.name = name;
	}

	public InputStream getInputStream() {
		return is;
	}

	public OutputStream getOutputStream() {
		return os;
	}

	public String getName() {
		return name;
	}
	
	/**
	 * Creates an IPC pipe and generates a name for it.
	 */
	public static IpcPipe create() throws IOException {
		return connect(null);
	}

	/**
	 * Connects to an IPC pipe with a given name.
	 */
	public static IpcPipe connect(String name) throws IOException {
		if (System.getProperty("os.name").startsWith("windows")) {
			return new IpcPipeWindows(name);
		} else {
			return new IpcPipePosix(name);
		}
	}

	/**
	 * Spins while attempting to connect.
	 */
	public void start() {
		while (true) {
			try {
				attemptConnect();
				return;
			} catch (IOException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					return;
				}
			}
		}
	}

	protected abstract void attemptConnect() throws IOException;

	protected abstract void close();
}
