package com.example.rescuemate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.rescuemate.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapsActivity extends AppCompatActivity implements GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private boolean permissionDenied = false;
    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Button reportButton;
    private Button confirmButton;
    private Button cancelButton;
    private Button confirmSightingButton;
    private Map<String, MarkerOptions> markerOptionsMap = new HashMap<>();
    private List<MarkerData> markers = new ArrayList<>();
    private BottomNavigationView bottomNavigationView;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private String userEmail;
    private enum DANGER_TYPE {
        BLOCKIERT(1,"Straße blockiert (z.B. von Bäumen)"),
        UEBERFLUTET(2, "Straße überflütet"),
        GESPERRT(3, "Straße gesperrt");

        public final int id;
        public final String description;
        DANGER_TYPE(int i, String s) {
            id = i;
            description = s;
        }
        public static DANGER_TYPE get(int idNum){
            return Arrays.stream(DANGER_TYPE.values()).filter(e->e.id == idNum).findAny().orElse(GESPERRT);
        }

        public static String[] getStrings(){
            String[] options = new String[DANGER_TYPE.values().length];
            for (int i = 0; i < DANGER_TYPE.values().length; i++){
                options[i] = DANGER_TYPE.get(i).description;
            }
            return options;
        }

        public static String getMessage(int idNum){
            return Arrays.stream(DANGER_TYPE.values()).filter(e->e.id==idNum).map(e->e.description).findAny().orElse(GESPERRT.description);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (mAuth.getCurrentUser() == null){
            Log.w("MapsActivity","Failed to get logged in user");
            Intent intent = new Intent(this,Login.class);
            startActivity(intent);
        }
        else{
            userEmail = mAuth.getCurrentUser().getEmail();
        }
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        binding.bottomNavigationView.setOnItemSelectedListener(
                item -> {
                    if(item.getItemId() == R.id.nav_map){
                        Toast.makeText(this,"MAP SELECTED",Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
        );
        reportButton = findViewById(R.id.report_button);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        confirmSightingButton = findViewById(R.id.comfirm_danger_button);
        confirmButton = findViewById(R.id.confirm_button);
        cancelButton = findViewById(R.id.cancel_button);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng wilhelmsburg = new LatLng(53.505873, 9.999809);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.style));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(wilhelmsburg));

        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(53.812290, 9.620169));
        builder.include(new LatLng(53.288459, 10.678486));
        builder.build();
        mMap.setLatLngBoundsForCameraTarget(builder.build());
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoContents(@NonNull Marker marker) {
                LinearLayout info = new LinearLayout(MapsActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                String[] snippets = Objects.requireNonNull(marker.getSnippet()).split("\n");
                Toast.makeText(MapsActivity.this,marker.getSnippet(),Toast.LENGTH_SHORT).show();

                TextView title = new TextView(MapsActivity.this);
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setText(marker.getTitle());

                TextView snippet1 = new TextView(MapsActivity.this);
                snippet1.setTextColor(Color.BLACK);
                snippet1.setTypeface(null, Typeface.BOLD);
                snippet1.setText(snippets[0]);

                TextView snippet2 = new TextView(MapsActivity.this);
                snippet2.setTextColor(Color.GRAY);
                snippet2.setText(snippets[1]);

                info.addView(title);
                info.addView(snippet1);
                info.addView(snippet2);

                return info;
            }

            @Nullable
            @Override
            public View getInfoWindow(@NonNull Marker marker) {
                return null;
            }
        });

        enableMyLocation();
        showMarkers();

        reportButton.setOnClickListener(e->createMarker());
    }

    private void showMarkers() {
        db.collection("markers").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (QueryDocumentSnapshot document : task.getResult()) {
                    MarkerData markerData = document.toObject(MarkerData.class);
                    MarkerOptions marker = new MarkerOptions()
                            .position(new LatLng(markerData.latitude,markerData.longitude))
                            .title(document.getId())
                            .snippet(DANGER_TYPE.getMessage(markerData.dangerID) +"\n" + "Confirmed By: "+ (markerData.confirmedBy == null ? 0 : markerData.confirmedBy.size()))
                            .icon(BitmapDescriptorFactory.fromResource(R.raw.alert));
                    mMap.addMarker(marker);

                    markers.add(markerData);
                }
            } else {
                Log.d(TAG, "Error getting documents: ", task.getException());
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            return;
        }
        PermissionUtils.requestLocationPermissions(this, LOCATION_PERMISSION_REQUEST_CODE);

    }

    public void createMarker(){
        reportButton.setEnabled(false);
        Marker marker = mMap.addMarker(new MarkerOptions().position(mMap.getCameraPosition().target).draggable(false).icon(BitmapDescriptorFactory.fromResource(R.raw.alert)));
        if (marker == null){
            Toast.makeText(this,"Bitte erneut versuchen",Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng currentLocation = mMap.getCameraPosition().target;
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        bottomNavigationView.setVisibility(View.GONE);
        reportButton.setVisibility(View.INVISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        mMap.setOnCameraMoveListener(() -> marker.setPosition(mMap.getCameraPosition().target));

        cancelButton.setOnClickListener(v -> {
            marker.remove();
            exitSettingMarker(confirmButton, cancelButton, currentLocation);
        });

        confirmButton.setOnClickListener(v ->{
            MarkerData markerData = new MarkerData(marker.getPosition().latitude,marker.getPosition().longitude);
            markerData.setDangerID(1);
            markerData.setReportedBy(userEmail);
            String[] choices = DANGER_TYPE.getStrings();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle("Grund der Meldung")
                    .setPositiveButton("OK", (dialog, which) -> {
                        db.collection("markers")
                                .add(markerData)
                                .addOnSuccessListener(documentReference -> Log.d(TAG, "Marker written with ID: " + documentReference.getId()))
                                .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
                        exitSettingMarker(confirmButton, cancelButton, currentLocation);

                    })
                    .setNegativeButton("Abbrechen", (dialog, which) -> {

                    })
                    .setSingleChoiceItems(choices, 0, (dialog, which) -> {
                        Toast.makeText(this,"CLICKED",Toast.LENGTH_SHORT).show();
                        markerData.setDangerID(which);
                    });

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void exitSettingMarker(Button confirmButton, Button cancelButton, LatLng currentLocation) {
        confirmButton.setVisibility(View.INVISIBLE);
        cancelButton.setVisibility(View.INVISIBLE);
        reportButton.setVisibility(View.VISIBLE);
        bottomNavigationView.setVisibility(View.VISIBLE);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
        reportButton.setEnabled(true);
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show();

        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }

    // [START maps_check_location_permission_result]
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION) || PermissionUtils
                .isPermissionGranted(permissions, grantResults,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Permission was denied. Display an error message
            // [START_EXCLUDE]
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true;
            // [END_EXCLUDE]
        }
    }
    // [END maps_check_location_permission_result]

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            permissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }
}