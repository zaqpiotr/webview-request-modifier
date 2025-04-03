package com.zaqpiotr.webviewrequestmodifier;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JavaScriptRequestModifier {

    private static final String LOG_TAG = "RequestInspectorJs";
    private static final String MULTIPART_FORM_BOUNDARY = "----WebKitFormBoundaryU7CgQs9WnqlZYKs6";
    private static final String INTERFACE_NAME = "RequestInspection";

    private final List<WebViewRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());

    public JavaScriptRequestModifier(WebView webView) {
        webView.addJavascriptInterface(this, INTERFACE_NAME);
    }

    public WebViewRequest findRecordedRequestForUrl(String url) {
        synchronized (recordedRequests) {
            for (int i = recordedRequests.size() - 1; i >= 0; i--) {
                WebViewRequest request = recordedRequests.get(i);
            if (url.equals(request.url) || url.contains(request.url)) {
                return request;
            }
        }
            return null;
        }
    }

    @JavascriptInterface
    public void recordFormSubmission(String url, String method, String formParameterList, String headers, String trace, String enctype) {
        try {
            JSONArray formParameterJsonArray = new JSONArray(formParameterList);
            Map<String, String> headerMap = parseHeaders(headers);
            Map<String, String> formParameterMap = parseFormParameters(formParameterJsonArray);
            String body;

            switch (enctype) {
                case "application/x-www-form-urlencoded":
                headerMap.put("content-type", enctype);
                body = buildUrlEncodedBody(formParameterJsonArray);
                break;
                case "multipart/form-data":
                headerMap.put("content-type", "multipart/form-data; boundary=" + MULTIPART_FORM_BOUNDARY);
                body = buildMultipartBody(formParameterJsonArray);
                break;
                case "text/plain":
                headerMap.put("content-type", enctype);
                body = buildPlainTextBody(formParameterJsonArray);
                break;
                default:
                Log.e(LOG_TAG, "Incorrect encoding: " + enctype);
                body = "";
            }

            addRecordedRequest(new WebViewRequest(RequestType.FORM, url, method, body, formParameterMap, headerMap, trace, enctype));
            Log.i(LOG_TAG, "Recorded form submission from JavaScript");
        } catch (JSONException e) {
            Log.e(LOG_TAG, "JSON error in form submission", e);
        }
    }

    @JavascriptInterface
    public void recordXhr(String url, String method, String body, String headers, String trace) {
        Map<String, String> headerMap = parseHeaders(headers);
        addRecordedRequest(new WebViewRequest(RequestType.XML_HTTP, url, method, body, Collections.emptyMap(), headerMap, trace, null));
        Log.i(LOG_TAG, "Recorded XHR from JavaScript");
    }

    @JavascriptInterface
    public void recordFetch(String url, String method, String body, String headers, String trace) {
        Map<String, String> headerMap = parseHeaders(headers);
        addRecordedRequest(new WebViewRequest(RequestType.FETCH, url, method, body, Collections.emptyMap(), headerMap, trace, null));
        Log.i(LOG_TAG, "Recorded fetch from JavaScript");
    }

    private void addRecordedRequest(WebViewRequest request) {
        recordedRequests.add(request);
    }

    private Map<String, String> parseHeaders(String headersJson) {
        Map<String, String> headersMap = new HashMap<>();
        try {
            JSONObject headers = new JSONObject(headersJson);
            Iterator<String> keys = headers.keys();  // Use .keys() instead of .keySet()

            while (keys.hasNext()) {
                String key = keys.next();
                headersMap.put(key.toLowerCase(Locale.getDefault()), headers.getString(key));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error parsing headers", e);
        }
        return headersMap;
    }

    private Map<String, String> parseFormParameters(JSONArray paramsArray) {
        Map<String, String> paramMap = new HashMap<>();
        for (int i = 0; i < paramsArray.length(); i++) {
            JSONObject param = paramsArray.optJSONObject(i);
            if (param != null && !isExcludedParam(param)) {
                paramMap.put(param.optString("name"), param.optString("value"));
            }
        }
        return paramMap;
    }

    private boolean isExcludedParam(JSONObject param) {
        String type = param.optString("type");
        boolean checked = param.optBoolean("checked", false);
        return ("radio".equals(type) || "checkbox".equals(type)) && !checked;
    }

    private String buildUrlEncodedBody(JSONArray paramsArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paramsArray.length(); i++) {
            JSONObject param = paramsArray.optJSONObject(i);
            if (param != null && !isExcludedParam(param)) {
                try {
                    if (builder.length() > 0) builder.append('&');
                    builder.append(param.getString("name"))
                        .append('=')
                        .append(URLEncoder.encode(param.optString("value"), "UTF-8"));
                } catch (UnsupportedEncodingException | JSONException ignored) { }
            }
        }
        return builder.toString();
    }

    private String buildMultipartBody(JSONArray paramsArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paramsArray.length(); i++) {
            JSONObject param = paramsArray.optJSONObject(i);
            if (param != null && !isExcludedParam(param)) {
                builder.append("--").append(MULTIPART_FORM_BOUNDARY).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(param.optString("name")).append("\"\r\n\r\n")
                    .append(param.optString("value")).append("\r\n");
            }
        }
        builder.append("--").append(MULTIPART_FORM_BOUNDARY).append("--");
        return builder.toString();
    }

    private String buildPlainTextBody(JSONArray paramsArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paramsArray.length(); i++) {
            JSONObject param = paramsArray.optJSONObject(i);
            if (param != null && !isExcludedParam(param)) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(param.optString("name"))
                    .append('=')
                    .append(param.optString("value"));
            }
        }
        return builder.toString();
    }

}
