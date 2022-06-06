package com.pxh.composemediasessionplayer.util

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.net.toUri
import com.pxh.composemediasessionplayer.R
import com.pxh.composemediasessionplayer.model.SongBean
class Util {
    companion object{
        fun init():ArrayList<SongBean>{
            val songList = ArrayList<SongBean>()
            songList.add(SongBean(R.raw.a.toString(),"a","singer","album"))
            songList.add(SongBean(R.raw.b.toString(),"b","singer","album"))
            songList.add(SongBean(R.raw.c.toString(),"c","singer","album"))
            return songList
        }
        fun transportUri(id:String):Uri{
            return "android.resource://com.pxh.composemediasessionplayer/$id".toUri()
        }
        fun transportId(uri:Uri):String{
            return uri.toString().substring(53)
        }
        fun transportMetadata(songList: ArrayList<SongBean>):ArrayList<MediaMetadataCompat>{
            val metadataList = ArrayList<MediaMetadataCompat>()
            for (song in songList)
            {
                metadataList.add(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,song.id)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,song.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.length.toLong())
                    .build())
            }
            return metadataList
        }
        fun transportMediaItem(metadataList: ArrayList<MediaMetadataCompat>):ArrayList<MediaBrowserCompat.MediaItem>{
            val itemList = ArrayList<MediaBrowserCompat.MediaItem>()
            for (metadata in metadataList)
            {
                itemList.add(MediaBrowserCompat.MediaItem(metadata.description,MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            return itemList
        }
        fun posCheck(pos:Int,size:Int):Int{
            var newPos = pos
            if (pos<0){
                newPos = size-1
            }else if (pos >=size)
            {
                newPos = 0
            }
            return newPos
        }
    }



}