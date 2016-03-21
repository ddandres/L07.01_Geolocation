package sdm.labs.l0701_geolocation;

import android.Manifest;
import android.content.Context;
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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class LocationFrameworkActivity extends AppCompatActivity {

    private static final int FINE_LOCATION_PERMISSION = 0;
    private static final int COARSE_LOCATION_PERMISSION = 1;
    private static final int REMOVE_LOCATION_UPDATES_PERMISSION = 2;

    String permissionGranted = null;

    LocationManager locationManager = null;
    MyLocationListener locationListener;
    Geocoder geocoder;

    boolean displayEnableLocation = true;

    EditText etLongitude;
    EditText etLatitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        locationListener = new MyLocationListener();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        geocoder = new Geocoder(this);

        etLongitude = (EditText) findViewById(R.id.etLongitude);
        etLatitude = (EditText) findViewById(R.id.etLatitude);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location, menu);
        menu.findItem(R.id.mEnableGps).setVisible(displayEnableLocation);
        menu.findItem(R.id.mEnableNetwork).setVisible(displayEnableLocation);
        menu.findItem(R.id.mDisableLocation).setVisible(!displayEnableLocation);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (locationManager != null) {
            switch (item.getItemId()) {
                case R.id.mEnableGps:
                    if (checkLocationPermission(Manifest.permission.ACCESS_FINE_LOCATION, FINE_LOCATION_PERMISSION)) {
                        requestLocationUpdates(
                                Manifest.permission.ACCESS_FINE_LOCATION, LocationManager.GPS_PROVIDER);
                    }
                    break;
                case R.id.mEnableNetwork:
                    if (checkLocationPermission(Manifest.permission.ACCESS_COARSE_LOCATION, COARSE_LOCATION_PERMISSION)) {
                        requestLocationUpdates(
                                Manifest.permission.ACCESS_COARSE_LOCATION, LocationManager.NETWORK_PROVIDER);
                    }
                    break;
                case R.id.mDisableLocation:
                    stopLocationUpdates();
                    break;
            }
        }
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if ((locationManager != null) && (permissionGranted != null)) {
            stopLocationUpdates();
        }
    }

    private void stopLocationUpdates() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permissionGranted)) {
            removeLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permissionGranted},
                    (permissionGranted.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) ?
                            COARSE_LOCATION_PERMISSION : FINE_LOCATION_PERMISSION);
        }
    }

    private void removeLocationUpdates() {
        locationManager.removeUpdates(locationListener);
        displayEnableLocation = true;
        permissionGranted = null;
        supportInvalidateOptionsMenu();
    }

    private boolean checkLocationPermission(String permission, int requestCode) {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, permission)) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
    }

    private void requestLocationUpdates(String permission, String provider) {
        permissionGranted = permission;
        if (locationManager.isProviderEnabled(provider)) {
            locationManager.requestLocationUpdates(provider, 5000, 10, locationListener);
            displayEnableLocation = false;
            supportInvalidateOptionsMenu();
        } else {
            Toast.makeText(LocationFrameworkActivity.this, R.string.provider_not_enabled, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((grantResults.length > 0) && (PackageManager.PERMISSION_GRANTED == grantResults[0])) {
            switch (requestCode) {
                case FINE_LOCATION_PERMISSION:
                    requestLocationUpdates(
                            Manifest.permission.ACCESS_FINE_LOCATION, LocationManager.GPS_PROVIDER);
                    break;
                case COARSE_LOCATION_PERMISSION:
                    requestLocationUpdates(
                            Manifest.permission.ACCESS_COARSE_LOCATION, LocationManager.NETWORK_PROVIDER);
                    break;
                case REMOVE_LOCATION_UPDATES_PERMISSION:
                    removeLocationUpdates();
            }
        } else {
            Toast.makeText(
                    LocationFrameworkActivity.this, R.string.permissions_not_granted, Toast.LENGTH_SHORT).show();
        }
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

    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            etLongitude.setText(String.valueOf(location.getLongitude()));
            etLatitude.setText(String.valueOf(location.getLatitude()));

            if (isConnectionAvailable()) {
                (new GeocoderAsyncTask()).execute(location.getLatitude(), location.getLongitude());
            }
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
                Toast.makeText(LocationFrameworkActivity.this, display, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(LocationFrameworkActivity.this, R.string.geocoder_not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
