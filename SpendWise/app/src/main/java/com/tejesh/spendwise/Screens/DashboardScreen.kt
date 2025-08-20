package com.tejesh.spendwise.Screens // Or your ui package

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.patrykandpatrick.vico.compose.component.textComponent

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardScreen() {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Coming Soon",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
    // IMPORTANT: Replace this with the public link to YOUR Tableau dashboard
//    val tableauUrl = "https://public.tableau.com/views/Superstore/Overview"
//
//    // State to track loading and errors
//    var isLoading by remember { mutableStateOf(true) }
//    var hasError by remember { mutableStateOf(false) }
//
//    Scaffold(
//        topBar = { TopAppBar(title = { Text("Dashboard") }) }
//    ) { innerPadding ->
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding),
//            contentAlignment = Alignment.Center
//        ) {
//            AndroidView(
//                factory = { context ->
//                    WebView(context).apply {
//                        layoutParams = ViewGroup.LayoutParams(
//                            ViewGroup.LayoutParams.MATCH_PARENT,
//                            ViewGroup.LayoutParams.MATCH_PARENT
//                        )
//                        // Add a custom WebViewClient to track loading
//                        webViewClient = object : WebViewClient() {
//                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
//                                super.onPageStarted(view, url, favicon)
//                                isLoading = true
//                                hasError = false
//                            }
//
//                            override fun onPageFinished(view: WebView?, url: String?) {
//                                super.onPageFinished(view, url)
//                                isLoading = false
//                            }
//
//                            override fun onReceivedError(
//                                view: WebView?,
//                                request: android.webkit.WebResourceRequest?,
//                                error: android.webkit.WebResourceError?
//                            ) {
//                                super.onReceivedError(view, request, error)
//                                isLoading = false
//                                hasError = true
//                            }
//                        }
//                        settings.javaScriptEnabled = true
//                        loadUrl(tableauUrl)
//                    }
//                }
//            )
//
//            // Show a loading indicator while the page is loading
//            if (isLoading) {
//                CircularProgressIndicator()
//            }
//
//            // Show an error message if loading fails
//            if (hasError) {
//                Text("Failed to load dashboard. Please check your internet connection.")
//            }
//        }
//    }
}