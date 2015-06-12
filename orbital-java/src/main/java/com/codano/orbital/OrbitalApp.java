package com.codano.orbital;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import com.codano.orbital.ipc.IpcPipe;
import com.codano.orbital.proxy.ProxyBuilder;

public class OrbitalApp {
	/**
	 * Semaphore that indicates we should defer the RPC response.
	 */
	private static final Object DEFER_RESPONSE = new Object();

	private String appRoot, electronPath;
	private LinkedBlockingQueue<OrbitalAppPacket> send = new LinkedBlockingQueue<>();
	private AtomicInteger seqId = new AtomicInteger();
	private HashMap<Integer, CallbackData<OrbitalAppCallback>> callbacks = new HashMap<>();
	private HashMap<String, CallbackData<OrbitalAppAsyncRpc>> listeners = new HashMap<>();
	private HashMap<String, CallbackData<OrbitalAppHttpEndpoint>> httpListeners = new HashMap<>();
	private ExecutorService utilityThread = Executors.newSingleThreadExecutor();

	private static interface IORunnable {
		void run() throws IOException, InterruptedException;
	}
	
	private interface OrbitalAppCallback {
		void callback(Object data);
	}

	private static class CallbackData<T> {
		Executor executor;
		T callback;
	}

	public OrbitalApp() {
		// Waiting on https://github.com/atom/electron/issues/410
//		registerListener("http.request", utilityThread, (ep, data) -> {
//			return handleIncomingHttpRequest(data);
//		});
		
		appRoot = System.getProperty("app.root");
		electronPath = System.getProperty("electron.path");
	}
	
	public void setAppRoot(String appRoot) {
		this.appRoot = appRoot;
	}
	
	public void setElectronPath(String electronPath) {
		this.electronPath = electronPath;
	}
	
	public Object callBlocking(String endpoint, List<Object> arguments) {
		LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>(1);
		call(endpoint, arguments, utilityThread, (retval) -> {
			try {
				q.put(retval);
			} catch (Exception e) {
				// abort
			}
		});
		
		try {
			return q.take();
		} catch (InterruptedException e) {
			// TODO: invocation exception?
			Thread.currentThread().interrupt();
			throw new RuntimeException("Thread was interrupted during blocking call");
		}
	}
	
	public void call(String endpoint, List<Object> arguments, Executor executor, OrbitalAppCallback callback) {
		int seq = seqId.incrementAndGet();
		CallbackData<OrbitalAppCallback> callbackData = new CallbackData<>();
		callbackData.callback = callback;
		callbackData.executor = executor;
		synchronized (callbacks) {
			callbacks.put(seq, callbackData);
		}
		
		send.add(new OrbitalAppPacket(true, endpoint, seq, arguments));
	}

	public void callNoReturn(String endpoint, List<Object> data) {
		// seq ID of zero -- no return value requested
		send.add(new OrbitalAppPacket(true, endpoint, 0, data));
	}

	public void start() throws IOException, InterruptedException {
		IpcPipe pipe;
		Process process = null;

		System.out.println("Hello 2!");

		if (System.getProperty("PIPE") == null) {
			System.out.println("Launching Electron");
			pipe = IpcPipe.create();
			process = launchElectron(pipe);
		} else {
			System.out.println("Connecting to pipe: " + System.getProperty("PIPE"));
			pipe = IpcPipe.connect(System.getProperty("PIPE"));
			System.out.println("Connected to pipe: " + System.getProperty("PIPE"));
		}
		
		pipe.start();
		
		startIOThread(() -> writeTo(pipe.getOutputStream()));
		startIOThread(() -> readFrom(pipe.getInputStream()));

		if (process != null) {
			System.out.println(process.waitFor());
			System.exit(0);
		}
	}

