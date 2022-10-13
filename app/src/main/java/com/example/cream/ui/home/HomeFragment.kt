package com.example.cream.ui.home

//import com.example.cream.databinding.ActivityMapsBinding

//import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.cream.Common
import com.example.cream.DriverHomeActivity
import com.example.cream.Model.Model.DriverRequestRecieved
import com.example.cream.R
import com.example.cream.Remote.IGoogleAPI
import com.example.cream.Remote.RetrofitClient
import com.example.cream.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit


class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var chip_decline: Chip
    private lateinit var layout_accept: CardView
    private lateinit var circularProgressBar: CircularProgressBar
    private lateinit var txt_estimate_time: TextView
    private lateinit var txt_estimate_distance: TextView

    private val compositeDisposable = CompositeDisposable()
    private lateinit var iGoogleAPI: IGoogleAPI
    private var blackPolyline: Polyline? = null
    private var greyPolyline: Polyline? = null
    private var polylineOptions: PolylineOptions? = null
    private var blackPolylineOptions: PolylineOptions? = null
    private var polylineList: ArrayList<LatLng?>? = null

    private lateinit var mMap: GoogleMap
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mapFragment:SupportMapFragment
    private var locationRequest: com.google.android.gms.location.LocationRequest?= null
    private var locationCallback: LocationCallback?= null
    private var fusedLocationProviderClient: FusedLocationProviderClient?= null

    //geofire
     private lateinit var onlineRef:DatabaseReference
     private var currentUserRef:DatabaseReference? = null
     private lateinit var driversLocationRef:DatabaseReference
     private lateinit var geoFire:GeoFire

     private val onlineValueEventListener = object:ValueEventListener{
         override fun onDataChange(snapshot: DataSnapshot) {
             if(snapshot.exists() && currentUserRef != null)
                 currentUserRef!!.onDisconnect().removeValue()
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

        initViews(root)

        init()

        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    private fun initViews(root: View?) {
        chip_decline = root!!.findViewById(R.id.chip_decline) as Chip
        layout_accept = root!!.findViewById(R.id.layout_accept) as CardView
        circularProgressBar = root!!.findViewById(R.id.circularProgressBar) as CircularProgressBar
        txt_estimate_distance = root!!.findViewById(R.id.txt_estimate_distance) as TextView
        txt_estimate_time = root!!.findViewById(R.id.txt_estimate_time) as TextView
    }

    //@SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun init() {

        iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")
/*
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(requireView(), getString(R.string.permision_require), Snackbar.LENGTH_LONG).show()
            return
        }
*/

        buildLocationRequest()

        buildLocationCallback()

        updateLocation()
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun updateLocation() {
        if (fusedLocationProviderClient == null)
        {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                //Snackbar.make(requireView(), getString(R.string.permision_require), Snackbar.LENGTH_LONG).show()
                return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(locationRequest!!,locationCallback!!, Looper.myLooper())
        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null)
        {
            locationCallback = object : LocationCallback() {

                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult != null) {
                        super.onLocationResult(locationResult)
                    }
                    val newPos = LatLng(locationResult!!.lastLocation!!.latitude, locationResult!!.lastLocation!!.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos,18f))

                    val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                    val addressList : List<Address>?
                    try {
                        addressList = geoCoder.getFromLocation(locationResult.lastLocation!!.latitude,
                            locationResult.lastLocation!!.longitude, 1)
                        val cityName = addressList[0].locality

                        driversLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE)
                            .child(cityName)
                        currentUserRef = driversLocationRef.child(
                            FirebaseAuth.getInstance().currentUser!!.uid
                        )
                        geoFire = GeoFire(driversLocationRef)

                        //update location
                        geoFire.setLocation(
                            FirebaseAuth.getInstance().currentUser!!.uid,
                            GeoLocation(locationResult.lastLocation!!.latitude, locationResult.lastLocation!!.longitude)
                        ){key:String?, error:DatabaseError? ->
                            if(error!=null)
                                Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()

                        }

                        registerOnlineSystem()

                    }catch (e:IOException)
                    {
                        Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
                    }

                }
            }

        }

    }

    private fun buildLocationRequest() {
        if (locationRequest == null)
        {
            locationRequest = com.google.android.gms.location.LocationRequest()
            locationRequest!!.setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            locationRequest!!.setFastestInterval(15000)
            locationRequest!!.interval = 10000
            locationRequest!!.setSmallestDisplacement(50f)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback!!)
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)

        compositeDisposable.clear()

        if (EventBus.getDefault().hasSubscriberForEvent(DriverHomeActivity::class.java))
            EventBus.getDefault().removeStickyEvent(DriverHomeActivity::class.java)
        EventBus.getDefault().unregister(this)

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


                    if (ActivityCompat.checkSelfPermission(
                            context!!,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            context!!,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Snackbar.make(requireView(), getString(R.string.permision_require), Snackbar.LENGTH_LONG).show()
                        return
                    }

                    mMap.setOnMyLocationClickListener {



                        fusedLocationProviderClient!!.lastLocation
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
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 50


                    buildLocationRequest()

                    buildLocationCallback()

                    updateLocation()

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

        Snackbar.make(mapFragment.requireView(),"Online",Snackbar.LENGTH_SHORT).setBackgroundTint(
            Color.parseColor("#00FF00")).show()

    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public fun onDriverRequestRecieved(event: DriverRequestRecieved){

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(requireView(), getString(R.string.permision_require), Snackbar.LENGTH_LONG).show()
            return
        }


        fusedLocationProviderClient!!.lastLocation
            .addOnFailureListener { e->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
            }
            .addOnSuccessListener { location->

                compositeDisposable.add(iGoogleAPI.getDirections("driving",
                    "less_driving",
                    StringBuilder()
                        .append(location!!.latitude)
                        .append(",")
                        .append(location!!.longitude)
                        .toString(),
                    event.pickupLocation,
                    getString(R.string.google_api_key))
                !!.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { returnResult ->
                        Log.d("API_RETURN", returnResult)
                        try {

                            val jsonObject = JSONObject(returnResult)
                            val jsonArray = jsonObject.getJSONArray("routes")
                            for (i in 0 until jsonArray.length())
                            {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")
                                polylineList = Common.decodePoly(polyline)
                            }


                            polylineOptions = PolylineOptions()
                            polylineOptions!!.color(Color.GRAY)
                            polylineOptions!!.width(12f)
                            polylineOptions!!.startCap(SquareCap())
                            polylineOptions!!.jointType(JointType.ROUND)
                            polylineOptions!!.addAll(polylineList!!)
                            greyPolyline = mMap.addPolyline(polylineOptions!!)

                            blackPolylineOptions = PolylineOptions()
                            blackPolylineOptions!!.color(Color.BLACK)
                            blackPolylineOptions!!.width(5f)
                            blackPolylineOptions!!.startCap(SquareCap())
                            blackPolylineOptions!!.jointType(JointType.ROUND)
                            blackPolylineOptions!!.addAll(polylineList!!)
                            blackPolyline = mMap.addPolyline(blackPolylineOptions!!)

                            val valueAnimator = ValueAnimator.ofInt(0,100)
                            valueAnimator.duration = 1100
                            valueAnimator.repeatCount = ValueAnimator.INFINITE
                            valueAnimator.interpolator = LinearInterpolator()
                            valueAnimator.addUpdateListener { value->
                                val points = greyPolyline!!.points
                                val percentValue = value.animatedValue.toString().toInt()
                                val size = points.size
                                val newpoints = (size * (percentValue/100.0f)).toInt()
                                val p = points.subList(0,newpoints)
                                blackPolyline!!.points = (p)

                            }
                            valueAnimator.start()

                            val origin = LatLng(location.latitude, location.longitude)
                            val destination = LatLng(event.pickupLocation.split("")[0].toDouble(),
                            event.pickupLocation.split("")[1].toDouble())


                            val latLngBound = LatLngBounds.Builder().include(origin)
                                .include(destination)
                                .build()

                            //TODO
                            val objects = jsonArray.getJSONObject(0)
                            val legs = objects.getJSONArray("legs")
                            val legsObject = legs.getJSONObject(0)

                            val time = legsObject.getJSONObject("duration")
                            val duration = time.getString("text")

                            val distanceEstimate = legsObject.getJSONObject("distance")
                            val distance= distanceEstimate.getString("text")

                            txt_estimate_time.setText(duration)
                            txt_estimate_distance.setText(distance)

                            mMap.addMarker(MarkerOptions().position(destination).icon(BitmapDescriptorFactory.defaultMarker())
                                .title("Pickup Location"))

                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                            mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))


                            chip_decline.visibility = View.VISIBLE
                            layout_accept.visibility = View.VISIBLE


                            io.reactivex.Observable.interval(100, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnNext { x->
                                    circularProgressBar.progress += 1f
                                }
                                .takeUntil {aLong -> aLong == "100".toLong()}
                                .doOnComplete{
                                    Toast.makeText(context!!, "Accept Action", Toast.LENGTH_LONG).show()
                                }.subscribe()




                        }catch (e:java.lang.Exception)
                        {
                            Toast.makeText(context!!, e.message!!, Toast.LENGTH_SHORT).show()
                        }

                    }
                )

            }
    }
}