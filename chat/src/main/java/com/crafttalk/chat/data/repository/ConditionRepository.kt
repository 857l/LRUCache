package com.crafttalk.chat.data.repository

import com.crafttalk.chat.data.api.socket.SocketApi
import com.crafttalk.chat.domain.repository.IConditionRepository
import com.crafttalk.chat.initialization.ChatMessageListener
import com.crafttalk.chat.presentation.ChatInternetConnectionListener
import com.crafttalk.chat.utils.ChatStatus
import javax.inject.Inject

class ConditionRepository
@Inject constructor(
    private val socketApi: SocketApi
) : IConditionRepository {

    override fun setInternetConnectionListener(listener: ChatInternetConnectionListener) {
        socketApi.setInternetConnectionListener(listener)
    }

    override fun setMessageListener(listener: ChatMessageListener) {
        socketApi.setMessageListener(listener)
    }

    override fun setStatusChat(newStatus: ChatStatus) {
        if (newStatus in listOf(ChatStatus.ON_CHAT_SCREEN_FOREGROUND_APP, ChatStatus.ON_CHAT_SCREEN_BACKGROUND_APP)) {
            socketApi.cleanBufferMessages()
        }
        socketApi.chatStatus = newStatus
    }

    override fun getStatusChat(): ChatStatus = socketApi.chatStatus

    override fun createSessionChat() {
        socketApi.initSocket()
    }

    override fun destroySessionChat() {
        socketApi.destroySocket()
    }

    override fun dropChat() {
        socketApi.dropChat()
    }

    override fun giveFeedbackOnOperator(countStars: Int) {
        socketApi.giveFeedbackOnOperator(countStars)
    }

}