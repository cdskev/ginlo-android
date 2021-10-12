// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.fragment.app.DialogFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;

public class LocationActivity
        extends BaseActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private static final String TAG = LocationActivity.class.getSimpleName();
    public static final String MODE_SHOW_LOCATION = "LocationActivity.modeShowLocation";
    public static final String MODE_GET_LOCATION = "LocationActivity.modeGetLocation";
    public static final String EXTRA_MODE = "LocationActivity.extraMode";
    public static final String EXTRA_LONGITUDE = "LocationActivity.longitude";
    public static final String EXTRA_LATITUDE = "LocationActivity.latitude";
    public static final String EXTRA_SCREENSHOT = "LocationActivity.screenShot";
    private static final int SCREENSHOT_QUALITY = 100;
    private static final int ZOOM_LEVEL = 15;
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final long UPDATE_INTERVAL = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    private static final long FASTEST_INTERVAL = MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private String mode;
    private ConnectionResult connectionResult;
    private GoogleApiClient locationClient;
    private LocationRequest locationRequest;
    private Button getLocationButton;
    private GoogleMap locationMap;
    private Location lastLocation;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        if (getSimsMeApplication().getPreferencesController().isLocationDisabled()) {
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_Location_access_not_allowed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getLocationButton = findViewById(R.id.location_button_get_location);
        if (RuntimeConfig.isBAMandant()) {
            getLocationButton.getBackground().setColorFilter(ColorUtil.getInstance().getAppAccentColor(getSimsMeApplication()), PorterDuff.Mode.SRC_ATOP);
            getLocationButton.setTextColor(ColorUtil.getInstance().getAppAccentContrastColor(getSimsMeApplication()));
        }
        SupportMapFragment locationMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.location_support_map_fragment);
        locationMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                locationMap = googleMap;

                if (locationMap == null) {
                    return;
                }

                locationMap.getUiSettings().setMyLocationButtonEnabled(false);
                try {
                    locationMap.setMyLocationEnabled(true);
                } catch (SecurityException e) {
                    LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                    // Androidstudio warnt hier, dass hier ene Permission erfordert wird.
                    // Diese wird jedoch vor dme Starten der Activity abgefragt.
                    //Damit alles schoen ist und keine Warnugn kommt -> try catch
                }

                mode = getIntent().hasExtra(EXTRA_MODE) ? getIntent().getStringExtra(EXTRA_MODE) : MODE_SHOW_LOCATION;
                if (mode.equals(MODE_SHOW_LOCATION)) {
                    double latitude = getIntent().getDoubleExtra(EXTRA_LATITUDE, 0.0);
                    double longitude = getIntent().getDoubleExtra(EXTRA_LONGITUDE, 0.0);

                    dismissIdleDialog();
                    getLocationButton.setVisibility(View.GONE);

                    Location location = new Location("ginlo");

                    location.setLatitude(latitude);
                    location.setLongitude(longitude);

                    locationMap.clear();
                    locationMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)));

                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                            location.getLongitude()), ZOOM_LEVEL);

                    locationMap.animateCamera(cameraUpdate);
                    lastLocation = location;
                } else if (mode.equals(MODE_GET_LOCATION)) {
                    showIdleDialog();
                    getLocationButton.setVisibility(View.VISIBLE);
                }

                MapsInitializer.initialize(LocationActivity.this);
            }
        });
        locationRequest = LocationRequest.create();

        locationClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();

        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_location;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (locationClient != null) {
            locationClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationClient != null) {
            if (locationClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(locationClient, this);
            }

            locationClient.disconnect();
        }
    }

    public void handleGetLocationClick(View view) {
        if (servicesConnected()) {
            try {
                final Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(locationClient);
                final Intent intent = new Intent();

                if (currentLocation == null) {
                    LocationActivity.this.setResult(Activity.RESULT_CANCELED, intent);
                    LocationActivity.this.finish();
                    return;
                }

                intent.putExtra(EXTRA_LATITUDE, currentLocation.getLatitude());
                intent.putExtra(EXTRA_LONGITUDE, currentLocation.getLongitude());

                final Bitmap placeHolder = ((BitmapDrawable) getDrawable(R.drawable.chat_location_placeholder)).getBitmap();

                // Convert placeHolder to jpeg ...
                intent.putExtra(EXTRA_SCREENSHOT, BitmapUtil.compress(placeHolder, SCREENSHOT_QUALITY));
                setResult(Activity.RESULT_OK, intent);

                finish();

            } catch (SecurityException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    private boolean servicesConnected() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (ConnectionResult.SUCCESS == resultCode) {
            LogUtil.d("Location Updates", "Google Play services is available.");
            return true;
        } else {
            int errorCode = connectionResult.getErrorCode();

            showErrorDialog(errorCode);
            return false;
        }
    }

    private void showErrorDialog(int errorCode) {
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this,
                CONNECTION_FAILURE_RESOLUTION_REQUEST);

        if (errorDialog != null) {
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            errorFragment.setDialog(errorDialog);
            errorFragment.show(getSupportFragmentManager(), "Location Updates");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        this.connectionResult = connectionResult;
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    @Override
    public void onConnected(Bundle dataBundle) {
        if (mode.equals(MODE_GET_LOCATION)) {
            final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if (manager != null && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                DialogBuilderUtil.buildErrorDialog(this, getString(R.string.location_service_not_enabled)).show();
            }

            try {
                LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, this);
            } catch (SecurityException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        LogUtil.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onLocationChanged(Location location) {
        dismissIdleDialog();
        if (!getLocationButton.isEnabled()) {
            getLocationButton.setEnabled(true);
        }

        if ((lastLocation == null) || (lastLocation.getLongitude() != location.getLongitude()) || (lastLocation.getLatitude() != location.getLatitude())) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                    location.getLongitude()), ZOOM_LEVEL);

            locationMap.animateCamera(cameraUpdate);
            lastLocation = location;
        }

        getLocationButton.setText(getString(R.string.chat_location_selection_position_info_info_text));
    }

    public static class ErrorDialogFragment
            extends DialogFragment {
        private Dialog errorDialog;

        public ErrorDialogFragment() {
            super();
            errorDialog = null;
        }

        void setDialog(Dialog dialog) {
            errorDialog = dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return errorDialog;
        }
    }
}
