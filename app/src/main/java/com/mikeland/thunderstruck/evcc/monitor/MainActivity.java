package com.mikeland.thunderstruck.evcc.monitor;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.mikeland.thunderstruck.evcc.monitor.controller.EvccGatewayClient;
import com.mikeland.thunderstruck.evcc.monitor.controller.EvccSessionLogger;
import com.mikeland.thunderstruck.evcc.monitor.view.ChargingTabViewController;

public class MainActivity extends AppCompatActivity {

    private ChargingTabViewController chargingController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize session logging to storage
        EvccSessionLogger.getInstance().init(this);

        View rootView = findViewById(R.id.main_container);
        chargingController = new ChargingTabViewController(this, rootView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chargingController != null) {
            chargingController.onTabSelected();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chargingController != null) {
            chargingController.onTabDeselected();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EvccGatewayClient.getInstance().disconnectWebSocket();
    }
}
