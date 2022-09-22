package com.example.cream.ui.home

//import com.example.cream.databinding.ActivityMapsBinding

//import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import android.Manifest
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.cream.Common
import com.example.cream.R
import com.example.cream.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener


class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mapFragment:SupportMapFragment
    private lateinit var locationRequest:com.google.android.gms.location.LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //geofire
     private lateinit var onlineRef:DatabaseReference
     private lateinit var currentUserRef:DatabaseReference
     private lateinit var driversLocationRef:DatabaseReference
     private lateinit var geoFire:GeoFire

     private val onlineValueEventListener = object:ValueEventListener{
         override fun onDataChange(snapshot: DataSnapshot) {
             if(snapshot.exists())
                 currentUserRef.onDisconnect().removeValue()
         }

         override fun onCancelled(error: DatabaseError) {
             Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
         }

     }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        init()

        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    //@SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun init() {

        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")
        driversLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE)
        currentUserRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(
            FirebaseAuth.getInstance().currentUser!!.uid
        )
        geoFire = GeoFire(driversLocationRef)
        registerOnlineSystem()

        locationRequest = com.google.android.gms.location.LocationRequest()
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.interval = 5000
        locationRequest.setSmallestDisplacement(10f)

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult != null) {
                    super.onLocationResult(locationResult)
                }
                val newPos = LatLng(locationResult!!.lastLocation!!.latitude, locationResult!!.lastLocation!!.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos,18f))

                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    GeoLocation(locationResult.lastLocation!!.latitude, locationResult.lastLocation!!.longitude)
                ){key:String?, error:DatabaseError? ->
                    if(error!=null)
                        Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
                    else
                        Snackbar.make(mapFragment.requireView(),"Online",Snackbar.LENGTH_SHORT).setBackgroundTint(
                            Color.parseColor("#00FF00")).show()

                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper())


    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
        super.onDestroy()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap!!

        Dexter.withContext(requireContext()!!)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object: PermissionListener {
                //@SuppressLint("MissingPermission")
                @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationClickListener {

                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { e->
                                Toast.makeText(context!!, e.message+"YOLO", Toast.LENGTH_SHORT).show()
                            }.addOnSuccessListener { location->
                                val userLatLng = LatLng(location.latitude,location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f))
                            }
                        true
                    }

                    val locationButton = (mapFragment.requireView()!!
                    .findViewById<View>("1".toInt())!!
                            .parent!! as View).findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 50

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(context!!, "Permission"+ p0!!.permissionName+" was denied", Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }

            }).check()

        try{
            val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.uber_maps_style))
            if(!success)
                Log.e("ERROR", "Style parsing")
        }catch (e: Resources.NotFoundException)
        {
            Log.e("ERROR", e.message!!)
        }

    }
}