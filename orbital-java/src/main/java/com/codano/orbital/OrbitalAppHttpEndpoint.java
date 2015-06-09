package com.codano.orbital;

import java.util.Map;

public interface OrbitalAppHttpEndpoint {
	OrbitalAppHttpResponse request(String url, Map<String, String> query);
}
