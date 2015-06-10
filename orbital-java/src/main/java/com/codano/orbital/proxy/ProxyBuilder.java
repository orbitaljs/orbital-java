package com.codano.orbital.proxy;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import com.codano.orbital.Blocking;
import com.codano.orbital.NonBlocking;
import com.codano.orbital.OrbitalApp;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

public class ProxyBuilder<T> {
	private Class<T> clazz;
	private T proxy;
	private static final Set<Class<?>> PRIMITIVE_TYPES = new HashSet<>();
	private OrbitalApp app;
	private String name;
	private Map<String, RpcReturnType> returnTypes = new HashMap<>();

	static {
		PRIMITIVE_TYPES.add(int.class);
		PRIMITIVE_TYPES.add(float.class);
		PRIMITIVE_TYPES.add(double.class);
		PRIMITIVE_TYPES.add(boolean.class);
		PRIMITIVE_TYPES.add(String.class);
	}
	
	private ProxyBuilder(OrbitalApp app, String name, Class<T> clazz) {
		this.app = app;
		this.name = name;
		this.clazz = clazz;
		
		validate();
		generate();
	}
	
	public static <X> ProxyBuilder<X> of(OrbitalApp app, String name,
			Executor executor, Class<X> clazz) {
		return new ProxyBuilder<X>(app, name, clazz);
	}
	
	private void validate() {
		for (Method method : clazz.getMethods()) {
			String methodName = name + "." + method.getName();
			
			RpcReturnType returnType;
			
			// TODO: Offer either Future<> or Callback<> return types
			if (method.getReturnType() == void.class) {
				// Void is cool if annotation properly
				boolean blocking = method.getAnnotation(Blocking.class) != null;
				boolean nonblocking = method.getAnnotation(NonBlocking.class) != null;
				
				if (!blocking && !nonblocking)
					throw new IllegalArgumentException("Void method "
							+ method.getName()
							+ " must be annotated as @Blocking or @NonBlocking");

				if (blocking && nonblocking)
					throw new IllegalArgumentException("Void method "
							+ method.getName()
							+ " cannot be annotated as both @Blocking and @NonBlocking");
				
				if (blocking)
					returnType = new VoidBlockingReturnType(app, methodName);
				else
					returnType = new VoidNonBlockingReturnType(app, methodName);
			} else {
				validate(method.getName(), method.getReturnType());
				returnType = new NonVoidBlockingReturnType(app, methodName);
			}
			
			returnTypes.put(method.getName(), returnType);
			
			for (Class<?> clazz : method.getParameterTypes()) {
				validate(method.getName(), clazz);
			}
		}
	}

	private void validate(String name, Class<?> type) {
		// These are cool
		if (PRIMITIVE_TYPES.contains(type))
			return;
		
		// Arrays of primitive types are cool
		if (type.isArray() && PRIMITIVE_TYPES.contains(type.getComponentType()))
			return;
		
		// Json types are cool
		if (type == JsonObject.class || type == JsonArray.class)
			return;
		
		throw new ClassCastException("Invalid type " + type + " for method " + name);
	}

	@SuppressWarnings("unchecked")
	private void generate() {
		proxy = (T) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { clazz }, (proxy, method, args) -> {
			return returnTypes.get(method.getName()).invoke(Arrays.asList(args));
		});
	}

	public T get() {
		return proxy;
	}
}
