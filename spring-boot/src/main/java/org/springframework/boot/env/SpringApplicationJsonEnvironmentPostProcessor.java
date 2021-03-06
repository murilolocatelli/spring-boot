/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.env;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * An {@link EnvironmentPostProcessor} that parses JSON from
 * {@code spring.application.json} or equivalently {@code SPRING_APPLICATION_JSON} and
 * adds it as a map property source to the {@link Environment}. The new properties are
 * added with higher priority than the system properties.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 1.3.0
 */
public class SpringApplicationJsonEnvironmentPostProcessor
		implements EnvironmentPostProcessor, Ordered {

	private static final String SERVLET_ENVIRONMENT_CLASS = "org.springframework.web."
			+ "context.support.StandardServletEnvironment";

	/**
	 * The default order for the processor.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	private static final Log logger = LogFactory
			.getLog(SpringApplicationJsonEnvironmentPostProcessor.class);

	private int order = DEFAULT_ORDER;

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {
		MutablePropertySources propertySources = environment.getPropertySources();
		PropertySource<?> source = StreamSupport.stream(propertySources.spliterator(), false)
				.filter(s -> getProperty(s) != null)
				.findFirst().orElse(null);
		if (source != null) {
			String json = (String) getProperty(source);
			processJson(environment, json, source);
		}
	}

	private Object getProperty(PropertySource<?> source) {
		if (source.containsProperty("spring.application.json")) {
			return source.getProperty("spring.application.json");
		}
		return source.getProperty("SPRING_APPLICATION_JSON");
	}

	private void processJson(ConfigurableEnvironment environment, String json, PropertySource source) {
		try {
			JsonParser parser = JsonParserFactory.getJsonParser();
			Map<String, Object> map = parser.parseMap(json);
			if (!map.isEmpty()) {
				addJsonPropertySource(environment,
						new OriginTrackedMapPropertySource("spring.application.json", flatten(map, source)));
			}
		}
		catch (Exception ex) {
			logger.warn("Cannot parse JSON for spring.application.json: " + json, ex);
		}
	}

	/**
	 * Flatten the map keys using period separator.
	 * @param map The map that should be flattened
	 * @param source The property source for spring.application.json or SPRING_APPLICATION_JSON
	 * @return the flattened map
	 */
	private Map<String, Object> flatten(Map<String, Object> map, PropertySource source) {
		Map<String, Object> result = new LinkedHashMap<>();
		flatten(null, result, map, source);
		return result;
	}

	private void flatten(String prefix, Map<String, Object> result,
			Map<String, Object> map, PropertySource source) {
		prefix = (prefix == null ? "" : prefix + ".");
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			extract(prefix + entry.getKey(), result, entry.getValue(), source);
		}
	}

	@SuppressWarnings("unchecked")
	private void extract(String name, Map<String, Object> result, Object value, PropertySource source) {
		if (value instanceof Map) {
			flatten(name, result, (Map<String, Object>) value, source);
		}
		else if (value instanceof Collection) {
			int index = 0;
			for (Object object : (Collection<Object>) value) {
				extract(name + "[" + index + "]", result, object, source);
				index++;
			}
		}
		else {
			OriginTrackedValue originTrackedValue = OriginTrackedValue.of(value, PropertySourceOrigin.get(source, name));
			result.put(name, originTrackedValue);
		}
	}

	private void addJsonPropertySource(ConfigurableEnvironment environment,
			PropertySource<?> source) {
		MutablePropertySources sources = environment.getPropertySources();
		String name = findPropertySource(sources);
		if (sources.contains(name)) {
			sources.addBefore(name, source);
		}
		else {
			sources.addFirst(source);
		}
	}

	private String findPropertySource(MutablePropertySources sources) {
		if (ClassUtils.isPresent(SERVLET_ENVIRONMENT_CLASS, null) && sources
				.contains(StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME)) {
			return StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME;

		}
		return StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;
	}

}
