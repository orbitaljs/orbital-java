package com.codano.orbital;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class OrbitalAppMain {
	public static void main(String[] args) {
		String main = args[0];
		System.out.println("Booting main: " + main);

		Thread t = new Thread(() -> invokeMain(main));
		t.setName("main");
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
					method.invoke(null, new Object[0]);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
