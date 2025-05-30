/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

package org.videolan.vlc.gui.audio

import android.annotation.TargetApi
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MotionEventCompat
import androidx.databinding.ViewDataBinding
import androidx.paging.PagedList
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.Settings
import org.videolan.vlc.BR
import org.videolan.vlc.databinding.AudioAlbumTrackItemBinding
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.media.MediaUtils

class AudioAlbumTracksAdapter @JvmOverloads constructor(
    type: Int, eventsHandler: IEventsHandler<MediaLibraryItem>,
    listEventsHandler: IListEventsHandler? = null,
    ) : AudioBrowserAdapter(type, eventsHandler, listEventsHandler)
{

    var forceNoTracks = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMediaItemViewHolder<ViewDataBinding> {
        if (!inflaterInitialized()) {
            inflater = LayoutInflater.from(parent.context)
        }
        val binding = AudioAlbumTrackItemBinding.inflate(inflater, parent, false)
        @Suppress("UNCHECKED_CAST")
        return TrackItemViewHolder(binding) as AbstractMediaItemViewHolder<ViewDataBinding>
    }

    override fun submitList(pagedList: PagedList<MediaLibraryItem>?) {
        super.submitList(pagedList)
        forceNoTracks = pagedList?.any { ((it as? MediaWrapper)?.trackNumber ?: 0) > 0 } == false
    }

    override fun playbackStateChanged(former: MediaWrapper?, currentMedia: MediaWrapper?) {
        super.playbackStateChanged(former, super.currentMedia)
        // The current song has changed.
        // If we want to hide the track numbers but the current playback is in this list, show the track number (with no value)
        if (!Settings.showTrackNumber || forceNoTracks) {
                if (currentList?.contains(former) == false && currentList?.contains(currentMedia) == true) {
                    notifyItemRangeChanged(0, itemCount)
                }
                //playback just stopped
                if (currentList?.contains(former) == true || currentMedia == null) notifyItemRangeChanged(0, itemCount)
            }
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class TrackItemViewHolder(binding: AudioAlbumTrackItemBinding) : AbstractMediaItemViewHolder<AudioAlbumTrackItemBinding>(binding) {
        var onTouchListener: View.OnTouchListener

        override val titleView: TextView = binding.title

        init {
            binding.holder = this
            defaultCover?.let { binding.cover = it }
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }

            onTouchListener = object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    if (listEventsHandler == null) {
                        return false
                    }
                    if (multiSelectHelper.getSelectionCount() != 0) {
                        return false
                    }
                    if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                        listEventsHandler.onStartDrag(this@TrackItemViewHolder)
                        return true
                    }
                    return false
                }
            }
            binding.imageWidth = listImageWidth
        }

        override fun selectView(selected: Boolean) {
            binding.setVariable(BR.selected, selected)
            binding.itemMore.visibility = if (multiSelectHelper.inActionMode) View.INVISIBLE else View.VISIBLE
        }

        override fun setItem(item: MediaLibraryItem?) {
            binding.item = item as MediaWrapper
            binding.trackNumber.text = if (item.trackNumber > 0 && Settings.showTrackNumber)  "${item.trackNumber}." else ""
            binding.subtitle.text = MediaUtils.getMediaSubtitle(item)
        }

        fun shouldShowTrackNumber() : Int {
            return when {
                currentMedia == binding.item -> View.INVISIBLE
                Settings.showTrackNumber && !forceNoTracks -> View.VISIBLE
                currentMedia != null && currentList?.contains(currentMedia) == true -> View.INVISIBLE
                else -> View.GONE
            }
        }

        override fun recycle() {
            binding.cover = defaultCover
            binding.title.isSelected = false
            binding.trackNumber.text = ""
        }

        override fun getMiniVisu() = binding.playing

        override fun changePlayingVisibility(isCurrent: Boolean) { }

    }
}

