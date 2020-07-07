package com.nujiak.reconnaissance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.modalsheets.ChainCreatorSheet
import com.nujiak.reconnaissance.modalsheets.PinCreatorSheet


class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    lateinit var viewModel: MainViewModel

    private lateinit var viewPagerAdapter: MainViewPagerAdapter

    private var actionMode: ActionMode? = null

    companion object {
        const val MAP_INDEX = 0
        const val PINS_INDEX = 1
        const val GPS_INDEX = 2
        const val RULER_INDEX = 3
    }

    private lateinit var display: Display
    private lateinit var imm: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up ViewPager2
        viewPager = findViewById(R.id.view_pager)
        viewPagerAdapter = MainViewPagerAdapter(this)
        viewPager.adapter = viewPagerAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    MAP_INDEX -> {
                        bottomNavigation.selectedItemId = R.id.btm_nav_map
                        viewPager.isUserInputEnabled = false
                        title = getString(R.string.map)
                    }
                    PINS_INDEX -> {
                        bottomNavigation.selectedItemId = R.id.btm_nav_pins
                        viewPager.isUserInputEnabled = true
                        title = getString(R.string.pins)
                    }
                    GPS_INDEX -> {
                        bottomNavigation.selectedItemId = R.id.btm_nav_gps
                        viewPager.isUserInputEnabled = true
                        title = getString(R.string.pins)
                    }
                    RULER_INDEX -> {
                        bottomNavigation.selectedItemId = R.id.btm_nav_ruler
                        viewPager.isUserInputEnabled = true
                        title = getString(R.string.pins)
                    }
                }
            }
        })

        // Set up Bottom Navigation
        bottomNavigation = findViewById(R.id.bottom_navigation_bar)
        bottomNavigation.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.btm_nav_map -> {
                    viewPager.currentItem = MAP_INDEX
                    true
                }
                R.id.btm_nav_pins -> {
                    viewPager.currentItem = PINS_INDEX
                    true
                }
                R.id.btm_nav_gps -> {
                    viewPager.currentItem = GPS_INDEX
                    true
                }
                R.id.btm_nav_ruler -> {
                    viewPager.currentItem = RULER_INDEX
                    true
                }
                else -> false
            }
        }

        // Set up ViewModel
        val application = requireNotNull(this).application
        val dataSource = ReconDatabase.getInstance(application).pinDatabaseDao
        val viewModelFactory = MainViewModelFactory(dataSource, application)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        // Set up Shared Preference
        viewModel.sharedPreference =
            getSharedPreferences("com.nujiak.reconnaissance", Context.MODE_PRIVATE)
        viewModel.initializePrefs()

        // Set up pin-adding sequence
        viewModel.pinToAdd.observe(this, Observer { pin ->
            if (pin != null) {
                openPinCreator(pin)
            }
        })

        // Set up chain-adding sequence
        viewModel.chainToAdd.observe(this, Observer { chain ->
            if (chain != null) {
                openChainCreator(chain)
            }
        })


        // Set up pin and checkpoint showing sequence
        viewModel.pinInFocus.observe(this, Observer { switchToMap() })
        viewModel.chainInFocus.observe(this, Observer { switchToMap() })

        // Set up ruler adding sequence
        viewModel.switchToRuler.observe(this, Observer { switchToRuler ->
            if (switchToRuler) {
                viewPager.currentItem = RULER_INDEX
                bottomNavigation.selectedItemId = R.id.btm_nav_ruler
            }
        })

        // Set up Action Mode
        val callback = object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                when (item?.itemId) {
                    R.id.add_to_ruler -> viewModel.onAddSelectionToRuler()
                    R.id.delete_pins -> viewModel.onDeleteSelectedPins()
                }
                return true
            }

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menuInflater.inflate(R.menu.pin_selector_action_options, menu)
                // Disable navigation to other fragments
                for (position in 0 until bottomNavigation.menu.size()) {
                    bottomNavigation.menu.getItem(position).isEnabled = false
                }
                viewPager.isUserInputEnabled = false
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                if (viewModel.isInSelectionMode.value != false) {
                    viewModel.exitSelectionMode()
                }
                // Re-enable navigation to other fragments
                for (position in 0 until bottomNavigation.menu.size()) {
                    bottomNavigation.menu.getItem(position).isEnabled = true
                }
                viewPager.isUserInputEnabled = true
            }
        }

        viewModel.isInSelectionMode.observe(this, Observer { isInSelectionMode ->
            if (isInSelectionMode) {
                actionMode = startSupportActionMode(callback)
            } else {
                actionMode?.finish()
            }
        })

        // Observe for pin selection changes
        viewModel.selectionChanged.observe(this, Observer {
            actionMode?.let {
                val selectedSize = viewModel.selectedPinIds.size + viewModel.selectedChainIds.size
                it.title = resources.getQuantityString(R.plurals.number_selected, selectedSize, selectedSize)
            }
        })


        display = windowManager.defaultDisplay
        object : OrientationEventListener(baseContext) {
            override fun onOrientationChanged(orientation: Int) {
                viewModel.screenRotation = display.rotation
            }
        }.enable()

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        viewModel.hideKeyboardFromThisView.observe(this, Observer {
            if (it != null) { hideKeyboard(it) }
        })

        if (!viewModel.isLocationGranted) {
            // Permission to access the location is missing. Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                0
            )
        }
    }

    private fun switchToMap() {
        viewPager.currentItem = MAP_INDEX
        bottomNavigation.selectedItemId = R.id.btm_nav_map
    }

    private fun openPinCreator(pin: Pin) {
        val bundle = Bundle()
        bundle.putParcelable("pin", pin)
        val pinCreatorSheet =
            PinCreatorSheet()
        pinCreatorSheet.arguments = bundle
        pinCreatorSheet.show(supportFragmentManager, pinCreatorSheet.tag)
    }

    private fun openChainCreator(chain: Chain) {
        val bundle = Bundle()
        bundle.putParcelable("chain", chain)
        val chainCreatorSheet =
            ChainCreatorSheet()
        chainCreatorSheet.arguments = bundle
        chainCreatorSheet.show(supportFragmentManager, chainCreatorSheet.tag)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            0 -> if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    this.recreate()
                }
            }
        }
    }

    override fun onBackPressed() {
        val isInPolylineMode = viewModel.isInPolylineMode.value!!
        if (isInPolylineMode && viewPager.currentItem != MAP_INDEX) {
            viewPager.currentItem = MAP_INDEX
            bottomNavigation.selectedItemId = R.id.btm_nav_map
            return
        }
        else if (isInPolylineMode) {
            viewModel.undoMapPolyline()
            return
        }
        else {
            super.onBackPressed()
        }
    }

    private fun hideKeyboard(view: View) {
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
