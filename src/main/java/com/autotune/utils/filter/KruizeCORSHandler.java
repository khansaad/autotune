/*******************************************************************************
 * Copyright (c) 2023 Red Hat, IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.autotune.utils.filter;

import com.autotune.utils.KruizeConstants;
import org.eclipse.jetty.server.handler.CrossOriginHandler;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for the Jetty {@link CrossOriginHandler} that enforces the project CORS policy.
 *
 * <p>The handler replaces the deprecated {@code CrossOriginFilter}-based approach and is
 * wired as a {@link org.eclipse.jetty.server.Handler.Wrapper} around the servlet context in
 * {@code Autotune.java}.</p>
 *
 * <p>Allowed origins are driven by {@link KruizeConstants.CORSConstants#ALLOWED_ORIGINS},
 * which defaults to an empty string (same-origin only) and can be overridden at runtime via
 * the {@code KRUIZE_ALLOWED_ORIGINS} environment variable.</p>
 */
public class KruizeCORSHandler {

    private KruizeCORSHandler() {
    }

    /**
     * Creates and returns a configured {@link CrossOriginHandler}.
     *
     * <p>The handler is <em>not</em> started here; callers must start it as part of the
     * normal Jetty server lifecycle.</p>
     *
     * @return a fully configured {@link CrossOriginHandler}
     */
    public static CrossOriginHandler getHandler() {
        CrossOriginHandler handler = new CrossOriginHandler();

        // Allowed origins — empty set means no cross-origin requests are permitted.
        String originsConfig = KruizeConstants.CORSConstants.ALLOWED_ORIGINS;
        Set<String> origins = parseCommaSeparated(originsConfig);
        handler.setAllowedOriginPatterns(origins);

        // Allowed HTTP methods
        Set<String> methods = parseCommaSeparated(KruizeConstants.CORSConstants.ALLOWED_METHODS);
        handler.setAllowedMethods(methods);

        // Allowed request headers
        Set<String> headers = parseCommaSeparated(KruizeConstants.CORSConstants.ALLOWED_HEADERS);
        handler.setAllowedHeaders(headers);

        // Preflight max-age
        handler.setPreflightMaxAge(Duration.ofSeconds(Long.parseLong(KruizeConstants.CORSConstants.MAX_AGE)));

        // Credentials are not needed for Kruize API consumers
        handler.setAllowCredentials(false);

        return handler;
    }

    private static Set<String> parseCommaSeparated(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
