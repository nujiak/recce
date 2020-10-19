package com.nujiak.reconnaissance

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
import com.nujiak.reconnaissance.database.*
import com.nujiak.reconnaissance.fragments.ruler.RulerItem
import com.nujiak.reconnaissance.fragments.ruler.generateRulerList
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.ANGLE_UNIT_KEY
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_KEY
import kotlinx.coroutines.*

class MainViewModel(dataSource: ReconDatabaseDao, application: Application) :
    AndroidViewModel(application) {

    private val database = dataSource

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    lateinit var sharedPreference: SharedPreferences
    var screenRotation = 0

    /* Shared preference trackers for showing guides */
    var chainsGuideShown = false // Used in MapFragment

    val allPins = database.getAllPins()
    val allChains = database.getAllChains()
    val lastPin = Transformations.map(allPins) { it.firstOrNull() }

    var lastAddedId = 0L // Tracks pinId of the last added pin for Map Fragment
        private set

    fun addPin(pin: Pin) =
        runBlocking { lastAddedId = insert(pin) } // Blocking to allow pinId to return

    fun updatePin(pin: Pin) = uiScope.launch { update(pin) }
    fun deletePin(pin: Pin) = uiScope.launch { deletePin(pin.pinId) }

    fun addChain(chain: Chain) = uiScope.launch { insert(chain) }
    fun updateChain(chain: Chain) = uiScope.launch { update(chain) }
    fun deleteChain(chain: Chain) = uiScope.launch { deleteChain(chain.chainId) }
    private fun addPinsAndChains(pins: List<Pin>, chains: List<Chain>) {
        uiScope.launch {
            pins.forEach { addPin(it) }
            chains.forEach { addChain(it) }
        }
    }

    val isLocationGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val _isLocPermGranted = MutableLiveData<Boolean>(isLocationGranted)
    val isLocPermGranted: LiveData<Boolean>
        get() = _isLocPermGranted

    private val _hideKeyboardFromThisView = MutableLiveData<View>(null)
    val hideKeyboardFromThisView: LiveData<View>
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
     * LiveData to hold pin to be added, MainActivity observes this
     * to start PinCreatorSheet
     */
    private val _pinToAdd = MutableLiveData<Pin>()
    val pinToAdd: LiveData<Pin>
        get() = _pinToAdd

    /**
     * LiveData to hold chain to be added, MainActivity observes this
     * to start ChainCreatorSheet
     */
    private val _chainToAdd = MutableLiveData<Chain>()
    val chainToAdd: LiveData<Chain>
        get() = _chainToAdd

    /**
     * Pin Creator
     */
    fun openPinCreator(pin: Pin?) {
        _pinToAdd.value = pin ?: Pin("", 0.0, 0.0)
        _pinToAdd.value = null // Reset to null
    }

    fun getGroupNames(): MutableList<String> {
        val groups = mutableListOf<String>()
        allPins.value?.let {
            for (pin in it) {
                if (pin.group !in groups) { groups.add(pin.group) }
            }
        }
        allChains.value?.let {
            for (chain in it) {
                if (chain.group !in groups) { groups.add(chain.group) }
            }
        }
        groups.apply {
            remove("")
            sort()
        }
        return groups
    }

    /**
     * Chain Creator
     */

    fun openChainCreator(chain: Chain) {
        _chainToAdd.value = chain

        // Reset to null
        _chainToAdd.value = null
    }


    /**
     * Preferences variables and functions
     */
    private val _coordinateSystem = MutableLiveData(0)
    val coordinateSystem: LiveData<Int>
        get() = _coordinateSystem

    fun updateCoordinateSystem(coordSysId: Int) {
        if (coordSysId != _coordinateSystem.value) {
            _coordinateSystem.value = coordSysId
        }
    }

    private val _angleUnit = MutableLiveData(0)
    val angleUnit: LiveData<Int>
        get() = _angleUnit

    fun updateAngleUnit(angleUnitId: Int) {
        if (angleUnitId != _angleUnit.value) {
            _angleUnit.value = angleUnitId
        }
    }

    fun initializePrefs() {
        updateCoordinateSystem(sharedPreference.getInt(COORD_SYS_KEY, 0))
        updateAngleUnit(sharedPreference.getInt(ANGLE_UNIT_KEY, 0))
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

    private val _isInPolylineMode = MutableLiveData<Boolean>(false)
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

    private val _toUndoPolyline = MutableLiveData<Boolean>(false)
    val toUndoPolyline : LiveData<Boolean>
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
        val newRulerList = mutableListOf<ReconData>()
        for (id in selectedIds) {
            if (id > 0) {
                newRulerList.add(allPins.value!!.first { it.pinId == id})
            } else {
                newRulerList.add(allChains.value!!.first { it.chainId == -id })
            }
        }
        exitSelectionMode()
        _rulerList.value = generateRulerList(_rulerList.value, newRulerList, getApplication<Application>().resources)
        // Prompt MainActivity to switch ViewPager to Ruler
        switchToRuler()
    }

    private val _switchToRuler = MutableLiveData<Boolean>(false)
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
        val adjustedId = if(isChain) -id else id

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

        uiScope.launch {
            pinIdsToDelete.forEach { pinId -> deletePin(pinId) }
            chainIdsToDelete.forEach { chainId -> deleteChain(chainId) }
        }
    }

    fun onRestoreLastDeletedPins() {
        lastMultiDeletedItems.value?.let{
            uiScope.launch {
                val (pins, chains) = it
                pins?.forEach { pin -> addPin(pin) }
                chains?.forEach { chain -> addChain(chain)}
            }
        }

    }

    private val _shareCode = MutableLiveData<String>()
    val shareCode: LiveData<String>
        get() = _shareCode
    var shareQuantity = Pair<Int?, Int?>(0, 0)

    fun resetShareCode() {
        _shareCode.value = null
    }

    fun onShareSelectedPins() {

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

    fun processShareCode(shareCode: String): Boolean {
        val (pins, chains) = toPinsAndChains(shareCode)
        addPinsAndChains(pins, chains)
        return !(pins.isEmpty() && chains.isEmpty())
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

    /**
     * Override's ViewModel onCleared() to stop any ongoing coroutine database operations
     */
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}