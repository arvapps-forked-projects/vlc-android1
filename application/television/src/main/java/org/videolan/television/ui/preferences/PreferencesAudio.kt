/*
 * *************************************************************************
 *  PreferencesAudio.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_DEFAULT
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_ENABLE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_MODE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PREAMP
import org.videolan.tools.LocaleUtils
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.LocaleUtil
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val TAG = "VLC/PreferencesAudio"
private const val FILE_PICKER_RESULT_CODE = 10000

@Suppress("DEPRECATION")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesAudio : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {

    private lateinit var preferredAudioTrack: ListPreference

    override fun getXml(): Int {
        return R.xml.preferences_audio
    }

    override fun getTitleId(): Int {
        return R.string.audio_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePassThroughSummary()
        preferredAudioTrack = findPreference(KEY_AUDIO_PREFERRED_LANGUAGE)!!
        updatePreferredAudioTrack()
        prepareLocaleList()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val f = super.buildPreferenceDialogFragment(preference)
        if (f is CustomEditTextPreferenceDialogFragment) {
            when (preference.key) {
                KEY_AUDIO_REPLAY_GAIN_DEFAULT, KEY_AUDIO_REPLAY_GAIN_PREAMP -> {
                    f.setFilters(arrayOf(InputFilter.LengthFilter(6)))
                    f.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
                }
            }
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }

    private fun updatePreferredAudioTrack() {
        val value = Settings.getInstance(activity).getString(KEY_AUDIO_PREFERRED_LANGUAGE, null)
        if (value.isNullOrEmpty())
            preferredAudioTrack.summary = getString(R.string.no_track_preference)
        else
            preferredAudioTrack.summary = getString(R.string.track_preference, LocaleUtil.getLocaleName(value))
    }

    private fun updatePassThroughSummary() {
        val pt = preferenceManager.sharedPreferences!!.getBoolean(KEY_AUDIO_DIGITAL_OUTPUT, false)
        findPreference<Preference>(KEY_AUDIO_DIGITAL_OUTPUT)?.setSummary(if (pt) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            KEY_AUDIO_DIGITAL_OUTPUT -> updatePassThroughSummary()
            KEY_AUDIO_PREFERRED_LANGUAGE -> updatePreferredAudioTrack()
            KEY_AUDIO_REPLAY_GAIN_ENABLE, KEY_AUDIO_REPLAY_GAIN_MODE, KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION -> launch { restartLibVLC() }
            KEY_AUDIO_REPLAY_GAIN_DEFAULT, KEY_AUDIO_REPLAY_GAIN_PREAMP -> {
                val defValue = if (key == KEY_AUDIO_REPLAY_GAIN_DEFAULT) "-7.0" else "0.0"
                val newValue = sharedPreferences.getString(key, defValue)
                var fmtValue = defValue
                try {
                    fmtValue = DecimalFormat("###0.0###", DecimalFormatSymbols(Locale.ENGLISH)).format(newValue?.toDouble())
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Could not parse value: $newValue. Setting $key to $fmtValue", e)
                } finally {
                    if (fmtValue != newValue) {
                        sharedPreferences.edit {
                            // putString will trigger another preference change event. Restart libVLC when it settles.
                            putString(key, fmtValue)
                            findPreference<EditTextPreference>(key)?.let { it.text = fmtValue }
                        }
                    } else launch { restartLibVLC() }
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            "soundfont" -> {
                val filePickerIntent = Intent(activity, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.SOUNDFONT.ordinal)
                startActivityForResult(
                    filePickerIntent,
                    FILE_PICKER_RESULT_CODE
                )
            }
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
            if (data == null) return
            if (requestCode == FILE_PICKER_RESULT_CODE) {
                if (data.hasExtra(EXTRA_MRL)) {
                    launch {
                        MediaUtils.useAsSoundFont(activity, Uri.parse(data.getStringExtra(
                            EXTRA_MRL
                        )))
                        VLCInstance.restart()
                    }
                    UiTools.restartDialog(activity!!, true, RESTART_CODE, this)
                }
        }
    }

    private suspend fun restartLibVLC() {
        VLCInstance.restart()
        restartMediaPlayer()
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(BuildConfig.TRANSLATION_ARRAY, getString(R.string.no_track_preference), activity.getLocales())
        preferredAudioTrack.entries = localePair.localeEntries
        preferredAudioTrack.entryValues = localePair.localeEntryValues
    }
}
