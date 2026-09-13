package com.icewolf.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ============ CONFIG ============
    private static final String BASE_URL = "https://icewolf-search.onrender.com";
    private static final String SEARCH_URL = BASE_URL + "/search?q=";
    private static final String HOME_PAGE = BASE_URL;
    private static final String BASE_HOST = "icewolf-search.onrender.com";

    private static final String UA_DESKTOP =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final String PREFS = "icewolf_prefs";
    private static final String KEY_DESKTOP = "desktop_mode";

    // ============ TAB ============
    private static class Tab {
        String url, title;
        Tab(String url, String title) { this.url = url; this.title = title; }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int currentTab = 0;

    // ============ VIEWS ============
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private EditText urlInput;
    private ProgressBar progressBar;
    private TextView tabCountView;
    private FrameLayout tabsButton;
    private ImageButton menuBtn, moreBtn, micBtn, clearBtn;
    private LinearLayout urlBar;
    private LinearLayout topBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_IceWolf);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        webView         = findViewById(R.id.webView);
        swipeRefresh    = findViewById(R.id.swipeRefresh);
        urlInput        = findViewById(R.id.urlInput);
        progressBar     = findViewById(R.id.progressBar);
        tabCountView    = findViewById(R.id.tabCount);
        tabsButton      = findViewById(R.id.tabsButton);
        menuBtn         = findViewById(R.id.menuBtn);
        moreBtn         = findViewById(R.id.moreBtn);
        micBtn          = findViewById(R.id.micBtn);
        clearBtn        = findViewById(R.id.clearBtn);
        urlBar          = findViewById(R.id.urlBar);
        topBar          = findViewById(R.id.topBar);

        // WebView settings
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // SwipeRefresh
        swipeRefresh.setColorSchemeColors(0xFF0EA5E9);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl().toString());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url);
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                updateTopBarVisibility(url);
                progressBar.setVisibility(View.VISIBLE);
                if (!urlInput.hasFocus()) urlInput.setText(url);
                updateCurrentTab(url, null);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (!urlInput.hasFocus()) urlInput.setText(url);
                updateCurrentTab(url, view.getTitle());
                updateTabCount();
            }
        });

        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                updateCurrentTab(view.getUrl(), title);
            }
        });

        // Download
        webView.setDownloadListener((url, ua, cd, mime, len) -> startDownload(url, ua, cd, mime));

        // URL input
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(urlInput.getText().toString());
                urlInput.clearFocus();
                return true;
            }
            return false;
        });
        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) urlInput.selectAll();
        });
        urlInput.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        // Buttons
        clearBtn.setOnClickListener(v -> {
            urlInput.setText("");
            urlInput.requestFocus();
        });
        micBtn.setOnClickListener(v -> Toast.makeText(this, "Ovozli qidiruv — tez orada", Toast.LENGTH_SHORT).show());
        menuBtn.setOnClickListener(v -> showMenu());
        moreBtn.setOnClickListener(v -> showMenu());
        tabsButton.setOnClickListener(v -> showTabsDialog());

        // Start
        openNewTab();
    }

    // ============ TABS ============
    private void openNewTab() {
        tabs.add(new Tab(HOME_PAGE, "Yangi tab"));
        currentTab = tabs.size() - 1;
        webView.loadUrl(HOME_PAGE);
        updateTabCount();
    }

    private void closeCurrentTab() {
        if (tabs.size() <= 1) {
            tabs.clear();
            tabs.add(new Tab(HOME_PAGE, "Yangi tab"));
            currentTab = 0;
            webView.loadUrl(HOME_PAGE);
        } else {
            tabs.remove(currentTab);
            if (currentTab >= tabs.size()) currentTab = tabs.size() - 1;
            webView.loadUrl(tabs.get(currentTab).url);
        }
        updateTabCount();
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabs.get(currentTab).url = webView.getUrl() != null ? webView.getUrl() : tabs.get(currentTab).url;
        currentTab = index;
        webView.loadUrl(tabs.get(currentTab).url);
        updateTabCount();
    }

    private void updateCurrentTab(String url, String title) {
        if (currentTab >= 0 && currentTab < tabs.size()) {
            tabs.get(currentTab).url = url;
            if (title != null) tabs.get(currentTab).title = title;
        }
    }

    private void updateTabCount() {
        tabCountView.setText(String.valueOf(tabs.size()));
    }

    // ============ MENU ============
    private void showMenu() {
        View menuView = LayoutInflater.from(this).inflate(R.layout.dialog_menu, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(menuView).create();

        menuView.findViewById(R.id.menuNewTab).setOnClickListener(v -> { dialog.dismiss(); openNewTab(); });
        menuView.findViewById(R.id.menuHome).setOnClickListener(v -> { dialog.dismiss(); webView.loadUrl(HOME_PAGE); });
        menuView.findViewById(R.id.menuShare).setOnClickListener(v -> {
            dialog.dismiss();
            String u = webView.getUrl();
            if (u != null) shareUrl(u);
        });
        menuView.findViewById(R.id.menuCopyUrl).setOnClickListener(v -> {
            dialog.dismiss();
            String u = webView.getUrl();
            if (u != null) copyToClipboard(u, "Havola nusxalandi");
        });
        menuView.findViewById(R.id.menuDesktopToggle).setOnClickListener(v -> {
            dialog.dismiss();
            toggleDesktopMode();
        });
        menuView.findViewById(R.id.menuRefresh).setOnClickListener(v -> { dialog.dismiss(); webView.reload(); });
        menuView.findViewById(R.id.menuCloseTab).setOnClickListener(v -> { dialog.dismiss(); closeCurrentTab(); });
        menuView.findViewById(R.id.menuExit).setOnClickListener(v -> { dialog.dismiss(); finish(); });

        dialog.show();
    }

    // ============ TABS DIALOG ============
    private void showTabsDialog() {
        if (tabs.isEmpty()) return;
        String[] items = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String title = t.title != null ? t.title : t.url;
            if (title.length() > 50) title = title.substring(0, 50) + "…";
            items[i] = (i == currentTab ? "● " : "○ ") + title;
        }
        new AlertDialog.Builder(this)
            .setTitle("Tablar (" + tabs.size() + ")")
            .setItems(items, (d, w) -> switchTab(w))
            .setNeutralButton("Yopish", null)
            .setNegativeButton("Joriy tabni yopish", (d, w) -> closeCurrentTab())
            .show();
    }

    // ============ DESKTOP MODE ============
    private void toggleDesktopMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean next = !prefs.getBoolean(KEY_DESKTOP, false);
        prefs.edit().putBoolean(KEY_DESKTOP, next).apply();
        webView.getSettings().setUserAgentString(next ? UA_DESKTOP : UA_MOBILE);
        webView.reload();
        Toast.makeText(this, next ? "Desktop rejimi: ON" : "Desktop rejimi: OFF", Toast.LENGTH_SHORT).show();
    }

    private void applySettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean desktop = prefs.getBoolean(KEY_DESKTOP, false);
        String ua = desktop ? UA_DESKTOP : UA_MOBILE;
        if (!ua.equals(webView.getSettings().getUserAgentString())) {
            webView.getSettings().setUserAgentString(ua);
        }
    }

    // ============ SHARE, COPY, DOWNLOAD ============
    private void shareUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(intent, "Ulashish"));
        } catch (Exception e) {}
    }

    private void copyToClipboard(String text, String msg) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("IceWolf", text));
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {}
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            if (mimetype != null && !mimetype.isEmpty()) req.setMimeType(mimetype);
            if (userAgent != null && !userAgent.isEmpty()) req.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.setTitle(fileName);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, "Yuklab olinmoqda: " + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Xato: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ============ NAVIGATE ============
    private boolean handleUrl(WebView view, String url) {
        if (url == null) return false;
        return false;
    }

    private void navigate(String input) {
        if (input == null) return;
        input = input.trim();
        if (input.isEmpty()) return;

        if (Patterns.WEB_URL.matcher(input).matches() ||
            input.startsWith("http://") || input.startsWith("https://")) {
            if (!input.startsWith("http")) input = "https://" + input;
            webView.loadUrl(input);
        } else {
            webView.loadUrl(SEARCH_URL + Uri.encode(input));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySettings();
    }
    private void updateTopBarVisibility(String url) {
        if (url == null) return;
        boolean isIceWolf = url.contains(BASE_HOST);
        if (topBar != null) {
            topBar.setVisibility(isIceWolf ? View.GONE : View.VISIBLE);
        }
    }


    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else if (tabs.size() > 1) closeCurrentTab();
        else super.onBackPressed();
    }
}
