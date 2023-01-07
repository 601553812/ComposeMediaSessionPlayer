package com.pxh.composemediasessionplayer.service

import android.app.Notification
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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import com.pxh.composemediasessionplayer.R
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.util.Util

private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val TAG = "MyService"
private const val CHANNEL_NAME = "MusicChannel"

class MyService : MediaBrowserServiceCompat() {
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationChannel: NotificationChannelCompat
    private lateinit var notification: Notification

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var songList: ArrayList<SongBean>
    private val attributesBuilder =
        AudioAttributesCompat.Builder().apply { setUsage(AudioAttributesCompat.USAGE_MEDIA) }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestBuilder = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
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
                        this@MyService.applicationContext,
                        "获取音乐焦点失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }
    @RequiresApi(Build.VERSION_CODES.O)
    private var request: AudioFocusRequestCompat = requestBuilder.setOnAudioFocusChangeListener (mOnAudioFocusChangeListener).build()
    private lateinit var audioManager: AudioManager
    private val mySessionCallback = MySessionCallback()
    private var focus = MutableLiveData<Int>(AUDIOFOCUS_GAIN)
    private val mediaItems: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
    private var pos = 0
    private var mediaPlayer = MediaPlayer().apply {
        setOnCompletionListener {
            pos++
            pos = Util.posCheck(pos, songList.size)
            reset()
            if (songList[pos].isInApp) {
                setDataSource(this@MyService, Util.transportUri(songList[pos].id))
            } else {
                setDataSource(songList[pos].id)
            }
            prepare()

            setNotificationInfo(songList[pos])
        }
    }.apply {
        setOnPreparedListener {
            if (focus.value == AUDIOFOCUS_GAIN) {
                start()
                mediaSession.setPlaybackState(
                    stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f).build()
                )
                mediaSession.setMetadata(metadataList[pos])
            }
        }
    }
    private var metadataList: ArrayList<MediaMetadataCompat> = ArrayList()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        mediaSession.release()
        releaseAudioFocus()
    }

    private fun setNotificationInfo(songBean: SongBean){
        notificationBuilder.apply {
            setContentTitle(songBean.title)
            setContentText(songBean.artist+songBean.showTime())
            setSmallIcon(R.mipmap.ic_launcher)
        }
        notification = notificationBuilder.build()
        notificationManager.notify(1,notification)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun releaseAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        focus.value = AudioManagerCompat.requestAudioFocus(audioManager, request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        notificationChannel = NotificationChannelCompat.Builder(
            this.packageName,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).build()
        audioManager =  getSystemService(AUDIO_SERVICE) as AudioManager
        requestBuilder.setAudioAttributes(attributesBuilder.build())
            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
        request = requestBuilder.build()
        notificationBuilder = NotificationCompat.Builder(this, this.packageName)
        notificationManager = NotificationManagerCompat.from(this)
        requestAudioFocus()
        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, "MBServiceCompat").apply {

            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

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
        setNotificationInfo(SongBean("","","","",false))
        startForeground(1,notification)

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
            songList = Util.init(this@MyService)
            for (song in songList) {
                if (song.isInApp) {
                    mediaPlayer.setDataSource(this@MyService, Util.transportUri(song.id))
                } else {
                    mediaPlayer.setDataSource(song.id)
                }
                mediaPlayer.prepare()
                song.length = mediaPlayer.duration
                mediaPlayer.reset()
            }
            metadataList = Util.transportMetadata(songList)
            mediaItems.addAll(Util.transportMediaItem(metadataList))
            mediaSession.sendSessionEvent("list", Bundle().apply {
                putSerializable("list", songList)
            })
            if (songList[0].isInApp) {
                mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[0].id))
            } else {
                mediaPlayer.setDataSource(songList[0].id)
            }
            mediaPlayer.prepare()
            result.sendResult(mediaItems)
        } else result.detach()
    }


    inner class MySessionCallback : MediaSessionCompat.Callback() {
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == "mode") {
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
            mediaPlayer.setDataSource(this@MyService, uri)
            mediaPlayer.prepare()
            for (i in songList.indices) {
                if (songList[i].id == Util.transportId(uri)) {
                    pos = i
                }
            }
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
                if (songList[pos].isInApp) {
                    mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[pos].id))
                } else {
                    mediaPlayer.setDataSource(songList[pos].id)
                }
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
                if (songList[pos].isInApp) {
                    mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[pos].id))
                } else {
                    mediaPlayer.setDataSource(songList[pos].id)
                }
                mediaPlayer.prepare()
                super.onSkipToPrevious()
            }
        }

    }
}


