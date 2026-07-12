package com.linker5.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AzureLinkerFunction {

    @FunctionName("createLink")
    public HttpResponseMessage createLink(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "link")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
        return handle(request, context, "/link");
    }

    @FunctionName("rootUi")
    public HttpResponseMessage rootUi(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
        return handle(request, context, "/");
    }

    @FunctionName("getRoute")
    public HttpResponseMessage getRoute(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "{*path}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("path") String path,
            final ExecutionContext context) throws Exception {
        return handle(request, context, normalizePath(path));
    }

    @FunctionName("headLink")
    public HttpResponseMessage headLink(
            @HttpTrigger(name = "req", methods = {HttpMethod.HEAD}, authLevel = AuthorizationLevel.ANONYMOUS, route = "{id}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) throws Exception {
        return handle(request, context, "/" + id);
    }

    @FunctionName("deleteLink")
    public HttpResponseMessage deleteLink(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS, route = "{id}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) throws Exception {
        return handle(request, context, "/" + id);
    }

    static HttpResponseMessage toHttpResponse(HttpRequestMessage<Optional<String>> request, LinkerResponse response) {
        HttpResponseMessage.Builder builder = request.createResponseBuilder(HttpStatus.valueOf(response.status()));
        if (response.contentType() != null) {
            builder.header("Content-Type", response.contentType());
        }
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        if (!response.omitBody()) {
            builder.body(new String(response.body(), StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private HttpResponseMessage handle(HttpRequestMessage<Optional<String>> request, ExecutionContext context, String path) throws Exception {
        Optional<LinkerResponse> staticResponse = StaticUiHandler.tryServe(path);
        if (staticResponse.isPresent()) {
            context.getLogger().info("Serving Azure Function static UI request " + request.getHttpMethod() + " " + path);
            return toHttpResponse(request, staticResponse.get());
        }

        LinkerApplicationRuntime runtime = RuntimeHolder.getOrCreate();
        context.getLogger().info("Dispatching Azure Function request " + request.getHttpMethod() + " " + path);
        LinkerApiHandler handler = new LinkerApiHandler(runtime.linker(), runtime.db(), runtime.observability(), runtime.gson(), runtime.logger());
        LinkerRequest linkerRequest = new LinkerRequest(
                request.getHttpMethod().name(),
                path,
                resolveHost(request),
                resolveScheme(request),
                request.getBody().orElse("")
        );
        return toHttpResponse(request, handler.handle(linkerRequest));
    }

    static String resolveHost(HttpRequestMessage<Optional<String>> request) {
        String forwardedHost = firstHeader(request, "x-forwarded-host");
        if (forwardedHost != null) {
            return forwardedHost;
        }
        return firstHeader(request, "host");
    }

    static String resolveScheme(HttpRequestMessage<Optional<String>> request) {
        String forwardedProto = firstHeader(request, "x-forwarded-proto");
        if (forwardedProto != null) {
            return forwardedProto;
        }
        return "https";
    }

    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return "/" + path;
    }

    private static String firstHeader(HttpRequestMessage<Optional<String>> request, String headerName) {
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static final class RuntimeHolder {
        private static volatile LinkerApplicationRuntime runtime;

        private RuntimeHolder() {
        }

        static LinkerApplicationRuntime getOrCreate() throws Exception {
            if (runtime == null) {
                synchronized (RuntimeHolder.class) {
                    if (runtime == null) {
                        runtime = LinkerApplicationRuntime.initialize();
                    }
                }
            }
            return runtime;
        }
    }
}
