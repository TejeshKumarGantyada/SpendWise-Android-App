package com.tejesh.spendwise.Screens.auth

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageReference
import com.tejesh.spendwise.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Data class to hold profile info from Firestore
data class UserProfile(
    val phone: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val storage: StorageReference,
    private val firestore: FirebaseFirestore,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState

    // --- NEW: StateFlow to hold user profile data from Firestore ---
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        // When the ViewModel is created, if a user is logged in, fetch their profile data
        auth.currentUser?.let {
            loadUserProfile(it.uid)
        }
    }

    private fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                val document = firestore.collection("users").document(userId).get().await()
                _userProfile.value = document.toObject(UserProfile::class.java) ?: UserProfile()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error loading user profile", e)
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        profilePicUri: Uri?,
        phone: String
    ) {
        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("User creation failed")

                if (phone.isNotBlank()) {
                    val userProfileData = mapOf("phone" to phone)
                    firestore.collection("users").document(firebaseUser.uid)
                        .set(userProfileData).await()
                    _userProfile.value = UserProfile(phone = phone)
                }

                var photoUrl: String? = null
                if (profilePicUri != null) {
                    val profilePicRef = storage.child("profile_pictures/${firebaseUser.uid}")
                    profilePicRef.putFile(profilePicUri).await()
                    photoUrl = profilePicRef.downloadUrl.await().toString()
                }

                val profileUpdates = userProfileChangeRequest {
                    displayName = "$firstName $lastName"
                    photoUri = photoUrl?.let { Uri.parse(it) }
                }
                firebaseUser.updateProfile(profileUpdates).await()
                transactionRepository.createDefaultCategories()

                _user.value = auth.currentUser
                _authState.value = AuthState(isLoading = false)

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign up failed", e)
                _authState.value = AuthState(isLoading = false, error = getReadableErrorMessage(e))
            }
        }
    }

    fun updateProfilePicture(profilePicUri: Uri?) {
        if (profilePicUri == null) return
        val firebaseUser = auth.currentUser ?: return

        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                val profilePicRef = storage.child("profile_pictures/${firebaseUser.uid}")
                profilePicRef.putFile(profilePicUri).await()
                val photoUrl = profilePicRef.downloadUrl.await().toString()

                val profileUpdates = userProfileChangeRequest {
                    photoUri = Uri.parse(photoUrl)
                }
                firebaseUser.updateProfile(profileUpdates).await()

                _user.value = auth.currentUser
                _authState.value = AuthState(isLoading = false)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Profile picture update failed", e)
                _authState.value = AuthState(isLoading = false, error = "Failed to update profile picture.")
            }
        }
    }

    fun updateProfile(firstName: String, lastName: String) {
        val firebaseUser = auth.currentUser ?: return
        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                val profileUpdates = userProfileChangeRequest {
                    displayName = "$firstName $lastName"
                }
                firebaseUser.updateProfile(profileUpdates).await()
                _user.value = auth.currentUser
                _authState.value = AuthState(isLoading = false)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Profile name update failed", e)
                _authState.value = AuthState(isLoading = false, error = "Failed to update name.")
            }
        }
    }

    fun updatePhoneNumber(phone: String) {
        val firebaseUser = auth.currentUser ?: return
        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                // Use .update() to only change the phone field without overwriting others
                firestore.collection("users").document(firebaseUser.uid)
                    .update("phone", phone).await()
                _userProfile.value = _userProfile.value.copy(phone = phone)
                _authState.value = AuthState(isLoading = false)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Phone number update failed", e)
                _authState.value = AuthState(isLoading = false, error = "Failed to update phone number.")
            }
        }
    }

    fun signIn(email: String, password: String) {
        _authState.value = AuthState(isLoading = true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _user.value = auth.currentUser
                    // After sign-in, load the user's profile data
                    auth.currentUser?.uid?.let { loadUserProfile(it) }
                    _authState.value = AuthState(isLoading = false)
                } else {
                    _authState.value = AuthState(isLoading = false, error = getReadableErrorMessage(task.exception))
                }
            }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        _userProfile.value = UserProfile() // Clear profile data on logout
    }

    fun deleteAccount() {
        _authState.value = AuthState(isLoading = true)
        val currentUser = auth.currentUser
        currentUser?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("AuthViewModel", "User account deleted successfully.")
                _user.value = null
                _authState.value = AuthState(isLoading = false)
            } else {
                Log.e("AuthViewModel", "Error deleting account", task.exception)
                _authState.value = AuthState(
                    isLoading = false,
                    error = "Error deleting account. Please sign out and sign back in, then try again."
                )
            }
        }
    }

    private fun getReadableErrorMessage(exception: Exception?): String {
        return exception?.message ?: "An unknown error occurred."
    }
}

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
)
