package com.crafttalk.chat.data.remote.socket_service


import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.crafttalk.chat.Events
import com.github.nkzawa.socketio.client.Manager
import com.github.nkzawa.socketio.client.Socket
import com.google.gson.Gson
import com.crafttalk.chat.data.Repository
import com.crafttalk.chat.data.local.pref.deleteVisitorFromPref
import com.crafttalk.chat.data.local.pref.saveVisitorToPref
import com.crafttalk.chat.data.model.MessageType
import com.crafttalk.chat.data.model.Visitor
import com.crafttalk.chat.data.remote.pojo.Message
import com.crafttalk.chat.utils.ChatAttr
import com.crafttalk.chat.utils.NetworkUtils
import com.crafttalk.chat.utils.ConstantsUtils
import com.crafttalk.chat.utils.ConstantsUtils.TAG_SOCKET
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

object SocketAPI {

    private val gson: Gson by lazy {
        Gson()
    }
    private var pref: SharedPreferences? = null
    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    fun setSharedPreferences(pref: SharedPreferences) {
        this.pref = pref
    }

    private val events: MutableLiveData<Events> by lazy {
        MutableLiveData<Events>()
    }

    fun getEventsFromSocket(): LiveData<Events> = events


    private lateinit var visitor: Visitor

//    private val socketAPIScope: CoroutineScope by lazy {
//        CoroutineScope(Dispatchers.IO)// + viewModelJob)
//    }

    private val socket: Socket? by lazy {
        try {
            val  manager = Manager(URI(ConstantsUtils.SOCKET_URL))
            manager.socket(ConstantsUtils.NAMESPACE)
        } catch (e: URISyntaxException) {
            Log.e(TAG_SOCKET, "fail init socket")
            events.value = Events.NO_INTERNET
            null
        }
    }

    fun setVisitor(visitor: Visitor?) {
        Log.d(TAG_SOCKET, "check has visitor - ${Repository.hasVisitor(pref!!)}; ${pref}; ${visitor}")
        Log.d(TAG_SOCKET, "setVisitor visitor - ${visitor?.getJsonObject()}")
        if (visitor == null) {
            Log.d(TAG_SOCKET, "set new state stateAuth - ${false}")
            events.postValue(Events.USER_NOT_FAUND)
        }
        else {
            SocketAPI.visitor = visitor
            if (Repository.hasVisitor(pref!!)) {
                Log.d(TAG_SOCKET, "sent USER_FAUND_WITHOUT_AUTH")
                events.postValue(Events.USER_FAUND_WITHOUT_AUTH)
            }
            Log.d(TAG_SOCKET, "setVisitor - visitor = ${visitor.getJsonObject()} ")
//            android.os.Handler().postDelayed({
                connectUser(socket!!)
//            }, 100)
        }
    }

