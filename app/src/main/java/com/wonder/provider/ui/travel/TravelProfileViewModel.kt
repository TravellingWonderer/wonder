package com.wonder.provider.ui.travel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wonder.provider.AppContainer
import com.wonder.provider.auth.GoogleAccountSession
import com.wonder.provider.auth.GoogleSignInOutcome
import com.wonder.provider.auth.GoogleDriveTimelineFetcher
import com.wonder.provider.auth.GoogleMapsAuth
import com.wonder.provider.data.maps.TravelProfileRepository
import com.wonder.provider.model.TravelProfileSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TravelProfileViewModel(
    private val repository: TravelProfileRepository,
    private val googleAuth: GoogleMapsAuth,
    private val driveFetcher: GoogleDriveTimelineFetcher
) : ViewModel() {

    private val _state = MutableStateFlow(TravelProfileUiState())
    val state: StateFlow<TravelProfileUiState> = _state.asStateFlow()
    private var signInInProgress = false

    init {
        viewModelScope.launch {
            repository.getSummary()?.let { applySummary(it) }
            refreshSignedInAccount()
        }
        viewModelScope.launch {
            repository.profile.collect { summary ->
                if (summary != null) applySummary(summary)
            }
        }
    }

    fun bindSignInBridge() {
        AppContainer.googleSignInBridge.setResultHandler { _, data ->
            onGoogleSignInResult(
                data = data,
                forDriveSync = AppContainer.googleSignInBridge.pendingDriveSync
            )
        }
    }

    fun unbindSignInBridge() {
        AppContainer.googleSignInBridge.setResultHandler(null)
    }

    private fun applySummary(summary: TravelProfileSummary) {
        _state.update { current ->
            current.copy(
                summary = summary,
                signedInEmail = current.signedInEmail ?: summary.googleAccountEmail,
                signedInName = current.signedInName ?: summary.googleDisplayName
            )
        }
    }

    fun refreshSignedInAccount() {
        val account = googleAuth.currentAccount()
        _state.update {
            it.copy(
                signedInEmail = account?.email ?: it.signedInEmail ?: it.summary?.googleAccountEmail,
                signedInName = account?.displayName ?: it.signedInName ?: it.summary?.googleDisplayName,
                oauthConfigured = googleAuth.isConfigured
            )
        }
    }

    fun onScreenResumed() {
        refreshSignedInAccount()
        val account = googleAuth.currentAccount()
        if (account != null) {
            val session = googleAuth.sessionFrom(account) ?: return
            if (!_state.value.isSignedIn || signInInProgress) {
                signInInProgress = false
                applySignedIn(
                    session,
                    forDriveSync = AppContainer.googleSignInBridge.pendingDriveSync
                )
            }
            return
        }
        if (!signInInProgress) return
        viewModelScope.launch {
            delay(500)
            if (!signInInProgress) return@launch
            val recovered = googleAuth.currentAccount()?.let { googleAuth.sessionFrom(it) }
            if (recovered != null) {
                signInInProgress = false
                applySignedIn(recovered, forDriveSync = AppContainer.googleSignInBridge.pendingDriveSync)
                return@launch
            }
            signInInProgress = false
            _state.update {
                it.copy(
                    isWorking = false,
                    status = TravelProfileStatus(
                        message = if (googleAuth.isConfigured) {
                            "Sign-in didn't finish. Try again, or import a Timeline file below."
                        } else {
                            "Google sign-in isn't set up for this build yet. " +
                                "Use Import Timeline file below — that works without Google sign-in."
                        },
                        isError = !googleAuth.isConfigured
                    )
                )
            }
        }
    }

    fun onGoogleSignInResult(data: Intent?, forDriveSync: Boolean = false) {
        signInInProgress = false
        when (val outcome = googleAuth.resolveSignInResult(data)) {
            is GoogleSignInOutcome.Success -> applySignedIn(outcome.session, forDriveSync)

            GoogleSignInOutcome.Cancelled -> {
                val recovered = googleAuth.currentAccount()?.let { googleAuth.sessionFrom(it) }
                if (recovered != null) {
                    applySignedIn(recovered, forDriveSync)
                } else {
                    _state.update {
                        it.copy(
                            isWorking = false,
                            needsDriveConsent = false,
                            status = TravelProfileStatus(
                                message = if (forDriveSync) {
                                    "Drive access was not granted."
                                } else {
                                    "Sign-in didn't finish. Try again, or import a Timeline file below."
                                },
                                isError = false
                            )
                        )
                    }
                }
            }

            is GoogleSignInOutcome.Failed -> {
                _state.update {
                    it.copy(
                        isWorking = false,
                        needsDriveConsent = false,
                        status = TravelProfileStatus(message = outcome.message, isError = true)
                    )
                }
            }
        }
    }

    fun launchGoogleSignIn(forDriveSync: Boolean) {
        signInInProgress = true
        _state.update {
            it.copy(
                status = TravelProfileStatus("Opening Google sign-in…", isError = false),
                isWorking = false
            )
        }
        AppContainer.googleSignInBridge.launchSignIn(
            googleAuth.signInIntent(includeDriveScope = forDriveSync),
            forDriveSync = forDriveSync
        )
    }

    private fun applySignedIn(session: GoogleAccountSession, forDriveSync: Boolean) {
        viewModelScope.launch {
            repository.updateGoogleAccount(session.email, session.displayName)
            _state.update {
                it.copy(
                    signedInEmail = session.email,
                    signedInName = session.displayName,
                    isWorking = forDriveSync,
                    needsDriveConsent = false,
                    status = TravelProfileStatus(
                        message = if (forDriveSync) {
                            "Signed in — searching Drive…"
                        } else {
                            "Signed in as ${session.email}"
                        },
                        isError = false
                    )
                )
            }
            refreshSignedInAccount()
            if (forDriveSync) syncFromDriveInternal(session)
        }
    }

    fun onDriveConsentLaunched() {
        _state.update { it.copy(needsDriveConsent = false) }
    }

    fun beginDriveSync() {
        val account = googleAuth.currentAccount()
        val email = _state.value.signedInEmail
        if (account == null && email == null) {
            _state.update {
                it.copy(status = TravelProfileStatus("Sign in with Google first", isError = true))
            }
            return
        }
        if (account != null && !googleAuth.hasDriveAccess(account)) {
            _state.update {
                it.copy(
                    needsDriveConsent = true,
                    status = TravelProfileStatus(
                        "Allow read-only Drive access to find Timeline exports.",
                        isError = false
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val session = when {
                account != null -> GoogleAccountSession(
                    email = account.email ?: email ?: return@launch,
                    displayName = account.displayName,
                    account = account.account ?: return@launch
                )
                else -> {
                    _state.update {
                        it.copy(status = TravelProfileStatus("Sign in again, then retry Drive sync.", isError = true))
                    }
                    return@launch
                }
            }
            syncFromDriveInternal(session)
        }
    }

    private fun syncFromDriveInternal(session: GoogleAccountSession) {
        viewModelScope.launch {
            _state.update {
                it.copy(isWorking = true, needsDriveConsent = false)
            }
            runCatching {
                val token = googleAuth.accessToken(session)
                val visits = driveFetcher.fetchTimelineVisits(token)
                repository.persistVisits(
                    parsed = visits,
                    sourceLabel = "drive",
                    email = session.email,
                    displayName = session.displayName,
                    syncedAt = System.currentTimeMillis()
                )
                visits.size
            }.onSuccess { count ->
                _state.update {
                    it.copy(
                        isWorking = false,
                        status = TravelProfileStatus("Imported $count visits from Google Drive", isError = false)
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isWorking = false,
                        status = TravelProfileStatus(
                            error.message ?: "Drive sync failed",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, status = null) }
            repository.importFromUri(uri)
                .onSuccess { count ->
                    _state.update {
                        it.copy(
                            isWorking = false,
                            status = TravelProfileStatus("Imported $count places into Wonder", isError = false)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isWorking = false,
                            status = TravelProfileStatus(error.message ?: "Import failed", isError = true)
                        )
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuth.signOut()
            _state.update {
                it.copy(
                    signedInEmail = null,
                    signedInName = null,
                    status = TravelProfileStatus("Signed out of Google", isError = false)
                )
            }
            refreshSignedInAccount()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _state.update {
                it.copy(
                    summary = null,
                    status = TravelProfileStatus("Cleared local maps history", isError = false)
                )
            }
        }
    }
}

data class TravelProfileStatus(
    val message: String,
    val isError: Boolean
)

data class TravelProfileUiState(
    val summary: TravelProfileSummary? = null,
    val signedInEmail: String? = null,
    val signedInName: String? = null,
    val oauthConfigured: Boolean = false,
    val needsDriveConsent: Boolean = false,
    val isWorking: Boolean = false,
    val status: TravelProfileStatus? = null
) {
    val isSignedIn: Boolean get() = !signedInEmail.isNullOrBlank()

    val message: String? get() = status?.message?.takeIf { !status.isError }
    val error: String? get() = status?.message?.takeIf { status.isError }
}

class TravelProfileViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TravelProfileViewModel(
            repository = AppContainer.travelProfile,
            googleAuth = AppContainer.googleMapsAuth,
            driveFetcher = AppContainer.driveTimelineFetcher
        ) as T
}
