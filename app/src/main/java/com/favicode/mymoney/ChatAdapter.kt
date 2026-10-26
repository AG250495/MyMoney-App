package com.favicode.mymoney

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messageList: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        val tvBotMessage: TextView = itemView.findViewById(R.id.tvBotMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messageList[position]
        if (msg.isUser) {
            holder.tvUserMessage.text = msg.message
            holder.tvUserMessage.visibility = View.VISIBLE
            holder.tvBotMessage.visibility = View.GONE
        } else {
            holder.tvBotMessage.text = msg.message
            holder.tvBotMessage.visibility = View.VISIBLE
            holder.tvUserMessage.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messageList.size

    fun addMessage(message: ChatMessage) {
        messageList.add(message)
        notifyItemInserted(messageList.size - 1)
    }
}