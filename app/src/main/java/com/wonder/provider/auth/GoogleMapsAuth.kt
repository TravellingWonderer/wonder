package com.wonder.provider.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.wonder.provider.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class GoogleAccountSession(
    val email: String,
    val displayName: String?,
    val account: Account
)

sealed class GoogleSignInOutcome {
    data class Success(val session: GoogleAccountSession) : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data class Failed(val message: String) : GoogleSignInOutcome()
}

class GoogleMapsAuth(private val context: Context) {

    private val appContext = context.applicationContext

    fun signInClient(includeDriveScope: Boolean = false): GoogleSignInClient =
        GoogleSignIn.getClient(appContext, signInOptions(includeDriveScope))

    fun signInIntent(includeDriveScope: Boolean = false): Intent =
        signInClient(includeDriveScope).signInIntent

    fun currentAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(appContext)

    fun hasDriveAccess(account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, Scope(DRIVE_READONLY_SCOPE))

    fun resolveSignInResult(data: Intent?): GoogleSignInOutcome {
        when (val parsed = parseSignInResult(data)) {
            is GoogleSignInOutcome.Success -> return parsed
            else -> {
                val cached = currentAccount()
                val session = cached?.let { sessionFromAccount(it) }
                if (session != null) return GoogleSignInOutcome.Success(session)
                return parsed
            }
        }
    }
    private fun parseSignInResult(data: Intent?): GoogleSignInOutcome {
        if (data == null) return GoogleSignInOutcome.Cancelled
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            sessionFromAccount(account)?.let { GoogleSignInOutcome.Success(it) }
                ?: GoogleSignInOutcome.Failed("Google account details were incomplete.")
        } catch (error: ApiException) {
            when (error.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> GoogleSignInOutcome.Cancelled
                GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                    GoogleSignInOutcome.Failed("Google sign-in failed. Try again.")
                10 -> GoogleSignInOutcome.Failed(
                    "Google sign-in is not configured for this build yet. " +
                        "You can still import a Timeline file below."
                )
                else -> GoogleSignInOutcome.Failed(
                    error.localizedMessage ?: "Google sign-in failed (${error.statusCode})."
                )
            }
        }
    }

    suspend fun accessToken(session: GoogleAccountSession): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(
            appContext,
            session.account,
            "oauth2:$DRIVE_READONLY_SCOPE"
        )
    }

    suspend fun signOut() {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                signInClient(includeDriveScope = true)
                    .signOut()
                    .addOnCompleteListener { continuation.resume(Unit) }
            }
        }
    }

    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun sessionFrom(account: GoogleSignInAccount): GoogleAccountSession? {
        val email = account.email ?: return null
        val androidAccount = account.account ?: return null
        return GoogleAccountSession(
            email = email,
            displayName = account.displayName,
            account = androidAccount
        )
    }

    private fun sessionFromAccount(account: GoogleSignInAccount): GoogleAccountSession? =
        sessionFrom(account)

    private fun signInOptions(includeDriveScope: Boolean): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (includeDriveScope) {
            builder.requestScopes(Scope(DRIVE_READONLY_SCOPE))
        }

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            builder.requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
        return builder.build()
    }

    companion object {
        private const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }
}
