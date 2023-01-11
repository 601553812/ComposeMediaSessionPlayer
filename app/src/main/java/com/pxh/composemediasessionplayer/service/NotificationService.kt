package com.pxh.composemediasessionplayer.service


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pxh.composemediasessionplayer.R
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.view.MainActivity


private const val TAG = "NotificationService"
private const val notificationChannelId = "notification_channel_id_01"
private const val channelName = "Foreground Service Notification"

@RequiresApi(Build.VERSION_CODES.N)
private const val importance = NotificationManager.IMPORTANCE_MIN


class NotificationService : MusicPlayerService() {

    /**
     * 通知频道.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    val notificationChannel =
        NotificationChannel(notificationChannelId, channelName, importance)

    /**
     * 意图对象.
     * 用于设置点击通知后跳转到Activity.
     */
    private lateinit var activityIntent: Intent

    /**
     * 延时意图对象.
     */
    private lateinit var pendingIntent: PendingIntent

    /**
     * 通知建造者.
     */
    private val builder: NotificationCompat.Builder =
        NotificationCompat.Builder(this, notificationChannelId)
    private lateinit var notificationManager: NotificationManager

    /**
     * 通知对象.
     */
    private lateinit var notification: Notification

    /**
     * 用来改变通知信息.
     * @param songBean 当前播放的歌曲对象.
     */
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
        notification = builder.build()
        notificationManager.notify(10, notification)
    }


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


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        activityIntent = Intent(this, MainActivity::class.java)
        pendingIntent =
            PendingIntent.getActivity(this, 1, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notification = createForegroundNotification()
        startForeground(10, notification)
    }




}


