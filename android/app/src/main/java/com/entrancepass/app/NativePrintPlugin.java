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
 * Prints HTML using Android {@link PrintManager} with a WebView attached to the activity.
 * Off-screen / application-context WebViews often produce blank print output on many devices.
 */
@CapacitorPlugin(name = "NativePrint")
public class NativePrintPlugin extends Plugin {

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

                        final boolean[] started = { false };

                        final Runnable cleanup =
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mainHandler.postDelayed(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        try {
                                                            root.removeView(webView);
                                                            webView.destroy();
                                                        } catch (Exception ignored) {
                                                        }
                                                    }
                                                },
                                                800);
                                    }
                                };

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
                                                            PrintManager pm =
                                                                    (PrintManager)
                                                                            activity.getSystemService(
                                                                                    Activity.PRINT_SERVICE);
                                                            if (pm == null) {
                                                                call.reject("PrintManager unavailable");
                                                                cleanup.run();
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
                                                            call.reject(
                                                                    e.getMessage() != null
                                                                            ? e.getMessage()
                                                                            : "Print failed",
                                                                    e);
                                                        } finally {
                                                            cleanup.run();
                                                        }
                                                    }
                                                },
                                                500);
                                    }
                                });

                        webView.loadDataWithBaseURL(
                                "https://localhost/", html, "text/html", "UTF-8", null);
                    }
                });
    }
}
