package com.pxh.composemediasessionplayer.view

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale.Companion.FillBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pxh.composemediasessionplayer.R
import com.pxh.composemediasessionplayer.model.SongBean
import com.pxh.composemediasessionplayer.service.MyService
import com.pxh.composemediasessionplayer.ui.theme.ComposeMediaSessionPlayerTheme
import com.pxh.composemediasessionplayer.util.Util
import com.pxh.composemediasessionplayer.viewModel.MyViewModel

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.e("MainActivity", "onConnected: ")
            val path = myViewModel.mediaBrowser.root
            myViewModel.mediaBrowser.unsubscribe(path)
            myViewModel.mediaBrowser.subscribe(
                path,
                object : MediaBrowserCompat.SubscriptionCallback() {
                    override fun onChildrenLoaded(
                        parentId: String,
                        children: MutableList<MediaBrowserCompat.MediaItem>
                    ) {
                        super.onChildrenLoaded(parentId, children)
                    }
                })
            myViewModel.mediaController =
                MediaControllerCompat(this@MainActivity, myViewModel.mediaBrowser.sessionToken)
            // Finish building the UI
            buildTransportControls()
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }
    }
    private lateinit var myViewModel: MyViewModel


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (!::myViewModel.isInitialized) {
            Log.e(TAG,"onCreate::isNotInit")
            myViewModel = ViewModelProvider(this)[MyViewModel::class.java]
            myViewModel.mediaBrowser = MediaBrowserCompat(
                this,
                ComponentName(this, MyService::class.java),
                connectionCallbacks,
                null // optional Bundle
            ).apply {
                if (!isConnected) {
                    connect()
                }
            }
        }
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, MyService::class.java))
        Log.e(TAG, "onCreate: ")
        setContent {
            ComposeMediaSessionPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Start(myViewModel)
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (!myViewModel.backAllowed.value!!) {
            myViewModel.mediaController.transportControls.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myViewModel.mediaBrowser.isConnected) {
            myViewModel.mediaBrowser.disconnect()
            MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
            stopService(Intent(this, MyService::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        if (!myViewModel.backAllowed.value!!) {
            myViewModel.mediaController.transportControls.pause()
        }
    }

    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
    }


    fun buildTransportControls() {
        // Register a Callback to stay in sync
        myViewModel.mediaController.registerCallback(controllerCallback)
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onSessionEvent(event: String?, extras: Bundle?) {
            myViewModel.songList =
                extras?.getSerializable("list").let {
                    it as ArrayList<SongBean>
                }
            super.onSessionEvent(event, extras)
        }


        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            myViewModel.setSong(
                SongBean(
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE),
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                        .startsWith("android.resource://com.pxh.composemediasessionplayer/")
                ).apply {
                    length = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
                })
            Log.e(TAG, "onMetadataChanged: ${myViewModel.song}")
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            myViewModel.playState.value = state.state == PlaybackStateCompat.STATE_PLAYING
            Log.e(TAG, "onPlaybackStateChanged: ${System.currentTimeMillis()}")
        }
    }


}

@Composable
fun Start(myViewModel: MyViewModel) {
    val controller = rememberNavController()
    NavHost(navController = controller, startDestination = "main") {
        composable("main") {
            GreetingWithStates(myViewModel = myViewModel, controller)
        }
        composable("list") {
            ShowList(myViewModel, controller)
        }
    }
}


@Composable
fun GreetingWithStates(myViewModel: MyViewModel, controller: NavController) {
    val mutableState = remember { mutableStateOf(myViewModel) }
    Greeting(myViewModel = mutableState.value, controller)
}


@Composable
fun Greeting(myViewModel: MyViewModel, controller: NavController) {
    val checkedState = myViewModel.backAllowed.observeAsState()
    val playState = myViewModel.playState.observeAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.clover),
            contentDescription = "A background image filled with clover",
            modifier = Modifier.fillMaxSize(),
            contentScale = FillBounds
        )
        Column(
            Modifier
                .fillMaxWidth()
        ) {
            Row(modifier = Modifier.weight(0.2f)) {
                Text(text = "后台播放", fontSize = 30.sp)
                Switch(
                    checked = checkedState.value!!,
                    onCheckedChange = {
                        myViewModel.backAllowed.value = it
                    }
                )
            }
            Column(
                horizontalAlignment = Alignment.Start, modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "歌曲id:${myViewModel.song.value.id}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black
                )
                Text(
                    text = "歌曲名:${myViewModel.song.value.title}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black
                )
                Text(
                    text = "歌手:${myViewModel.song.value.artist}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black
                )
                Text(
                    text = "专辑:${myViewModel.song.value.album}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black
                )
                Text(
                    text = "歌曲时长:${myViewModel.song.value.showTime()}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f),
                verticalArrangement = Arrangement.Bottom
            ) {

                Button(modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (myViewModel.playState.value!!) myViewModel.mediaController.transportControls.pause() else myViewModel.mediaController.transportControls.play()
                        Log.e(
                            TAG,
                            "Greeting:playState:${playState.value} ,songInfo:${myViewModel.song.value}"
                        )
                    }) {
                    Text(text = if (playState.value == true) "暂停" else "播放")
                }
                Button(modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        myViewModel.mediaController.transportControls.skipToPrevious()
                    }) {
                    Text(text = "上一首")
                }
                Button(modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        myViewModel.mediaController.transportControls.skipToNext()
                    }) {
                    Text(text = "下一首")
                }
                Button(modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        controller.navigate("list")
                    }) {
                    Text(text = "播放列表")
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: SongBean,
    mediaController: MediaControllerCompat,
    controller: NavController
) {
    Row(
        modifier = Modifier
            .clickable {
                controller.navigate("main")
                mediaController.transportControls.playFromUri(Util.transportUri(song.id), Bundle())
            }
            .fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround
    ) {
        Text(text = song.id, Modifier.weight(1f), textAlign = TextAlign.Left)
        Spacer(modifier = Modifier.width(30.dp))
        Text(text = song.title, Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(30.dp))
        Text(text = song.showTime(), Modifier.weight(1f), textAlign = TextAlign.Right)
    }
}


@Composable
fun ShowList(myViewModel: MyViewModel, controller: NavController) {
    val reverseState = remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            Switch(checked = reverseState.value, onCheckedChange = {
                myViewModel.songList.reverse()
                reverseState.value = !reverseState.value
            })
            Text(text = "倒转列表")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "歌曲ID")
            Text(text = "歌曲名")
            Text(text = "歌曲时长")
        }
        LazyColumn {
            items(myViewModel.songList) { song ->
                SongListItem(song = song, myViewModel.mediaController, controller)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ComposeMediaSessionPlayerTheme {
        Start(myViewModel = MyViewModel())
    }
}
