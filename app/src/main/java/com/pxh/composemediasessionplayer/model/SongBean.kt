package com.pxh.composemediasessionplayer.model

import java.io.Serializable

data class SongBean (var id:String,var title:String,var artist:String,var album:String):Serializable{
    var length = 0
    fun showTime():String{
        return "${length/1000/60}:${length/1000%60}"
    }

    override fun toString(): String {
        return "SongBean(id='$id', title='$title', artist='$artist', album='$album', length=$length)"
    }
}