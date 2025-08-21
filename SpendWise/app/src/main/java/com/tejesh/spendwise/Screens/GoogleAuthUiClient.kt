package com.tejesh.spendwise.Screens.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthUiClient(
    private val context: Context
) {
    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        // THIS IS THE FIX: Paste your Web Client ID here
        .requestIdToken("938074637485-t5si1rcvlf92nd1ud7pn4qf5tt3dd3i5.apps.googleusercontent.com")
        .requestEmail()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun getSignInResultFromIntent(intent: Intent): GoogleSignInResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                GoogleSignInResult(
                    isSuccess = true,
                    credential = credential,
                    displayName = account.displayName,
                    photoUrl = account.photoUrl?.toString()
                )
            } else {
                GoogleSignInResult(isSuccess = false, error = "Google sign-in failed: idToken is null")
            }
        } catch (e: ApiException) {
            GoogleSignInResult(isSuccess = false, error = "Google sign-in failed: ${e.message}")
        }
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class GoogleSignInResult(
    val isSuccess: Boolean,
    val credential: com.google.firebase.auth.AuthCredential? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val error: String? = null
)
