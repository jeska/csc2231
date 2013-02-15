package com.example.csc2231_project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	
	private static final int MIC = AudioSource.MIC;
	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
	private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	
	private static final String SERVER = "127.0.0.1";
	private static final int CONNECT_PORT = 1234;
	private static final int AUDIO_PORT = 2345;
	private static Socket connectSocket = null;
	private static Socket audioSocket = null;
	
	private static String id;
	private static byte[] idByte;
	private static long time;
	private static byte[] timeByte;
	
	private ToggleButton recordButton;
	private TextView txt;
	
	private static final String TAG = "CSC2231";
	
	private AudioRecord audioRecorder = null;
	private Thread recordThread = null;
	private boolean currentlyRecording = false;
	private int audioBufferSize;
	private byte[] packetData;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.activity_main);
        
        serverConnect();
                
        id = "A2D4A2D4";
        idByte = id.getBytes();
        time = System.currentTimeMillis();
        timeByte = getBytes(time);
    	audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
    	packetData = new byte[idByte.length + timeByte.length + audioBufferSize];
    	
        txt = (TextView) findViewById(R.id.record_text);
        recordButton = (ToggleButton) findViewById(R.id.record_button);
    }

    /* @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    } */
    
    private void serverConnect() {
    	BufferedReader connectResponse;
    	PrintWriter connectWrite;
    	try {
    		connectSocket = new Socket(SERVER, CONNECT_PORT);
    		connectWrite = new PrintWriter(connectSocket.getOutputStream(), true);
    		connectResponse = new BufferedReader(new InputStreamReader(connectSocket.getInputStream()));
    		
    		connectWrite.println("");
    	} catch (IOException e) {
    		
    	} finally {
    		// connectResponse.close();
    		// connectWrite.close();
    		// connectSocket.close();
    	}
    }
    
    private void prepare_recording() {
    	
    	Log.v(TAG, "Buffer size: " + audioBufferSize);
    	audioRecorder = new AudioRecord(MIC, SAMPLE_RATE, CHANNEL, ENCODING, audioBufferSize);
    }

    public void record_button_click(View view) {
    	if(recordButton.isChecked()) {
    		currentlyRecording = true;

    		prepare_recording();
    		audioRecorder.startRecording();
    		recordThread = new Thread(new Runnable() {
				@Override
				public void run() {
					write_audio();
				}    			
    		}, "Recording Thread");
    		recordThread.start();
    		Log.v(TAG, "Trying to start recording...");
    		
    		txt.setText(R.string.record_stop);
    	} else {
    		Log.v(TAG, "Trying to stop recording...");
    		
    		currentlyRecording = false;
    		audioRecorder.stop();
        	audioRecorder.release();
        	audioRecorder = null;
        	recordThread = null;
        	
        	txt.setText(R.string.record_start);
        	
    	}
    }  
    
    public void write_audio() {
    	byte[] audio_data = new byte[audioBufferSize];
    	int read = 0;
    	
    	if(audioRecorder != null) {
    		while(currentlyRecording) {
    			read = audioRecorder.read(audio_data, 0, audioBufferSize);
    			
    			if(read != AudioRecord.ERROR_INVALID_OPERATION) {
    				System.arraycopy(idByte, 0, packetData, 0, idByte.length);
    				timeByte = getBytes(System.currentTimeMillis());
    				System.arraycopy(timeByte, 0, packetData, idByte.length, timeByte.length);
    				System.arraycopy(audio_data, 0, packetData, idByte.length + timeByte.length, audio_data.length);
    				
    				String output = "";
    				for(int b = 0; b < 30; b++) {
    					output += packetData[b];
    					if(b == idByte.length + timeByte.length - 1) {
    						output += " | ";
    					} else {
    						output += ", ";
    					}
    				}
    				output += "etc.";
    				
    				Log.v(TAG, output);
    			}
    		}
    	}
    }
    
    public byte[] getBytes(Long val)
	{
	    ByteBuffer buf = ByteBuffer.allocate(8);
	    buf.order(ByteOrder.BIG_ENDIAN);
	    buf.putLong(val);
	    return buf.array();
	}
}

/*audio_recorder = new MediaRecorder();
audio_recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
audio_recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
audio_recorder.setOutputFile(file_name);
audio_recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);*/        	
/*try {
audio_recorder.prepare();
} catch (IOException e) {
Log.e(TAG, "failed on preparing mic");
}*/

// audio_recorder.start();


// String file = set_file_name();
// String file = Environment.getExternalStorageDirectory().getAbsolutePath() + "/csc2231/temp.raw";
// Log.v(TAG, file);
// FileOutputStream recording = null;

// try {
//	recording = new FileOutputStream(file);
// } catch (FileNotFoundException e) {
// 	Log.e(TAG, "File not found exception in write_audio");
// 	e.printStackTrace();
// }



// try {
	// recording.write(audio_data);
	// Log.v(TAG, "Writing data");
// } catch (IOException e) {
// 	Log.e(TAG, "Error writing data in write_audio");
// }
// try {
// 	recording.close();
// } catch (IOException e) {
// 	Log.e(TAG, "Error closing file in write_audio");
// }
// }