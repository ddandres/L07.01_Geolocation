package dadm.labs.l0701_geolocation;

import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class GeocoderThread extends Thread {

    final private WeakReference<LocationActivity> activity;
    final double longitude;
    final double latitude;

    GeocoderThread(LocationActivity activity, double latitude, double longitude) {
        this.activity = new WeakReference<>(activity);
        this.longitude = longitude;
        this.latitude = latitude;
    }

    @Override
    public void run() {
        // Translates coordinates into address in a background thread
        Address address = null;

        // Hold reference to a Geocoder to translate coordinates into human readable addresses
        Geocoder geocoder;
        // Initialize the Geocoder
        geocoder = new Geocoder(activity.get());

        try {
            // Gets a maximum of 1 address from the Geocoder
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            // Check that the Geocoder has obtained at least 1 address
            if ((addresses != null) && (addresses.size() > 0)) {
                address = addresses.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Update the interface of activity that launched the asynchronous task.
        StringBuilder display = new StringBuilder();

        // Check that the Geocoder got an address
        if ((address != null) && (address.getMaxAddressLineIndex() != -1)) {

            // Get the whole address (comma separated lines) in a single String
            display.append(address.getAddressLine(0));
            for (int i = 1; i <= address.getMaxAddressLineIndex(); i++) {
                display.append(", ").append(address.getAddressLine(i));
            }
        }
        // If no address available then show a message saying so
        else {
            display.append(activity.get().getResources().getString(R.string.geocoder_not_available));
        }
        if (activity.get() != null) {
            // Update the user interface
            activity.get().runOnUiThread(() -> activity.get().tvAddress.setText(display.toString()));
        }

    }
}