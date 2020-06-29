package com.crafttalk.chat.ui.chat_view.holders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.crafttalk.chat.R

class HolderServerImageMessage(view: View, val clickHandler: (imageUrl: String) -> Unit): RecyclerView.ViewHolder(view), View.OnClickListener {

    override fun onClick(view: View) {
        imageUrl?.let{
            clickHandler(it)
        }
    }

    val img: ImageView = view.findViewById(R.id.server_image)
    val time: TextView = view.findViewById(R.id.time)
    var imageUrl: String? = null

    init {
        view.setOnClickListener(this)
    }

}