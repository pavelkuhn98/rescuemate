package com.example.rescuemate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.rescuemate.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
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
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

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
    private static final int TAKE_PHOTO = 3;
    private boolean locationPermission = false;
    private boolean notificationsPermission = false;
    private GoogleMap mMap;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Button reportButton;
    private Button okButton;
    private Button cancelButton;
    private final Map<String, Marker> markers = new HashMap<>();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private FirebaseStorage storage;
    private String userEmail;
    private ImageView imageView;
    private final String NOTIFICATION_CHANNEL_ID = "ALERTS_RESCUEMATE";
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation = null;
    private NotificationManager notificationManager;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private StorageReference storageReference;
    private Uri photoUri;
    private ShapeableImageView shapeableImageView;
    private String selectedOption = "";

    class MyLocationCallback extends LocationCallback{

        public MyLocationCallback(){}
        @Override
        public void onLocationResult(LocationResult result){
            lastKnownLocation = result.getLastLocation();
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
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: " + uri);
                photoUri = uri;
                if (shapeableImageView != null){
                    shapeableImageView.setImageURI(uri);
                }
            } else {
                Log.d("PhotoPicker", "No media selected");
            }
        });
        com.example.rescuemate.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        storage = FirebaseStorage.getInstance("gs://rescuemate-96692");

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
        storageReference = storage.getReference();
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
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoContents(@NonNull Marker marker) {

                View info = getLayoutInflater().inflate(R.layout.infowindow,null);
                MarkerData markerData = (MarkerData) marker.getTag();
                String markerId = marker.getTitle();
                ImageView imageView = info.findViewById(R.id.image);
                imageView.setVisibility(View.GONE);
                StorageReference imgRef = storageReference.getRoot();
                if (markerId != null){
                    Log.d("Download Photo","Marker assigned");
                    Glide.with(MapsActivity.this).load(imgRef.child(markerId)).diskCacheStrategy(DiskCacheStrategy.DATA).into(imageView);
                    if (imageView.getDrawable() != null){
                        imageView.setVisibility(View.VISIBLE);
                    }
                    else{
                        Log.d("Download photo","No photo has been assigned");
                    }
                }

                if (markerData == null){
                    Toast.makeText(MapsActivity.this,"Failed to get marker data",Toast.LENGTH_SHORT).show();
                    return null;
                }
                String snippetText1 = markerData.danger;
                String snippetText2 = "Confirmed By: "+ (markerData.confirmedBy == null ? 0 : markerData.confirmedBy.size()) + "\n" + "HINWEIS:" + "\n" + "Halten dieses Fenster gedrückt, \n um die Gefahr zu bestätigen";

                TextView snippet1 = info.findViewById(R.id.desc);
                snippet1.setTextColor(Color.BLACK);
                snippet1.setTypeface(null, Typeface.BOLD);
                snippet1.setText(snippetText1);

                TextView snippet2 = info.findViewById(R.id.reported_status);
                snippet2.setTextColor(Color.GRAY);
                snippet2.setText(snippetText2);

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
            AtomicInteger counter = new AtomicInteger();

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
                        if (lastKnownLocation != null && lastKnownLocation.distanceTo(location) < 150){
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
            fusedLocationClient.requestLocationUpdates(
                    new com.google.android.gms.location.LocationRequest.Builder(3000).setDurationMillis(5000).build(),new MyLocationCallback(), Looper.myLooper());
            return;
        }
        PermissionUtils.requestLocationPermissions(this, LOCATION_PERMISSION_REQUEST_CODE);

    }

    public void createMarker(){
        reportButton.setEnabled(false);
        imageView.setVisibility(View.VISIBLE);
        if (lastKnownLocation != null){
            mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude())));
        }
        else{
            Log.w("createMarker","lastKnownLocation is null");
        }
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        LatLng currentLocation = mMap.getCameraPosition().target;
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        reportButton.setVisibility(View.INVISIBLE);
        okButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        final String[] options = getResources().getStringArray(R.array.dangers);
        selectedOption = options[0];

        cancelButton.setOnClickListener(v -> exitSettingMarker(okButton, cancelButton, currentLocation));

        okButton.setOnClickListener(v ->{
            final LatLng cameraPos = mMap.getCameraPosition().target;
            MarkerData markerData = new MarkerData(cameraPos.latitude, cameraPos.longitude);
            markerData.setReportedBy(userEmail);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = MapsActivity.this.getLayoutInflater();
            builder.setView(inflater.inflate(R.layout.alert_dialog,null))
                    .setPositiveButton("OK", (dialog, which) -> {
                        markerData.setDanger(selectedOption);
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
                                    if (photoUri != null){
                                        storageReference.getRoot().child(newID).putFile(photoUri).addOnFailureListener(exception -> {
                                            Toast.makeText(MapsActivity.this,"Uploading photo failed.",Toast.LENGTH_LONG).show();
                                            // Handle unsuccessful uploads
                                        }).addOnSuccessListener(taskSnapshot -> {
                                            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
                                            // ...
                                        });

                                    }
                                })
                                .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
                        exitSettingMarker(okButton, cancelButton, currentLocation);

                    })
                    .setNegativeButton("Abbrechen", (dialog, which) -> {

                    });


            AlertDialog dialog = builder.create();
            dialog.show();
            shapeableImageView = dialog.findViewById(R.id.img_showcase);
            Button gallerybutton = dialog.findViewById(R.id.galleryButton);
            Button camerabutton = dialog.findViewById(R.id.cameraButton);
            Spinner spinner = dialog.findViewById(R.id.dangerOptions);
            if (shapeableImageView == null || gallerybutton == null || camerabutton == null || spinner == null){
                Log.e("MAPS ACTIVITY","Imageview not loaded");
            }
            else{
                gallerybutton.setOnClickListener(v1 -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build()));
                camerabutton.setOnClickListener(v1 -> dispatchTakePictureIntent());
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedOption = (String) parent.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        selectedOption = options[0];
                    }
                });
            }
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivity(takePictureIntent);
        } catch (ActivityNotFoundException e) {
            // display error state to the user
            Log.d("MapsActivity","Failed to start camera");
        }
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
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
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
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
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