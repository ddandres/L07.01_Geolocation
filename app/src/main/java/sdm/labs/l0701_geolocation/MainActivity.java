package sdm.labs.l0701_geolocation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void buttonClicked(View view) {
        Intent intent = null;
        switch(view.getId()) {
            case R.id.bLocationFramework:
                intent = new Intent(this, LocationActivity.class);
                intent.putExtra("system", LocationActivity.ANDROID_LOCATION_FRAMEWORK);
                break;
            case R.id.bGoogleLocation:
                intent = new Intent(this, LocationActivity.class);
                intent.putExtra("system", LocationActivity.GOOGLE_LOCATION);
                break;
        }
        startActivity(intent);
    }
}
