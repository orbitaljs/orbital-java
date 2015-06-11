package com.codano.orbital;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class OrbitalAppMain {
	private static Thread t;
	
	public static void main(String[] args) {
		// Disable stdin as we've got two VMs fighting for it
		System.setIn(new ByteArrayInputStream(new byte[0]));
		
		String main = System.getProperty("MAIN");
		System.out.println("Booting main: " + main);

		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			e.printStackTrace();
		});
		
		t = new Thread(() -> invokeMain(main));
		t.setName("main");
		t.setDaemon(false);
		t.setUncaughtExceptionHandler((t, e) -> {
			e.printStackTrace();
		});
		t.start();
	}

	private static void invokeMain(String main) {
		try {
			Class<?> mainClass = Class.forName(main);
			for (Method method : mainClass.getDeclaredMethods()) {
				if (method.getName().equals("main")
						&& ((method.getModifiers() & Modifier.STATIC) != 0)) {
					// TODO: would be nice to pass process.argv from node.js
					// here
					System.out.println("Found " + method);
					method.invoke(null, new Object[] { new String[0] });
					System.out.println("Done");
					return;
				}
			}
			
			throw new Exception("Unable to locate static main() method on class " + main);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
