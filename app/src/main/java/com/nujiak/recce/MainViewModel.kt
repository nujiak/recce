package com.nujiak.recce

import android.Manifest
import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.ChainNode
import com.nujiak.recce.database.Pin
import com.nujiak.recce.database.RecceData
import com.nujiak.recce.database.RecceDatabaseDao
import com.nujiak.recce.database.toPinsAndChains
import com.nujiak.recce.database.toShareCode
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.enums.SharedPrefsKey
import com.nujiak.recce.fragments.ruler.RulerItem
import com.nujiak.recce.fragments.ruler.generateRulerList
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.livedatas.RotationLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val database: RecceDatabaseDao,
    val sharedPreference: SharedPreferences,
    application: Application
) :
    AndroidViewModel(application) {

    var screenRotation = 0
        set(newRotation) {
            field = newRotation
            rotationLiveData.updateRotation(newRotation)
        }

    /* Shared preference trackers for showing guides */
    var chainsGuideShown = false // Used in MapFragment

    val allPins = database.getAllPins()
    val allChains = database.getAllChains()
    val lastPin = Transformations.map(allPins) { it.firstOrNull() }

    var lastAddedId = 0L // Tracks pinId of the last added pin for Map Fragment
        private set

    fun addPin(pin: Pin) =
        runBlocking { lastAddedId = insert(pin) } // Blocking to allow pinId to return

    fun updatePin(pin: Pin) = viewModelScope.launch { update(pin) }
    fun deletePin(pin: Pin) = viewModelScope.launch { deletePin(pin.pinId) }

    fun addChain(chain: Chain) =
        runBlocking { lastAddedId = insert(chain) }

    fun updateChain(chain: Chain) = viewModelScope.launch { update(chain) }
    fun deleteChain(chain: Chain) = viewModelScope.launch { deleteChain(chain.chainId) }
    private fun addPinsAndChains(pins: List<Pin>, chains: List<Chain>) {
        viewModelScope.launch {
            pins.forEach { addPin(it) }
            chains.forEach { addChain(it) }
        }
    }

    val isLocationGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val _isLocPermGranted = MutableLiveData(isLocationGranted)
    val isLocPermGranted: LiveData<Boolean>
        get() = _isLocPermGranted

    private val _hideKeyboardFromThisView = MutableLiveData<View?>(null)
    val hideKeyboardFromThisView: LiveData<View?>
        get() = _hideKeyboardFromThisView

    fun hideKeyboardFromView(view: View) {
        _hideKeyboardFromThisView.value = view
        _hideKeyboardFromThisView.value = null
    }

    /**
     * FusedLocationLiveData for fetching and observing Fused Location
     */
    val fusedLocationData = FusedLocationLiveData(application)

    /**
     * RotationLiveData for fetching and observing device rotation
     */
    val rotationLiveData = RotationLiveData(application, screenRotation)

    /**
     * LiveData to hold pin to be shown as a PinInfoFragment dialog,
     * MainActivity observes this to start PinInfoFragment
     */
    private val _pinToShowInfo = MutableLiveData<Long?>()
    val pinToShowInfo: LiveData<Long?>
        get() = _pinToShowInfo

    fun showPinInfo(pinId: Long?) {
        _pinToShowInfo.value = pinId
    }

    fun hidePinInfo() = showPinInfo(null)

    /**
     * LiveData to hold chain to be shown as a ChainInfoFragment dialog,
     * MainActivity observes this to start ChainInfoFragment
     */
    private val _chainToShowInfo = MutableLiveData<Long?>()
    val chainToShowInfo: LiveData<Long?>
        get() = _chainToShowInfo

    fun showChainInfo(chainId: Long?) {
        _chainToShowInfo.value = chainId
    }

    fun hideChainInfo() = showChainInfo(null)

    /**
     * LiveData to hold Boolean on whether to open Settings sheet.
     * MainActivity observes this to start SettingsSheet
     */
    private val _toOpenSettings = MutableLiveData<Boolean>(false)
    val toOpenSettings: LiveData<Boolean>
        get() = _toOpenSettings

    fun openSettings() {
        _toOpenSettings.value = true
    }

    fun finishedOpenSettings() {
        _toOpenSettings.value = false
    }

    /**
     * LiveData to hold Boolean on whether to open Go To dialog.
     * MainActivity observes this to start GoToFragment
     */
    private val _toOpenGoTo = MutableLiveData<LatLng?>(null)
    val toOpenGoTo: LiveData<LatLng?>
        get() = _toOpenGoTo

    fun openGoTo(currentLat: Double, currentLng: Double) {
        _toOpenGoTo.value = LatLng(currentLat, currentLng)
    }

    fun hideGoTo() {
        _toOpenGoTo.value = null
    }

    private val _mapGoTo = MutableLiveData<LatLng?>()
    val mapGoTo: LiveData<LatLng?>
        get() = _mapGoTo

    fun mapGoTo(latLng: LatLng) {
        _mapGoTo.value = latLng
    }

    fun finishedMapGoTo() {
        _mapGoTo.value = null
    }

    /*
     * Pin Creator
     */

    /**
     * LiveData to hold pin to be added, MainActivity observes this
     * to start PinCreatorSheet
     */
    private val _pinToAdd = MutableLiveData<Pin?>()
    val pinToAdd: LiveData<Pin?>
        get() = _pinToAdd

    fun openPinCreator(pin: Pin?) {
        _pinToAdd.value = pin ?: Pin("", 0.0, 0.0)
        _pinToAdd.value = null // Reset to null
    }

    fun getGroupNames(): MutableList<String> {
        val groups = mutableListOf<String>()
        allPins.value?.let {
            for (pin in it) {
                if (pin.group !in groups) {
                    groups.add(pin.group)
                }
            }
        }
        allChains.value?.let {
            for (chain in it) {
                if (chain.group !in groups) {
                    groups.add(chain.group)
                }
            }
        }
        groups.apply {
            remove("")
            sort()
        }
        return groups
    }

    /*
     * Chain Creator
     */

    /**
     * LiveData to hold chain to be added, MainActivity observes this
     * to start ChainCreatorSheet
     */
    private val _chainToAdd = MutableLiveData<Chain?>()
    val chainToAdd: LiveData<Chain?>
        get() = _chainToAdd

    fun openChainCreator(chain: Chain) {
        _chainToAdd.value = chain

        // Reset to null
        _chainToAdd.value = null
    }

    /**
     * Preferences variables and functions
     */
    private val _coordinateSystem = MutableLiveData(CoordinateSystem.atIndex(sharedPreference.getInt(SharedPrefsKey.COORDINATE_SYSTEM.key, 0)))
    val coordinateSystem: LiveData<CoordinateSystem>
        get() = _coordinateSystem

    fun updateCoordinateSystem(coordSysIndex: Int) {
        val coordSys = CoordinateSystem.atIndex(coordSysIndex)
        if (coordSys != _coordinateSystem.value) {
            _coordinateSystem.value = coordSys
        }
    }

    private val _angleUnit =
        MutableLiveData(
            AngleUnit.atIndex(
                sharedPreference.getInt(SharedPrefsKey.ANGLE_UNIT.key, 0)
            )
        )
    val angleUnit: LiveData<AngleUnit>
        get() = _angleUnit

    fun updateAngleUnit(angleUnitIndex: Int) {
        val angleUnit = AngleUnit.atIndex(angleUnitIndex)
        if (angleUnit != _angleUnit.value) {
            _angleUnit.value = angleUnit
        }
    }

    /**
     * Map variables and functions
     */

    private val _pinInFocus = MutableLiveData<Pin>()
    val pinInFocus: LiveData<Pin>
        get() = _pinInFocus

    private val _chainInFocus = MutableLiveData<Chain>()
    val chainInFocus: LiveData<Chain>
        get() = _chainInFocus

    fun showPinOnMap(pin: Pin) {
        _pinInFocus.value = pin
    }

    fun showChainOnMap(chain: Chain) {
        _chainInFocus.value = chain
    }

    private val _toAddPinFromMap = MutableLiveData(false)
    val toAddPinFromMap: LiveData<Boolean>
        get() = _toAddPinFromMap

    private val _isInPolylineMode = MutableLiveData(false)
    val isInPolylineMode: LiveData<Boolean>
        get() = _isInPolylineMode

    val currentPolylinePoints = mutableListOf<ChainNode>()

    fun enterPolylineMode() {
        _isInPolylineMode.value = true
    }

    fun exitPolylineMode() {
        _isInPolylineMode.value = false
        currentPolylinePoints.clear()
    }

    private val _toUndoPolyline = MutableLiveData(false)
    val toUndoPolyline: LiveData<Boolean>
        get() = _toUndoPolyline

    fun undoMapPolyline() {
        _toUndoPolyline.value = true
        _toUndoPolyline.value = false
    }

    /**
     * Ruler variables
     */

    private val _rulerList = MutableLiveData<List<RulerItem>>(listOf(RulerItem.RulerEmptyItem))
    val rulerList: LiveData<List<RulerItem>>
        get() = _rulerList

    fun onAddSelectionToRuler() {
        val newRulerList = mutableListOf<RecceData>()
        for (id in selectedIds) {
            if (id > 0) {
                newRulerList.add(allPins.value!!.first { it.pinId == id })
            } else {
                newRulerList.add(allChains.value!!.first { it.chainId == -id })
            }
        }
        exitSelectionMode()
        _rulerList.value = generateRulerList(
            _rulerList.value,
            newRulerList,
            getApplication<Application>().resources
        )
        // Prompt MainActivity to switch ViewPager to Ruler
        switchToRuler()
    }

    private val _switchToRuler = MutableLiveData(false)
    val switchToRuler: LiveData<Boolean>
        get() = _switchToRuler

    private fun switchToRuler() {
        _switchToRuler.value = true
        _switchToRuler.value = false
    }

    fun clearRuler() {
        _rulerList.value = listOf(RulerItem.RulerEmptyItem)
    }

    /**
     * Pins selection
     */

    private val _isInSelectionMode = MutableLiveData(false)
    val isInSelectionMode: LiveData<Boolean>
        get() = _isInSelectionMode

    val selectedIds = mutableListOf<Long>()
    private val _selectionChanged = MutableLiveData(false)
    val selectionChanged: LiveData<Boolean>
        get() = _selectionChanged

    fun enterSelectionMode() {
        _isInSelectionMode.value = true
    }

    fun exitSelectionMode() {
        // Remove all items from list of selected IDs
        selectedIds.clear()
        _isInSelectionMode.value = false
    }

    fun toggleSelection(id: Long, isChain: Boolean) {
        val adjustedId = if (isChain) -id else id

        if (selectedIds.contains(adjustedId)) {
            selectedIds.remove(adjustedId)
        } else {
            selectedIds.add(adjustedId)
        }
        if (selectedIds.size == 0) {
            exitSelectionMode()
        } else {
            _selectionChanged.value = true
            _selectionChanged.value = false
        }
    }

    private val _lastMultiDeletedItems = MutableLiveData<Pair<List<Pin>?, List<Chain>?>>()
    val lastMultiDeletedItems: LiveData<Pair<List<Pin>?, List<Chain>?>>
        get() = _lastMultiDeletedItems

    fun onDeleteSelectedPins() {

        val pinIdsToDelete = mutableListOf<Long>()
        val chainIdsToDelete = mutableListOf<Long>()

        for (id in selectedIds) {
            if (id > 0) {
                pinIdsToDelete.add(id)
            } else {
                chainIdsToDelete.add(-id)
            }
        }

        val lastMultiDeletedPins = allPins.value?.filter { it.pinId in pinIdsToDelete }
        val lastMultiDeletedChains = allChains.value?.filter { it.chainId in chainIdsToDelete }

        _lastMultiDeletedItems.value = Pair(lastMultiDeletedPins, lastMultiDeletedChains)

        exitSelectionMode()

        viewModelScope.launch {
            pinIdsToDelete.forEach { pinId -> deletePin(pinId) }
            chainIdsToDelete.forEach { chainId -> deleteChain(chainId) }
        }
    }

    fun onRestoreLastDeletedPins() {
        lastMultiDeletedItems.value?.let {
            viewModelScope.launch {
                val (pins, chains) = it
                pins?.forEach { pin -> addPin(pin) }
                chains?.forEach { chain -> addChain(chain) }
            }
        }
    }

    private val _shareCode = MutableLiveData<String?>()
    val shareCode: LiveData<String?>
        get() = _shareCode
    var shareQuantity = Pair<Int?, Int?>(0, 0)

    fun resetShareCode() {
        _shareCode.value = null
    }

    /**
     * Filters the selected pins and chains for sharing
     */
    fun onShareSelectedPins() {
        viewModelScope.launch(Dispatchers.IO) {
            val pinIdsToShare = mutableListOf<Long>()
            val chainIdsToShare = mutableListOf<Long>()

            for (id in selectedIds) {
                if (id > 0) {
                    pinIdsToShare.add(id)
                } else {
                    chainIdsToShare.add(-id)
                }
            }

            val pinsToShare = allPins.value?.filter { it.pinId in pinIdsToShare }
            val chainsToShare = allChains.value?.filter { it.chainId in chainIdsToShare }

            shareQuantity = Pair(pinsToShare?.size, chainsToShare?.size)
            _shareCode.value = toShareCode(pinsToShare, chainsToShare)
        }
    }

    /**
     * Parses the share code input and adds parsed [Pin]s and [Chain]s to the database
     *
     * @param shareCode
     * @return
     */
    fun processShareCode(shareCode: String): Boolean {
        val pair = toPinsAndChains(shareCode)
        return if (pair != null && !(pair.first.isEmpty() && pair.second.isEmpty())) {
            addPinsAndChains(pair.first, pair.second)
            true
        } else {
            false
        }
    }

    /**
     * Coroutine database functions
     */

    private suspend fun insert(pin: Pin) = withContext(Dispatchers.IO) {
        database.insert(pin)
    }

    private suspend fun update(pin: Pin) = withContext(Dispatchers.IO) {
        database.update(pin)
    }

    private suspend fun deletePin(pinId: Long) = withContext(Dispatchers.IO) {
        database.deletePin(pinId)
    }

    private suspend fun insert(chain: Chain) = withContext(Dispatchers.IO) {
        database.insert(chain)
    }

    private suspend fun update(chain: Chain) = withContext(Dispatchers.IO) {
        database.update(chain)
    }

    private suspend fun deleteChain(chainId: Long) = withContext(Dispatchers.IO) {
        database.deleteChain(chainId)
    }
}
