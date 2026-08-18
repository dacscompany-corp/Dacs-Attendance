package com.dacs.attendance.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.dacs.attendance.BuildConfig
import com.dacs.attendance.R
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.TextMuted

/**
 * The Cloudflare Turnstile challenge, hosted in a WebView.
 *
 * Supabase Auth on this project enforces captcha, and that setting is
 * project-wide -- the worker app cannot opt out without also removing the
 * protection from the admin and client web portals. Turnstile ships no
 * native Android SDK, and a widget rendered from a file:// page has no
 * origin the sitekey can validate, so the challenge is served from the
 * DAC's domain (attendance-captcha.html) and the token comes back over a
 * JavaScript bridge.
 *
 * The WebView is locked down to that one page: JavaScript is on because
 * Turnstile cannot work without it, but file and content access are off
 * and any navigation away from the two expected hosts is refused.
 */
@Composable
fun TurnstileChallenge(
    onToken: (String) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnToken by rememberUpdatedState(onToken)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Bridge callbacks arrive on a WebView worker thread; everything they
    // touch is Compose state, so hop to main first.
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onToken(token: String) = mainHandler.post {
                if (token.isNotBlank()) currentOnToken(token) else currentOnFailed()
            }

            @JavascriptInterface
            fun onError(code: String) = mainHandler.post { currentOnFailed() }
        }
    }

    Dialog(onDismissRequest = currentOnFailed) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.RadiusLarge),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Dimens.GapMedium),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    factory = { context -> buildChallengeWebView(context, bridge, currentOnFailed) },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
                Text(
                    text = stringResource(R.string.captcha_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mainHandler.removeCallbacksAndMessages(null) }
    }
}

// JavaScript is required: Turnstile is a JavaScript challenge. The
// surface is one page we serve, over https, with no file access.
@SuppressLint("SetJavaScriptEnabled")
private fun buildChallengeWebView(
    context: android.content.Context,
    bridge: Any,
    onFailed: () -> Unit
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportMultipleWindows(false)
    settings.builtInZoomControls = false
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    addJavascriptInterface(bridge, "DacsAttendanceBridge")

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            // Nothing in this flow ever navigates. Anything that tries is
            // refused rather than followed.
            return request.url.host !in ALLOWED_HOSTS
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            // Only the challenge page itself failing is fatal; a failed
            // sub-resource is Turnstile's own problem to report.
            if (request.isForMainFrame) onFailed()
        }
    }

    loadUrl(BuildConfig.CAPTCHA_URL)
}

private val ALLOWED_HOSTS = setOf(
    Uri.parse(BuildConfig.CAPTCHA_URL).host,
    "challenges.cloudflare.com"
)
