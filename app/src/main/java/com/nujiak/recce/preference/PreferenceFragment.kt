package com.nujiak.recce.preference

import android.os.Bundle
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import com.nujiak.recce.R

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)

        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val colorRes = if (typedValue.resourceId != 0) typedValue.resourceId else typedValue.data
        val color = ContextCompat.getColor(requireContext(), colorRes)

        val coordSysPreference = findPreference<IntListPreference>("coordinate_system")
        coordSysPreference?.let {
            it.entryValues = it.entries.mapIndexed { index, _ -> index.toString() }.toTypedArray()
            it.icon.setTint(color)
        }

        val angleUnitPreference = findPreference<IntListPreference>("angle_unit")
        angleUnitPreference?.let {
            it.entryValues = it.entries.mapIndexed { index, _ -> index.toString() }.toTypedArray()
            it.icon.setTint(color)
        }

        val themePreference = findPreference<IntListPreference>("theme_pref")
        themePreference?.apply {
            entryValues = entries.mapIndexed { index, _ -> index.toString() }.toTypedArray()
            icon.setTint(color)
        }
    }
}
