package com.pxh.composemediasessionplayer.service


import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.util.Util

private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val TAG = "MusicPlayerService"
const val REVERSE_FLAG = "reverse"

open class MusicPlayerService : MediaBrowserServiceCompat() {

    private lateinit var _songBean: MutableLiveData<SongBean>

    /**
     * 负责通知Activity当前播放状态的MediaSession对象.
     */
    private lateinit var mediaSession: MediaSessionCompat

    /**
     * 负责创建播放状态对象.
     */
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    /**
     * 播放列表.
     */
     lateinit var songList: ArrayList<SongBean>

    /**
     * 音频焦点申请参数.
     */
    private val attributesBuilder =
        AudioAttributesCompat.Builder().apply { setUsage(AudioAttributesCompat.USAGE_MEDIA) }

    /**
     * 音频焦点申请对象建造者.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private val requestBuilder = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)


    /**
     * 焦点管理者.
     */
    private lateinit var audioManager: AudioManager

    /**
     * 负责接受Activity发来的事件的对象.
     */
    private val mySessionCallback = MySessionCallback()

    /**
     * 当前焦点状态.
     */
    private var focus = MutableLiveData(AUDIOFOCUS_GAIN)

    /**
     * 音频信息列表.
     */
    private val mediaItems: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()

    /**
     * 当前播放歌曲在播放列表中的位置.
     */
     var pos = 0


    var preparedListener = MediaPlayer.OnPreparedListener {
        if (focus.value == AUDIOFOCUS_GAIN) {
            Log.e(TAG, "pos:$pos")
            it.start()
            mediaSession.setPlaybackState(
                stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f).build()
            )

            Log.e(TAG, "metadataList:${metadataList.size}")
            mediaSession.setMetadata(metadataList[pos])
        }
    }

    private val completionListener = MediaPlayer.OnCompletionListener {
        pos++
        pos = Util.posCheck(pos, songList.size)
        it.reset()
        setDataSourceForSong(songList[pos])
        it.prepare()
    }

    /**
     * 播放器对象.
     */
    lateinit var mediaPlayer: MediaPlayer


    /**
     * 焦点变化监听器.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    val mOnAudioFocusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            focus.value = focusChange
            when (focus.value) {
                AUDIOFOCUS_LOSS -> mySessionCallback.onPause()
                AUDIOFOCUS_LOSS_TRANSIENT -> {
                    mySessionCallback.onPause()
                }
                AUDIOFOCUS_GAIN -> mySessionCallback.onPlay()
                AUDIOFOCUS_REQUEST_FAILED -> {
                    Toast.makeText(
                        this.applicationContext,
                        "获取音乐焦点失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }

    /**
     * 焦点申请对象.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private var request: AudioFocusRequestCompat =
        requestBuilder.setOnAudioFocusChangeListener(mOnAudioFocusChangeListener).build()

    /**
     * metadata对象列表.
     */
    private var metadataList: ArrayList<MediaMetadataCompat> = ArrayList()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        mediaSession.release()
        releaseAudioFocus()
    }

    /**
     * 负责设置播放源.
     * @param songBean 需要设置为播放源的歌曲
     *
     */
    private fun setDataSourceForSong(songBean: SongBean) {
        if (songBean.isInApp) {
            mediaPlayer.setDataSource(this, Util.transportUri(songBean.id))
        } else {
            mediaPlayer.setDataSource(songBean.id)
        }
    }

    /**
     * 释放焦点.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun releaseAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
    }

    /**
     * 申请焦点.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        focus.value = AudioManagerCompat.requestAudioFocus(audioManager, request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener(completionListener)
            setOnPreparedListener(preparedListener)
        }
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        requestBuilder.setAudioAttributes(attributesBuilder.build())
            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
        request = requestBuilder.build()
        requestAudioFocus()
        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(this, "MusicPlayerService").apply {

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                ).setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
            setPlaybackState(stateBuilder.build())

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(mySessionCallback)

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)
        }

    }


    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {

        // (Optional) Control the level of access for the specified package name.
        // You'll need to write your own logic to do this.
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }


    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {

        //  Browsing not allowed
        if (MY_EMPTY_MEDIA_ROOT_ID == parentMediaId) {
            result.sendResult(null)
            return
        }

        // Assume for example that the music catalog is already loaded/cached.

        // Check if this is the root menu:
        if (MY_MEDIA_ROOT_ID == parentMediaId && metadataList.isEmpty()) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...
            songList = Util.init(this)
            for (song in songList) {
                Log.e(TAG, song.title)
                setDataSourceForSong(song)
                mediaPlayer.prepare()
                song.length = mediaPlayer.duration
                mediaPlayer.reset()
            }
            metadataList = Util.transportMetadata(songList)
            mediaItems.addAll(Util.transportMediaItem(metadataList))
            mediaSession.sendSessionEvent("list", Bundle().apply {
                putSerializable("list", songList)
            })
            setDataSourceForSong(songList[pos])
            Log.e(TAG, "onLoadChildren:setDataSourceForSong$songList")
            mediaPlayer.prepare()
            result.sendResult(mediaItems)
        } else result.detach()
    }


    inner class MySessionCallback : MediaSessionCompat.Callback() {
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (REVERSE_FLAG == action) {
                songList.reverse()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPlay() {
            Log.e(TAG, "onPlay")
            requestAudioFocus()
            if (focus.value == AUDIOFOCUS_GAIN) {
                mediaPlayer.start()
                mediaSession.setPlaybackState(
                    stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f).build()
                )
            }
            super.onPlay()
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle?) {
            mediaPlayer.reset()
            Log.e(TAG, "onPlayFromUri:pos:$pos")
            for (i in songList.indices) {
                if (songList[i].id == Util.transportId(uri)) {
                    pos = i
                }
            }
            Log.e(TAG, "onPlayFromUri:pos2:$pos")
            setDataSourceForSong(songList[pos])
            mediaPlayer.prepare()
            super.onPlayFromUri(uri, extras)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPause() {
            Log.e(TAG, "onPause")
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                mediaSession.setPlaybackState(
                    stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE)
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1.0f).build()
                )
                releaseAudioFocus()
            }

            super.onPause()
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onSkipToNext() {
            requestAudioFocus()
            if (focus.value == AUDIOFOCUS_GAIN) {
                mediaPlayer.reset()
                pos++
                pos = Util.posCheck(pos, songList.size)
                setDataSourceForSong(songList[pos])
                mediaPlayer.prepare()
                super.onSkipToNext()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onSkipToPrevious() {
            requestAudioFocus()
            if (focus.value == AUDIOFOCUS_GAIN) {
                mediaPlayer.reset()
                pos--
                pos = Util.posCheck(pos, songList.size)
                setDataSourceForSong(songList[pos])
                mediaPlayer.prepare()
                super.onSkipToPrevious()
            }
        }

    }
}


