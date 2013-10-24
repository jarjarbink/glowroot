/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.container.javaagent;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import checkers.nullness.quals.Nullable;
import com.google.common.net.MediaType;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Cookie;
import com.ning.http.client.Response;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import io.informant.markers.ThreadSafe;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
class JavaagentHttpClient {

    private final int uiPort;
    private final AsyncHttpClient asyncHttpClient;
    @Nullable
    private volatile Cookie sessionIdCookie;

    JavaagentHttpClient(int uiPort, AsyncHttpClient asyncHttpClient) {
        this.uiPort = uiPort;
        this.asyncHttpClient = asyncHttpClient;
    }

    String get(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + uiPort
                + path);
        Response response = execute(request);
        return validateAndReturnBody(response);
    }

    InputStream getAsStream(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + uiPort
                + path);
        Response response = execute(request);
        return validateAndReturnBodyAsStream(response);
    }

    String post(String path, String data) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.preparePost("http://localhost:" + uiPort
                + path);
        request.setBody(data);
        Response response = execute(request);
        return validateAndReturnBody(response);
    }

    private Response execute(BoundRequestBuilder request) throws InterruptedException,
            ExecutionException, IOException {
        populateSessionIdCookie(request);
        Response response = request.execute().get();
        extractSessionIdCookie(response);
        return response;
    }

    private void populateSessionIdCookie(BoundRequestBuilder request) {
        if (sessionIdCookie != null) {
            request.addCookie(sessionIdCookie);
        }
    }

    private void extractSessionIdCookie(Response response) {
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getName().equals("INFORMANT_SESSION_ID")) {
                if (cookie.getValue().equals("")) {
                    sessionIdCookie = null;
                } else {
                    sessionIdCookie = cookie;
                }
                return;
            }
        }
    }

    private static String validateAndReturnBody(Response response) throws Exception {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBody();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    private static InputStream validateAndReturnBodyAsStream(Response response) throws Exception {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBodyAsStream();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    // this method relies on io.informant.testkit.InformantContainer.SaveTheEncodingHandler
    // being inserted into the Netty pipeline before the decompression handler (which removes the
    // Content-Encoding header after decompression) so that the original Content-Encoding can be
    // still be retrieved via the alternate http header X-Original-Content-Encoding
    private static boolean wasUncompressed(Response response) {
        String contentType = response.getHeader(CONTENT_TYPE);
        if (MediaType.ZIP.toString().equals(contentType)) {
            // zip file downloads are never compressed (e.g. trace export)
            return false;
        }
        String contentLength = response.getHeader(CONTENT_LENGTH);
        if ("0".equals(contentLength)) {
            // zero-length responses are never compressed
            return false;
        }
        String contentEncoding = response.getHeader("X-Original-Content-Encoding");
        return !"gzip".equals(contentEncoding);
    }
}
