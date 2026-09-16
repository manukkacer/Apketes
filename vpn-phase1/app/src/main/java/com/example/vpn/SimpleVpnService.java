package com.example.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.util.Log;


public class SimpleVpnService extends VpnService {
    private static final String TAG = "SimpleVpnService";
    private Thread mVpnThread;
    private volatile boolean mRunning = false;
    
    // Statistics counters (static for easy access from UI)
    private static long packetsReceived = 0;
    private static long bytesReceived = 0;
    private static long packetsSent = 0;
    private static long bytesSent = 0;
    private static long packetsDropped = 0;
    
    // Filtering rules (simplified for Phase 2)
    private static final boolean DROP_INBOUND_HTTP = false; // Drop incoming HTTP packets for demo
    private static final int LOG_EVERY_N_PACKETS = 100; // Log stats every 100 packets

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VPN Service created");
        resetStatistics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "VPN Service started");
        startVpn();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "VPN Service destroyed");
        stopVpn();
        logFinalStatistics();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "VPN Service bound");
        return null;
    }

    private void startVpn() {
        mRunning = true;
        mVpnThread = new Thread(new VpnRunnable());
        mVpnThread.start();
    }

    private void stopVpn() {
        mRunning = false;
        if (mVpnThread != null) {
            mVpnThread.interrupt();
            try {
                mVpnThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while stopping VPN thread", e);
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    public static void resetStatistics() {
        packetsReceived = 0;
        bytesReceived = 0;
        packetsSent = 0;
        bytesSent = 0;
        packetsDropped = 0;
        Log.i(TAG, "Statistics reset");
    }

    public static long getPacketsReceived() {
        return packetsReceived;
    }

    public static long getBytesReceived() {
        return bytesReceived;
    }

    public static long getPacketsSent() {
        return packetsSent;
    }

    public static long getBytesSent() {
        return bytesSent;
    }

    public static long getPacketsDropped() {
        return packetsDropped;
    }

    private static void logStatistics() {
        Log.i(TAG, String.format(
                "VPN Statistics - RX: %d packets (%d bytes), TX: %d packets (%d bytes), Dropped: %d packets",
                packetsReceived, bytesReceived, packetsSent, bytesSent, packetsDropped));
    }

    private void logFinalStatistics() {
        Log.i(TAG, "=== FINAL VPN STATISTICS ===");
        logStatistics();
    }

    /**
     * Simple packet filtering logic for Phase 2 demo
     * @param packet The packet data
     * @param length Length of packet data
     * @return true if packet should be processed, false if dropped
     */
    private boolean shouldProcessPacket(byte[] packet, int length) {
        // For demonstration, we'll implement a simple filter:
        // Drop incoming HTTP packets (port 80) if enabled
        if (DROP_INBOUND_HTTP && length >= 20) { // Minimum IP header size
            try {
                // Parse IP header (simplified)
                int versionAndHeaderLength = packet[0] & 0xFF;
                int headerLength = (versionAndHeaderLength & 0x0F) * 4; // IHL * 4
                int protocol = packet[9] & 0xFF; // Protocol field
                
                if (protocol == 6 && headerLength + 20 <= length) { // TCP protocol
                    // Parse TCP header to get destination port
                    int srcPort = ((packet[headerLength] & 0xFF) << 8) | (packet[headerLength + 1] & 0xFF);
                    int dstPort = ((packet[headerLength + 2] & 0xFF) << 8) | (packet[headerLength + 3] & 0xFF);
                    
                    // Drop if destination port is 80 (HTTP)
                    if (dstPort == 80) {
                        Log.d(TAG, "Dropping incoming HTTP packet (port 80)");
                        return false;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                // If we can't parse properly, let it through
                Log.d(TAG, "Could not parse packet for filtering, allowing through");
            }
        }
        return true;
    }

    private class VpnRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "VPN thread started");
            
            var vpnInterface = null;
            try {
                // Configure and establish the VPN interface
                VpnService.Builder builder = new VpnService.Builder();
                
                // Minimum viable configuration - just enough to establish tunnel
                builder.setMtu(1500)
                       .addAddress("10.0.0.1", 24)
                       .addRoute("0.0.0.0", 0)
                       .addDnsServer("8.8.8.8")
                       .setSession("SimpleVPN")
                       .setBlocking(true); // Blocking mode for simplicity
                
                // Establish the VPN interface
                vpnInterface = builder.establish();
                
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface");
                    return;
                }
                
                Log.i(TAG, "VPN interface established");
                
                // Simple packet processing loop with statistics and filtering
                byte[] packet = new byte[1500];
                while (mRunning && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Read packet from VPN interface
                        int length = vpnInterface.read(packet);
                        if (length > 0) {
                            packetsReceived++;
                            bytesReceived += length;
                            
                            Log.d(TAG, "Received packet of length: " + length);
                            
                            // Apply filtering logic
                            if (shouldProcessPacket(packet, length)) {
                                // For minimum viable tunnel with statistics, echo packet back
                                // In a real implementation, this would forward to actual network
                                vpnInterface.write(packet, 0, length);
                                packetsSent++;
                                bytesSent += length;
                                Log.d(TAG, "Echoed packet back");
                            } else {
                                packetsDropped++;
                                Log.d(TAG, "Packet dropped due to filtering");
                            }
                            
                            // Log statistics periodically
                            if (packetsReceived % LOG_EVERY_N_PACKETS == 0) {
                                logStatistics();
                            }
                        }
                    } catch (Exception e) {
                        if (mRunning) {
                            Log.e(TAG, "Error in VPN tunnel loop", e);
                        }
                        break;
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize VPN interface", e);
            } finally {
                // Clean up
                if (vpnInterface != null) {
                    try {
                        vpnInterface.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing VPN interface", e);
                    }
                }
                Log.i(TAG, "VPN thread stopped");
            }
        }
    }
}
