package com.example.csc2231_project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.AsyncTask;
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
	
	private static final String SERVER = "192.168.74.140";
	private static final String AUDIO_PORT = "4444";
	private static Socket audioSocket = null;
	private static PrintWriter audioWrite;
	
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
        
        String[] params = {SERVER, AUDIO_PORT};
        new ServerConnectTask().execute(params);
        
        id = "A2D4A2D4";
        idByte = id.getBytes();
        time = System.currentTimeMillis();
        timeByte = getBytes(time);
    	audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
    	packetData = new byte[idByte.length + timeByte.length + audioBufferSize];
    	
        txt = (TextView) findViewById(R.id.record_text);
        recordButton = (ToggleButton) findViewById(R.id.record_button);
        recordButton.setEnabled(false);
    }
    
	private class ServerConnectTask extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... params) {
			// TODO Auto-generated method stub
			try {
			    JSONObject connectInfo = new JSONObject(pingServer());
				audioSocket = new Socket(params[0], connectInfo.getInt("port"));
			} catch (NumberFormatException e) {
				Log.e(TAG, "error in NumberFormatException");
				e.printStackTrace();
			} catch (UnknownHostException e) {
			 	Log.e(TAG, "error in UnknownHost");
			 	e.printStackTrace();
			} catch (IOException e) {
			 	Log.e(TAG, "error in IOException");
			 	e.printStackTrace();
			} catch (JSONException e) {
				Log.e(TAG, "JSON IS AWFULLL");
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			recordButton.setEnabled(true);
		}
	}
    
    private String pingServer() {

    	StringBuilder builder = new StringBuilder();
    	HttpClient client = new DefaultHttpClient();
    	HttpGet get = new HttpGet("http://www.cs.toronto.edu/~jdavid/csc2231_site/connect");
		
    	try {
    		HttpResponse response = client.execute(get);
    		StatusLine status = response.getStatusLine();
    		int code = status.getStatusCode();
    		if (code == 200) {
    			HttpEntity entity = response.getEntity();
    			InputStream content = entity.getContent();
    			BufferedReader reader = new BufferedReader(new InputStreamReader(content));
    			String line;
    			while((line = reader.readLine()) != null) {
    				builder.append(line);
    			}
    			reader.close();
    			content.close();
    		} else {
    			Log.e(TAG, "Error: could not get connect JSON");
    		}

		} catch (ClientProtocolException e) {
			Log.e(TAG, "ClientProtocolException in serverConnect");
		} catch (IOException e) {
			Log.e(TAG, "IOException in serverConnect");
		}
    	
    	return builder.toString();
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
    		audioWrite.println("STOP");
    		audioWrite.close();
    		try {
				audioSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
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
    	try {
			audioWrite = new PrintWriter(audioSocket.getOutputStream(), true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	if(audioRecorder != null) {
    		while(currentlyRecording) {
    			read = audioRecorder.read(audio_data, 0, audioBufferSize);
    			
    			if(read != AudioRecord.ERROR_INVALID_OPERATION) {
    				System.arraycopy(idByte, 0, packetData, 0, idByte.length);
    				timeByte = getBytes(System.currentTimeMillis());
    				System.arraycopy(timeByte, 0, packetData, idByte.length, timeByte.length);
    				System.arraycopy(audio_data, 0, packetData, idByte.length + timeByte.length, audio_data.length);
    				
    				// TODO: change this code so it actually does something useful
    				String output = "";
    				for(int b = 0; b < 30; b++) {
    					output += packetData[b];
    					if(b == idByte.length + timeByte.length - 1) {
    						output += " | ";
    					} else {
    						output += ", ";
    					}
    				}
    				output += "etc.\n";
    				
    				audioWrite.println(output);
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