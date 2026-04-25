package com.haidianfirstteam.nostalgiaai.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate

/**
 * Renders a LaTeX math block inside a WebView using KaTeX.
 *
 * Follows the same pattern as [MermaidView]:
 * - Loads KaTeX from bundled assets (katex/katex.min.js + katex.min.css)
 * - Supports horizontal scrolling via CSS overflow-x:auto
 * - Scales with the app's font scale setting
 * - Reports its height via JavaScript bridge for RecyclerView layout
 * - Intended to be embedded in RecyclerView rows (chat messages)
 *
 * Android 4.4.2+ (API 19) compatible.
 */
class MathWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val webView: WebView = WebView(context)
    private var pageLoaded = false
    private var pendingLatex: String? = null
    /** Cached CSS font-size in px, recalculated when [setLatex] is called. */
    private var fontSizePx: Int = 16

    init {
        // Transparent background to blend with chat bubble
        setBackgroundColor(Color.TRANSPARENT)

        addView(
            webView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = true
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                pendingLatex?.let { doRenderLatex(it) }
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "Android")

        // Consume touch events so the parent RecyclerView does not intercept
        // horizontal scroll gestures.
        webView.setOnTouchListener { v, ev ->
            try {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            } catch (_: Throwable) {
                // ignore
            }
            false
        }
    }

    /**
     * Render a LaTeX expression (without any delimiters like $$ or \[ \]).
     *
     * @param latex Raw LaTeX source, e.g. "\begin{cases} x + y = 1 \end{cases}"
     */
    fun setLatex(latex: String) {
        val night = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        // Font scale from app settings (applied via BaseActivity.attachBaseContext).
        val fontScale = context.resources.configuration.fontScale
        // Base 16sp in CSS px (~= dp), scaled by fontScale.
        fontSizePx = (16f * fontScale).toInt().coerceIn(8, 80)

        val cssBg = if (night) "#1E1E1E" else "transparent"
        val cssColor = if (night) "#DDDDDD" else "#1A1A1A"
        val escapedLatex = latex
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val html = buildHtml(escapedLatex, fontSizePx, cssBg, cssColor)

        // Reset height for re-binding
        val lp = webView.layoutParams
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        webView.layoutParams = lp

        pageLoaded = false
        pendingLatex = latex

        // Use assets base URL so <script src="katex/katex.min.js"> resolves.
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "utf-8",
            null
        )
    }

    private fun doRenderLatex(latex: String) {
        // Font scale may have changed; recalculate.
        val fontScale = context.resources.configuration.fontScale
        fontSizePx = (16f * fontScale).toInt().coerceIn(8, 80)

        val escapedLatex = latex
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        // Re-render with current font-size via JS.
        val js = buildRenderJs(escapedLatex, fontSizePx)
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {
            // fallback: just reload
            val night = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            val cssBg = if (night) "#1E1E1E" else "transparent"
            val cssColor = if (night) "#DDDDDD" else "#1A1A1A"
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                buildHtml(escapedLatex, fontSizePx, cssBg, cssColor),
                "text/html",
                "utf-8",
                null
            )
        }
    }

    private fun buildHtml(
        escapedLatex: String,
        fontSize: Int,
        cssBg: String,
        cssColor: String
    ): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
              <link rel="stylesheet" href="katex/katex.min.css"/>
              <style>
                html, body {
                  margin: 0; padding: 0;
                  background: $cssBg;
                  overflow: hidden;
                }
                .math-wrap {
                  overflow-x: auto;
                  overflow-y: hidden;
                  padding: 2px 0;
                  font-size: ${fontSize}px;
                  color: $cssColor;
                  text-align: center;
                }
                .math-wrap .katex { white-space: nowrap; }
                .err {
                  font-family: sans-serif;
                  font-size: 12px;
                  color: #d32f2f;
                  white-space: pre-wrap;
                  text-align: left;
                }
              </style>
              <script src="katex/katex.min.js"></script>
            </head>
            <body>
              <div id="wrap" class="math-wrap"></div>
              <script>
                (function(){
                  function render() {
                    var wrap = document.getElementById('wrap');
                    if (!wrap) return;
                    var latex = "$escapedLatex";
                    if (typeof katex === 'undefined') {
                      wrap.innerHTML = '<div class="err">KaTeX 脚本未加载（katex is not defined）\\n请确认 assets/katex/katex.min.js 已打包进 APK</div>';
                      postHeight();
                      return;
                    }
                    try {
                      katex.render(latex, wrap, {
                        displayMode: true,
                        throwOnError: false,
                        output: 'html'
                      });
                    } catch (e) {
                      wrap.innerHTML = '<div class="err">KaTeX 渲染失败\\n' + String(e) + '</div>';
                    }
                    postHeight();
                  }

                  function postHeight() {
                    try {
                      var h = document.documentElement.scrollHeight || document.body.scrollHeight || 1;
                      Android.onHeight(h);
                    } catch (_e) {}
                  }

                  // Wait for DOM and KaTeX to be ready
                  if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', render);
                  } else {
                    render();
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildRenderJs(escapedLatex: String, fontSize: Int): String {
        return """
            (function(){
              var wrap = document.getElementById('wrap');
              if (!wrap) return;
              wrap.style.fontSize = '${fontSize}px';
              try {
                katex.render("$escapedLatex", wrap, {
                  displayMode: true,
                  throwOnError: false,
                  output: 'html'
                });
              } catch(e) {
                wrap.innerHTML = '<span class="err">' + String(e) + '</span>';
              }
              var h = document.documentElement.scrollHeight || document.body.scrollHeight || 1;
              Android.onHeight(h);
            })();
        """.trimIndent()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onHeight(heightCssPx: Int) {
            // WebView CSS px ≈ Android density-independent pixels (dp).
            // Convert to real px for Android layout.
            val density = resources.displayMetrics.density
            val px = (heightCssPx * density).toInt().coerceAtLeast(1)
            post {
                val lp = webView.layoutParams
                if (lp.height != px) {
                    lp.height = px
                    webView.layoutParams = lp
                }
            }
        }
    }
}
