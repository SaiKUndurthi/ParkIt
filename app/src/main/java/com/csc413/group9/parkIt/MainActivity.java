package com.csc413.group9.parkIt;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.csc413.group9.parkIt.Database.DatabaseManager;
import com.csc413.group9.parkIt.SFPark.ParkingInformation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnMapLoadedCallback,
        SensorEventListener {

    private static final float CAMERA_ZOOM_LEVEL = 18f;

    private GoogleMap mMap;
    private Marker mCLMarker;
    private Circle mCLMarkerCircle;
    private GoogleApiClient mGoogleApiClient;
    private ParkingInformation mParkingInfo;
    private CurrentLocation mCurrentLocation;
    private SensorManager mSensorManager;
    private float mMarkerRotation;
    private boolean mapLoaded = false;
    private boolean showPrice = true;
    private boolean showOnStreetParking = true;
    private boolean showOffStreetParking = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (!servicesAvailable()) {
            finish();
        }

        setContentView(R.layout.activity_main);

        DatabaseManager.initializeInstance(this);

        buildGoogleApiClient();
        buildSensorManager();

        mCurrentLocation = new CurrentLocation(this);

        buildGoogleMap();

        mParkingInfo = new ParkingInformation(this, mMap);
    }

    private synchronized void buildGoogleMap() {

        mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMap();

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                placeMarkerOnMap(latLng);

                mCurrentLocation.stopLocationUpdates();
            }
        });

        mMap.setOnMapLoadedCallback(this);
    }

    /**
     * Build the Google API client.
     */
    private synchronized void buildGoogleApiClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /**
     * Build and initialize the SensorManager.
     */
    private synchronized void buildSensorManager() {

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mGoogleApiClient != null) {

            mGoogleApiClient.connect();
            mCurrentLocation.startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {

            mCurrentLocation.stopLocationUpdates();
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle dataBundle) {

        trackDeviceLocation(null);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
 //       getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
 /*       int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
*/
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        mMarkerRotation = event.values[0] - 170.0f;

        if (mCLMarker != null) {
            mCLMarker.setRotation(mMarkerRotation);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onMapLoaded() {

        mapLoaded = true;

        if (mParkingInfo.isSFParkDataReady())
            mParkingInfo.highlightStreet(showOnStreetParking, showOffStreetParking);
    }

    /**
     * Track device's current location. The GPS will keep track of the location of the device
     * until somewhere on the map is clicked.
     * @param view The view of the application
     */
    public void trackDeviceLocation(View view) {

        // Start tracking
        mCurrentLocation.startLocationUpdates();

        // If the GPS is available, then get the current location of the device
        if (mCurrentLocation.canGetLocation()) {

            Location location = mCurrentLocation.getLocation();

            if (location != null) {
                placeMarkerOnMap(new LatLng(location.getLatitude(), location.getLongitude()));
            }

        } else {
            // Otherwise just get the last known location that is stored in the database

            String[] location = mCurrentLocation.getLastKnownLocation();

            double latitude = Double.parseDouble(location[1]);
            double longitude = Double.parseDouble(location[2]);

            placeMarkerOnMap(new LatLng(latitude, longitude));
        }
    }

    /**
     * Place a marker on the map on the specified latitude and longitude coordinate.
     * @param point The latitude and longitude coordinate on the map
     */
    public void placeMarkerOnMap(LatLng point) {

        // For testing purpose
        Toast.makeText(getApplicationContext(), point.latitude + ", " + point.longitude, Toast.LENGTH_SHORT).show();

        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            buildGoogleMap();
        }

        if (mMap != null) {

            if (mCLMarker != null) {

                mCLMarker.setPosition(new LatLng(point.latitude, point.longitude));
                mCLMarker.setRotation(mMarkerRotation);

            } else {
                mCLMarker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(point.latitude, point.longitude))
                        .rotation(mMarkerRotation)
                        .anchor(0.5f, 0.75f)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_user)));
            }

            if (mCLMarkerCircle != null) {

                mCLMarkerCircle.setCenter(new LatLng(point.latitude, point.longitude));

            } else {
                mCLMarkerCircle = mMap.addCircle(new CircleOptions()
                        .center(new LatLng(point.latitude, point.longitude))
                        .radius(45f)
                        .fillColor(Color.TRANSPARENT)
                        .strokeWidth(1.5f)
                        .strokeColor(0xFFE01368));
            }

            // Move the camera to the marker
            mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(mCLMarker.getPosition(), CAMERA_ZOOM_LEVEL));
        }
    }

    /**
     * Check if the Google Play Service is available. Return true if the Google Play Service is
     * available, otherwise false.
     * @return true if the Google Play Service is available, otherwise false
     */
    private boolean servicesAvailable() {

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        }
        else {
            GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0).show();
            return false;
        }
    }
}
