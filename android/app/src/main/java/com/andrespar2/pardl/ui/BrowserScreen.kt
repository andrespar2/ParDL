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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.Warning

/**
 * Navegador interno. Permite navegar a YouTube, SoundCloud, Bandcamp u otras
 * webs y enviar la URL actual al motor yt-dlp para descargar audio o video.
 *
 * No inyecta JavaScript ni elude controles de acceso; solo es un WebView con
 * navegación normal. El usuario es responsable del contenido que descarga.
 */
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
        val value = input.trim()
        if (value.isEmpty()) return startUrl
        return if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            value
        } else {
            "https://$value"
        }
    }

    fun load(input: String) {
        val target = normalize(input)
        error = null
        keyboard?.hide()
        webView?.loadUrl(target)
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
                    onClick = {
                        keyboard?.hide()
                        onDownload(currentUrl, DownloadKind.AUDIO)
                    },
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1.4f)
                )
                PillButton(
                    text = "Video",
                    onClick = {
                        keyboard?.hide()
                        onDownload(currentUrl, DownloadKind.VIDEO)
                    },
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
