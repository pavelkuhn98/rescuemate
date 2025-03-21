package com.example.rescuemate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.rescuemate.databinding.ActivityMapsBinding;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class MapsActivity extends AppCompatActivity implements GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 2;
    private boolean locationPermission = false;
    private boolean notificationsPermission = false;
    private GoogleMap mMap;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Button reportButton;
    private Button okButton;
    private Button cancelButton;
    private final Map<String, Marker> markers = new HashMap<>();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();
    private String userEmail;
    private ImageView imageView;
    private final String NOTIFICATION_CHANNEL_ID = "ALERTS_RESCUEMATE";
    private FusedLocationProviderClient fusedLocationClient;
    private NotificationManager notificationManager;
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
        com.example.rescuemate.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
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
        PermissionUtils.requestNotificationPermissions(this,POST_NOTIFICATIONS_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
        reportButton = findViewById(R.id.report_button);
        okButton = findViewById(R.id.ok_button);
        cancelButton = findViewById(R.id.cancel_button);
        imageView = findViewById(R.id.alertPlaceholder);
    }

    private void createNotificationChannel() {

        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        notificationManager.createNotificationChannel(channel);
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
        mMap.setOnMarkerClickListener(marker -> {
            Toast.makeText(MapsActivity.this,"HINWEIS: Halten dieses Infofenster gedrückt, um die Gefahr zu bestätigen",Toast.LENGTH_SHORT).show();
            return false;
        });
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoContents(@NonNull Marker marker) {
                LinearLayout info = new LinearLayout(MapsActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                MarkerData markerData = (MarkerData) marker.getTag();
                if (markerData == null){
                    Toast.makeText(MapsActivity.this,"Failed to get marker data",Toast.LENGTH_SHORT).show();
                    return null;
                }
                String snippetText1 = DANGER_TYPE.getMessage(markerData.dangerID);
                String snippetText2 = "Confirmed By: "+ (markerData.confirmedBy == null ? 0 : markerData.confirmedBy.size());

                TextView snippet1 = new TextView(MapsActivity.this);
                snippet1.setTextColor(Color.BLACK);
                snippet1.setTypeface(null, Typeface.BOLD);
                snippet1.setText(snippetText1);

                TextView snippet2 = new TextView(MapsActivity.this);
                snippet2.setTextColor(Color.GRAY);
                snippet2.setText(snippetText2);

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
        mMap.setOnInfoWindowLongClickListener(marker -> {
            MarkerData markerData = (MarkerData) marker.getTag();

            if (markerData == null){
                Toast.makeText(MapsActivity.this,"Metadaten zu diesem Marker nicht gefunden",Toast.LENGTH_LONG).show();
                return;
            }

            if (markerData.confirmedBy.contains(userEmail)){
                Toast.makeText(MapsActivity.this,"Sie haben diese Gefahr schon bestätigt",Toast.LENGTH_SHORT).show();
                return;
            }
            markerData.confirmedBy.add(userEmail);

            db.collection("markers")
                    .document(Objects.requireNonNullElse(marker.getTitle(),"unknown"))
                    .set(markerData)
                    .addOnSuccessListener(documentReference -> Log.d(TAG, "Marker changed"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
            Toast.makeText(MapsActivity.this,"Sie haben diese Gefahr bestätigt",Toast.LENGTH_SHORT).show();



        });

        reportButton.setOnClickListener(e->createMarker());
    }

    private void showMarkers() {
        db.collection("markers").addSnapshotListener((value, error) -> {
            final Location[] currentLocation = new Location[1];
            final boolean[] locationAccessed = {false};
            AtomicInteger counter = new AtomicInteger();
            CurrentLocationRequest currentLocationRequest = new CurrentLocationRequest.Builder()
                    .setDurationMillis(5000)
                    .build();
            CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(currentLocationRequest,cancellationTokenSource.getToken()).addOnSuccessListener(location -> {
                    currentLocation[0] = new Location(location);
                    locationAccessed[0] = true;
                });
            }

            if (error != null) {
                Log.w("MapsActivity", "listen:error", error);
                return;
            }
            if (value == null){
                Toast.makeText(MapsActivity.this,"Failed to get marker updates",Toast.LENGTH_SHORT).show();
                return;
            }
            for (DocumentChange dc : value.getDocumentChanges()) {
                switch (dc.getType()) {
                    case ADDED:
                        QueryDocumentSnapshot documentToAdd = dc.getDocument();
                        MarkerData markerData = documentToAdd.toObject(MarkerData.class);
                        Marker marker = Objects.requireNonNull(mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(markerData.latitude, markerData.longitude))
                                .title(documentToAdd.getId())
                                .icon(BitmapDescriptorFactory.fromResource(R.raw.alert))));
                        marker.setTag(markerData);
                        Location location = new Location("");
                        location.setLatitude(markerData.latitude);
                        location.setLongitude(markerData.longitude);

                        markers.put(documentToAdd.getId(), marker);
                        if (locationAccessed[0] && currentLocation[0].distanceTo(location) < 150){
                            counter.getAndIncrement();
                        }
                        break;
                    case MODIFIED:
                        String idToChange = dc.getDocument().getId();
                        QueryDocumentSnapshot documentToModify = dc.getDocument();
                        MarkerData updatedMarkerData = documentToModify.toObject(MarkerData.class);
                        Marker markerToChange = markers.get(idToChange);
                        if (markerToChange != null) {
                            markerToChange.setTag(updatedMarkerData);
                        }
                        break;
                    case REMOVED:
                        String idToRemove = dc.getDocument().getId();
                        Marker markerToRemove = markers.get(idToRemove);
                        if (markerToRemove != null) {
                            markerToRemove.remove();
                            markers.remove(idToRemove);
                        }
                        break;
                }
            }
            Log.d("MainActivity","Are notifications enabled?" + notificationManager.areNotificationsEnabled());
            Log.d("MainActivity","Updates: " + counter.get());
            if (counter.get() > 0 && notificationManager.areNotificationsEnabled()){
                final int NOTIFICATION_ID = 30;
                Notification builder = new NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.danger)
                        .setContentTitle("RESCUEMATE")
                        .setContentText("Es wurden "+ counter.get()+" Gefahren in ihrer Nähe gemeldet. Bleiben sie vorsichtig")
                        .setAutoCancel(true)
                        .build();
                notificationManager.notify(NOTIFICATION_ID,builder);
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
        imageView.setVisibility(View.VISIBLE);

        LatLng currentLocation = mMap.getCameraPosition().target;
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        reportButton.setVisibility(View.INVISIBLE);
        okButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));

        cancelButton.setOnClickListener(v -> exitSettingMarker(okButton, cancelButton, currentLocation));

        okButton.setOnClickListener(v ->{
            final LatLng cameraPos = mMap.getCameraPosition().target;
            MarkerData markerData = new MarkerData(cameraPos.latitude, cameraPos.longitude);
            markerData.setDangerID(1);
            markerData.setReportedBy(userEmail);
            String[] choices = DANGER_TYPE.getStrings();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle("Grund der Meldung")
                    .setPositiveButton("OK", (dialog, which) -> {

                        db.collection("markers")
                                .add(markerData)
                                .addOnSuccessListener(documentReference -> {
                                    String newID = documentReference.getId();
                                    Log.d(TAG, "Marker written with ID: " + newID);
                                    Marker marker = addMarker(cameraPos,newID);
                                    while(marker == null){
                                        marker = addMarker(cameraPos,newID);
                                    }
                                    marker.setTag(markerData);
                                    markers.put(newID,marker);
                                })
                                .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
                        exitSettingMarker(okButton, cancelButton, currentLocation);

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

    private Marker addMarker(LatLng pos, String title){
        return mMap.addMarker(new MarkerOptions()
                .position(pos)
                .icon(BitmapDescriptorFactory.fromResource(R.raw.alert))
                .title(title));
    }

    private void exitSettingMarker(Button confirmButton, Button cancelButton, LatLng currentLocation) {
        imageView.setVisibility(View.INVISIBLE);
        confirmButton.setVisibility(View.INVISIBLE);
        cancelButton.setVisibility(View.INVISIBLE);
        reportButton.setVisibility(View.VISIBLE);
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
        switch (requestCode){
            case LOCATION_PERMISSION_REQUEST_CODE:{
                if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                        Manifest.permission.ACCESS_FINE_LOCATION) || PermissionUtils
                        .isPermissionGranted(permissions, grantResults,
                                Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    // Enable the my location layer if the permission has been granted.
                    enableMyLocation();
                } else {
                    // Permission was denied.
                    locationPermission = true;
                }
                break;
            }
            case POST_NOTIFICATIONS_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){

                }
                else{
                    notificationsPermission = true;
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                return;
        }


    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (locationPermission) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            locationPermission = false;
        }
        if (notificationsPermission){
            Toast.makeText(MapsActivity.this,"Die App wird keine Benachrichtigungen schicken",Toast.LENGTH_LONG).show();
            notificationsPermission = false;
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