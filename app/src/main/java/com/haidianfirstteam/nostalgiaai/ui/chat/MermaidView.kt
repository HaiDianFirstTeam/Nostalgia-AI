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
 * Renders a Mermaid diagram inside a WebView.
 *
 * Implementation notes:
 * - Uses local mermaid.min.js from assets (downloaded during build).
 * - Adjusts height after rendering to avoid internal scrolling.
 * - Intended to be embedded in RecyclerView rows (chat messages).
 */
class MermaidView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val webView: WebView = WebView(context)
    private var lastKey: String? = null

    init {
        setBackgroundResource(com.haidianfirstteam.nostalgiaai.R.drawable.bg_bubble_ai)
        val pad = (8f * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        addView(
            webView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {}
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "Android")

        // Make it non-interactive to avoid scroll conflicts inside RecyclerView.
        webView.setOnTouchListener { _, _ -> true }
    }

    fun setDiagram(mermaidCode: String) {
        val night = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
        val key = (if (night) "dark:" else "light:") + mermaidCode.hashCode().toString()
        if (key == lastKey) return
        lastKey = key

        // Reset height to avoid keeping a stale height when re-binding.
        val lp = webView.layoutParams
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        webView.layoutParams = lp

        val html = buildHtml(mermaidCode, night)
        // Use assets base URL so <script src="mermaid/mermaid.min.js"> resolves.
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
    }

    private fun buildHtml(code: String, dark: Boolean): String {
        // Safely embed code into JS string.
        val jsCode = code
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val theme = if (dark) "dark" else "default"
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                html, body { margin: 0; padding: 0; background: transparent; }
                #wrap { padding: 0; }
                svg { width: 100%; height: auto; }
                .err { font-family: sans-serif; font-size: 12px; color: #d32f2f; white-space: pre-wrap; }
              </style>
              <script src="mermaid/mermaid.min.js"></script>
            </head>
            <body>
              <div id="wrap"></div>
              <script>
                (function(){
                  if (typeof mermaid === 'undefined') {
                    document.getElementById('wrap').innerHTML = '<div class="err">Mermaid 脚本未加载（mermaid is not defined）\n请确认 assets/mermaid/mermaid.min.js 已打包进 APK</div>';
                    try { Android.onHeight(document.documentElement.scrollHeight || document.body.scrollHeight || 1); } catch(_e) {}
                    return;
                  }
                  try {
                    mermaid.initialize({ startOnLoad: false, theme: "$theme", securityLevel: 'strict' });
                  } catch (e) {
                    document.getElementById('wrap').innerHTML = '<div class="err">Mermaid 初始化失败\n' + String(e) + '</div>';
                    try { Android.onHeight(document.documentElement.scrollHeight || document.body.scrollHeight || 1); } catch(_e) {}
                    return;
                  }

                  var code = "$jsCode";

                  function postHeight(){
                    try {
                      var h = document.documentElement.scrollHeight || document.body.scrollHeight || 1;
                      Android.onHeight(h);
                    } catch (e) {
                      // ignore
                    }
                  }

                  try {
                    var id = 'm' + Date.now();
                    // Mermaid v10+: mermaid.render(id, code).then(({svg}) => ...)
                    var p = mermaid.render(id, code);
                    if (p && typeof p.then === 'function') {
                      p.then(function(res){
                        var svg = res.svg || res;
                        document.getElementById('wrap').innerHTML = svg;
                        postHeight();
                      }).catch(function(err){
                        document.getElementById('wrap').innerHTML = '<div class="err">Mermaid 渲染失败\n' + String(err) + '</div>';
                        postHeight();
                      });
                    } else {
                      // Older mermaid: render returns svg string
                      document.getElementById('wrap').innerHTML = String(p);
                      postHeight();
                    }
                  } catch (e) {
                    document.getElementById('wrap').innerHTML = '<div class="err">Mermaid 渲染异常\n' + String(e) + '</div>';
                    postHeight();
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onHeight(heightCssPx: Int) {
            // Convert CSS px to Android px approximately (WebView uses CSS px ~= dp)
            val px = (heightCssPx * resources.displayMetrics.density).toInt().coerceAtLeast(1)
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
