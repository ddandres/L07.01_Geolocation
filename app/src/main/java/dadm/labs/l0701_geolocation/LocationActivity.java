/*
 * Copyright (c) 2016. David de Andrés and Juan Carlos Ruiz, DISCA - UPV, Development of apps for mobile devices.
 */

package dadm.labs.l0701_geolocation;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

/**
 * Displays the current location of the device and translates the latitude and longitude coordinates
 * into a human readable address. It manages both the Android Location Framework and the
 * Google Location API to request updates from the location provider.
 */
public class LocationActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    // Constants defining the location framework to be used
    public static final int ANDROID_LOCATION_FRAMEWORK = 0;
    public static final int GOOGLE_LOCATION_API = 1;
    int selectedLocationFramework;

    // Constant defining that permission were requested to remove location updates
    private static final int REMOVE_LOCATION_UPDATES_PERMISSION = 2;
    private static final int REQUEST_RESOLVE_ERROR = 3;

    // Hold references required for the Android Location Framework
    LocationManager locationManager = null;
    MyAndroidFrameworkLocationListener androidFrameworkLocationListener;

    // Hold references required for Google Play Services and Google Location API
    GoogleApiClient client;
    FusedLocationProviderClient fusedLocationclient;
    LocationRequest request;

    MyGoogleLocationCallback googleLocationCallback;

    // Hold reference to the last permission granted
    String permissionGranted = null;

    // States whether the options menu to enable location should be displayed
    boolean displayEnableLocation = true;

    // Hold reference to Views
    TextView tvLongitude;
    TextView tvLatitude;
    TextView tvAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        // Keep references to View elements
        tvLongitude = findViewById(R.id.etLongitude);
        tvLatitude = findViewById(R.id.etLatitude);
        tvAddress = findViewById(R.id.tvAddress);

        // Initially display an "Unknown" longitude and latitude
        tvLongitude.setText(
                String.format(getResources().getString(R.string.latitude),
                        getResources().getString(R.string.unknown)));
        tvLatitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        getResources().getString(R.string.unknown)));

        // Initialize elements according to the selected location framework
        switch (getIntent().getIntExtra("location_framework", -1)) {

            // Android Location Framework
            case ANDROID_LOCATION_FRAMEWORK:

                selectedLocationFramework = ANDROID_LOCATION_FRAMEWORK;
                // Listener to receive location updates
                androidFrameworkLocationListener = new MyAndroidFrameworkLocationListener();
                // LocationManager giving access to the location services
                locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                break;

            // Google Location Framework
            case GOOGLE_LOCATION_API:

                selectedLocationFramework = GOOGLE_LOCATION_API;
                // Listener to receive location updates
                googleLocationCallback = new MyGoogleLocationCallback();
                // Initialize GoogleApiClient for LocationServices API
                client = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
                fusedLocationclient = LocationServices.getFusedLocationProviderClient(this);
                break;
        }

    }

    /**
     * This method is executed when the activity is created to populate the ActionBar with actions.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_location, menu);

        // Determine whether the GoogleApiClient is connected when using the Google Location Framework
        boolean clientConnected = (selectedLocationFramework != GOOGLE_LOCATION_API) || client.isConnected();
        // Display the actions to enable or disable location updates
        menu.findItem(R.id.mEnableGps).setVisible(displayEnableLocation && clientConnected);
        menu.findItem(R.id.mEnableNetwork).setVisible(displayEnableLocation && clientConnected);
        menu.findItem(R.id.mDisableLocation).setVisible(!displayEnableLocation && clientConnected);

        return true;
    }

    /**
     * This method is executed when any action from the ActionBar is selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Determine the action to take place according to the Id of the action selected
        switch (item.getItemId()) {

            // Enable precise/fine location
            case R.id.mEnableGps:
                enableLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, Manifest.permission.ACCESS_FINE_LOCATION);
                break;

            // Enable loose/coarse location
            case R.id.mEnableNetwork:
                enableLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, Manifest.permission.ACCESS_COARSE_LOCATION);
                break;

            // Disable location updates
            case R.id.mDisableLocation:
                disableLocation();
                break;
        }
        return true;
    }

    /**
     * Connects the GoogleApiClient, when using the Google Location API, whenever the activity starts.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (selectedLocationFramework == GOOGLE_LOCATION_API) {
            client.connect();
        }
    }

    /**
     * Disables the location updates (if enabled) and disconnects the GoogleApiClient
     * (when using the Google Location API), whenever the activity stops.
     */
    @Override
    protected void onStop() {
        super.onStop();

        // Check the location framework in use
        switch (selectedLocationFramework) {

            // Android Location Framework
            case ANDROID_LOCATION_FRAMEWORK:
                if ((locationManager != null) && (permissionGranted != null)) {
                    disableLocation();
                }
                break;

            // Google Location API
            case GOOGLE_LOCATION_API:
                if (permissionGranted != null) {
                    disableLocation();
                }
                client.disconnect();
                break;
        }
    }

    /**
     * Tries to enable location updates for the selected location framework.
     *
     * @param priority   Determines the priority when selecting the location provider.
     * @param permission Permission required to request updates from the selected location provider.
     */
    private void enableLocation(int priority, String permission) {

        switch (selectedLocationFramework) {

            // Android Location Framework
            case ANDROID_LOCATION_FRAMEWORK:
                enableAndroidLocationFramework(priority, permission);
                break;

            // Google Location API
            case GOOGLE_LOCATION_API:
                enableGoogleLocation(priority, permission);
                break;
        }

    }


    /**
     * Tries to enable location updates for the Android Location Framework.
     *
     * @param priority   Determines the priority when selecting the location provider.
     * @param permission Permission required to request updates from the selected location provider.
     */
    private void enableAndroidLocationFramework(int priority, String permission) {
        // Check for permissions
        checkLocationPermissions(priority, permission);
    }

    /**
     * Tries to enable location updates for the Google Location Service.
     *
     * @param priority   Determines the priority when selecting the location provider.
     * @param permission Permission required to request updates from the selected location provider.
     */
    private void enableGoogleLocation(final int priority, final String permission) {

        // Perform the whole procedure only if the GoogleCApiClient is connected
        if (client.isConnected()) {

            // Create a new request for updates each 10s (each 5s at most)
            request = new LocationRequest();
            request.setPriority(priority);
            request.setInterval(10000);
            request.setFastestInterval(5000);

            // Object specifying the type of location services the user is interested in
            LocationSettingsRequest.Builder builder =
                    new LocationSettingsRequest.Builder()
                            .addLocationRequest(request);

            // Check that the request location services are available
            Task<LocationSettingsResponse> results =
                    LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());
            // Callback to receive the response from the previous check
            results.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
                @Override
                public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                    // Location settings are satisfied, so proceed to request location updates
                    // Check that the requires permissions are granted
                    checkLocationPermissions(priority, permission);
                }
            });
            results.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    if (e instanceof ResolvableApiException) {

                        ResolvableApiException resolvable = (ResolvableApiException) e;

                        try {
                            // Show the user a system dialog for handling the problem
                            resolvable.startResolutionForResult(
                                    LocationActivity.this, priority);
                        } catch (IntentSender.SendIntentException sie) {
                            sie.printStackTrace();
                        }
                    } else {
                        // Notify the user if this problem
                        Toast.makeText(
                                LocationActivity.this,
                                R.string.location_settings_not_satisfied,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        // The GoogleApiClient is not connected, show a notification to the user
        else {
            Toast.makeText(
                    LocationActivity.this,
                    R.string.google_client_connecting,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Checks that permissions are granted for the selected location framework.
     *
     * @param priority   Determines the priority when selecting the location provider.
     * @param permission Permission required to request updates from the selected location provider.
     */
    private void checkLocationPermissions(int priority, String permission) {
        if (isLocationPermissionGranted(permission, priority)) {
            locationPermissionsGranted(priority, permission);
        }
    }

    /**
     * Checks that permissions are granted for the selected location framework.
     *
     * @param permission  Permission required to request updates from the selected location provider.
     * @param requestCode Code used to later identify the request in the callback.
     */
    private boolean isLocationPermissionGranted(String permission, int requestCode) {

        // Determine whether the user has granted that particular permission
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permission)) {
            return true;
        }
        // If not, display an activity to request that permission
        else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
    }

    /**
     * Requests location updates for the selected location framework.
     *
     * @param priority   Determines the priority when selecting the location provider.
     * @param permission Permission required to request updates from the selected location provider.
     */
    private void locationPermissionsGranted(int priority, String permission) {

        // Keep reference of the granted permission
        permissionGranted = permission;

        switch (selectedLocationFramework) {

            // Android Location Framework
            case ANDROID_LOCATION_FRAMEWORK:
                requestAndroidLocationFrameworkUpdates(priority);
                break;

            // Google Location API
            case GOOGLE_LOCATION_API:
                requestGoogleLocationUpdates();
                break;
        }
    }

    /**
     * Requests location updates for the Android Location Framework.
     *
     * @param priority Determines the priority when selecting the location provider.
     */
    private void requestAndroidLocationFrameworkUpdates(int priority) {

        // Determine the required location provider according to the requested priority
        String provider = (priority == LocationRequest.PRIORITY_HIGH_ACCURACY) ?
                LocationManager.GPS_PROVIDER :
                LocationManager.NETWORK_PROVIDER;
        // Check whether that location provider is enabled
        if (locationManager.isProviderEnabled(provider)) {

            // Request location updates each 5s with a minimum distance of 10m
            locationManager.requestLocationUpdates(provider, 5000, 10, androidFrameworkLocationListener);

            // Set to false the flag controlling whether to display the actions to enable the location udpates
            displayEnableLocation = false;
            // Ask the system to rebuild the options of the ActionBar
            supportInvalidateOptionsMenu();
        }
        // Display a notification to the user stating that the location provider is not enabled
        else {
            Toast.makeText(LocationActivity.this, R.string.provider_not_enabled, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Requests location updates for the Google Location API.
     */
    private void requestGoogleLocationUpdates() {
        fusedLocationclient.requestLocationUpdates(request, googleLocationCallback, null);


        // Set to false the flag controlling whether to display the actions to enable the location udpates
        displayEnableLocation = false;
        // Ask the system to rebuild the options of the ActionBar
        supportInvalidateOptionsMenu();
    }

    /**
     * This callback is executed whenever an activity was started expecting a result.
     * In this case it covers two different possibilities related to the used of Google Location API:
     * The GoogleApiClient was not able to connect.
     * The location settings did not match those requested by the user.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check which was request has been processed
        switch (requestCode) {

            // The GoogleApiClient was not able to connect
            case REQUEST_RESOLVE_ERROR:
                // If the problem has been solved, retry the connection
                if (resultCode == RESULT_OK) {
                    if (!client.isConnecting() && !client.isConnected()) {
                        client.connect();
                    }
                }
                break;

            // The location settings did not match those requested by the user
            case LocationRequest.PRIORITY_HIGH_ACCURACY:
            case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                // If the settings have been corrected, enable request updates via Google Location API
                if (resultCode == RESULT_OK) {
                    enableGoogleLocation(
                            requestCode,
                            (requestCode == LocationRequest.PRIORITY_HIGH_ACCURACY) ?
                                    Manifest.permission.ACCESS_FINE_LOCATION :
                                    Manifest.permission.ACCESS_COARSE_LOCATION);
                }
                // The user has not changed the settings, so display a notification
                else {
                    Toast.makeText(
                            LocationActivity.this,
                            R.string.location_settings_not_changed,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * This callback is executed whenever the GoogleApiClient fails to connect.
     * Try to solve the problem or show an error dialog.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        // If the problem can be solved then start the required activity
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        }
        // The problem cannot be directly solved, so display an error dialog
        else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    /**
     * Displays an error dialog to notify that the GoogleApiClient was not able to connect and
     * no direct solution is directly available.
     *
     * @param errorCode Error code obtained while trying to connect.
     */
    private void showErrorDialog(int errorCode) {

        // Custom FragmentDialog
        MyErrorDialogFragment dialog = new MyErrorDialogFragment();
        // Include the obtained error code as an arguments
        Bundle args = new Bundle();
        args.putInt("dialog_error", errorCode);
        dialog.setArguments(args);
        // Display the error dialog
        dialog.show(getSupportFragmentManager(), "errorDialog");
    }

    /**
     * Creates an error dialog fragment according to the received connection error code.
     */
    public static class MyErrorDialogFragment extends SupportErrorDialogFragment {

        public MyErrorDialogFragment() {
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code passed as argument
            int errorCode = this.getArguments().getInt("dialog_error");
            // Create the error frgamen dialog provided by the GoogleApiAvailability
            return GoogleApiAvailability
                    .getInstance().getErrorDialog(this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }
    }

    /**
     * This callback is executed whenever the user has been asked to grant permissions.
     * In this case it will deal with permission to request fine/coarse location updates,
     * and to remove any previous requested location update.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        // Check whether any permission has been granted
        if ((grantResults.length > 0) && (PackageManager.PERMISSION_GRANTED == grantResults[0])) {

            // Determine the course of action according to the requested action
            switch (requestCode) {

                // Get permission to access the GPS_PROVIDER
                case LocationRequest.PRIORITY_HIGH_ACCURACY:
                    // Request fine location updates according to the selected framework
                    locationPermissionsGranted(requestCode, Manifest.permission.ACCESS_FINE_LOCATION);
                    break;

                // Get permission to access the GPS_PROVIDER
                case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                    // Request coarse location updates according to the selected framework
                    locationPermissionsGranted(requestCode, Manifest.permission.ACCESS_COARSE_LOCATION);
                    break;

                // Get permission to remove the requested updates from the provider in use
                case REMOVE_LOCATION_UPDATES_PERMISSION:

                    // Stop receiving location updates according to the selected location framework
                    switch (selectedLocationFramework) {

                        // Android Location framework
                        case ANDROID_LOCATION_FRAMEWORK:
                            removeGoogleLocationUpdates();
                            break;

                        // Google Location API
                        case GOOGLE_LOCATION_API:
                            removeAndroidLocationFrameworkUpdates();
                            break;
                    }
            }
        }
        // Notify the user that permission were not granted
        else {
            Toast.makeText(
                    LocationActivity.this, R.string.permissions_not_granted, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Tries to disable location updates for the selected location framework.
     */
    private void disableLocation() {

        /*
         * If required permissions have been granted then
         * stop receiving location updates from the selected framework.
         */
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permissionGranted)) {

            switch (selectedLocationFramework) {

                // Android Location Framework
                case ANDROID_LOCATION_FRAMEWORK:
                    removeAndroidLocationFrameworkUpdates();
                    break;

                // Google Location API
                case GOOGLE_LOCATION_API:
                    removeGoogleLocationUpdates();
                    break;
            }
            // Enable the flag controlling whether to display the actions to enable location udpates
            displayEnableLocation = true;
            // Clear up the variable holding a reference to the granted permission
            permissionGranted = null;
            // Ask the system to rebuild the options of the ActionBar
            supportInvalidateOptionsMenu();
        }
        // If not, display an activity to request that permission
        else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permissionGranted},
                    (permissionGranted.equals(Manifest.permission.ACCESS_FINE_LOCATION)) ?
                            LocationRequest.PRIORITY_HIGH_ACCURACY :
                            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        }
    }

    /**
     * Removes the current listener to stop receiving location updates from the Android Location Framework.
     */
    private void removeAndroidLocationFrameworkUpdates() {
        locationManager.removeUpdates(androidFrameworkLocationListener);
    }

    /**
     * Removes the current listener to stop receiving location updates from the Google Location API.
     */
    private void removeGoogleLocationUpdates() {
        fusedLocationclient.removeLocationUpdates(googleLocationCallback);
    }

    /**
     * Check whether Internet connectivity is available.
     */
    private boolean isConnectionAvailable() {

        // Get a reference to the ConnectivityManager
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // Get information for the current default data network
        NetworkInfo info = manager.getActiveNetworkInfo();
        // Return true if there is network connectivity
        return ((info != null) && info.isConnected());
    }

    /**
     * This callback is executed whenever the GoogleApiClient connects.
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        /*
         * Ask the system to rebuild the options of the ActionBar,
         * so it will display the options to enable location updates
         */
        supportInvalidateOptionsMenu();
    }

    /**
     * This callback is executed whenever the GoogleApiClient gets disconnected.
     */
    @Override
    public void onConnectionSuspended(int i) {
        // No action defined, although it could try to reconnect the client
    }

    /**
     * Updates the user interface to display the new latitude and longitude.
     * It will also start an asynchronous task to translate those coordinates into a human readable address.
     */
    private void updateUI(Location location) {
        // Display current longitude
        tvLongitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        String.valueOf(location.getLongitude())));
        // Display current latitude
        tvLatitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        String.valueOf(location.getLatitude())));
        // Start asynchronous task to translate coordinates into an address
        if (isConnectionAvailable()) {
            (new GeocoderAsyncTask(this)).execute(location.getLatitude(), location.getLongitude());
        }
    }

    /**
     * Custom LocationListener for the Android Location Framework.
     */
    private class MyAndroidFrameworkLocationListener implements LocationListener {

        /**
         * This callback is executed whenever a new location update is received.
         */
        @Override
        public void onLocationChanged(Location location) {
            // Update the user interface
            updateUI(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private class MyGoogleLocationCallback extends LocationCallback {

        /**
         * This callback is executed whenever a new location update is received.
         */
        @Override
        public void onLocationResult(LocationResult locationResult) {
            // Update the user interface
            updateUI(locationResult.getLocations().get(0));
        }
    }
}