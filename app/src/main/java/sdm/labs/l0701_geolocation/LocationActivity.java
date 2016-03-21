package sdm.labs.l0701_geolocation;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.List;

/**
 * Displays the current location of the device and translates the latitude and longitude coordinates
 * into a human readable address. It manages both the Android Location Framework and the
 * Google Location API to request updates from the location provider.
 */
public class LocationActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    // Constants defining the location framework to be used
    public static final int ANDROID_LOCATION_FRAMEWORK = 0;
    public static final int GOOGLE_LOCATION = 1;
    int locationSystem;

    // Constant defining that permission were requested to remove location updates
    private static final int REMOVE_LOCATION_UPDATES_PERMISSION = 2;
    private static final int REQUEST_RESOLVE_ERROR = 3;

    // Hold references required for the Android Location Framework
    LocationManager locationManager = null;
    MyAndroidFrameworkLocationListener androidframeworkLocationListener;

    // Hold references required for Google Play Services and Google Location API
    GoogleApiClient client;
    LocationRequest request;
    MyGoogleLocationListener googleLocationListener;

    // Hold reference toa Geocoder to translated coordinates into human readable addresses
    Geocoder geocoder;

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
        tvLongitude = (TextView) findViewById(R.id.etLongitude);
        tvLatitude = (TextView) findViewById(R.id.etLatitude);
        tvAddress = (TextView) findViewById(R.id.tvAddress);
        tvLongitude.setText(
                String.format(getResources().getString(R.string.latitude),
                        getResources().getString(R.string.unknown)));
        tvLatitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        getResources().getString(R.string.unknown)));

        // Initialize elements according to the selected location framework
        switch (getIntent().getIntExtra("system", -1)) {

            // Android Location Framework
            case ANDROID_LOCATION_FRAMEWORK:

                locationSystem = ANDROID_LOCATION_FRAMEWORK;
                // Listener to receive th location udpates
                androidframeworkLocationListener = new MyAndroidFrameworkLocationListener();
                // LocationManager giving access to the location services
                locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                break;

            // Google Location Framework
            case GOOGLE_LOCATION:

                locationSystem = GOOGLE_LOCATION;
                // Listener to receive th location udpates
                googleLocationListener = new MyGoogleLocationListener();
                // Initialize GoogleApiClient fro LocationServices API
                client = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
                break;
        }

        // Initialize the Geocoder
        geocoder = new Geocoder(this);
    }

    /**
     * This method is executed when the activity is created to populate the ActionBar with actions.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location, menu);
        // Determine whether the GoogleApiClient is connected when using the Google Location Framework
        boolean clientConnected = (locationSystem != GOOGLE_LOCATION) || client.isConnected();

        menu.findItem(R.id.mEnableGps).setVisible(displayEnableLocation && clientConnected);
        menu.findItem(R.id.mEnableNetwork).setVisible(displayEnableLocation && clientConnected);
        menu.findItem(R.id.mDisableLocation).setVisible(!displayEnableLocation && clientConnected);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.mEnableGps:
                enableLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, Manifest.permission.ACCESS_FINE_LOCATION);
                break;
            case R.id.mEnableNetwork:
                enableLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            case R.id.mDisableLocation:
                disableLocation();
                break;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (locationSystem == GOOGLE_LOCATION) {
            client.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        switch (locationSystem) {
            case ANDROID_LOCATION_FRAMEWORK:
                if ((locationManager != null) && (permissionGranted != null)) {
                    disableLocation();
                }
                break;
            case GOOGLE_LOCATION:
                if (permissionGranted != null) {
                    disableLocation();
                }
                client.disconnect();
                break;
        }
    }

    private void enableLocation(int priority, String permission) {
        switch (locationSystem) {
            case ANDROID_LOCATION_FRAMEWORK:
                enableAndroidLocationFramework(priority, permission);
                break;
            case GOOGLE_LOCATION:
                enableGoogleLocation(priority, permission);
                break;
        }

    }

    private void enableAndroidLocationFramework(int priority, String permission) {
        checkLocationPermissions(priority, permission);
    }

    private void enableGoogleLocation(final int priority, final String permission) {
        if (client.isConnected()) {
            request = new LocationRequest();
            request.setPriority(priority);
            request.setInterval(10000);
            request.setFastestInterval(5000);

            LocationSettingsRequest.Builder builder =
                    new LocationSettingsRequest.Builder()
                            .addLocationRequest(request);

            PendingResult<LocationSettingsResult> results =
                    LocationServices.SettingsApi.checkLocationSettings(client, builder.build());
            results.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                    switch (locationSettingsResult.getStatus().getStatusCode()) {
                        // Location settings are satisfied, request location updates
                        case CommonStatusCodes.SUCCESS:
                            checkLocationPermissions(priority, permission);
                            break;
                        // Location settings are not satisfied, but the user can fix them through a dialog
                        case CommonStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                locationSettingsResult.getStatus().startResolutionForResult(
                                        LocationActivity.this, priority);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }
                            break;
                        // Location settings are not satisfied and the system cannot fix them
                        default:
                            Toast.makeText(
                                    LocationActivity.this,
                                    R.string.location_settings_not_satisfied,
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            });
        } else {
            Toast.makeText(
                    LocationActivity.this,
                    R.string.google_client_connecting,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void checkLocationPermissions(int priority, String permission) {
        if (isLocationPermissionGranted(permission, priority)) {
            locationPermissionsGranted(priority, permission);
        }
    }

    private void locationPermissionsGranted(int priority, String permission) {
        permissionGranted = permission;
        switch (locationSystem) {
            case ANDROID_LOCATION_FRAMEWORK:
                requestAndroidLocationFrameworkUpdates(priority);
                break;
            case GOOGLE_LOCATION:
                requestGoogleLocationUpdates();
                break;
        }
    }

    private void requestAndroidLocationFrameworkUpdates(int priority) {
        String provider = (priority == LocationRequest.PRIORITY_HIGH_ACCURACY) ?
                LocationManager.GPS_PROVIDER :
                LocationManager.NETWORK_PROVIDER;
        if (locationManager.isProviderEnabled(provider)) {
            locationManager.requestLocationUpdates(provider, 5000, 10, androidframeworkLocationListener);
            displayEnableLocation = false;
            supportInvalidateOptionsMenu();
        } else {
            Toast.makeText(LocationActivity.this, R.string.provider_not_enabled, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestGoogleLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(client, request, googleLocationListener);
        displayEnableLocation = false;
        supportInvalidateOptionsMenu();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_RESOLVE_ERROR:
                if (resultCode == RESULT_OK) {
                    if (!client.isConnecting() && !client.isConnected()) {
                        client.connect();
                    }
                }
                break;
            case LocationRequest.PRIORITY_HIGH_ACCURACY:
            case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                if (resultCode == RESULT_OK) {
                    // Settings should have been corrected, try again
                    enableGoogleLocation(
                            requestCode,
                            (requestCode == LocationRequest.PRIORITY_HIGH_ACCURACY) ?
                                    Manifest.permission.ACCESS_FINE_LOCATION :
                                    Manifest.permission.ACCESS_COARSE_LOCATION);
                } else {
                    // The user has selected not to make the required changes
                    Toast.makeText(
                            LocationActivity.this,
                            R.string.location_settings_not_changed,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    private void showErrorDialog(int errorCode) {
        MyErrorDialogFragment dialog = new MyErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialog_error", errorCode);
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "errorDialog");
    }

    private boolean isLocationPermissionGranted(String permission, int requestCode) {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permission)) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((grantResults.length > 0) && (PackageManager.PERMISSION_GRANTED == grantResults[0])) {
            switch (requestCode) {
                case LocationRequest.PRIORITY_HIGH_ACCURACY:
                    locationPermissionsGranted(requestCode, Manifest.permission.ACCESS_FINE_LOCATION);
                    break;
                case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                    locationPermissionsGranted(requestCode, Manifest.permission.ACCESS_COARSE_LOCATION);
                case REMOVE_LOCATION_UPDATES_PERMISSION:
                    switch (locationSystem) {
                        case ANDROID_LOCATION_FRAMEWORK:
                            removeGoogleLocationUpdates();
                            break;
                        case GOOGLE_LOCATION:
                            removeAndroidLocationFrameworkUpdates();
                            break;
                    }
            }
        } else {
            Toast.makeText(
                    LocationActivity.this, R.string.permissions_not_granted, Toast.LENGTH_SHORT).show();
        }
    }

    private void disableLocation() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permissionGranted)) {
            switch (locationSystem) {
                case ANDROID_LOCATION_FRAMEWORK:
                    removeAndroidLocationFrameworkUpdates();
                    break;
                case GOOGLE_LOCATION:
                    removeGoogleLocationUpdates();
                    break;
            }
            displayEnableLocation = true;
            permissionGranted = null;
            supportInvalidateOptionsMenu();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permissionGranted},
                    (permissionGranted.equals(Manifest.permission.ACCESS_FINE_LOCATION)) ?
                            LocationRequest.PRIORITY_HIGH_ACCURACY :
                            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        }
    }

    private void removeAndroidLocationFrameworkUpdates() {
        locationManager.removeUpdates(androidframeworkLocationListener);
    }

    private void removeGoogleLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(client, googleLocationListener);
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private void updateUI(Location location) {
        tvLongitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        String.valueOf(location.getLongitude())));
        tvLatitude.setText(
                String.format(getResources().getString(R.string.longitude),
                        String.valueOf(location.getLatitude())));

        if (isConnectionAvailable()) {
            (new GeocoderAsyncTask()).execute(location.getLatitude(), location.getLongitude());
        }
    }

    private class MyAndroidFrameworkLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
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

    private class MyGoogleLocationListener implements com.google.android.gms.location.LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            updateUI(location);
        }
    }

    public static class MyErrorDialogFragment extends SupportErrorDialogFragment {

        public MyErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int errorCode = this.getArguments().getInt("dialog_error");
            return GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }
    }

    private class GeocoderAsyncTask extends AsyncTask<Double, Void, Address> {

        @Override
        protected Address doInBackground(Double... params) {
            try {
                List<Address> addresses = geocoder.getFromLocation(params[0], params[1], 1);
                if ((addresses != null) && (addresses.size() > 0)) {
                    return addresses.get(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Address address) {
            StringBuilder display;

            display = new StringBuilder();
            if (address != null) {
                int addressLines = address.getMaxAddressLineIndex();
                if (addressLines != -1) {
                    display.append(address.getAddressLine(0));
                    for (int i = 1; i <= addressLines; i++) {
                        display.append(", ").append(address.getAddressLine(i));
                    }
                } else {
                    display.append(getResources().getString(R.string.geocoder_not_available));
                }
            } else {
                display.append(getResources().getString(R.string.geocoder_not_available));
            }
            tvAddress.setText(display.toString());
        }
    }
}