package com.nujiak.recce

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin
import com.nujiak.recce.enums.SharedPrefsKey
import com.nujiak.recce.enums.ThemePreference
import com.nujiak.recce.fragments.ChainInfoFragment
import com.nujiak.recce.fragments.GoToFragment
import com.nujiak.recce.fragments.PinInfoFragment
import com.nujiak.recce.modalsheets.ChainCreatorSheet
import com.nujiak.recce.modalsheets.PinCreatorSheet
import com.nujiak.recce.modalsheets.SettingsSheet
import com.nujiak.recce.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private val viewModel: MainViewModel by viewModels()

    private lateinit var viewPagerAdapter: MainViewPagerAdapter

    private var actionMode: ActionMode? = null

    companion object {
        const val MAP_INDEX = 0
        const val SAVED_INDEX = 1
        const val GPS_INDEX = 2
        const val RULER_INDEX = 3
    }

    private val mDisplay: Display? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
    }
    private val imm: InputMethodManager by lazy {
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        setTheme(R.style.Theme_Recce)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Run Onboarding
        if (!viewModel.sharedPreference.getBoolean(SharedPrefsKey.ONBOARDING_COMPLETED.key, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }

        // Set app theme (auto/light/dark)
        viewModel.sharedPreference.getInt(SharedPrefsKey.THEME_PREF.key, 0).let {
            setDefaultNightMode(
                when (ThemePreference.atIndex(it)) {
                    ThemePreference.AUTO -> MODE_NIGHT_FOLLOW_SYSTEM
                    ThemePreference.LIGHT -> MODE_NIGHT_NO
                    ThemePreference.DARK -> MODE_NIGHT_YES
                }
            )
        }

        // Set up ViewPager2
        viewPager = findViewById(R.id.view_pager)
        viewPagerAdapter = MainViewPagerAdapter(this)
        viewPager.apply {
            adapter = viewPagerAdapter
            offscreenPageLimit = 3
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    when (position) {
                        MAP_INDEX -> {
                            bottomNavigation.selectedItemId = R.id.btm_nav_map
                            viewPager.isUserInputEnabled = false
                            title = getString(R.string.map)
                        }
                        SAVED_INDEX -> {
                            bottomNavigation.selectedItemId = R.id.btm_nav_saved
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
        }

        // Set up Bottom Navigation
        bottomNavigation = findViewById(R.id.bottom_navigation_bar)
        bottomNavigation.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.btm_nav_map -> {
                    viewPager.currentItem = MAP_INDEX
                    true
                }
                R.id.btm_nav_saved -> {
                    viewPager.currentItem = SAVED_INDEX
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
        bottomNavigation.menu.forEach {
            findViewById<View>(it.itemId).apply{
                setOnLongClickListener { callOnClick() }
                isHapticFeedbackEnabled = false
            }
        }

        // Set up pin-adding sequence
        viewModel.pinToAdd.observe(this, { pin ->
            if (pin != null) {
                openPinCreator(pin)
            }
        })

        // Set up chain-adding sequence
        viewModel.chainToAdd.observe(this, { chain ->
            if (chain != null) {
                openChainCreator(chain)
            }
        })


        // Set up pin and checkpoint showing sequence
        viewModel.pinInFocus.observe(this, { switchToMap() })
        viewModel.chainInFocus.observe(this, { switchToMap() })

        // Set up pin info showing sequence
        viewModel.pinToShowInfo.observe(this) { openPinInfo(it) }
        viewModel.chainToShowInfo.observe(this) { openChainInfo(it) }

        // Set up ruler adding sequence
        viewModel.switchToRuler.observe(this, { switchToRuler ->
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
                    R.id.share -> viewModel.onShareSelectedPins()
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

                // Change status bar color
                TypedValue().let {
                    theme.resolveAttribute(R.attr.colorSurface, it, true)
                    window.statusBarColor = it.data
                }
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

                // Delay switching navigation bar colour to prevent black bar showing.
                Handler(Looper.getMainLooper()).postDelayed({
                    window.statusBarColor =
                        ContextCompat.getColor(baseContext, android.R.color.transparent)
                }, 500)
                viewPager.isUserInputEnabled = true
            }
        }

        viewModel.isInSelectionMode.observe(this, { isInSelectionMode ->
            if (isInSelectionMode) {
                actionMode = startSupportActionMode(callback)
            } else {
                actionMode?.finish()
            }
        })

        // Observe for pin selection changes
        viewModel.selectionChanged.observe(this, {
            actionMode?.let {
                val selectedSize = viewModel.selectedIds.size
                it.title = resources.getQuantityString(
                    R.plurals.number_selected,
                    selectedSize,
                    selectedSize
                )
            }
        })

        // Set up Sharing
        viewModel.shareCode.observe(this, Observer { shareCode ->

            if (shareCode == null) {
                return@Observer
            }

            val alertDialog = AlertDialog.Builder(this)
                .setView(R.layout.dialog_share)
                .create()
            alertDialog.show()

            alertDialog.setOnDismissListener { viewModel.resetShareCode() }

            val shareDescBuilder = StringBuilder()
            viewModel.shareQuantity.let {
                val (pinQty, chainQty) = it
                if (pinQty != null && chainQty != null) {
                    shareDescBuilder.apply {
                        if (pinQty > 0) {
                            append(resources.getQuantityString(R.plurals.pins, pinQty, pinQty))
                            append(' ')
                        }
                        if (pinQty > 0 && chainQty > 0) {
                            append('&')
                            append(' ')
                        }
                        if (chainQty > 0) {
                            append(
                                resources.getQuantityString(
                                    R.plurals.routes_areas,
                                    chainQty,
                                    chainQty
                                )
                            )
                        }
                    }
                }
            }

            alertDialog.findViewById<TextView>(R.id.share_description)?.text =
                shareDescBuilder.toString()

            alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val shareCodeEditText = alertDialog.findViewById<EditText>(R.id.share_string)
            shareCodeEditText?.apply {
                setText(shareCode)
                typeface = Typeface.MONOSPACE
                inputType = InputType.TYPE_NULL
                isSingleLine = false
                maxLines = 10
            }
            alertDialog.findViewById<Button>(R.id.copy)?.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("", shareCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                shareCodeEditText?.requestFocus()
            }
        })

        // Set up Settings sequence
        viewModel.toOpenSettings.observe(this) { toOpenSettings ->
            if (toOpenSettings) {
                openSettings()
            }
        }

        // Set up Go To sequence
        viewModel.toOpenGoTo.observe(this) { initialLatLng ->
            if (initialLatLng != null) {
                openGoTo(initialLatLng)
            }
        }

        object : OrientationEventListener(baseContext) {
            override fun onOrientationChanged(orientation: Int) {
                mDisplay?.let {
                    viewModel.screenRotation = it.rotation
                }

            }
        }.enable()

        viewModel.hideKeyboardFromThisView.observe(this, {
            if (it != null) {
                hideKeyboard(it)
            }
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
        // pinCreatorSheet.setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Recce_BottomSheetDialog_Creators)
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

    private fun openPinInfo(pinId: Long?) {
        pinId?.let {
            val pinInfoFragment = PinInfoFragment()
            pinInfoFragment.pinId = pinId
            pinInfoFragment.show(supportFragmentManager, "pin_info")
            viewModel.hidePinInfo()
        }
    }

    private fun openChainInfo(chainId: Long?) {
        chainId?.let {
            val chainInfoFragment = ChainInfoFragment()
            chainInfoFragment.chainId = chainId
            chainInfoFragment.show(supportFragmentManager, "chain_info")
            viewModel.hideChainInfo()
        }
    }

    private fun openGoTo(initialLatLng: LatLng?) {
        val goToFragment = GoToFragment()
        goToFragment.initialLatLng = initialLatLng
        goToFragment.show(supportFragmentManager, "go_to")
        viewModel.hideGoTo()
    }

    private fun openSettings() {
        val settingsSheet = SettingsSheet()
        settingsSheet.show(supportFragmentManager, settingsSheet.tag)
        viewModel.finishedOpenSettings()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
        } else if (isInPolylineMode) {
            viewModel.undoMapPolyline()
            return
        } else {
            super.onBackPressed()
        }
    }

    private fun hideKeyboard(view: View) {
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
