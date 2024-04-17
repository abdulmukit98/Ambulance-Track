package com.example.ambulance;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
//import com.example.ambulance.databinding.ActivityDriversMapBinding;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class CustomersMapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Marker currentLocationMarker;

    FirebaseAuth mAuth;
    FirebaseUser currentUser;

    private Button CustomerLogoutButton;
    private Button CallAmbulanceButton;
    private String CustomerID;
    private DatabaseReference CustomerDatabaseRef;
    private DatabaseReference DriverAvailableRef;
    private int radius = 1;
    private Boolean driverFound = false, requestType = false;
    private String driverFoundID;
    private LatLng CustomerPickUpLocation;
    private DatabaseReference DriversRef;
    private DatabaseReference DriverLocationRef;
    Marker DriverMarker, PickUpMarker;
    GeoQuery geoQuery;
    private ValueEventListener DriverLocationRefListner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers_maps);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        CustomerID = mAuth.getCurrentUser().getUid();

        CustomerLogoutButton = findViewById(R.id.customer_logout_button);
        CallAmbulanceButton = findViewById(R.id.customer_call_ambulance_button);
        CustomerDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Customers Requests");
        DriverAvailableRef = FirebaseDatabase.getInstance().getReference().child("Drivers Available");
        DriverLocationRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map2);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000); // 5 seconds
        locationRequest.setFastestInterval(1000); // 3 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    CustomerPickUpLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    updateLocation(CustomerPickUpLocation);
                }
            }
        };

        CustomerLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
                Intent welcomeIntent = new Intent(getApplicationContext(), WelcomeActivity.class);
                welcomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(welcomeIntent);
                finish();
            }
        });

        CallAmbulanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestType)
                {
                    requestType = false;
                    geoQuery.removeAllListeners();
                    DriverLocationRef.removeEventListener(DriverLocationRefListner);
                    if (driverFound != null)
                    {
                        DriversRef = FirebaseDatabase.getInstance().getReference()
                                .child("Users").child("Drivers").child(driverFoundID).child("CustomerRideID");
                        DriversRef.removeValue();
                        driverFoundID = null;
                    }
                    driverFound = false;
                    radius = 1;
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    GeoFire geoFire = new GeoFire(CustomerDatabaseRef);
                    geoFire.removeLocation(customerId);
                    if (PickUpMarker != null)
                    {
                        PickUpMarker.remove();
                    }
                    if (DriverMarker != null)
                    {
                        DriverMarker.remove();
                    }
                    CallAmbulanceButton.setText("Call a Ambulance");
                }
                else
                {
                    requestType = true;
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    GeoFire geoFire = new GeoFire(CustomerDatabaseRef); //child("Customers Requests");
                    geoFire.setLocation(customerId, new GeoLocation(CustomerPickUpLocation.latitude, CustomerPickUpLocation.longitude));
                    PickUpMarker = mMap.addMarker(new MarkerOptions().position(CustomerPickUpLocation).title("My Location"));
                    CallAmbulanceButton.setText("Getting your driver...");
                    GetClosestDriverAmbulance();
                }

            }
        });


    }

    private void GetClosestDriverAmbulance() {
//        Toast.makeText(getApplicationContext(), "GetClosestDriver", Toast.LENGTH_SHORT).show();
        GeoFire geoFire = new GeoFire(DriverAvailableRef);  //child("Drivers Available");
        geoQuery = geoFire.queryAtLocation(new GeoLocation(CustomerPickUpLocation.latitude, CustomerPickUpLocation.longitude), radius);
//        Toast.makeText(getApplicationContext(), " "+lastlatLng.latitude + " "+ lastlatLng.longitude, Toast.LENGTH_SHORT).show();
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestType)
                {
                    Toast.makeText(getApplicationContext(), "driver found true", Toast.LENGTH_SHORT).show();
                    driverFound = true;
                    driverFoundID = key;

                    DriversRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    HashMap driversMap = new HashMap();
                    driversMap.put("CustomerRideID", CustomerID);
                    DriversRef.updateChildren(driversMap);
                    Toast.makeText(CustomersMapsActivity.this, "Driver Get", Toast.LENGTH_SHORT).show();


                    GettingDriverLocation();
                    CallAmbulanceButton.setText("Looking for driver location...");
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound)
                {
                    radius = radius +1;
                    GetClosestDriverAmbulance();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void GettingDriverLocation() {
        Toast.makeText(CustomersMapsActivity.this, "Getting Driver Location", Toast.LENGTH_SHORT).show();
//        Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        },2000);


//        DriverLocationRefListner = DriverLocationRef.child(driverFoundID).child("l")        //child("Driver Working");
//                .addValueEventListener(new ValueEventListener() {
//                    @Override
//                    public void onDataChange(@NonNull DataSnapshot snapshot) {
//                        if (snapshot.exists() && requestType)
//                        {
//                            List<Object> driverLocationMap = (List<Object>) snapshot.getValue();
//                            double LocationLat = 0;
//                            double LocationLng = 0;
//                            CallAmbulanceButton.setText("Driver Found");
//
//                            if (driverLocationMap.get(0) != null)
//                            {
//                                LocationLat = Double.parseDouble(driverLocationMap.get(0).toString());
//                            }
//                            if (driverLocationMap.get(1) != null)
//                            {
//                                LocationLng = Double.parseDouble(driverLocationMap.get(1).toString());
//                            }
//
//                            LatLng DriverLatLng = new LatLng(LocationLat, LocationLng);
//                            if (DriverMarker != null)
//                            {
//                                DriverMarker.remove();
//                            }
//
//                            Location location1 = new Location("");
//                            location1.setLatitude(CustomerPickUpLocation.latitude);
//                            location1.setLongitude(CustomerPickUpLocation.longitude);
//
//                            Location location2 = new Location("");
//                            location2.setLatitude(DriverLatLng.latitude);
//                            location2.setLongitude(DriverLatLng.longitude);
//
//                            float Distance = location1.distanceTo(location2);
//                            System.out.println("Distance " + Distance);
//                            if (Distance < 90)
//                            {
//                                CallAmbulanceButton.setText("Driver's Reached");
//                            }
//                            else
//                            {
//                                CallAmbulanceButton.setText("Driver Found: " + String.valueOf(Distance));
//                            }
//
//                            DriverMarker = mMap.addMarker(new MarkerOptions().position(DriverLatLng).title("your driver is here"));
//
//                        }
//
//                    }
//
//                    @Override
//                    public void onCancelled(@NonNull DatabaseError error) {
//                        Toast.makeText(CustomersMapsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
//                    }
//                });

        DriverLocationRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");
        Toast.makeText(this, driverFoundID, Toast.LENGTH_SHORT).show();
//        Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        },2000);

        DriverLocationRef = DriverLocationRef.child(driverFoundID);
        GeoFire geoDriver = new GeoFire(DriverLocationRef);
        geoDriver.getLocation("l", new com.firebase.geofire.LocationCallback() {
            @Override
            public void onLocationResult(String s, GeoLocation geoLocation) {
                if (geoLocation != null)
                {
                    Toast.makeText(CustomersMapsActivity.this, "" + geoLocation.latitude + " " + geoLocation.longitude, Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(CustomersMapsActivity.this, "no location found", Toast.LENGTH_SHORT).show();    
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CustomersMapsActivity.this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check for location permission
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
            return;
        }

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                updateLocation(currentLocation);
            }
        });

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void updateLocation(LatLng latLng) {
//        if (currentLocationMarker != null) {
//            currentLocationMarker.remove();
//        }
//
//        MarkerOptions markerOptions = new MarkerOptions()
//                .position(latLng)
//                .title("Current Location");
//        currentLocationMarker = mMap.addMarker(markerOptions);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(17));


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize map
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map2);
                mapFragment.getMapAsync(this);
            }
        }
    }


}