/*
 * Copyright (c) 2016. David de Andrés and Juan Carlos Ruiz, DISCA - UPV, Development of apps for mobile devices.
 */

package dadm.labs.l0701_geolocation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Gives access to two different geolocation frameworks to get the current location of the device:
 * Android Location Framework
 * Google Location API
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * Starts the LocationActivity passing as parameter the location framework to be used.
     */
    public void buttonClicked(View view) {
        Intent intent = null;

        final int clickedButton = view.getId();
        if (clickedButton == R.id.bLocationFramework) {
            // Android Location Framework
            intent = new Intent(this, LocationActivity.class);
            intent.putExtra("location_framework", LocationActivity.ANDROID_LOCATION_FRAMEWORK);
        } else if (clickedButton == R.id.bGoogleLocation) {
            // Google Location API
            intent = new Intent(this, LocationActivity.class);
            intent.putExtra("location_framework", LocationActivity.GOOGLE_LOCATION_API);
        }
        // Start the activity
        startActivity(intent);
    }
}
