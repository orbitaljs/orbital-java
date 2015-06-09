package com.codano.hybridapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import com.codano.hybridapp.ipc.IpcPipe;

public class HybridApp {
	/**
	 * Semaphore that indicates we should defer the RPC response.
	 */
	private static final HybridAppData DEFER_RESPONSE = new HybridAppData();

	private String appRoot, webRoot, electronPath;
	private LinkedBlockingQueue<HybridAppPacket> send = new LinkedBlockingQueue<>();
	private AtomicInteger seqId = new AtomicInteger();
	private HashMap<Integer, CallbackData<HybridAppCallback>> callbacks = new HashMap<>();
	private HashMap<String, CallbackData<HybridAppAsyncRpc>> listeners = new HashMap<>();
	private HashMap<String, CallbackData<HybridAppHttpEndpoint>> httpListeners = new HashMap<>();
	private ExecutorService utilityThread = Executors.newSingleThreadExecutor();

	private static interface IORunnable {
		void run() throws IOException, InterruptedException;
	}
	
	private static class CallbackData<T> {
		Executor executor;
		T callback;
	}

	public HybridApp() {
		registerListener("http.request", utilityThread, (ep, data) -> {
			return handleIncomingHttpRequest(data);
		});
	}
	
	public void setAppRoot(String appRoot) {
		this.appRoot = appRoot;
	}

	public void setWebRoot(String webRoot) {
		this.webRoot = webRoot;
	}
	
	public void setElectronPath(String electronPath) {
		this.electronPath = electronPath;
	}
	
	public HybridAppData callBlocking(String endpoint, HybridAppData data) {
		LinkedBlockingQueue<HybridAppData> q = new LinkedBlockingQueue<>(1);
		call(endpoint, data, utilityThread, (retval) -> {
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
	
	public void call(String endpoint, HybridAppData data, Executor executor, HybridAppCallback callback) {
		int seq = seqId.incrementAndGet();
		CallbackData<HybridAppCallback> callbackData = new CallbackData<>();
		callbackData.callback = callback;
		callbackData.executor = executor;
		synchronized (callbacks) {
			callbacks.put(seq, callbackData);
		}
		
		send.add(new HybridAppPacket(true, endpoint, seq, data));
	}

	public void callNoReturn(String endpoint, HybridAppData data) {
		// seq ID of zero -- no return value requested
		send.add(new HybridAppPacket(true, endpoint, 0, data));
	}

	public void start() throws IOException, InterruptedException {
		IpcPipe pipe;
		Process process = null;

		if (System.getenv("PIPE") == null) {
			pipe = IpcPipe.create();
			process = launchElectron(pipe);
		} else {
			pipe = IpcPipe.connect(System.getenv("PIPE"));
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
		File parent = File.createTempFile("hybrid-app-", "");
		parent.delete();
		parent.mkdirs();
		File include = new File(parent, "include");
		include.mkdirs();
		
		File index = new File(parent, "index.js");
		File rpc = new File(include, "rpc.js");

		try (FileOutputStream out = new FileOutputStream(index)) {
			try (InputStream in = getClass().getResourceAsStream("/index.js")) {
				IOUtils.copy(in, out);
			}
		}
		try (FileOutputStream out = new FileOutputStream(rpc)) {
			try (InputStream in = getClass().getResourceAsStream("/rpc.js")) {
				IOUtils.copy(in, out);
			}
		}

		index.deleteOnExit();
		rpc.deleteOnExit();
		parent.deleteOnExit();
		
		ProcessBuilder processBuilder = new ProcessBuilder(electronPath, index.getAbsolutePath());
		processBuilder.environment().put("PIPE", pipe.getName());
		processBuilder.environment().put("NODE_PATH", include.getAbsolutePath());
		processBuilder.environment().put("APP_PATH", appRoot);
		processBuilder.environment().put("WEB_PATH", webRoot);
		Process process = processBuilder.start();
		
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
			HybridAppPacket packet = send.take();
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

	private HybridAppData handleIncomingHttpRequest(HybridAppData data) {
		String url = data.getJson().getString("url");
		CallbackData<HybridAppHttpEndpoint> callback = httpListeners.get(url);
		callback.executor.execute(() -> {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Map<String, String> query = (Map)data.getJson().getObject("query");
			HybridAppHttpResponse response = callback.callback.request(url, query);
			
		});
		
		return DEFER_RESPONSE;
	}

	private void handleIncomingPacket(byte[] buffer) {
		HybridAppPacket packet = PacketCoder.decode(buffer);
		String ep = packet.getEndpoint();
		int seqId = packet.getSeqId();
		if (ep == null) {
			CallbackData<HybridAppCallback> callback;
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
				CallbackData<HybridAppAsyncRpc> listener = listeners.get(ep);
				listener.executor.execute(() -> {
					listener.callback.handle(ep, packet.getData(), (data) -> {
						// The listener will deal with this
						if (data == DEFER_RESPONSE)
							return;
						
						if (data == null)
							data = new HybridAppData();
						
						if (seqId != 0) {
							// Send return value
							send.add(new HybridAppPacket(false, null, seqId, data));
						}
					});					
				});
			} else {
				System.err.println("No listener for " + ep);
			}
		}
	}

	public void registerListener(String string, Executor executor, HybridAppVoidRpc rpc) {
		CallbackData<HybridAppAsyncRpc> data = new CallbackData<>();
		data.callback = (ep, d, cb) -> {
			rpc.handle(ep, d);
			cb.accept(null);
		};
		data.executor = executor;
		listeners.put(string, data);
	}
	
	public void registerListener(String string, Executor executor, HybridAppRpc rpc) {
		CallbackData<HybridAppAsyncRpc> data = new CallbackData<>();
		data.callback = (ep, d, cb) -> {
			cb.accept(rpc.handle(ep, d));
		};
		data.executor = executor;
		listeners.put(string, data);
	}

	public void registerListener(String string, Executor executor, HybridAppAsyncRpc rpc) {
		CallbackData<HybridAppAsyncRpc> data = new CallbackData<>();
		data.callback = rpc;
		data.executor = executor;
		listeners.put(string, data);
	}

	public void registerHttpEndpoint(String endpoint, Executor executor, HybridAppHttpEndpoint listener) {
		CallbackData<HybridAppHttpEndpoint> data = new CallbackData<>();
		data.callback = listener;
		data.executor = executor;
		httpListeners.put(endpoint, data);
	}
}
