// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;

public class LocationActivityOSM
        extends BaseActivity
        implements LocationListener {
    private static final String TAG = LocationActivityOSM.class.getSimpleName();
    public static final String MODE_SHOW_LOCATION = "LocationActivity.modeShowLocation";
    public static final String MODE_GET_LOCATION = "LocationActivity.modeGetLocation";
    public static final String EXTRA_MODE = "LocationActivity.extraMode";
    public static final String EXTRA_LONGITUDE = "LocationActivity.longitude";
    public static final String EXTRA_LATITUDE = "LocationActivity.latitude";
    public static final String EXTRA_SCREENSHOT = "LocationActivity.screenShot";
    private static final int SCREENSHOT_QUALITY = 100;
    private static final double ZOOM_LEVEL = 17.0;

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 5000; // 5 seconds

    private String mode;
    private boolean isGPSEnabled = false;
    private boolean isNetworkEnabled = false;
    private LocationManager mLocationManager;
    private Button getLocationButton;
    private Location mLocation = null;
    private double latitude; // latitude
    private double longitude; // longitude

    private MapView map = null;
    private IMapController mapController;
    private MyLocationNewOverlay mLocationOverlay = null;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        if (getSimsMeApplication().getPreferencesController().isLocationDisabled()) {
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_Location_access_not_allowed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        /*
        if(BuildConfig.DEBUG) {
            Configuration.getInstance().setDebugMode(true);
        }
         */

        map = (MapView) findViewById(R.id.mapview);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        mapController = map.getController();
        mapController.setZoom(ZOOM_LEVEL);

        getLocationButton = findViewById(R.id.location_button_get_location);
        if (RuntimeConfig.isBAMandant()) {
            getLocationButton.getBackground().setColorFilter(ScreenDesignUtil.getInstance().getAppAccentColor(getSimsMeApplication()), PorterDuff.Mode.SRC_ATOP);
            getLocationButton.setTextColor(ScreenDesignUtil.getInstance().getAppAccentContrastColor(getSimsMeApplication()));
        }

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        requestPermission(PermissionUtil.PERMISSION_FOR_LOCATION, R.string.permission_rationale_location,
                (permission, permissionGranted) -> {
                    if ((permission == PermissionUtil.PERMISSION_FOR_LOCATION) && permissionGranted) {
                        mode = getIntent().hasExtra(EXTRA_MODE) ? getIntent().getStringExtra(EXTRA_MODE) : MODE_SHOW_LOCATION;

                        // Initialize location mapping
                        try {
                            // getting GPS status
                            isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                            // getting network status
                            isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                            if(isGPSEnabled) {
                                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                                mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                            } else if (isNetworkEnabled) {
                                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                                mLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            } else {
                                LogUtil.w(TAG, "onCreateActivity: requestLocationUpdates has no available provider: GPS=false, Network=false");
                                Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.location_service_not_enabled), Toast.LENGTH_SHORT).show();
                                LocationActivityOSM.this.finish();
                                return;
                            }
                        } catch ( Exception e) {
                            LogUtil.w(TAG, "onCreateActivity: requestLocationUpdates failed with " + e.getMessage());
                            LocationActivityOSM.this.finish();
                            return;
                        }

                        if (mode.equals(MODE_SHOW_LOCATION)) {
                            //showIdleDialog();
                            latitude = getIntent().getDoubleExtra(EXTRA_LATITUDE, 0.0);
                            longitude = getIntent().getDoubleExtra(EXTRA_LONGITUDE, 0.0);

                            LogUtil.d(TAG, "onCreateActivity: MODE_SHOW_LOCATION latitude = " + latitude + ", longitude = " + longitude);

                            getLocationButton.setVisibility(View.GONE);

                        } else if (mode.equals(MODE_GET_LOCATION)) {
                            //showIdleDialog();
                            getLocationButton.setVisibility(View.VISIBLE);
                        } else {
                            LogUtil.e(TAG, "onCreateActivity: Unknown mode!");
                            LocationActivityOSM.this.finish();
                        }

                    } else {
                        LogUtil.e(TAG, "onCreateActivity: Request for location permission failed!");
                        mLocation = null;
                        LocationActivityOSM.this.finish();
                    }
                });

    }

    private void showLocationOnMap() {
        GeoPoint startPoint = new GeoPoint(latitude, longitude);
        mapController.setCenter(startPoint);

        if (mode.equals(MODE_SHOW_LOCATION)) { // Add marker for partner location
            ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
            items.add(new OverlayItem("", "", new GeoPoint(latitude,longitude)));
            ItemizedOverlayWithFocus<OverlayItem> overlay = new ItemizedOverlayWithFocus<OverlayItem>(items,
                    new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                        @Override
                        public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                            //do something
                            return true;
                        }
                        @Override
                        public boolean onItemLongPress(final int index, final OverlayItem item) {
                            return false;
                        }
                    }, LocationActivityOSM.this);
            overlay.setFocusItemsOnTap(true);
            map.getOverlays().add(overlay);
        }

        // Add own location
        mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        mLocationOverlay.enableMyLocation();
        map.getOverlays().add(this.mLocationOverlay);
    }

    @Override
    public void onLocationChanged(final Location location) {
        //dismissIdleDialog();
        if(mode.equals(MODE_SHOW_LOCATION)) {
            getLocationButton.setEnabled(false);
        } else {
            if(mLocation == null) {
                LogUtil.w(TAG, "onLocationChanged: mLocation = null.");
                Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.location_could_not_be_determined), Toast.LENGTH_SHORT).show();
                LocationActivityOSM.this.finish();
                return;
            } else {
                getLocationButton.setEnabled(true);
                latitude = mLocation.getLatitude();
                longitude = mLocation.getLongitude();
            }
        }
        getLocationButton.setText(getString(R.string.chat_location_selection_position_info_info_text));
        showLocationOnMap();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_location_osm;
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onResumeActivity() {
        map.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mLocationOverlay != null) {
            mLocationOverlay.disableMyLocation();
        }
        mLocationManager.removeUpdates(this);
    }

    @SuppressLint("MissingPermission")
    public void handleGetLocationClick(View view) {

        final Intent intent = new Intent();

        if(mLocation != null) {
            intent.putExtra(EXTRA_LATITUDE, latitude);
            intent.putExtra(EXTRA_LONGITUDE, longitude);

            final Bitmap placeHolder = ((BitmapDrawable) getDrawable(R.drawable.chat_location_placeholder)).getBitmap();
            // Convert placeHolder to jpeg ...
            intent.putExtra(EXTRA_SCREENSHOT, BitmapUtil.compress(placeHolder, SCREENSHOT_QUALITY));
            setResult(Activity.RESULT_OK, intent);

        } else {
            setResult(Activity.RESULT_CANCELED, intent);
        }

        if(mLocationOverlay != null) {
            mLocationOverlay.disableMyLocation();
        }
        mLocationManager.removeUpdates(this);
        finish();
    }
}
