package com.zaqpiotr.webviewrequestmodifier;

import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class WebViewRequest {
    public final RequestType type;
    public final String url;
    public final String method;
    public final String body;
    public final Map<String, String> formParameters;
    public final Map<String, String> headers;
    public final String trace;
    public final String enctype;

    public WebViewRequest(
        RequestType type,
        String url,
        String method,
        String body,
        Map<String, String> formParameters,
        Map<String, String> headers,
        String trace,
        String enctype
    ) {
        this.type = type;
        this.url = url;
        this.method = method;
        this.body = body;
        this.formParameters = formParameters;
        this.headers = headers;
        this.trace = trace;
        this.enctype = enctype;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Type: ").append(type).append("\n");
        sb.append("  URL: ").append(url).append("\n");
        sb.append("  Method: ").append(method).append("\n");
        sb.append("  Body: ").append(body).append("\n");

        sb.append("  Headers:");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append("\n       ").append(entry.getKey()).append(": ").append(entry.getValue());
        }

        sb.append("\n  FormParameters:");
        for (Map.Entry<String, String> entry : formParameters.entrySet()) {
            sb.append("\n       ").append(entry.getKey()).append(": ").append(entry.getValue());
        }

        sb.append("\n  Trace:").append(formatTrace(trace));
        sb.append("\n  Encoding type (form submissions only): ").append(enctype);

        return sb.toString();
    }

    public static WebViewRequest create(
        WebResourceRequest webResourceRequest,
        WebViewRequest recordedRequest
    ) {
        RequestType type = recordedRequest != null ? recordedRequest.type : RequestType.HTML;
        String url = webResourceRequest.getUrl().toString();
        String cookies = safeString(CookieManager.getInstance().getCookie(url));

        Map<String, String> headers = new HashMap<>();
        headers.put("cookie", cookies);

        if (recordedRequest != null) {
            mergeHeaders(headers, recordedRequest.headers);
        }

        mergeHeaders(headers, webResourceRequest.getRequestHeaders());

        return new WebViewRequest(
            type,
            url,
            webResourceRequest.getMethod(),
            recordedRequest != null ? recordedRequest.body : "",
            recordedRequest != null ? recordedRequest.formParameters : Collections.emptyMap(),
            headers,
            recordedRequest != null ? recordedRequest.trace : "",
            recordedRequest != null ? recordedRequest.enctype : null
        );
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }

    private static void mergeHeaders(Map<String, String> target, Map<String, String> source) {
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                target.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
    }

    private static String formatTrace(String trace) {
        if (trace == null || trace.isEmpty()) return "";
        String[] lines = trace.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            sb.append("\n    ").append(lines[i].trim());
        }
        return sb.toString();
    }
}