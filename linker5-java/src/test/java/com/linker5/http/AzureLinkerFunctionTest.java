package com.linker5.http;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureLinkerFunctionTest {

    @Test
    void shouldPreferForwardedHeadersWhenResolvingPublicBaseUrl() {
        FakeHttpRequest request = new FakeHttpRequest(HttpMethod.POST, URI.create("https://example.azurewebsites.net/link"), Optional.of(""));
        request.getHeaders().put("host", "ignored-host");
        request.getHeaders().put("x-forwarded-host", "short.example.com");
        request.getHeaders().put("x-forwarded-proto", "https");

        assertEquals("short.example.com", AzureLinkerFunction.resolveHost(request));
        assertEquals("https", AzureLinkerFunction.resolveScheme(request));
        assertEquals("https://short.example.com", LinkerApiHandler.derivePublicBaseUrl(AzureLinkerFunction.resolveHost(request), AzureLinkerFunction.resolveScheme(request)));
    }

    @Test
    void shouldMapSharedResponsesToAzureHttpResponses() {
        FakeHttpRequest request = new FakeHttpRequest(HttpMethod.GET, URI.create("https://example.azurewebsites.net/healthz"), Optional.empty());
        LinkerResponse response = LinkerResponse.json(200, "{\"status\":\"ok\"}");

        HttpResponseMessage httpResponse = AzureLinkerFunction.toHttpResponse(request, response);

        assertEquals(HttpStatus.OK, httpResponse.getStatus());
        assertEquals("application/json", httpResponse.getHeader("Content-Type"));
        assertEquals("{\"status\":\"ok\"}", httpResponse.getBody());
    }

    @Test
    void shouldServeRootUiContentForAzure() throws Exception {
        FakeHttpRequest request = new FakeHttpRequest(HttpMethod.GET, URI.create("https://example.azurewebsites.net/"), Optional.empty());

        HttpResponseMessage httpResponse = AzureLinkerFunction.toHttpResponse(request, StaticUiHandler.tryServe("/").orElseThrow());

        assertEquals(HttpStatus.OK, httpResponse.getStatus());
        assertEquals("text/html", httpResponse.getHeader("Content-Type"));
        assertTrue(httpResponse.getBody().toString().contains("<title>Linker</title>"));
    }

    @Test
    void shouldServeCssAndJsAssetsForAzureWithExpectedContentTypes() throws Exception {
        FakeHttpRequest request = new FakeHttpRequest(HttpMethod.GET, URI.create("https://example.azurewebsites.net/css/style.css"), Optional.empty());

        HttpResponseMessage cssResponse = AzureLinkerFunction.toHttpResponse(request, StaticUiHandler.tryServe("/css/style.css").orElseThrow());
        HttpResponseMessage jsResponse = AzureLinkerFunction.toHttpResponse(request, StaticUiHandler.tryServe("/js/app.js").orElseThrow());

        assertEquals(HttpStatus.OK, cssResponse.getStatus());
        assertEquals("text/css", cssResponse.getHeader("Content-Type"));
        assertTrue(cssResponse.getBody().toString().contains("font-family:Arial"));

        assertEquals(HttpStatus.OK, jsResponse.getStatus());
        assertEquals("application/javascript", jsResponse.getHeader("Content-Type"));
        assertTrue(jsResponse.getBody().toString().contains("fetch(\"/link\""));
    }

    @Test
    void shouldNormalizeAzureCatchAllPath() {
        assertEquals("/", AzureLinkerFunction.normalizePath(null));
        assertEquals("/", AzureLinkerFunction.normalizePath(""));
        assertEquals("/healthz", AzureLinkerFunction.normalizePath("healthz"));
        assertEquals("/css/style.css", AzureLinkerFunction.normalizePath("css/style.css"));
    }

    private static final class FakeHttpRequest implements HttpRequestMessage<Optional<String>> {
        private final HttpMethod method;
        private final URI uri;
        private final Optional<String> body;
        private final Map<String, String> headers = new HashMap<>();

        private FakeHttpRequest(HttpMethod method, URI uri, Optional<String> body) {
            this.method = method;
            this.uri = uri;
            this.body = body;
        }

        @Override public HttpMethod getHttpMethod() { return method; }
        @Override public URI getUri() { return uri; }
        @Override public Map<String, String> getHeaders() { return headers; }
        @Override public Map<String, String> getQueryParameters() { return Map.of(); }
        @Override public Optional<String> getBody() { return body; }
        @Override public HttpResponseMessage.Builder createResponseBuilder(HttpStatus status) { return new FakeHttpResponseBuilder(status); }
        @Override public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) { return new FakeHttpResponseBuilder(status); }
    }

    private static final class FakeHttpResponseBuilder implements HttpResponseMessage.Builder {
        private final HttpStatusType status;
        private final Map<String, String> headers = new HashMap<>();
        private Object body;

        private FakeHttpResponseBuilder(HttpStatusType status) {
            this.status = status;
        }

        @Override public HttpResponseMessage.Builder status(HttpStatusType status) { return new FakeHttpResponseBuilder(status); }
        @Override public HttpResponseMessage.Builder header(String key, String value) { headers.put(key, value); return this; }
        @Override public HttpResponseMessage.Builder body(Object body) { this.body = body; return this; }
        @Override public HttpResponseMessage build() { return new FakeHttpResponse(status, headers, body); }
    }

    private static final class FakeHttpResponse implements HttpResponseMessage {
        private final HttpStatusType status;
        private final Map<String, String> headers;
        private final Object body;

        private FakeHttpResponse(HttpStatusType status, Map<String, String> headers, Object body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        @Override public int getStatusCode() { return status.value(); }
        @Override public String getHeader(String key) { return headers.get(key); }
        @Override public Object getBody() { return body; }
        @Override public HttpStatusType getStatus() { return status; }
    }
}
