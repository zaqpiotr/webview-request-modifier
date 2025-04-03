package com.zaqpiotr.webviewrequestmodifier;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.monitglass.MainActivity;
import com.monitglass.R;
import com.monitglass.tasklist.TaskListActivity;
import com.monitglass.token.Token;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

@SuppressLint("SetJavaScriptEnabled")
public class RequestInspectorWebViewClient extends WebViewClient {

    private static final String LOG_TAG = "RequestInspectorWebView";

    private final Token token;
    private final JavaScriptRequestModifier javaScriptRequestModifierApi;
    private final OkHttpClient client;

    public RequestInspectorWebViewClient(WebView webView, Token token) {
        this.token = token;
        javaScriptRequestModifierApi = new JavaScriptRequestModifier(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        client = new OkHttpClient.Builder().build();
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        WebViewRequest recordedRequest = javaScriptRequestModifierApi.findRecordedRequestForUrl(request.getUrl().toString());
        WebViewRequest webViewRequest = WebViewRequest.create(request, recordedRequest);
        return shouldInterceptRequest(view, webViewRequest, request);
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, WebViewRequest webViewRequest, WebResourceRequest request) {
        logWebViewRequest(webViewRequest);
        String url = webViewRequest.getUrl().toString();

        try {
            Request.Builder builder = new Request.Builder().url(url);

            builder.header("Authorization", "Bearer " + token.getAccessToken());

            Map<String, String> originalHeaders = webViewRequest.getHeaders();
            for (Map.Entry<String, String> entry : originalHeaders.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase("Authorization")) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            if (webViewRequest.getMethod().equalsIgnoreCase("PUT") || webViewRequest.getMethod().equalsIgnoreCase("POST")) {
                String body = webViewRequest.getBody();

                RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body);

                builder.method(webViewRequest.getMethod(), requestBody);
            }

            Response response = client.newCall(builder.build()).execute();

            if (response.isSuccessful()) {
                String mimeType = response.header("Content-Type", "text/html");
                MediaType mediaType = MediaType.parse(mimeType);
                String charset = mediaType != null && mediaType.charset() != null ? mediaType.charset().name() : StandardCharsets.UTF_8.name();
                InputStream responseBodyStream = response.body().byteStream();

                Map<String, String> responseHeaders = new HashMap<>();
                for (Map.Entry<String, java.util.List<String>> header : response.headers().toMultimap().entrySet()) {
                    responseHeaders.put(header.getKey(), header.getValue().get(0));
                }

                WebResourceResponse webResponse = new WebResourceResponse(
                    mediaType.type() + "/" + mediaType.subtype(),
                    charset,
                    responseBodyStream);
                webResponse.setResponseHeaders(responseHeaders);
                return webResponse;
            }
            return super.shouldInterceptRequest(view, request);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Network error intercepting request: " + request.getUrl(), e);
            return super.shouldInterceptRequest(view, request);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unexpected error intercepting request: " + request.getUrl(), e);
            return super.shouldInterceptRequest(view, request);
        }
    }

    protected void logWebViewRequest(WebViewRequest webViewRequest) {
        Log.i(LOG_TAG, "Sending request from WebView: " + webViewRequest);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        Log.i(LOG_TAG, "Page started loading, enabling request inspection. URL: " + url);
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        hideProgressBar(MainActivity.getMainActivityInstance().findViewById(R.id.serverResponseProgressBar));
        hideProgressBar(TaskListActivity.getTaskListActivityInstance().findViewById(R.id.serverResponseProgressBar));
    }

    private void hideProgressBar(View view) {
        if (view != null) view.setVisibility(View.GONE);
    }
}