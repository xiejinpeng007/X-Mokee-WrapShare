/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mokee.warpshare.ui.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.mokee.warpshare.ConfigManager
import org.mokee.warpshare.R
import org.mokee.warpshare.ui.service.ReceiverService

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var mConfigManager: ConfigManager? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        mConfigManager = ConfigManager.apply {
            findPreference<SwitchPreference>(KEY_DISCOVERABLE)?.also{
                it.setSummary(if (this.isDiscoverable) R.string.settings_discoverable_on else R.string.settings_discoverable_off)
            }
            findPreference<EditTextPreference>(KEY_NAME)?.also {
                it.text = this.nameWithoutDefault
                it.summary = this.name
                it.setOnBindEditTextListener { editText: EditText ->
                    editText.hint = this.defaultName
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            ConfigManager.KEY_DISCOVERABLE -> {
                val discoverablePref =
                    findPreference<SwitchPreference>(ConfigManager.KEY_DISCOVERABLE)
                discoverablePref?.setSummary(if (mConfigManager?.isDiscoverable == true) {
                    R.string.settings_discoverable_on
                } else {
                    R.string.settings_discoverable_off
                }
                )
                context?.also{
                    ReceiverService.updateDiscoverability(it)
                }
            }

            ConfigManager.KEY_NAME -> {
                val namePref = findPreference<EditTextPreference>(ConfigManager.KEY_NAME)
                if (namePref != null) {
                    namePref.summary = mConfigManager?.name
                }
            }
        }
    }
}
