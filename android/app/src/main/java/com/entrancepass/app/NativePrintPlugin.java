package com.entrancepass.app;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Fallback HTML print: WebView must stay alive while PrintDocumentAdapter runs; do not destroy
 * immediately after {@link PrintManager#print}. Previous WebView is removed when the next print
 * starts.
 */
@CapacitorPlugin(name = "NativePrint")
public class NativePrintPlugin extends Plugin {

    private static WebView sPendingWebView;
    private static ViewGroup sPendingParent;

    private static void removePendingWebView() {
        if (sPendingWebView != null && sPendingParent != null) {
            try {
                sPendingParent.removeView(sPendingWebView);
                sPendingWebView.destroy();
            } catch (Exception ignored) {
            }
            sPendingWebView = null;
            sPendingParent = null;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @PluginMethod
    public void printHtml(final PluginCall call) {
        final String html = call.getString("html");
        final String jobName = call.getString("name", "Document");
        if (html == null || html.isEmpty()) {
            call.reject("html is required");
            return;
        }

        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity is null");
            return;
        }

        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        removePendingWebView();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            WebView.enableSlowWholeDocumentDraw();
                        }

                        final WebView webView = new WebView(activity);
                        final WebSettings ws = webView.getSettings();
                        ws.setJavaScriptEnabled(false);
                        ws.setLoadsImagesAutomatically(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                        }

                        final ViewGroup root = activity.findViewById(android.R.id.content);
                        webView.setVisibility(View.INVISIBLE);
                        final int match = ViewGroup.LayoutParams.MATCH_PARENT;
                        root.addView(webView, new ViewGroup.LayoutParams(match, match));

                        sPendingWebView = webView;
                        sPendingParent = root;

                        final boolean[] started = { false };

                        webView.setWebViewClient(
                                new WebViewClient() {
                                    @Override
                                    public void onPageFinished(WebView view, String url) {
                                        mainHandler.postDelayed(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (started[0]) {
                                                            return;
                                                        }
                                                        started[0] = true;
                                                        try {
                                                            int w =
                                                                    View.MeasureSpec.makeMeasureSpec(
                                                                            1080,
                                                                            View.MeasureSpec.EXACTLY);
                                                            int h =
                                                                    View.MeasureSpec.makeMeasureSpec(
                                                                            0,
                                                                            View.MeasureSpec
                                                                                    .UNSPECIFIED);
                                                            webView.measure(w, h);
                                                            webView.layout(
                                                                    0,
                                                                    0,
                                                                    webView.getMeasuredWidth(),
                                                                    webView.getMeasuredHeight());

                                                            PrintManager pm =
                                                                    (PrintManager)
                                                                            activity.getSystemService(
                                                                                    Activity
                                                                                            .PRINT_SERVICE);
                                                            if (pm == null) {
                                                                call.reject("PrintManager unavailable");
                                                                removePendingWebView();
                                                                return;
                                                            }
                                                            String documentName = jobName + "_document";
                                                            PrintDocumentAdapter adapter =
                                                                    webView.createPrintDocumentAdapter(
                                                                            documentName);
                                                            pm.print(
                                                                    jobName,
                                                                    adapter,
                                                                    new PrintAttributes.Builder()
                                                                            .build());
                                                            call.resolve();
                                                        } catch (Exception e) {
                                                            removePendingWebView();
                                                            call.reject(
                                                                    e.getMessage() != null
                                                                            ? e.getMessage()
                                                                            : "Print failed",
                                                                    e);
                                                        }
                                                    }
                                                },
                                                800);
                                    }
                                });

                        webView.loadDataWithBaseURL(
                                "https://localhost/", html, "text/html", "UTF-8", null);
                    }
                });
    }
}
