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

/**
 * View model used for [MainActivity] and all its fragments
 *
 * @property database
 * @property sharedPreference
 * @constructor constructs a view model with the injected [database] and [sharedPreference]
 *
 * @param application
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val database: RecceDatabaseDao,
    val sharedPreference: SharedPreferences,
    application: Application
) :
    AndroidViewModel(application) {

    /**
     * Screen rotation of the device
     *
     * Updated by [MainActivity]
     */
    var screenRotation = 0
        set(newRotation) {
            field = newRotation
            rotationLiveData.updateRotation(newRotation)
        }

    /**
     * Whether the chain guide has been shown before
     */
    var chainsGuideShown = false // Used in MapFragment

    /**
     * [LiveData] for all pins fetched from the database
     */
    val allPins = database.getAllPins()

    /**
     * [LiveData] for all chains fetched from the database
     */
    val allChains = database.getAllChains()

    /**
     * [LiveData] for the pin added first
     */
    val lastPin = Transformations.map(allPins) { it.firstOrNull() }

    /**
     * The id of the last pin added
     *
     * Used for setting the colors in the Map
     */
    var lastAddedId = 0L // Tracks pinId of the last added pin for Map Fragment
        private set

    /**
     * Adds a [Pin] to the database
     *
     * @param pin
     */
    fun addPin(pin: Pin) {
        runBlocking {
            lastAddedId = insert(pin)
        } // Blocking to allow pinId to return
    }

    /**
     * Updates a [Pin] in the database
     *
     * @param pin
     */
    fun updatePin(pin: Pin) {
        viewModelScope.launch { update(pin) }
    }

    /**
     * Deletes a [Pin] in the database
     *
     * @param pin
     */
    fun deletePin(pin: Pin) {
        viewModelScope.launch { deletePin(pin.pinId) }
    }

    /**
     * Adds a [Chain] to the database
     *
     * @param chain
     */
    fun addChain(chain: Chain)= runBlocking {
        lastAddedId = insert(chain)
    }

    /**
     * Updates a [Chain in the database]
     *
     * @param chain
     * @return
     */
    fun updateChain(chain: Chain) = viewModelScope.launch { update(chain) }

    /**
     * Deletes a [Chain] from the database
     *
     * @param chain
     * @return
     */
    fun deleteChain(chain: Chain) = viewModelScope.launch { deleteChain(chain.chainId) }

    /**
     * Adds all the [pins] and [chains] into the database
     *
     * @param pins
     * @param chains
     */
    private fun addPinsAndChains(pins: List<Pin>, chains: List<Chain>) {
        viewModelScope.launch {
            pins.forEach { addPin(it) }
            chains.forEach { addChain(it) }
        }
    }

    val isLocationGranted: Boolean
        get() {
            val permission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION)
            return permission == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Backing property for [hideKeyboardFromThisView]
     */
    private val _hideKeyboardFromThisView = MutableLiveData<View?>(null)

    /**
     * [LiveData] holding the [View] for the keyboard to be hidden from
     */
    val hideKeyboardFromThisView: LiveData<View?>
        get() = _hideKeyboardFromThisView

    /**
     * Hides the keyboard from the [view]
     *
     * @param view
     */
    fun hideKeyboardFromView(view: View) {
        _hideKeyboardFromThisView.value = view
        _hideKeyboardFromThisView.value = null
    }

    /**
     * [FusedLocationLiveData] for fetching and observing Fused Location
     */
    val fusedLocationData = FusedLocationLiveData(application)

    /**
     * [RotationLiveData] for fetching and observing device rotation
     */
    val rotationLiveData = RotationLiveData(application, screenRotation)

    /**
     * Backing property for [pinToShowInfo]
     */
    private val _pinToShowInfo = MutableLiveData<Long?>()

    /**
     * [LiveData] to hold [Pin] to be shown as a [PinInfoFragment][com.nujiak.recce.fragments.PinInfoFragment] dialog
     *
     * Observed by [MainActivity] to start the dialog
     */
    val pinToShowInfo: LiveData<Long?>
        get() = _pinToShowInfo

    /**
     * Shows the [PinInfoFragment][com.nujiak.recce.fragments.PinInfoFragment] dialog for the [Pin] with [pinId]
     *
     * @param pinId
     */
    fun showPinInfo(pinId: Long?) {
        _pinToShowInfo.value = pinId
    }

    /**
     * Acknowledges that the Pin info dialog has been opened
     *
     */
    fun hidePinInfo() = showPinInfo(null)

    /**
     * Backing property for [chainToShowInfo]
     */
    private val _chainToShowInfo = MutableLiveData<Long?>()

    /**
     * [LiveData] to hold [Chain] to be shown as a ChainInfoFragment dialog
     *
     * [MainActivity] observes this to start [ChainInfoFragment][com.nujiak.recce.fragments.ChainInfoFragment]
     */
    val chainToShowInfo: LiveData<Long?>
        get() = _chainToShowInfo

    /**
     * Shows the info dialog for the [Chain]
     *
     * @param chainId
     */
    fun showChainInfo(chainId: Long?) {
        _chainToShowInfo.value = chainId
    }

    /**
     * Acknowledges the info dialog for the [Chain] has been shown
     *
     * Called by [MainActivity] after observing a change in [showChainInfo]
     */
    fun hideChainInfo() = showChainInfo(null)

    /**
     * Backing property for [toOpenSettings]
     */
    private val _toOpenSettings = MutableLiveData<Boolean>(false)

    /**
     * LiveData to determine whether to open Settings sheet
     *
     * Observed by [MainActivity] to start [SettingsSheet][com.nujiak.recce.modalsheets.SettingsSheet]
     */
    val toOpenSettings: LiveData<Boolean>
        get() = _toOpenSettings

    /**
     * Opens the settings dialog
     */
    fun openSettings() {
        _toOpenSettings.value = true
    }

    /**
     * Acknowledges that the settings dialog has been opened
     *
     * Called by [MainActivity] after opening the dialog
     */
    fun finishedOpenSettings() {
        _toOpenSettings.value = false
    }

    /**
     * Backing property for [toOpenGoTo]
     */
    private val _toOpenGoTo = MutableLiveData<LatLng?>(null)

    /**
     * [LiveData] to determine whether to open the Go To dialog
     *
     * Observed by [MainActivity] to start [GoToFragment][com.nujiak.recce.fragments.GoToFragment]
     */
    val toOpenGoTo: LiveData<LatLng?>
        get() = _toOpenGoTo

    /**
     * Opens the Go To dialog
     *
     * @param currentLat latitude to preset in the dialog
     * @param currentLng longitude to preset in the dialog
     */
    fun openGoTo(currentLat: Double, currentLng: Double) {
        _toOpenGoTo.value = LatLng(currentLat, currentLng)
    }

    /**
     * Acknowledges that the Go To dialog has been opened
     *
     * Called by [MainActivity] after opening the dialog
     */
    fun hideGoTo() {
        _toOpenGoTo.value = null
    }

    /**
     * Backing property for [mapGoTo]
     */
    private val _mapGoTo = MutableLiveData<LatLng?>()

    /**
     * [LiveData] containing the coordinates for the map to go to
     */
    val mapGoTo: LiveData<LatLng?>
        get() = _mapGoTo

    /**
     * Moves the map target to a specified location
     *
     * @param latLng coordinates to move the map camera target to
     */
    fun mapGoTo(latLng: LatLng) {
        _mapGoTo.value = latLng
    }

    /**
     * Acknowledges that the map has been moved to the Go To location
     *
     * Called by [MapFragment][com.nujiak.recce.fragments.MapFragment] after moving the map
     */
    fun finishedMapGoTo() {
        _mapGoTo.value = null
    }

    /*
     * Pin Creator
     */

    /**
     * Backing property for [pinToAdd]
     */
    private val _pinToAdd = MutableLiveData<Pin?>()

    /**
     * [LiveData] to hold the [Pin] to be added
     *
     * Observed by [MainActivity] to start [PinCreatorSheet][com.nujiak.recce.modalsheets.PinCreatorSheet]
     */
    val pinToAdd: LiveData<Pin?>
        get() = _pinToAdd

    /**
     * Opens the [PinCreatorSheet][com.nujiak.recce.modalsheets.PinCreatorSheet] for adding a new [Pin]
     *
     * @param pin values to preset in the dialog
     */
    fun openPinCreator(pin: Pin?) {
        _pinToAdd.value = pin ?: Pin("", 0.0, 0.0)
        _pinToAdd.value = null // Reset to null
    }

    /**
     * Returns all group names present in the database
     *
     * @return list of group names
     */
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
     * Backing property for [chainToAdd]
     */
    private val _chainToAdd = MutableLiveData<Chain?>()

    /**
     * [LiveData] to hold the [Chain] to be added
     *
     * Observed by [MainActivity] to start [ChainCreatorSheet][com.nujiak.recce.modalsheets.ChainCreatorSheet]
     */
    val chainToAdd: LiveData<Chain?>
        get() = _chainToAdd

    /**
     * Opens the [ChainCreatorSheet][com.nujiak.recce.modalsheets.ChainCreatorSheet] for a new [Chain]
     *
     * @param chain
     */
    fun openChainCreator(chain: Chain) {
        _chainToAdd.value = chain

        // Reset to null
        _chainToAdd.value = null
    }

    /*
     * Preferences variables and functions
     */

    /**
     * Backing property for [coordinateSystem]
     */
    private val _coordinateSystem = MutableLiveData(CoordinateSystem.atIndex(sharedPreference.getInt(SharedPrefsKey.COORDINATE_SYSTEM.key, 0)))

    /**
     * [LiveData] holding the [CoordinateSystem] to be used according to the
     * user's preference
     */
    val coordinateSystem: LiveData<CoordinateSystem>
        get() = _coordinateSystem

    /**
     * Update the coordinate system used
     *
     * @param coordSysIndex index of the coordinate system in [CoordinateSystem] to be switched to
     */
    fun updateCoordinateSystem(coordSysIndex: Int) {
        val coordSys = CoordinateSystem.atIndex(coordSysIndex)
        if (coordSys != _coordinateSystem.value) {
            _coordinateSystem.value = coordSys
        }
    }

    /**
     * Backing property for [angleUnit]
     */
    private val _angleUnit =
        MutableLiveData(
            AngleUnit.atIndex(sharedPreference.getInt(SharedPrefsKey.ANGLE_UNIT.key, 0))
        )

    /**
     * [LiveData] holding the [AngleUnit] to be used according to the user's preference
     */
    val angleUnit: LiveData<AngleUnit>
        get() = _angleUnit

    /**
     * Updates the angle unit used
     *
     * @param angleUnitIndex index of the angle unit in [AngleUnit] to be used
     */
    fun updateAngleUnit(angleUnitIndex: Int) {
        val angleUnit = AngleUnit.atIndex(angleUnitIndex)
        if (angleUnit != _angleUnit.value) {
            _angleUnit.value = angleUnit
        }
    }

    /*
     * Map variables and functions
     */

    /**
     * Backing property for [pinInFocus]
     */
    private val _pinInFocus = MutableLiveData<Pin>()

    /**
     * [LiveData] holding the [Pin] for the map to move to
     */
    val pinInFocus: LiveData<Pin>
        get() = _pinInFocus

    /**
     * Moves the map to the [pin]'s location
     *
     * @param pin
     */
    fun showPinOnMap(pin: Pin) {
        _pinInFocus.value = pin
    }

    /**
     * Backing property for [chainInFocus]
     */
    private val _chainInFocus = MutableLiveData<Chain>()

    /**
     * [LiveData] holding the [Chain] for the map to move to
     */
    val chainInFocus: LiveData<Chain>
        get() = _chainInFocus

    /**
     * Moves the map to the [chain]'s location
     *
     * @param chain
     */
    fun showChainOnMap(chain: Chain) {
        _chainInFocus.value = chain
    }

    /**
     * Backing property for [toAddPinFromMap]
     */
    private val _toAddPinFromMap = MutableLiveData(false)

    /**
     * [LiveData] to determine whether to add the current location in the map
     *
     * Observed by [MapFragment][com.nujiak.recce.fragments.MapFragment] to call [openPinCreator]
     */
    val toAddPinFromMap: LiveData<Boolean>
        get() = _toAddPinFromMap

    /**
     * Backing property for [isInPolylineMode]
     */
    private val _isInPolylineMode = MutableLiveData(false)

    /**
     * [LiveData] to determine whether the map is in polyline mode
     */
    val isInPolylineMode: LiveData<Boolean>
        get() = _isInPolylineMode

    /**
     * List of the points added to the polyline while the map is in polyline mode
     */
    val currentPolylinePoints = mutableListOf<ChainNode>()

    /**
     * Switches the app to polyline adding mode
     */
    fun enterPolylineMode() {
        _isInPolylineMode.value = true
    }

    /**
     * Switches the app out of polyline adding mode
     */
    fun exitPolylineMode() {
        _isInPolylineMode.value = false
        currentPolylinePoints.clear()
    }

    /**
     * Backing property for [toUndoPolyline]
     */
    private val _toUndoPolyline = MutableLiveData(false)

    /**
     * [LiveData] to determine whether to remove the last added polyline point
     */
    val toUndoPolyline: LiveData<Boolean>
        get() = _toUndoPolyline

    /**
     * Removes the last added polyline point
     */
    fun undoMapPolyline() {
        _toUndoPolyline.value = true
        _toUndoPolyline.value = false
    }

    /*
     * Ruler variables
     */

    /**
     * Backing property for [rulerList]
     */
    private val _rulerList = MutableLiveData<List<RulerItem>>(listOf(RulerItem.RulerEmptyItem))

    /**
     * [LiveData] holding the list of items to display in the Ruler
     */
    val rulerList: LiveData<List<RulerItem>>
        get() = _rulerList

    /**
     * Adds the list of items with ids from [selectedIds] into the Ruler
     *
     */
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

    /**
     * Backing property for [switchToRuler]
     */
    private val _switchToRuler = MutableLiveData(false)

    /**
     * [LiveData] to determine whether to switch to the Ruler tab
     */
    val switchToRuler: LiveData<Boolean>
        get() = _switchToRuler

    /**
     * Switches the app to the Ruler tab
     */
    private fun switchToRuler() {
        _switchToRuler.value = true
        _switchToRuler.value = false
    }

    /**
     * Removes all items from the Ruler
     *
     */
    fun clearRuler() {
        _rulerList.value = listOf(RulerItem.RulerEmptyItem)
    }

    /*
     * Pins selection
     */

    /**
     * Backing property for [isInSelectionMode]
     */
    private val _isInSelectionMode = MutableLiveData(false)

    /**
     * [LiveData] determining whether the app is in selection mode
     */
    val isInSelectionMode: LiveData<Boolean>
        get() = _isInSelectionMode

    /**
     * List of ids selected in the [SavedFragment][com.nujiak.recce.fragments.saved.SavedFragment]
     */
    val selectedIds = mutableListOf<Long>()

    /**
     * Backing property for [selectionChanged]
     */
    private val _selectionChanged = MutableLiveData(false)

    /**
     * [LiveData] determining whether the id selection has changed
     *
     * Observed by [MainActivity] to update action mode
     */
    val selectionChanged: LiveData<Boolean>
        get() = _selectionChanged

    /**
     * Shifts the app into selection mode
     */
    fun enterSelectionMode() {
        _isInSelectionMode.value = true
    }

    /**
     * Shifts the app out of selection mode
     */
    fun exitSelectionMode() {
        // Remove all items from list of selected IDs
        selectedIds.clear()
        _isInSelectionMode.value = false
    }

    /**
     * Selects or unselects the [Pin]/[Chain]
     *
     * @param id id of [Pin]/[Chain] to toggle selection
     * @param isChain
     */
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

    /**
     * Backing property for [lastMultiDeletedItems]
     */
    private val _lastMultiDeletedItems = MutableLiveData<Pair<List<Pin>?, List<Chain>?>>()

    /**
     * [LiveData] holding the last [Pin]s and [Chain]s that were mass-deleted
     *
     * Observed by [SavedFragment][com.nujiak.recce.fragments.saved.SavedFragment] to allow for undoing
     */
    val lastMultiDeletedItems: LiveData<Pair<List<Pin>?, List<Chain>?>>
        get() = _lastMultiDeletedItems

    /**
     * Updates [lastMultiDeletedItems] and deletes the selected items
     */
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

    /**
     * Adds back the deleted [Pin]s and [Chain]s from [lastMultiDeletedItems]
     */
    fun onRestoreLastDeletedPins() {
        lastMultiDeletedItems.value?.let {
            viewModelScope.launch {
                val (pins, chains) = it
                pins?.forEach { pin -> addPin(pin) }
                chains?.forEach { chain -> addChain(chain) }
            }
        }
    }

    /**
     * Backing property for [shareCode]
     */
    private val _shareCode = MutableLiveData<String?>()

    /**
     * [LiveData] holding the last share code to be displayed
     */
    val shareCode: LiveData<String?>
        get() = _shareCode

    /**
     * The number of [Pin]s and [Chain]s in the last share code contained in [shareCode]
     */
    var shareQuantity = Pair<Int?, Int?>(0, 0)

    /**
     * Acknowledges that the last share code has been displayed
     *
     */
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

    /*
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
