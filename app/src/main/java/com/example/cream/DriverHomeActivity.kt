package com.example.cream

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.example.cream.Utils.UserUtils
import com.example.cream.databinding.ActivityDriverHomeBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class DriverHomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityDriverHomeBinding
    private lateinit var img_avatar:ImageView
    private lateinit var waitaingDialog:AlertDialog
    private lateinit var storageReference:StorageReference
    private lateinit var drawer:DrawerLayout
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDriverHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarDriverHome.toolbar)


        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_driver_home)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //init
        drawer = drawerLayout

        storageReference = FirebaseStorage.getInstance().getReference()

        waitaingDialog = AlertDialog.Builder(this)
            .setMessage("...")
            .setCancelable(false)
            .create()

        navView.setNavigationItemSelectedListener { it->
            if(it.itemId == R.id.nav_sign_out)
            {
                val builder = AlertDialog.Builder(this@DriverHomeActivity)
                builder.setTitle("Sign Out")
                    .setMessage("You sure?")
                    .setNegativeButton("CANCEL", {dialogInterface, _-> dialogInterface.dismiss()})

                    .setPositiveButton("SIGN OUT"){dialogInterface, _->

                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this@DriverHomeActivity,SplashScreenActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()

                    }.setCancelable(false)

                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(resources.getColor(R.color.colorAccent))
                }
                dialog.show()
            }
            true
        }
        val headerView = navView.getHeaderView(0)
        val text_name = headerView.findViewById<View>(R.id.text_name) as TextView
        val text_phone = headerView.findViewById<View>(R.id.text_phone) as TextView
        val text_star = headerView.findViewById<View>(R.id.text_star) as TextView
        img_avatar = headerView.findViewById<View>(R.id.img_avatar) as ImageView

        text_name.setText(Common.buildWelcomeMessage())
        text_phone.setText(Common.currentUser!!.phoneNumber)
        text_star.setText(StringBuilder().append(Common.currentUser!!.rating))

        if(Common.currentUser != null && Common.currentUser!!.avatar != null && !TextUtils.isEmpty(Common.currentUser!!.avatar))
        {
            Glide.with(this)
                .load(Common.currentUser!!.avatar)
                .into(img_avatar)
        }

        img_avatar.setOnClickListener {
            val intent = Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(Intent.createChooser(intent, "Pick image"), PICK_IMAGE_REQUEST)
        }



    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK)
        {
            if(data != null && data.data != null)
            {
                imageUri = data.data
                img_avatar.setImageURI(imageUri)
                showDialogUpload()
            }
        }
    }

    private fun showDialogUpload() {
        val builder = AlertDialog.Builder(this@DriverHomeActivity)
        builder.setTitle("Change Profile image")
            .setMessage("You sure?")
            .setNegativeButton("CANCEL", {dialogInterface, _-> dialogInterface.dismiss()})
            .setPositiveButton("CHANGE"){dialogInterface, _->

                if(imageUri != null)
                {
                    waitaingDialog.show()
                    val avatarFolder = storageReference.child("avatars/"+FirebaseAuth.getInstance().currentUser!!.uid)

                    avatarFolder.putFile(imageUri!!)
                        .addOnFailureListener{ e->

                            Snackbar.make(drawer,e.message!!,Snackbar.LENGTH_LONG).show()
                            waitaingDialog.dismiss()

                        }.addOnCompleteListener{ task->
                            if(task.isSuccessful)
                            {
                                avatarFolder.downloadUrl.addOnSuccessListener { uri->
                                    val update_data = HashMap<String,Any>()
                                    update_data.put("avatar",uri)

                                    UserUtils.updateUser(drawer,update_data)
                                }
                            }
                            waitaingDialog.dismiss()

                        }.addOnProgressListener { taskSnapshot->
                            val progress = (100.0*taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                            waitaingDialog.setMessage(StringBuilder("Uploading: ").append(progress).append("%"))
                        }
                }



            }.setCancelable(false)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(resources.getColor(android.R.color.holo_red_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(resources.getColor(R.color.colorAccent))
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.driver_home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_driver_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object{
        val PICK_IMAGE_REQUEST = 7272
    }

}