package com.pxh.composemediasessionplayer.service


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import com.pxh.composemediasessionplayer.R
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.util.Util
import com.pxh.composemediasessionplayer.view.MainActivity


private const val notificationChannelId = "notification_channel_id_01"
private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val TAG = "MyService"
private const val channelName = "Foreground Service Notification"

@RequiresApi(Build.VERSION_CODES.N)
private const val importance = NotificationManager.IMPORTANCE_MIN


class MyService : MediaBrowserServiceCompat() {
    //通道的重要程度
    @RequiresApi(Build.VERSION_CODES.O)
    val notificationChannel =
        NotificationChannel(notificationChannelId, channelName, importance)

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
    private lateinit var songList: ArrayList<SongBean>

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
                        this@MyService.applicationContext,
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
    private var pos = 0

    /**
     *
     */
    private lateinit var notification: Notification

    /**
     * 播放器对象.
     */
    private var mediaPlayer = MediaPlayer().apply {
        setOnCompletionListener {
            pos++
            pos = Util.posCheck(pos, songList.size)
            reset()
            setDataSourceForSong(songList[pos], it)
            prepare()

        }
    }.apply {
        setOnPreparedListener {
            if (focus.value == AUDIOFOCUS_GAIN) {
                changeNotificationInfo(songList[pos])
                start()
                mediaSession.setPlaybackState(
                    stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f).build()
                )
                mediaSession.setMetadata(metadataList[pos])
            }
        }
    }

    private fun changeNotificationInfo(songBean: SongBean) {
        //通知小图标
        builder.setSmallIcon(R.mipmap.zero_small)
        //通知标题
        builder.setContentTitle(songBean.title)
        //通知内容
        Log.e(TAG, songBean.artist)
        builder.setContentText(songBean.artist)
        //设定通知显示的时间
        builder.setWhen(System.currentTimeMillis())
        //设定启动的内容
        builder.setContentIntent(pendingIntent)
        Log.e(TAG, notification.toString())
        notification = builder.build()
        Log.e(TAG, notification.toString())
        notificationManager.notify(10, notification)
    }

    /**
     * metadata对象列表.
     */
    private var metadataList: ArrayList<MediaMetadataCompat> = ArrayList()

    private val builder: NotificationCompat.Builder =
        NotificationCompat.Builder(this, notificationChannelId)
    private lateinit var notificationManager: NotificationManager

    private fun createForegroundNotification(): Notification {
        // 唯一的通知通道的id.
        // Android8.0以上的系统，新建消息通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //用户可见的通道名称
            notificationChannel.description = "矮人播放器通知频道"
            notificationManager.createNotificationChannel(notificationChannel)
        }
        //通知小图标
        builder.setSmallIcon(R.mipmap.zero_small)
        //通知标题
        builder.setContentTitle("ContentTitle")
        //通知内容
        builder.setContentText("ContentText")
        //设定通知显示的时间
        builder.setWhen(System.currentTimeMillis())
        builder.setContentIntent(pendingIntent)
        //创建通知并返回
        return builder.build()
    }

    //设定启动的内容
    private lateinit var activityIntent: Intent
    private lateinit var pendingIntent: PendingIntent

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
     * @param mediaPlayer 播放器对象
     */
    private fun setDataSourceForSong(songBean: SongBean, mediaPlayer: MediaPlayer) {
        if (songBean.isInApp) {
            mediaPlayer.setDataSource(this@MyService, Util.transportUri(songBean.id))
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
        activityIntent = Intent(this, MainActivity::class.java)
        pendingIntent =
            PendingIntent.getActivity(this, 1, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        requestBuilder.setAudioAttributes(attributesBuilder.build())
            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
        request = requestBuilder.build()
        requestAudioFocus()
        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, "MBServiceCompat").apply {

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
        notification = createForegroundNotification()
        startForeground(10, notification)

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
                Log.e(TAG, song.title)
                setDataSourceForSong(song, mediaPlayer)
                mediaPlayer.prepare()
                song.length = mediaPlayer.duration
                mediaPlayer.reset()
            }
            metadataList = Util.transportMetadata(songList)
            mediaItems.addAll(Util.transportMediaItem(metadataList))
            mediaSession.sendSessionEvent("list", Bundle().apply {
                putSerializable("list", songList)
            })
            setDataSourceForSong(songList[0], mediaPlayer)
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
            setDataSourceForSong(songList[pos], mediaPlayer)
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
                setDataSourceForSong(songList[pos], mediaPlayer)
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
                setDataSourceForSong(songList[pos], mediaPlayer)
                mediaPlayer.prepare()
                super.onSkipToPrevious()
            }
        }

    }
}


