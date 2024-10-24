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

    private val _navigateToQrCodeShare = MutableLiveData(false)
    val navigateToQrCodeShare: LiveData<Boolean> = _navigateToQrCodeShare

    private val _navigateToTowerMap = MutableLiveData(false)
    val navigateToTowerMap: LiveData<Boolean> = _navigateToTowerMap

    private val _navigateToWifiSpectrum = MutableLiveData(false)
    val navigateToWifiSpectrum: LiveData<Boolean> = _navigateToWifiSpectrum

    private val _navigateToWifiDetails = MutableLiveData(false)
    val navigateToWifiDetails: LiveData<Boolean> = _navigateToWifiDetails

    private val _navigateToBluetooth = MutableLiveData(false)
    val navigateToBluetooth: LiveData<Boolean> = _navigateToBluetooth

    fun triggerNavigationToQrCodeScanner() {
        _navigateToQrCodeScanner.value = true
    }

    fun triggerNavigationToQrCodeShare() {
        _navigateToQrCodeShare.value = true
    }

    fun triggerNavigationToTowerMap() {
        _navigateToTowerMap.value = true
    }

    fun triggerNavigationToWifiSpectrum() {
        _navigateToWifiSpectrum.value = true
    }

    fun triggerNavigationToWifiDetails() {
        _navigateToWifiDetails.value = true
    }

    fun triggerNavigationToBluetooth() {
        _navigateToBluetooth.value = true
    }

    fun resetNavigationFlag() {
        // TODO is this a good idea to set them all in one method?
        _navigateToQrCodeScanner.value = false
        _navigateToQrCodeShare.value = false
        _navigateToTowerMap.value = false
        _navigateToWifiSpectrum.value = false
        _navigateToWifiDetails.value = false
        _navigateToBluetooth.value = false
    }
}