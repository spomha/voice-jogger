package com.example.voicejogger;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int port = 50005;
    private String ip_addr = "192.168.1.227";
    private String sco_state_pattern = "";

    AudioRecord recorder;

    private int sampleRate = 16000 ; // 44100 for music
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize;
    private boolean status = false;

    // Bluetooth Receiver
    private final BroadcastReceiver mBluetoothScoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            sco_state_pattern = sco_state_pattern + state;
            Log.d("mBluetoothScoReceiver", "Bluetooth State Pattern: " + sco_state_pattern);

            if (sco_state_pattern.equals("021")) {
                // Start recording audio
                Log.d("mBluetoothScoReceiver","Bluetooth headset connected.\n" +
                        " Starting streaming from Bluetooth receiver");
                // Hide Bluetooth icon
                ImageView imageView = (ImageView) findViewById(R.id.image_bluetooth);
                imageView.setVisibility(View.VISIBLE);
                startStreaming();
            }
            else if(sco_state_pattern.equals("020"))
            {
                Log.d("mBluetoothScoReceiver","Bluetooth headset is not connected. \n" +
                        " Using inbuilt microphone");
                startStreaming();
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        checkPermission();

        ImageButton settingButton = (ImageButton) findViewById(R.id.button_settings);
        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.togglebutton_startstop);

        settingButton.setOnClickListener(settingListener);
        toggleButton.setOnCheckedChangeListener(toggleListener);

        // Add default values to shared preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains("port")) {
            editor.putInt("port", port);
        }
        if (!preferences.contains("rate")) {
            editor.putInt("rate", sampleRate);
        }
        if (!preferences.contains("ip")) {
            editor.putString("ip", ip_addr);
        }
        editor.apply();

    }

    public void checkPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Get data from settings activity
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String get_ip = preferences.getString("ip", "");
        int get_port = preferences.getInt("port", -1);
        int get_rate = preferences.getInt("rate", -1);

        if (get_port != -1)
        {
            port = get_port;
        }

        if (get_rate != -1)
        {
            sampleRate = get_rate;
        }

        if(!get_ip.equals(""))
        {
            ip_addr = get_ip;
        }

        Log.d("onResume", "Server Port set to: " + port);
        Log.d("onResume", "Server IP set to: " + ip_addr);
        Log.d("onResume", "Audio sampling rate set to: " + sampleRate);

    }

    private final CompoundButton.OnCheckedChangeListener toggleListener = (compoundButton, isChecked) -> {
        if(isChecked)
        {
            Log.d("onCheckedChanged","Check ON");
            checkPermission();
            startTransmission();
        }
        else
        {
            Log.d("onCheckedChanged","Check OFF");
            stopTransmission();
            // Hide Bluetooth icon
            ImageView imageView = (ImageView) findViewById(R.id.image_bluetooth);
            imageView.setVisibility(View.INVISIBLE);
        }
    };

    private final OnClickListener settingListener = arg0 -> {
        // Navigate to setting activity
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    };

    private void startTransmission()
    {
        if (!status)
        {
            status = true;
            IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            Intent intent = registerReceiver(mBluetoothScoReceiver, intentFilter);
            if (intent == null) {
                Log.e("startTransmission", "Failed to register bluetooth sco receiver...");
                return;
            }

            // Ensure the SCO audio connection stays active in case the
            // current initiator stops it.
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.startBluetoothSco();

        }
    }

    private void stopTransmission()
    {
        status = false;
        sco_state_pattern = "";
        try {
            unregisterReceiver(mBluetoothScoReceiver);
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.stopBluetoothSco();
        }
        catch (IllegalArgumentException e)
        {
            Log.d("stopTransmission", "Receiver already unregistered");
        }

        if (recorder != null){
            recorder.release();
            Log.d("stopTransmission","Recorder released");
        }
    }

    public void startStreaming() {

        minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);


        Thread streamThread = new Thread(() -> {
            try {

                DatagramSocket socket = new DatagramSocket();
                Log.d("startStreaming", "Socket Created");

                byte[] buffer = new byte[minBufSize];

                Log.d("startStreaming","Buffer created of size " + minBufSize);
                DatagramPacket packet;

                final InetAddress destination = InetAddress.getByName(ip_addr);
                Log.d("startStreaming", "Address retrieved");


                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,minBufSize*10);
                Log.d("startStreaming", "Recorder initialized");

                recorder.startRecording();


                while(status) {


                    //reading data from MIC into buffer
                    minBufSize = recorder.read(buffer, 0, buffer.length);

                    //putting buffer in the packet
                    packet = new DatagramPacket (buffer,buffer.length,destination,port);

                    socket.send(packet);
                    System.out.println("MinBufferSize: " +minBufSize);


                }



            } catch(UnknownHostException e) {
                Log.e("startStreaming", "UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("startStreaming", "IOException");
            }
        });
        streamThread.start();
    }
}