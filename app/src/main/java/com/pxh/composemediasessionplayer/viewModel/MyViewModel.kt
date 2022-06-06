package com.pxh.composemediasessionplayer.viewModel

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pxh.composemediasessionplayer.model.SongBean

class MyViewModel : ViewModel() {
    val song = mutableStateOf(SongBean("id","title","artist","album"))
    var songList = ArrayList<SongBean>()
    private var reverse = false
    lateinit var mediaBrowser: MediaBrowserCompat
    lateinit var mediaController: MediaControllerCompat
    var playState = true
    var backAllowed = true
    fun getSong(): SongBean {
        return song.value!!
    }

    fun setSong(newSong: SongBean) {
        song.value = newSong
    }

    fun changeReverse() {
        reverse = !reverse
        songList.reverse()
    }

}