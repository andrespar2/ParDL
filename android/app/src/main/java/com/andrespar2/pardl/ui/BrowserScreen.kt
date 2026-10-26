package com.andrespar2.pardl.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.Warning
import org.json.JSONTokener

/**
 * Intenta obtener la URL del vídeo que se está reproduciendo, no la URL
 * histórica de la barra superior. En YouTube prioriza la URL /watch actual y
 * el videoId del reproductor; en otras webs usa iframe, canonical u og:url.
 */
private const val RESOLVE_PLAYING_URL_JS = """
(function() {
  try {
    var host = (location.hostname || '').toLowerCase();
    var href = location.href || '';
    var yt = window.ytInitialPlayerResponse;
    var vid = yt && yt.videoDetails && yt.videoDetails.videoId;
    if (!vid) {
      var el = document.querySelector('ytd-watch-flexy, ytm-watch, [video-id]');
      if (el) {
        vid = el.getAttribute('video-id') || (el.data && el.data.videoId);
      }
    }
    if (host.indexOf('youtube') >= 0) {
      if (href.indexOf('/watch') >= 0 || href.indexOf('/embed/') >= 0) return href;
      if (vid) return 'https://www.youtube.com/watch?v=' + vid;
    }
    var iframe = document.querySelector('iframe[src*="youtube.com/embed/"], iframe[src*="youtube-nocookie.com/embed/"]');
    if (iframe && iframe.src && iframe.src.indexOf('http') === 0) return iframe.src;
    var canonical = document.querySelector('link[rel="canonical"]');
    if (canonical && canonical.href && canonical.href.indexOf('http') === 0) return canonical.href;
    var og = document.querySelector('meta[property="og:url"]');
    if (og && og.content && og.content.indexOf('http') === 0) return og.content;
    var v = document.querySelector('video');
    if (v && v.currentSrc && v.currentSrc.indexOf('http') === 0) return v.currentSrc;
    return href;
  } catch (e) {
    return location.href;
  }
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onDownload: (url: String, kind: DownloadKind) -> Unit
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val keyboard = LocalSoftwareKeyboardController.current
    val startUrl = "https://m.youtube.com"
    var address by remember { mutableStateOf(startUrl) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return startUrl
        val compact = raw.filterNot { it.isWhitespace() }
        val looksLikeUrl = compact.contains("://") || compact.contains('.')
        if (!looksLikeUrl) {
            return "https://www.google.com/search?q=${Uri.encode(raw)}"
        }
        return if (compact.startsWith("http://", true) || compact.startsWith("https://", true)) {
            compact
        } else {
            "https://$compact"
        }
    }

    fun load(input: String) {
        val target = normalize(input)
        error = null
        keyboard?.hide()
        webView?.loadUrl(target)
    }

    fun extractWebUrl(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            JSONTokener(raw).nextValue() as? String
        }.getOrNull()?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    fun resolveAndDownload(kind: DownloadKind) {
        keyboard?.hide()
        val view = webView
        if (view == null) {
            onDownload(currentUrl, kind)
            return
        }
        view.evaluateJavascript(RESOLVE_PLAYING_URL_JS) { result ->
            val resolved = extractWebUrl(result)
                ?: view.url
                ?: currentUrl
            currentUrl = resolved
            address = resolved
            onDownload(resolved, kind)
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Navegador",
            subtitle = Uri.parse(currentUrl).host?.removePrefix("www.") ?: "YouTube, SoundCloud, Bandcamp…",
            onBack = onBack,
            actions = {
                GlassIconButton(
                    Icons.Default.OpenInBrowser,
                    "Abrir fuera",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                            )
                        }
                    }
                )
            }
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                Icons.Default.ArrowBack,
                "Atrás",
                size = 38.dp,
                onClick = { webView?.goBack() }
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                Icons.Default.ArrowForward,
                "Adelante",
                size = 38.dp,
                onClick = { webView?.goForward() }
            )
            Spacer(Modifier.width(6.dp))
            GlassTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = "Busca o escribe una dirección",
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Default.Search,
                keyboardType = KeyboardType.Uri,
                imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                keyboardActions = KeyboardActions(onGo = { load(address) })
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                Icons.Default.Search,
                "Ir",
                size = 38.dp,
                onClick = { load(address) }
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                Icons.Default.Refresh,
                "Recargar",
                size = 38.dp,
                onClick = { webView?.reload() }
            )
        }

        if (loading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = glass.accent,
                trackColor = glass.glassFill
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = true
                            allowFileAccess = false
                            allowContentAccess = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(true)
                            cacheMode = WebSettings.LOAD_DEFAULT
                            builtInZoomControls = true
                            displayZoomControls = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                loading = true
                                progress = 5
                                url?.let {
                                    currentUrl = it
                                    address = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                loading = false
                                progress = 100
                                url?.let {
                                    currentUrl = it
                                    address = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                url?.takeIf { it.startsWith("http") }?.let {
                                    currentUrl = it
                                    address = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, errorResponse)
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    error = "No se pudo cargar la página."
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                loading = newProgress < 100
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val newWebView = WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            request?.url?.toString()?.let { view?.loadUrl(it) }
                                            destroy()
                                            return true
                                        }
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                if (transport != null) {
                                    transport.webView = newWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                                newWebView.destroy()
                                return false
                            }
                        }
                        loadUrl(startUrl)
                        webView = this
                    }
                },
                update = { it.settings.javaScriptEnabled = true }
            )
        }

        if (error != null) {
            Text(
                error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Danger,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(
                    text = "Descargar audio",
                    onClick = { resolveAndDownload(DownloadKind.AUDIO) },
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1.4f)
                )
                PillButton(
                    text = "Video",
                    onClick = { resolveAndDownload(DownloadKind.VIDEO) },
                    icon = Icons.Default.Download,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Se usará el motor yt-dlp sobre la página actual. Usa solo contenido " +
                    "propio o autorizado; descargar de YouTube puede infringir sus términos.",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
