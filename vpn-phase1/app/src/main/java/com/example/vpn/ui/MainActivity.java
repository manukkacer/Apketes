package com.example.vpn.ui;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.vpn.R;
import com.example.vpn.SimpleVpnService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PREPARE_VPN = 1;
    private static final int STATS_UPDATE_INTERVAL = 1000; // Update stats every second
    
    private Button startButton;
    private Button stopButton;
    private TextView statusTextView;
    private TextView statsTextView;
    private boolean vpnRunning = false;
    
    private Handler statsHandler = new Handler();
    private Runnable statsRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatisticsDisplay();
            statsHandler.postDelayed(this, STATS_UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        statusTextView = findViewById(R.id.statusTextView);
        statsTextView = findViewById(R.id.statsTextView);
        
        startButton.setOnClickListener(v -> startVpn());
        stopButton.setOnClickListener(v -> stopVpn());
        
        updateUI();
        // Start statistics updates
        statsHandler.postDelayed(statsRunnable, STATS_UPDATE_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statsHandler.removeCallbacks(statsRunnable);
    }

    private void startVpn() {
        if (vpnRunning) {
            Log.i(TAG, "VPN is already running");
            stopVpn();
        }
        
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            // Need permission
            startActivityForResult(intent, REQUEST_CODE_PREPARE_VPN);
        } else {
            // Already have permission
            onActivityResult(REQUEST_CODE_PREPARE_VPN, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PREPARE_VPN && resultCode == RESULT_OK) {
            Intent vpnIntent = new Intent(this, SimpleVpnService.class);
            startService(vpnIntent);
            vpnRunning = true;
            updateUI();
            Log.i(TAG, "VPN service started");
            // Reset statistics when starting new session
            SimpleVpnService.resetStatistics();
        } else {
            Log.e(TAG, "VPN permission not granted");
            statusTextView.setText("VPN permission denied");
        }
    }

    private void stopVpn() {
        Intent vpnIntent = new Intent(this, SimpleVpnService.class);
        stopService(vpnIntent);
        vpnRunning = false;
        updateUI();
        Log.i(TAG, "VPN service stopped");
    }

    private void updateUI() {
        if (vpnRunning) {
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusTextView.setText("VPN: Connected");
        } else {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusTextView.setText("VPN: Disconnected");
        }
    }

    private void updateStatisticsDisplay() {
        long rxPackets = SimpleVpnService.getPacketsReceived();
        long rxBytes = SimpleVpnService.getBytesReceived();
        long txPackets = SimpleVpnService.getPacketsSent();
        long txBytes = SimpleVpnService.getBytesSent();
        long dropped = SimpleVpnService.getPacketsDropped();
        
        String statsText = String.format(
                "Statistics:\n" +
                "RX: %d packets (%d bytes)\n" +
                "TX: %d packets (%d bytes)\n" +
                "Dropped: %d packets",
                rxPackets, rxBytes, txPackets, txBytes, dropped);
        
        statsTextView.setText(statsText);
    }
}