    private fun setAllListeners(socket: Socket) {
        socket.on("connect") {
            Log.d(TAG_SOCKET, "connecting: ${socket.connected()}")
            authenticationUser(socket)
        }

        socket.on("reconnect") {
            Log.d(TAG_SOCKET, "reconnect")
            events.postValue(Events.RECONNECT)
        }

        socket.on("hide") {
            Log.d(TAG_SOCKET, "hide")
            deleteVisitorFromPref(pref!!)
        }

        socket.on("authorized") {
            Log.d(TAG_SOCKET, "authorized")
            events.postValue(Events.USER_AUTHORIZAT)
            saveVisitorToPref(pref!!, visitor)
        }

        socket.on("message") {
            Log.d(TAG_SOCKET, "message, size = ${it.size}; it = ${it}")
            val messageJson = it[0] as JSONObject
            Log.d("SOCKET_API", "json message___ methon message - ${messageJson}")
            val messageObj = gson.fromJson(messageJson.toString(), Message::class.java)
            if (!messageJson.toString().contains(""""message":"\/start"""")) {
                Repository.insertNewMessageFromServer(messageObj)
                events.postValue(Events.MESSAGE_GET)
            }
        }

        socket.on("history-messages-loaded") {
            Log.d(TAG_SOCKET, "history-messages-loaded, ${it.size}")
            val listMessages = gson.fromJson(it[0].toString(), Array<Message>::class.java)

            listMessages.forEach {
                Log.d(TAG_SOCKET, "history: ${it.toString()}")
            }

            if (listMessages.isEmpty()) {
                greet()
            } else {
                Repository.margeMessages(listMessages)
            }
        }

        socket.on(Socket.EVENT_CONNECT) {
            events.postValue(Events.HAS_INTERNET)
        }
        socket.on(Socket.EVENT_CONNECT_ERROR) {
            Log.d(TAG_SOCKET, "EVENT_CONNECT_ERROR")
            //events.postValue(Events.NO_INTERNET)
        }
        socket.on(Socket.EVENT_CONNECT_TIMEOUT) {
            Log.d(TAG_SOCKET, "EVENT_CONNECT_TIMEOUT")
        }
        socket.on(Socket.EVENT_DISCONNECT) {
            events.postValue(Events.NO_INTERNET)
            Log.d(TAG_SOCKET, "EVENT_DISCONNECT")
        }
        socket.on(Socket.EVENT_ERROR) {
            Log.d(TAG_SOCKET, "EVENT_ERROR")
        }
        socket.on(Socket.EVENT_RECONNECTING) {
            Log.d(TAG_SOCKET, "EVENT_RECONNECTING")
        }
        socket.on(Socket.EVENT_RECONNECT_ATTEMPT) {
            Log.d(TAG_SOCKET, "EVENT_RECONNECT_ATTEMPT")
        }
        socket.on(Socket.EVENT_RECONNECT_ERROR) {
            Log.d(TAG_SOCKET, "EVENT_RECONNECT_ERROR")
        }
        socket.on(Socket.EVENT_RECONNECT_FAILED) {
            Log.d(TAG_SOCKET, "EVENT_RECONNECT_FAILED")
        }

    }


    fun destroy() {
        events.value = Events.SOCKET_DESTROY
        Log.d(TAG_SOCKET, "destroy")
        socket?.disconnect()
        socket?.off()
        pref = null
    }

    private fun greet() {
        viewModelScope.launch {
            if (NetworkUtils.isOnline()) {
                socket!!.emit("visitor-message", "/start", 1, null, 0, null, null, null)
                events.postValue(Events.START_EVENT_SEND)
            } else {
                events.postValue(Events.NO_INTERNET)
            }
        }
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            if (NetworkUtils.isOnline()) {
                socket!!.emit("visitor-message", message, MessageType.VISITOR_MESSAGE.valueType, null, 0, null, null, null)
                events.postValue(Events.MESSAGE_SEND)
            } else {
                events.postValue(Events.NO_INTERNET)
            }
        }
    }

    private fun connectUser(socket: Socket) {
//        viewModelScope.launch {
//            инициализация сокета не должна происходить если сравниваем с null
            if (!socket.connected()) {
                setAllListeners(socket)
                Log.d(TAG_SOCKET, "setVisitor - socket connect}")
//                if (NetworkUtils.isOnline()) {
                    socket.connect()
//                } else {
//                    events.postValue(Events.NO_INTERNET)
//                }
            } else {
                Log.d(TAG_SOCKET, "setVisitor - socket auth}")
                authenticationUser(socket)
            }
//        }
    }

    fun selectAction(actionId: String) {
        viewModelScope.launch {
            if (NetworkUtils.isOnline()) {
                socket!!.emit("visitor-action", actionId)
                events.postValue(Events.ACTION_SELECT)
            } else {
                events.postValue(Events.NO_INTERNET)
            }
        }
    }

    private fun authenticationUser(socket: Socket) {
        Log.d(TAG_SOCKET, "authenticationUser")
        viewModelScope.launch {
            visitor.let {
                if (NetworkUtils.isOnline()) {
                    socket.emit("me", it.getJsonObject(), (ChatAttr.mapAttr["auth_with_hash"] as Boolean))
                } else {
                    events.postValue(Events.NO_INTERNET)
                }
            }
        }
    }

    fun sync(timestamp: Long) {
        viewModelScope.launch {
            if (NetworkUtils.isOnline()) {
                socket!!.emit("history-messages-requested", timestamp)
            } else {
                events.postValue(Events.NO_INTERNET)
            }
        }
    }

}