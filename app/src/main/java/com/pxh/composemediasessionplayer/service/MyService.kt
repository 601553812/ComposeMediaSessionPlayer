package com.pxh.composemediasessionplayer.service

import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import androidx.media.MediaBrowserServiceCompat
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.util.Util

private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"

class MyService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var songList: ArrayList<SongBean>
    private val attributesBuilder =
        AudioAttributes.Builder().apply { setUsage(AudioAttributes.USAGE_MEDIA) }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestBuilder = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)

    @RequiresApi(Build.VERSION_CODES.O)
    private var request: AudioFocusRequest = requestBuilder.build()
    private lateinit var audioManager: AudioManager
    private val mySessionCallback = MySessionCallback()
    private var focus = 0
    private val mediaItems: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
    private var pos = 0
    private var mediaPlayer = MediaPlayer().apply {
        setOnCompletionListener {
            pos++
            pos = Util.posCheck(pos, songList.size)
            reset()
            setDataSource(this@MyService, Util.transportUri(songList[pos].id))
            prepare()
        }
    }.apply {
        setOnPreparedListener {
            if (focus == AUDIOFOCUS_GAIN) {
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
        audioManager.abandonAudioFocusRequest(request)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        focus = audioManager.requestAudioFocus(request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val mOnAudioFocusChangeListener =
            OnAudioFocusChangeListener { focusChange ->
                focus = focusChange
                when (focus) {
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
        requestBuilder.setAudioAttributes(attributesBuilder.build())
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        request = requestBuilder.build()
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
            songList = Util.init()
            for (song in songList) {
                mediaPlayer.setDataSource(this@MyService, Util.transportUri(song.id))
                mediaPlayer.prepare()
                song.length = mediaPlayer.duration
                mediaPlayer.reset()
            }
            metadataList = Util.transportMetadata(songList)
            mediaItems.addAll(Util.transportMediaItem(metadataList))
            mediaSession.sendSessionEvent("list", Bundle().apply {
                putSerializable("list", songList)
            })
            mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[0].id))
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
            if (focus == AUDIOFOCUS_GAIN) {
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
            for (i in songList.indices){
                if (songList[i].id == Util.transportId(uri)){
                    pos = i
                }
            }
            super.onPlayFromUri(uri, extras)
        }

        override fun onPause() {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                mediaSession.setPlaybackState(
                    stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE)
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1.0f).build()
                )
            }

            super.onPause()
        }

        override fun onSkipToNext() {
            mediaPlayer.reset()
            pos++
            pos = Util.posCheck(pos, songList.size)
            mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[pos].id))
            mediaPlayer.prepare()
            super.onSkipToNext()
        }

        override fun onSkipToPrevious() {
            mediaPlayer.reset()
            pos--
            pos = Util.posCheck(pos, songList.size)
            mediaPlayer.setDataSource(this@MyService, Util.transportUri(songList[pos].id))
            mediaPlayer.prepare()
            super.onSkipToPrevious()
        }
    }
}


