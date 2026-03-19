package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.firebase.database.*

class FirebaseService : Service() {

    private lateinit var database: DatabaseReference
    private var lastState = false

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannel(this)

        database = FirebaseDatabase.getInstance()
            .getReference("Kitchen/gas_cut")

        database.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val gasCut = snapshot.getValue(Boolean::class.java) ?: false

                if (gasCut && !lastState) {

                    NotificationHelper.showGasAlert(applicationContext)

                }

                lastState = gasCut
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}