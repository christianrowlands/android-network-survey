package com.craxiom.networksurvey.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {
    private val _navigateToQrCodeScanner = MutableLiveData(false)
    val navigateToQrCodeScanner: LiveData<Boolean> = _navigateToQrCodeScanner

    fun triggerNavigationToQrCodeScanner() {
        _navigateToQrCodeScanner.value = true
    }

    fun resetNavigationFlag() {
        _navigateToQrCodeScanner.value = false
    }
}