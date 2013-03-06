package com.example.csc2231_project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

	/* Log tag */
	private static final String TAG = "CSC2231";

	/* Microphone information and recording variables */
	private static final int MIC = AudioSource.MIC;
	private static int sampleRate;
	private static int channel;
	private static int encoding;
	private AudioRecord audioRecorder = null;
	private Thread recordThread = null;
	private boolean currentlyRecording = false;
	private int audioBufferSize;

	/* connection and socket information 
	 * currently the IP is the external for my work machine
	 * */
	private static final String SERVER = "192.168.74.140";
	private static final String CONNECT_PORT = "2444";
	private static int audioPort;
	private static DatagramSocket audioSocket;

	/* Byte arrays that get sent in the packet */
	private static int id;
	private static byte[] idByte;
	private static byte[] timeByte;
	private static long count = 0;
	private static byte[] sizeByte;
	private byte[] packetData;

	/* Android view objects */
	private ToggleButton recordButton;
	private TextView txt;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Initialize layout
		super.onCreate(savedInstanceState);        
		setContentView(R.layout.activity_main);       

		// Connect to the server
		new ServerConnectTask().execute();

		// Initialize the views we will manipulate later on
		txt = (TextView) findViewById(R.id.record_text);
		recordButton = (ToggleButton) findViewById(R.id.record_button);
		recordButton.setEnabled(false); // until we connect
	}

	/* ServerConnectTask
	 * async connection to the server, gets the JSON and sets various parameters
	 * on completion, initialize the audio buffer size, packet data, and
	 * enable the recording button. we did it!
	 */
	private class ServerConnectTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			try {
				JSONObject connectInfo = new JSONObject(pingServer());
				id = connectInfo.getInt("Sessid");
				audioPort = connectInfo.getInt("Port");
				
				JSONObject audioInfo = connectInfo.getJSONObject("AudioInfo");
				sampleRate = audioInfo.getInt("SampleRate");
				
				switch(audioInfo.getInt("BytesPerSample")) {
					case 16:
						encoding = AudioFormat.ENCODING_PCM_16BIT;
						break;
					case 8:
						encoding = AudioFormat.ENCODING_PCM_8BIT;
						break;
					default:
						encoding = AudioFormat.ENCODING_DEFAULT;
						break;
				}
				
				switch(audioInfo.getInt("Channels")) {
					case 1:
						channel = AudioFormat.CHANNEL_IN_MONO;
						break;
					case 2:
						channel = AudioFormat.CHANNEL_IN_STEREO;
						break;
					default:
						channel = AudioFormat.CHANNEL_IN_DEFAULT;
						break;
				}
			} catch (NumberFormatException e) {
				Log.e(TAG, "error: NumberFormatException");
				e.printStackTrace();
			} catch (JSONException e) {
				Log.e(TAG, "JSON IS AWFULLL");
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, encoding);
			packetData = new byte[4 + // idByte.length
			                      8 + // timeByte.length
			                      8 + // num samples in (count)
			                      4 + // size of audio buffer
			                      audioBufferSize];
			recordButton.setEnabled(true);
		}
	}

	/* pingServer
	 * Pings /connect to get the JSON information
	 */
	private String pingServer() {

		StringBuilder builder = new StringBuilder();
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet("http://" + SERVER + ":" + CONNECT_PORT + "/connect");

		try {
			HttpResponse response = client.execute(get);
			StatusLine status = response.getStatusLine();
			if (status.getStatusCode() == 200) {
				InputStream content = response.getEntity().getContent();
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
			// TODO: Something better here?
			Log.e(TAG, "ClientProtocolException in serverConnect");
		} catch (IOException e) {
			// TODO: Something better here?
			Log.e(TAG, "IOException in serverConnect");
		}

		return builder.toString();
	}
	
	// TODO: would be nice to pull some of this out into functions, like prepare_recording
	/* recordButtonClick
	 * Event handler for pressing the toggle button. If this name ever changes, make
	 * sure to update res/layout/activity_main.xml
	 */
	public void recordButtonClick(View view) {
		if(recordButton.isChecked()) {
			writeBytes();			
			prepareRecording();

			recordThread = new Thread(new Runnable() {
				@Override
				public void run() {
					writeAudio();
				}    			
			}, "Recording Thread");

			try {
				audioSocket = new DatagramSocket();
				audioRecorder.startRecording();
				recordThread.start();
			} catch (SocketException e) {
				// TODO Handle this better
				e.printStackTrace();
			}

			txt.setText(R.string.record_stop);
		} else {
			currentlyRecording = false;
			audioSocket.close();
			audioRecorder.stop();
			audioRecorder.release();
			audioRecorder = null;
			recordThread = null;

			txt.setText(R.string.record_start);
		}
	}
	
	/* 
	 * Functions to reduce the noise in recordButtonClock
	 */
	private void writeBytes() {
		idByte = getBytes(id);
		sizeByte = getBytes(audioBufferSize);
		System.arraycopy(idByte, 0, packetData, 0, idByte.length);
		System.arraycopy(sizeByte, 0, packetData, 8 + 8 + 8, sizeByte.length);
	}

	private void prepareRecording() {
		currentlyRecording = true;
		audioRecorder = new AudioRecord(MIC, sampleRate, channel, encoding, audioBufferSize);
	}

	/* writeAudio
	 * Gets each audio byte and packages it up with the metadata.
	 * Then off it goes to the server!
	 */
	public void writeAudio() {
		byte[] audioData = new byte[audioBufferSize];
		int read = 0;
		
		/* If we want nano time, this can be uncommented */
		// long start = System.currentTimeMillis() * 1000000; 
		// long startNano = System.nanoTime();
		if(audioRecorder != null) {
			while(currentlyRecording) {
				read = audioRecorder.read(audioData, 0, audioBufferSize);

				if(read != AudioRecord.ERROR_INVALID_OPERATION) {
					/* If we want nano time, this can be uncommented
					 * Currently should work using milli time */
					// long now = System.nanoTime();
					// long newTime = start + (now - startNano);
					// timeByte = getBytes(newTime);
					timeByte = getBytes(System.currentTimeMillis());
					System.arraycopy(timeByte, 0, packetData, idByte.length, timeByte.length);

					byte[] countByte = getBytes(count);
					System.arraycopy(countByte, 0, packetData, idByte.length + timeByte.length, countByte.length);
					count += audioData.length; // TODO: count correctly

					System.arraycopy(audioData, 0, packetData, idByte.length + timeByte.length + countByte.length + sizeByte.length, audioData.length);
					
					try {
						DatagramPacket packet = new DatagramPacket(packetData, packetData.length, InetAddress.getByName(SERVER), audioPort);
						audioSocket.send(packet);
					} catch (IOException e) {
						// This IOException typically only occurs when we've stopped the stream and it's trying to send the last byte
						// It's okay that we lose it, not terribly crucial
						// For now, just log it!
						Log.e(TAG, "IOException trying to send packet in write_audio.");
					}
				}
			}
		}
	}

	/* getBytes functions
	 * Turns an int or long into a byte array for the packet data
	 */
	public byte[] getBytes(int val)
	{
		ByteBuffer int_buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
		return int_buffer.putInt(val).array();
	}

	public byte[] getBytes(long val)
	{
		ByteBuffer long_buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
   	    return long_buffer.putLong(val).array();
	}
}