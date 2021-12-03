package com.example.secureperformancemonitor;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.secureperformancemonitor.databinding.ActivityMapsBinding;

/*
Class to display raw BLE data
Author: Google
Edited Tyler Haskins
*/

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // initialize lat and lng with out of bounds coordinates
        double lat = 999, lng = 999;

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lat = extras.getDouble("lat");
            lng = extras.getDouble("lng");
        }

        if (lat != 999 && lng != 999) {
            // add a marker for current position and move the camera
            LatLng currentPos = new LatLng(lat, lng);
            mMap.addMarker(new MarkerOptions().position(currentPos).title("Current Position"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(currentPos));
        } else {
            // if no known position add a marker in College Station and move the camera
            LatLng cstat = new LatLng(30.6280, -96.3344);
            mMap.addMarker(new MarkerOptions().position(cstat).title("Welcome to Aggieland!"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cstat, 10));
        }

    }
}