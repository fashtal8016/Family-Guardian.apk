package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FamilyLink
import com.example.data.model.LocationRecord
import com.example.data.model.UserProfile
import com.example.data.repository.FamilyRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FamilyRepository(application)
    private val auth = Firebase.auth

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String
        get() = auth.currentUser?.email ?: "کاربر خانواده"

    private val _selectedRole = MutableStateFlow<String?>("parent")
    val selectedRole: StateFlow<String?> = _selectedRole.asStateFlow()

    private val _selectedChild = MutableStateFlow<FamilyLink?>(null)
    val selectedChild: StateFlow<FamilyLink?> = _selectedChild.asStateFlow()

    private val _childPairingLink = MutableStateFlow<FamilyLink?>(null)
    val childPairingLink: StateFlow<FamilyLink?> = _childPairingLink.asStateFlow()

    private val _isRequestingLocation = MutableStateFlow(false)
    val isRequestingLocation: StateFlow<Boolean> = _isRequestingLocation.asStateFlow()

    private val _isAddingChild = MutableStateFlow(false)
    val isAddingChild: StateFlow<Boolean> = _isAddingChild.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Children links for parent
    val childrenLinks: StateFlow<List<FamilyLink>> =
        if (currentUserId != null) {
            repository.observeChildrenLinks(currentUserId!!)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = emptyList()
                )
        } else {
            MutableStateFlow(emptyList())
        }

    // Locations for selected child
    private val _selectedChildLocations = MutableStateFlow<List<LocationRecord>>(emptyList())
    val selectedChildLocations: StateFlow<List<LocationRecord>> = _selectedChildLocations.asStateFlow()

    fun selectRole(role: String) {
        _selectedRole.value = role
        viewModelScope.launch {
            try {
                repository.getOrCreateUserProfile(
                    displayName = auth.currentUser?.displayName ?: "Family User",
                    email = currentUserEmail,
                    role = role
                )
            } catch (_: Exception) {}
        }
    }

    fun selectChild(child: FamilyLink) {
        _selectedChild.value = child
        observeLocationsForChild(child.parentId, child.childId)
    }

    fun clearSelectedChild() {
        _selectedChild.value = null
        _selectedChildLocations.value = emptyList()
    }

    private fun observeLocationsForChild(parentId: String, childId: String) {
        viewModelScope.launch {
            repository.observeLocations(parentId, childId).collect { locs ->
                _selectedChildLocations.value = locs
            }
        }
    }

    fun addChild(name: String, code: String) {
        _isAddingChild.value = true
        viewModelScope.launch {
            val result = repository.createChildLink(name, code)
            _isAddingChild.value = false
            result.onFailure {
                _errorMessage.value = it.message
            }
        }
    }

    fun requestLocation(parentId: String, childId: String, note: String = "") {
        _isRequestingLocation.value = true
        viewModelScope.launch {
            val result = repository.requestLocation(parentId, childId, note)
            _isRequestingLocation.value = false
            result.onFailure {
                _errorMessage.value = it.message
            }
        }
    }

    fun unlinkChild(linkId: String) {
        viewModelScope.launch {
            repository.unlinkChild(linkId)
            clearSelectedChild()
        }
    }

    fun setChildPaired(link: FamilyLink) {
        _childPairingLink.value = link
    }
}
