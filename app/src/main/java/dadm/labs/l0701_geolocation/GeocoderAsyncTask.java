package dadm.labs.l0701_geolocation;

import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Custom asynchronous task to translate received coordinates into a human readable address.
 */
public class GeocoderAsyncTask extends AsyncTask<Double, Void, Address> {

    private WeakReference<LocationActivity> activity;

    GeocoderAsyncTask(LocationActivity activity) {
        this.activity = new WeakReference<>(activity);
    }

    /**
     * Translates coordinates into address in a background thread.
     */
    @Override
    protected Address doInBackground(Double... params) {

        // Hold reference to a Geocoder to translate coordinates into human readable addresses
        Geocoder geocoder;
        // Initialize the Geocoder
        geocoder = new Geocoder(activity.get());

        try {
            // Gets a maximum of 1 address from the Geocoder
            List<Address> addresses = geocoder.getFromLocation(params[0], params[1], 1);
            // Check that the Geocoder has obtained at least 1 address
            if ((addresses != null) && (addresses.size() > 0)) {
                return addresses.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update the interface of activity that launched the asynchronous task.
     */
    @Override
    protected void onPostExecute(Address address) {
        StringBuilder display = new StringBuilder();

        if (activity.get() != null) {
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
            // Update the user interface
            activity.get().tvAddress.setText(display.toString());
        }
    }
}
