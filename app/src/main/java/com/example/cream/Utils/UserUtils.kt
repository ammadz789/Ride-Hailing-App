package com.example.cream.Utils

import android.view.View
import com.example.cream.Common
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object UserUtils {

    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
    ){
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e->
                Snackbar.make(view!!,e.message!!,Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"Info updated successfully", Snackbar.LENGTH_LONG).show()
            }

    }

}