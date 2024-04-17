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
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
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

import java.util.List;

public class DriversMapActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Marker currentLocationMarker;

    private Button LogoutDriverButton;
    private Button SettingsDriverButton;
    private Boolean currentLogoutDriverStatus = false;

    FirebaseAuth mAuth;
    FirebaseUser currentUser;
    private DatabaseReference AssignedCustomerRef, AssignedCustomerPickUpRef;
    private ValueEventListener AssignedCustomerPickUpRefListner;
    private String driverID, customerID = "";
//    private LatLng CustomerLatLng;
    Marker PickUpMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drivers_map);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        driverID = mAuth.getCurrentUser().getUid();
        LogoutDriverButton = findViewById(R.id.driver_logout_btn);
        SettingsDriverButton = findViewById(R.id.driver_settings_btn);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
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
                    updateLocation(new LatLng(location.getLatitude(), location.getLongitude()));
                }
            }
        };

        LogoutDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentLogoutDriverStatus = true;
                DisconnectTheDriver();
                mAuth.signOut();
                LogoutDriver();
            }
        });

        getAssignedCustomerRequest();
    }

    private void getAssignedCustomerRequest() {
        AssignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users")
                .child("Drivers").child(driverID).child("CustomerRideID");

        AssignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists())
                {
                    customerID = snapshot.getValue().toString();
                    GetAssignedCustomerPickUpLocation();
                }
                else
                {
                    customerID = "";
                    if (PickUpMarker != null)
                    {
                        PickUpMarker.remove();
                    }
                    if (AssignedCustomerPickUpRefListner != null)
                    {
                        AssignedCustomerPickUpRef.removeEventListener(AssignedCustomerPickUpRefListner);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private void GetAssignedCustomerPickUpLocation() {
        AssignedCustomerPickUpRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests")
                .child(customerID).child("l");
        AssignedCustomerPickUpRefListner = AssignedCustomerPickUpRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists())
                {
                    List<Object> customerLocationMap = (List<Object>) snapshot.getValue();
                    double LocationLat = 0;
                    double LocationLng = 0;
                    if (customerLocationMap.get(0)!=null)
                    {
                        LocationLat = Double.parseDouble(customerLocationMap.get(0).toString());
                    }
                    if (customerLocationMap.get(1)!=null)
                    {
                        LocationLng = Double.parseDouble(customerLocationMap.get(1).toString());
                    }
                    LatLng CustomerLatLng = new LatLng(LocationLat, LocationLng);
                    mMap.addMarker(new MarkerOptions().position(CustomerLatLng).title("Customer PickUp Location"));

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void LogoutDriver() {
        Intent welcomeIntent = new Intent(getApplicationContext(), WelcomeActivity.class);
        welcomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(welcomeIntent);
        finish();
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
        if (getApplicationContext() != null)
        {
            if (currentLocationMarker != null) {
                currentLocationMarker.remove();
            }

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title("Current Location");
            currentLocationMarker = mMap.addMarker(markerOptions);

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(17));

            ///////////////////////
            currentUser = mAuth.getCurrentUser();
            if (currentUser != null)
            {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference DriverAvailablityRef = FirebaseDatabase.getInstance().getReference().child("Drivers Available");
                GeoFire geoFireAvailablity = new GeoFire(DriverAvailablityRef);

                DatabaseReference DriverWorkingRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");
                GeoFire geoFireWorking = new GeoFire(DriverWorkingRef);

                switch (customerID)
                {
                    case "":
                        geoFireWorking.removeLocation(userId);
                        geoFireAvailablity.setLocation(userId, new GeoLocation(latLng.latitude, latLng.longitude));
                        break;

                    default:
                        geoFireAvailablity.removeLocation(userId);
                        geoFireWorking.setLocation(userId, new GeoLocation(latLng.latitude, latLng.longitude));
                        break;

                }

            }

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize map
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
                mapFragment.getMapAsync(this);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!currentLogoutDriverStatus)
        {
            DisconnectTheDriver();
        }

    }

    private void DisconnectTheDriver() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference DriverAvailablityRef = FirebaseDatabase.getInstance().getReference().child("Drivers Available");
        GeoFire geoFire = new GeoFire(DriverAvailablityRef);
        geoFire.removeLocation(userId);
    }
}