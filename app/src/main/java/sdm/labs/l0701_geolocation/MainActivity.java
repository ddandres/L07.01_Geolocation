/*
 * Copyright (c) 2016. David de Andrés and Juan Carlos Ruiz, DISCA - UPV, Development of apps for mobile devices.
 */

package sdm.labs.l0701_geolocation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

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

        switch (view.getId()) {

            // Android Location Framework
            case R.id.bLocationFramework:
                intent = new Intent(this, LocationActivity.class);
                intent.putExtra("location_framework", LocationActivity.ANDROID_LOCATION_FRAMEWORK);
                break;

            // Google Location API
            case R.id.bGoogleLocation:
                intent = new Intent(this, LocationActivity.class);
                intent.putExtra("location_framework", LocationActivity.GOOGLE_LOCATION_API);
                break;
        }
        // Start the activity
        startActivity(intent);
    }
}