	private void startIOThread(IORunnable runnable) {
		Thread t = new Thread(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		});
		t.setName("IO thread");
		t.start();
	}
	
	private Process launchElectron(IpcPipe pipe) throws IOException,
			FileNotFoundException {
		ProcessBuilder processBuilder = new ProcessBuilder(electronPath, appRoot);
		processBuilder.environment().put("PIPE", pipe.getName());
		Process process = processBuilder.start();
		
		System.out.println("Launching " + processBuilder.command());
		
		drain(process.getInputStream(), System.out);
		drain(process.getErrorStream(), System.err);

		return process;
	}

	private void drain(InputStream is, PrintStream out) {
		startIOThread(() -> {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					is, Charsets.UTF_8));
			while (true) {
				String s = in.readLine();
				if (s == null)
					return;
				out.println(s);
			}
		});
	}

	private void writeTo(OutputStream out) throws InterruptedException,
			IOException {
		while (true) {
			OrbitalAppPacket packet = send.take();
			byte[] buffer = PacketCoder.encode(packet);
			String len = "*" + Integer.toString(buffer.length, 16) + "\n";
			out.write(len.getBytes(Charsets.US_ASCII));
			out.write(buffer);
			out.flush();
		}
	}

	private void readFrom(InputStream in) throws IOException {
		StringBuilder outOfSync = new StringBuilder();
		
		while (true) {
			int b = in.read();
			// EOF
			if (b == -1)
				return;

			// Look for the sync byte (0xff)
			if (b != 0xff) {
				if (b == '\n') {
					System.err.println("Out of sync data received: " + outOfSync);
					outOfSync.setLength(0);
				} else {
					outOfSync.append((char)b);
				}
				continue;
			}
			
			if (outOfSync.length() > 0) {
				System.err.println("Out of sync data received: " + outOfSync);
				outOfSync.setLength(0);
			}
			
			StringBuilder length = new StringBuilder();
			while (true) {				
				b = in.read();
				
				// EOF
				if (b == -1)
					return;
				
				if (b == 0xa) {
					byte[] buffer = new byte[Integer.parseInt(length.toString(), 16)];
					IOUtils.readFully(in, buffer);
					
					handleIncomingPacket(buffer);
					break;
				} else {
					length.append((char)b);
				}
			}
		}
	}

//	private List<Object> handleIncomingHttpRequest(List<Object> data) {
//		String url = data.getJson().getString("url");
//		CallbackData<OrbitalAppHttpEndpoint> callback = httpListeners.get(url);
//		callback.executor.execute(() -> {
//			@SuppressWarnings({ "unchecked", "rawtypes" })
//			Map<String, String> query = (Map)data.getJson().getObject("query");
//			OrbitalAppHttpResponse response = callback.callback.request(url, query);
//			
//		});
//		
//		return DEFER_RESPONSE;
//	}

	private void handleIncomingPacket(byte[] buffer) {
		OrbitalAppPacket packet = PacketCoder.decode(buffer);
		String ep = packet.getEndpoint();
		int seqId = packet.getSeqId();
		if (ep == null) {
			CallbackData<OrbitalAppCallback> callback;
			synchronized (callbacks) {
				callback = callbacks.remove(seqId);
			}
			if (callback != null) {
				callback.executor.execute(() -> {
					callback.callback.callback(packet.getData());
				});
			} else {
				System.err.println("No callback for seq ID " + seqId);
			}
		} else {
			if (listeners.containsKey(ep)) {
				CallbackData<OrbitalAppAsyncRpc> listener = listeners.get(ep);
				listener.executor.execute(() -> {
					listener.callback.handle(ep, packet.getData(), (data) -> {
						// The listener will deal with this
						if (data == DEFER_RESPONSE)
							return;
						
						if (seqId != 0) {
							// Send return value
									send.add(new OrbitalAppPacket(false, null,
											seqId, data == null ? null : Arrays
													.asList(data)));
						}
					});					
				});
			} else {
				System.err.println("No listener for " + ep);
			}
		}
	}

	public void registerListener(String string, Executor executor, OrbitalAppVoidRpc rpc) {
		CallbackData<OrbitalAppAsyncRpc> data = new CallbackData<>();
		data.callback = (ep, d, cb) -> {
			rpc.handle(ep, d);
			cb.accept(null);
		};
		data.executor = executor;
		listeners.put(string, data);
	}
	
	public void registerListener(String string, Executor executor, OrbitalAppRpc rpc) {
		CallbackData<OrbitalAppAsyncRpc> data = new CallbackData<>();
		data.callback = (ep, d, cb) -> {
			cb.accept(rpc.handle(ep, d));
		};
		data.executor = executor;
		listeners.put(string, data);
	}

	public void registerListener(String string, Executor executor, OrbitalAppAsyncRpc rpc) {
		CallbackData<OrbitalAppAsyncRpc> data = new CallbackData<>();
		data.callback = rpc;
		data.executor = executor;
		listeners.put(string, data);
	}

	public void registerHttpEndpoint(String endpoint, Executor executor, OrbitalAppHttpEndpoint listener) {
		CallbackData<OrbitalAppHttpEndpoint> data = new CallbackData<>();
		data.callback = listener;
		data.executor = executor;
		httpListeners.put(endpoint, data);
	}

	public <T extends OrbitalRemoteProxy> T getRemoteProxy(String name, Class<T> clazz) {
		return ProxyBuilder.of(this, name, null, clazz).get();
	}
	
	public <T extends OrbitalRemoteProxy> T getRemoteProxy(String name, Executor executor, Class<T> clazz) {
		return ProxyBuilder.of(this, name, executor, clazz).get();
	}
}
