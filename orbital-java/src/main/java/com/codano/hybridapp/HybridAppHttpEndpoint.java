package com.codano.hybridapp;

import java.util.Map;

public interface HybridAppHttpEndpoint {
	HybridAppHttpResponse request(String url, Map<String, String> query);
}
