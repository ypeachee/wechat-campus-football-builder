package xyz.cugbsoccer.muddy;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.URISyntaxException;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int STORAGE_PERMISSION_REQUEST = 4102;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout errorView;
    private Button retryButton;
    private ValueCallback<Uri[]> filePathCallback;
    private PendingDownload pendingDownload;
    private boolean errorVisible;
    private long lastBackPressedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.brand_dark));
        window.setNavigationBarColor(getColor(R.color.brand_dark));

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.web_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);
        errorView = findViewById(R.id.error_view);
        retryButton = findViewById(R.id.retry_button);

        configureWebView();
        configureSwipeRefresh();
        retryButton.setOnClickListener(view -> retryCurrentPage());

        if (savedInstanceState == null) {
            webView.loadUrl(getString(R.string.app_url));
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.setBackgroundColor(getColor(R.color.brand_dark));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSafeBrowsingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        settings.setUserAgentString(
                settings.getUserAgentString() + " MuddySoccerAndroid/1.0.0"
        );

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.setDownloadListener(new AppDownloadListener());
    }

    private void configureSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(getColor(R.color.brand_lime));
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.brand_dark));
        swipeRefresh.setOnRefreshListener(() -> {
            hideError();
            webView.reload();
        });
    }

    private boolean isInternalUrl(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("cugbsoccer.xyz") || host.endsWith(".cugbsoccer.xyz");
    }

    private void openExternalUrl(String url) {
        try {
            Intent intent;
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            } else {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | URISyntaxException | SecurityException exception) {
            Toast.makeText(this, R.string.external_app_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void showError() {
        if (errorVisible) {
            return;
        }
        errorVisible = true;
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        errorView.setVisibility(View.VISIBLE);
        errorView.requestFocus();
    }

    private void hideError() {
        errorVisible = false;
        errorView.setVisibility(View.GONE);
    }

    private void retryCurrentPage() {
        hideError();
        String currentUrl = webView.getUrl();
        if (currentUrl != null && isInternalUrl(Uri.parse(currentUrl))) {
            webView.loadUrl(currentUrl);
        } else {
            webView.loadUrl(getString(R.string.app_url));
        }
    }

    private void handleBack() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt <= 2000L) {
            finish();
            return;
        }
        lastBackPressedAt = now;
        Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_REQUEST || pendingDownload == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload(pendingDownload);
        } else {
            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show();
        }
        pendingDownload = null;
    }

    private void requestDownload(PendingDownload download) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = download;
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST
            );
            return;
        }
        enqueueDownload(download);
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            String fileName = URLUtil.guessFileName(
                    download.url,
                    download.contentDisposition,
                    download.mimeType
            );
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url));
            request.setTitle(fileName);
            request.setDescription(getString(R.string.app_name));
            request.setMimeType(download.mimeType);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "泥地soccer/" + fileName
            );

            String cookie = CookieManager.getInstance().getCookie(download.url);
            if (cookie != null && !cookie.isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }
            if (download.userAgent != null && !download.userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", download.userAgent);
            }

            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class AppWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isInternalUrl(uri)) {
                return false;
            }
            openExternalUrl(uri.toString());
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!errorVisible) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(5);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame()) {
                showError();
            }
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse
        ) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                showError();
            }
        }

        @Override
        public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                android.net.http.SslError error
        ) {
            handler.cancel();
            showError();
        }
    }

    private final class AppWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams fileChooserParams
        ) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            String[] acceptTypes = fileChooserParams.getAcceptTypes();
            if (acceptTypes != null && acceptTypes.length > 0 && !acceptTypes[0].isEmpty()) {
                if (acceptTypes.length == 1) {
                    intent.setType(acceptTypes[0]);
                } else {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
                }
            }
            if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            try {
                startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST);
            } catch (ActivityNotFoundException exception) {
                filePathCallback = null;
                Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    }

    private final class AppDownloadListener implements DownloadListener {

        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(MainActivity.this, R.string.download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String effectiveMime = mimeType;
            if (effectiveMime == null || effectiveMime.isEmpty()) {
                effectiveMime = "application/octet-stream";
            }
            requestDownload(new PendingDownload(
                    url,
                    userAgent,
                    contentDisposition,
                    effectiveMime
            ));
        }
    }

    private static final class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType
        ) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }
}