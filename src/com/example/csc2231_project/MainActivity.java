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

	/* Microphone information and recording variables
	 * TODO: have this get information from server 
	 * */
	private static final int MIC = AudioSource.MIC;
	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
	private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
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
	private static long id;
	private static byte[] idByte;
	private static byte[] timeByte;
	private static long count;
	private static byte[] sizeByte;
	private byte[] packetData;

	/* Android view objects */
	private ToggleButton recordButton;
	private TextView txt;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);        
		setContentView(R.layout.activity_main);       

		new ServerConnectTask().execute();

		audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
		sizeByte = getBytes(audioBufferSize);

		txt = (TextView) findViewById(R.id.record_text);
		recordButton = (ToggleButton) findViewById(R.id.record_button);
		recordButton.setEnabled(false);
	}

	private class ServerConnectTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			try {
				JSONObject connectInfo = new JSONObject(pingServer());
				id = connectInfo.getLong("Sessid");
				audioPort = connectInfo.getInt("Port");
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
			idByte = getBytes(id);
			packetData = new byte[8 + // idByte.length
			                      8 + // timeByte.length
			                      8 + // num samples in
			                      4 + // size of audio buffer
			                      audioBufferSize];
			count = 231; // TODO: Not this!
			recordButton.setEnabled(true);
		}
	}

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
		} finally {
			if(builder.length() == 0) {
				builder.append("ERROR");
			}
		}

		return builder.toString();
	}

	private void prepare_recording() {
		audioRecorder = new AudioRecord(MIC, SAMPLE_RATE, CHANNEL, ENCODING, audioBufferSize);
	}

	// TODO: would be nice to pull some of this out into functions, like prepare_recording
	public void recordButtonClick(View view) {
		if(recordButton.isChecked()) {
			currentlyRecording = true;
			prepare_recording();
			idByte = getBytes(id);

			recordThread = new Thread(new Runnable() {
				@Override
				public void run() {
					write_audio();
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

	public void write_audio() {
		byte[] audio_data = new byte[audioBufferSize];
		int read = 0;
		try {
			if(audioRecorder != null) {
				while(currentlyRecording) {
					read = audioRecorder.read(audio_data, 0, audioBufferSize);

					if(read != AudioRecord.ERROR_INVALID_OPERATION) {
						// TODO: This write and the sizeByte only need to happen once. Figure out a place to move this.
						System.arraycopy(idByte, 0, packetData, 0, idByte.length);

						timeByte = getBytes(System.currentTimeMillis());
						System.arraycopy(timeByte, 0, packetData, idByte.length, timeByte.length);

						byte[] countByte = getBytes(count);
						System.arraycopy(countByte, 0, packetData, idByte.length + timeByte.length, countByte.length);

						System.arraycopy(sizeByte, 0, packetData, idByte.length + timeByte.length + countByte.length, sizeByte.length);

						System.arraycopy(audio_data, 0, packetData, idByte.length + timeByte.length + countByte.length + sizeByte.length, audio_data.length);

						// TODO: make sure this does all it needs to do!
						DatagramPacket packet = new DatagramPacket(packetData, packetData.length, InetAddress.getByName(SERVER), audioPort);
						audioSocket.send(packet);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public byte[] getBytes(int val)
	{
		ByteBuffer int_buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		return int_buffer.putInt(val).array();
	}

	public byte[] getBytes(long val)
	{
		ByteBuffer long_buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
		return long_buffer.putLong(val).array();
	}
}