package com.haidianfirstteam.nostalgiaai.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton

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

    private var scale: Float = 1.0f
    private var pageLoaded: Boolean = false
    private var pendingScaleApply: Boolean = false

    private val tvScale: TextView = TextView(context)
    private val btnPlus: MaterialButton = MaterialButton(context)
    private val btnMinus: MaterialButton = MaterialButton(context)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Toggle between 100% and +10% (subsequent double-tap shrinks back).
            scale = if (kotlin.math.abs(scale - 1.0f) < 0.001f) 1.1f else 1.0f
            applyScaleToWeb()
            return true
        }
    })

    init {
        setBackgroundResource(com.haidianfirstteam.nostalgiaai.R.drawable.bg_bubble_ai)
        val pad = (8f * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        addView(
            webView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        addView(buildZoomControls())
        initWebView()
    }

    private fun buildZoomControls(): View {
        val density = resources.displayMetrics.density
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun styleBtn(b: MaterialButton, text: String) {
            b.text = text
            b.minHeight = (28 * density).toInt()
            b.minimumHeight = (28 * density).toInt()
            b.setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        }

        styleBtn(btnMinus, "-")
        styleBtn(btnPlus, "+")

        tvScale.textSize = 12f
        tvScale.alpha = 0.85f
        tvScale.text = "100%"
        val lpText = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpText.leftMargin = (6 * density).toInt()
        lpText.rightMargin = (6 * density).toInt()
        tvScale.layoutParams = lpText

        btnMinus.setOnClickListener {
            setScale(scale - 0.1f)
        }
        btnPlus.setOnClickListener {
            setScale(scale + 0.1f)
        }

        wrap.addView(btnMinus)
        wrap.addView(tvScale)
        wrap.addView(btnPlus)

        return wrap.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.TOP
            }
        }
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                if (pendingScaleApply) {
                    pendingScaleApply = false
                    applyScaleToWeb()
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "Android")

        // Interaction rules:
        // - Always capture double-tap.
        // - When zoomed (scale != 1), allow WebView scrolling for panning.
        // - When not zoomed, block to avoid scroll conflicts inside RecyclerView.
        webView.setOnTouchListener { v, ev ->
            val consumed = gestureDetector.onTouchEvent(ev)
            if (consumed) return@setOnTouchListener true

            if (kotlin.math.abs(scale - 1.0f) >= 0.001f) {
                try {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                } catch (_: Throwable) {
                    // ignore
                }
                false
            } else {
                true
            }
        }
    }

    private fun setScale(next: Float) {
        scale = next.coerceIn(0.1f, 5.0f)
        applyScaleToWeb()
    }

    private fun applyScaleToWeb() {
        val percent = (scale * 100).toInt().coerceIn(10, 500)
        tvScale.text = "${percent}%"
        if (!pageLoaded) {
            pendingScaleApply = true
            return
        }
        // Apply scale inside page; use width scaling so WebView can scroll horizontally.
        val js = "try{ if(window.setMermaidScale){ window.setMermaidScale(${scale} ); } }catch(e){}"
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun setDiagram(mermaidCode: String) {
        val night = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
        // Basic contrast for the scale label.
        tvScale.setTextColor(if (night) Color.parseColor("#DDDDDD") else Color.parseColor("#555555"))

        // Preprocess: add quotes around node labels with special characters
        val fixedCode = fixMermaidLabels(mermaidCode)

        val key = (if (night) "dark:" else "light:") + fixedCode.hashCode().toString()
        if (key == lastKey) return
        lastKey = key

        // Reset height to avoid keeping a stale height when re-binding.
        val lp = webView.layoutParams
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        webView.layoutParams = lp

        pageLoaded = false
        pendingScaleApply = true
        scale = 1.0f
        tvScale.text = "100%"

        val html = buildHtml(fixedCode, night)
        // Use assets base URL so <script src="mermaid/mermaid.min.js"> resolves.
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
    }

    /**
     * Fix Mermaid flowchart node labels that contain special characters (like parentheses,
     * arithmetic operators) which confuse Mermaid's PEG parser.
     *
     * Wraps unquoted [label] in ["label"] when the label contains `()`, `*`, `/`, `+`, `-`, `=`.
     * This tells Mermaid to treat the content as literal text.
     *
     * Example:
     *   B->C[计算：y=(F-2*H)/2]    →   B->C["计算：y=(F-2*H)/2"]
     */
    private fun fixMermaidLabels(code: String): String {
        // Pattern: unquoted [label] where label contains special characters
        val special = Regex("""\[([^"]*?[()*/=+\-][^"]*?)]""")
        return code.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            // Skip header/directive lines (graph, flowchart, subgraph)
            if (trimmed.startsWith("graph ") || trimmed.startsWith("flowchart ") || trimmed.startsWith("subgraph ")) {
                return@joinToString line
            }
            special.replace(line) { m ->
                val content = m.groupValues[1]
                // Avoid double-quoting (already has quotes)
                if (content.startsWith("\"") && content.endsWith("\"")) {
                    m.value
                } else {
                    "[\"$content\"]"
                }
            }
        }
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

                  // zoom state
                  window.__scale = 1.0;
                  window.setMermaidScale = function(s){
                    try {
                      var v = Number(s);
                      if (!isFinite(v)) return;
                      if (v < 0.1) v = 0.1;
                      if (v > 5.0) v = 5.0;
                      window.__scale = v;
                      applyScale();
                    } catch (_e) {}
                  };

                  function applyScale(){
                    try {
                      var wrap = document.getElementById('wrap');
                      var svg = wrap.querySelector('svg');
                      if (!svg) { postHeight(); return; }
                      var pct = (window.__scale * 100).toFixed(0) + '%';
                      svg.style.width = pct;
                      svg.style.height = 'auto';
                      postHeight();
                    } catch (_e) {
                      postHeight();
                    }
                  }

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
                        applyScale();
                        postHeight();
                      }).catch(function(err){
                        document.getElementById('wrap').innerHTML = '<div class="err">Mermaid 渲染失败\n' + String(err) + '</div>';
                        postHeight();
                      });
                    } else {
                      // Older mermaid: render returns svg string
                      document.getElementById('wrap').innerHTML = String(p);
                      applyScale();
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
            val density = resources.displayMetrics.density
            val pxRaw = (heightCssPx * density).toInt().coerceAtLeast(1)
            // When zoomed, keep a bounded viewport so the bubble does not grow unbounded.
            val maxZoomViewportPx = (320f * density).toInt()
            val px = if (kotlin.math.abs(scale - 1.0f) < 0.001f) pxRaw else kotlin.math.min(pxRaw, maxZoomViewportPx)
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
