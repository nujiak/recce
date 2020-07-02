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
import com.google.android.gms.maps.model.LatLng
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.ReconDatabaseDao
import com.nujiak.reconnaissance.fragments.ruler.RulerItem
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_KEY
import kotlinx.coroutines.*

class MainViewModel(dataSource: ReconDatabaseDao, application: Application) :
    AndroidViewModel(application) {

    private val database = dataSource

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    lateinit var sharedPreference: SharedPreferences
    var screenRotation = 0

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

    val isLocationGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val _isLocPermGranted = MutableLiveData<Boolean>(isLocationGranted)
    val isLocPermGranted: LiveData<Boolean>
        get() = _isLocPermGranted

    fun updateLocPerm(isGranted: Boolean) {
        _isLocPermGranted.value = isGranted
    }

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
        if (pin != null) {
            _pinToAdd.value = pin
        } else {
            _pinToAdd.value = Pin("", 0.0, 0.0)
        }
        // Reset to null
        _pinToAdd.value = null
    }

    fun getPinGroups(): MutableList<String> {
        val groups = mutableListOf<String>()
        allPins.value?.let {
            for (pin in it) {
                if (pin.group !in groups) { groups.add(pin.group) }
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

    fun initializePrefs() {
        updateCoordinateSystem(sharedPreference.getInt(COORD_SYS_KEY, 0))
    }

    /**
     * Map variables and functions
     */

    private val _pinInFocus = MutableLiveData<Pin>()
    val pinInFocus: LiveData<Pin>
        get() = _pinInFocus

    fun showPinOnMap(pin: Pin) {
        _pinInFocus.value = pin
    }

    fun putPinInFocus(pin: Pin) = showPinOnMap(pin)

    private val _toAddPinFromMap = MutableLiveData(false)
    val toAddPinFromMap: LiveData<Boolean>
        get() = _toAddPinFromMap

    private val _isInPolylineMode = MutableLiveData<Boolean>(false)
    val isInPolylineMode: LiveData<Boolean>
        get() = _isInPolylineMode

    val currentPolylinePoints = mutableListOf<Pair<LatLng, String>>()

    fun enterPolylineMode() {
        _isInPolylineMode.value = true
    }

    fun exitPolylineMode() {
        _isInPolylineMode.value = false
        currentPolylinePoints.clear()
    }

    /**
     * Ruler variables
     */

    private val _rulerList = MutableLiveData<List<RulerItem>>(listOf(RulerItem.RulerEmptyItem))
    val rulerList: LiveData<List<RulerItem>>
        get() = _rulerList

    fun onAddSelectionToRuler() {
        val lastSelectedPinIds = selectedPinIds.toList()
        exitSelectionMode()
        allPins.value?.let { allPins ->
            val pinList = allPins.filter {
                lastSelectedPinIds.contains(it.pinId)
            }.toMutableList()

            pinList.sortBy {
                lastSelectedPinIds.indexOf(it.pinId)
            }
            val newList = _rulerList.value?.toMutableList()
            if (newList != null) {
                for (pin in pinList) {
                    if (newList.size == 1 && newList[0] is RulerItem.RulerEmptyItem) {
                        newList[0] = RulerItem.RulerPinItem(pin)
                    } else {
                        newList.add(RulerItem.RulerMeasurementItem)
                        newList.add(RulerItem.RulerPinItem(pin))
                    }
                }
            }
            _rulerList.value = newList
        }
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

    val selectedPinIds = mutableListOf<Long>()
    val selectedChainIds = mutableListOf<Long>()
    private val _selectionChanged = MutableLiveData(false)
    val selectionChanged: LiveData<Boolean>
        get() = _selectionChanged


    fun enterSelectionMode() {
        _isInSelectionMode.value = true
    }

    fun exitSelectionMode() {
        // Remove all items from list of selected IDs
        selectedPinIds.clear()
        selectedChainIds.clear()
        _isInSelectionMode.value = false
    }

    fun togglePinSelection(pinId: Long) {
        if (selectedPinIds.contains(pinId)) {
            selectedPinIds.remove(pinId)
        } else {
            selectedPinIds.add(pinId)
        }
        if (selectedChainIds.size + selectedPinIds.size == 0) {
            exitSelectionMode()
        } else {
            _selectionChanged.value = true
            _selectionChanged.value = false
        }
    }

    fun toggleChainSelection(chainId: Long) {
        if (selectedChainIds.contains(chainId)) {
            selectedChainIds.remove(chainId)
        } else {
            selectedChainIds.add(chainId)
        }
        if (selectedChainIds.size + selectedPinIds.size == 0) {
            exitSelectionMode()
        } else {
            _selectionChanged.value = true
            _selectionChanged.value = false
        }
    }

    private val _lastMultiDeletedPins = MutableLiveData<List<Pin>>()
    val lastMultipleDeletedPins: LiveData<List<Pin>>
        get() = _lastMultiDeletedPins

    fun onDeleteSelectedPins() {
        _lastMultiDeletedPins.value = allPins.value?.filter { it.pinId in selectedPinIds }
        val idsToDelete = selectedPinIds.toList()

        exitSelectionMode()

        uiScope.launch {
            for (pinId in idsToDelete) {
                deletePin(pinId)
            }
        }
    }

    fun onRestoreLastDeletedPins() {
        lastMultipleDeletedPins.value?.let{
            uiScope.launch {
                for (pin in it) {
                    addPin(pin)
                }
            }
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

    /**
     * Override's ViewModel onCleared() to stop any ongoing coroutine database operations
     */
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}