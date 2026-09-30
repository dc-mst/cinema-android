package com.devcrumbs.cinema.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Asks the person to pick a Google account and returns its ID token, or null
 * when they dismiss the sheet. [webClientId] is the *web* client id (the token
 * is issued for it); the Android OAuth client of the same Google project,
 * matched by package + signing-key SHA-1, is what lets this app ask. [context]
 * must be an Activity.
 */
suspend fun requestGoogleIdToken(context: Context, webClientId: String): String? {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
        .build()
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (e: GetCredentialCancellationException) {
        return null
    }
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("Unexpected credential type: ${credential.type}")
}
