package com.pxh.composemediasessionplayer.viewModel

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pxh.composemediasessionplayer.model.SongBean

class MyViewModel : ViewModel() {
    /**
     * 默认播放歌曲.
     */
    val song = mutableStateOf(SongBean("id", "title", "artist", "album", true))

    /**
     * 歌曲列表.
     */
    var songList = ArrayList<SongBean>()

    /**
     * 是否翻转播放列表.
     */
    private var reverse = false

    /**
     * MediaBrowser对象,负责接受Service发来的信息.
     */
    lateinit var mediaBrowser: MediaBrowserCompat

    /**
     * MediaController对象,负责向Service发送用户的点击事件.
     */
    lateinit var mediaController: MediaControllerCompat

    /**
     * 当前播放状态.
     * true:正在播放
     * false:暂停
     */
    var playState = MutableLiveData(true)

    /**
     * 是否允许背景播放.
     *
     */
    var backAllowed = MutableLiveData(true)

    fun getSong(): SongBean {
        return song.value
    }

    fun setSong(newSong: SongBean) {
        song.value = newSong
    }

    fun changeReverse() {
        reverse = !reverse
        songList.reverse()
    }

}