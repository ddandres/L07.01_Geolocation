package sdm.labs.l0701_geolocation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import java.io.IOException;
import java.util.List;

public class GoogleLocationActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
                                                                        GoogleApiClient.ConnectionCallbacks,
                                                                        com.google.android.gms.location.LocationListener {

    private static final int FINE_LOCATION_PERMISSION = 0;
    private static final int COARSE_LOCATION_PERMISSION = 1;
    private static final int REMOVE_LOCATION_UPDATES_PERMISSION = 2;

    GoogleApiClient client;
    LocationRequest request;
    Geocoder geocoder;

    String permissionGranted = null;

    boolean displayEnableLocation = true;

    EditText etLongitude;
    EditText etLatitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        etLongitude = (EditText) findViewById(R.id.etLongitude);
        etLatitude = (EditText) findViewById(R.id.etLatitude);

        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        geocoder = new Geocoder(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location, menu);
        menu.findItem(R.id.mEnableGps).setVisible(displayEnableLocation && client.isConnected());
        menu.findItem(R.id.mEnableNetwork).setVisible(displayEnableLocation && client.isConnected());
        menu.findItem(R.id.mDisableLocation).setVisible(!displayEnableLocation && client.isConnected());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.mEnableGps:
                enableGoogleLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, Manifest.permission.ACCESS_FINE_LOCATION);
                break;
            case R.id.mEnableNetwork:
                enableGoogleLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            case R.id.mDisableLocation:
                disableGoogleLocation();
                break;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        client.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if ((client.isConnected()) && (permissionGranted != null)) {
            disableGoogleLocation();
        }
        client.disconnect();
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
                            if (checkLocationPermission(permission, priority)) {
                                permissionGranted = permission;
                                requestLocationUpdates();
                            }
                            break;
                        // Location settings are not satisfied, but the user can fix them through a dialog
                        case CommonStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                locationSettingsResult.getStatus().startResolutionForResult(
                                        GoogleLocationActivity.this, priority);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }
                            break;
                        // Location settings are not satisfied and the system cannot fix them
                        default:
                            Toast.makeText(
                                    GoogleLocationActivity.this,
                                    R.string.location_settings_not_satisfied,
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            });
        } else {
            Toast.makeText(
                    GoogleLocationActivity.this,
                    R.string.google_client_connecting,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void requestLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(client, request, GoogleLocationActivity.this);
        displayEnableLocation = false;
        supportInvalidateOptionsMenu();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case LocationRequest.PRIORITY_HIGH_ACCURACY:
            case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                switch (resultCode) {
                    // Settings should have been corrected, try again
                    case RESULT_OK:
                        enableGoogleLocation(
                                requestCode,
                                (requestCode == LocationRequest.PRIORITY_HIGH_ACCURACY) ?
                                        Manifest.permission.ACCESS_FINE_LOCATION :
                                        Manifest.permission.ACCESS_COARSE_LOCATION);
                        break;
                    // The user has selected not to make the required changes
                    case RESULT_CANCELED:
                    default:
                        Toast.makeText(
                                GoogleLocationActivity.this,
                                R.string.location_settings_not_changed,
                                Toast.LENGTH_SHORT).show();
                        break;
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(GoogleLocationActivity.this, R.string.google_location_not_available, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LocationFrameworkActivity.class));
        this.finish();
    }

    @Override
    public void onLocationChanged(Location location) {
        etLongitude.setText(String.valueOf(location.getLongitude()));
        etLatitude.setText(String.valueOf(location.getLatitude()));

        if (isConnectionAvailable()) {
            (new GeocoderAsyncTask()).execute(location.getLatitude(), location.getLongitude());
        }
    }

    private boolean checkLocationPermission(String permission, int requestCode) {
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
                    permissionGranted = Manifest.permission.ACCESS_FINE_LOCATION;
                    requestLocationUpdates();
                    break;
                case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                    permissionGranted = Manifest.permission.ACCESS_COARSE_LOCATION;
                    requestLocationUpdates();
                    break;
                case REMOVE_LOCATION_UPDATES_PERMISSION:
                    removeLocationUpdates();
            }
        } else {
            Toast.makeText(
                    GoogleLocationActivity.this, R.string.permissions_not_granted, Toast.LENGTH_SHORT).show();
        }
    }

    private void disableGoogleLocation() {
        if (client.isConnected()) {
            if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permissionGranted)) {
                removeLocationUpdates();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{permissionGranted},
                        (permissionGranted.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) ?
                                COARSE_LOCATION_PERMISSION : FINE_LOCATION_PERMISSION);
            }
        } else {
            Toast.makeText(
                    GoogleLocationActivity.this,
                    R.string.google_client_connecting,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void removeLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        displayEnableLocation = true;
        permissionGranted = null;
        supportInvalidateOptionsMenu();
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
            if (address != null) {
                int addressLines = address.getMaxAddressLineIndex();
                StringBuilder line = new StringBuilder();
                if (addressLines != -1) {
                    line.append(address.getAddressLine(0));
                    for (int i = 1; i < addressLines; i++) {
                        line.append(", ").append(address.getAddressLine(i));
                    }
                }
                String display = (!(line.toString().equals("")) ? line.toString() : "") +
                        ((address.getPostalCode() != null) ? ", " + address.getPostalCode() : "") +
                        ((address.getLocality() != null) ? ", " + address.getLocality() : "") +
                        ((address.getCountryName() != null) ? ", " + address.getCountryName() : "");
                Toast.makeText(GoogleLocationActivity.this, display, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(GoogleLocationActivity.this, R.string.geocoder_not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

}
