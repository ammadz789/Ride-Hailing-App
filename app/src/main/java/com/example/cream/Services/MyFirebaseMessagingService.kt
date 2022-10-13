package com.example.cream.Services

import com.example.cream.Common
import com.example.cream.Model.Model.DriverRequestRecieved
import com.example.cream.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService(){

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if(FirebaseAuth.getInstance().currentUser != null)
        {
            UserUtils.updateToken(this,token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data != null)
        {
            if (data[Common.NOTI_TITLE].equals(Common.REQUEST_DRIVER_TITLE))
            {
                EventBus.getDefault()
                    .postSticky(
                        DriverRequestRecieved(
                        data[Common.RIDER_KEY]!!,
                        data[Common.PICKUP_LOCATION]!!
                    )
                    )
            }
            else {
                Common.showNotification(
                    this, Random.nextInt(),
                    data[Common.NOTI_TITLE],
                    data[Common.NOTI_BODY],
                    null
                )
            }
        }
    }
}