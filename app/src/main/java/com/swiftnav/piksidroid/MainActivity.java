/*
 * # Copyright (C) 2011-2015 Swift Navigation Inc.
 * # Contact: Vlad Ungureanu <vvu@vdev.ro>
 * #
 * # This source is subject to the license found in the file 'LICENSE' which must
 * # be be distributed together with this source. All other rights reserved.
 * #
 * # THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND,
 * # EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * # WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A PARTICULAR PURPOSE.
 *
 */

package com.swiftnav.piksidroid;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.HashMap;
import java.util.Iterator;


public class MainActivity extends AppCompatActivity {
	public String TAG = "PiksiDroid";
	private static final String ACTION_USB_PERMISSION =
			"com.android.example.USB_PERMISSION";
	private FT_Device piksi = null;
	boolean mThreadIsStopped = true;
	static final int READBUF_SIZE  = 256;
	byte[] rbuf  = new byte[READBUF_SIZE];
	int mReadSize=0;
	Handler mHandler = new Handler();
	Thread mThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		registerReceiver(mUsbReceiver, filter);
		HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while(deviceIterator.hasNext()){
			UsbDevice device = deviceIterator.next();
			if ((device.getVendorId() == 0x403) && (device.getProductId() == 0x6014))
				mUsbManager.requestPermission(device, mPermissionIntent);
		}

		this.setupUI();
	}

	View.OnClickListener read_listen = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (piksi == null) {
				showToast("Piksi not connected!");
				return;
			}
			new Thread(mLoop).start();
		}
	};

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if(device != null){
							try {
								D2xxManager d2xx = D2xxManager.getInstance(context);
								int devCount = 0;
								devCount = d2xx.createDeviceInfoList(context);
								D2xxManager.FtDeviceInfoListNode[] devList = new D2xxManager.FtDeviceInfoListNode[devCount];
								d2xx.getDeviceInfoList(devCount, devList);
								piksi = d2xx.openByIndex(context, 0);
								if (piksi == null) {
									D2xxManager.D2xxException myException = new D2xxManager.D2xxException("Cannot open device!");
									throw myException;
								}
								if (!piksi.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE)) {
									D2xxManager.D2xxException myException = new D2xxManager.D2xxException("Cannot set 8,1,N!");
									throw myException;
								}
								if (!piksi.setBaudRate(Utils.baudrate)) {
									D2xxManager.D2xxException myException = new D2xxManager.D2xxException("Cannot set baudrate!!");
									throw myException;
								}
								piksi.stopInTask();
								piksi.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
								piksi.restartInTask();
								((Button)findViewById(R.id.read_button)).setEnabled(true);

							} catch (D2xxManager.D2xxException e) {
								Log.d(TAG, e.toString());
							}
						}
					}
					else {
						Log.d(TAG, "permission denied for device " + device);
					}
				}
			}
		}
	};

	private void showToast(final String message) {
		runOnUiThread(new Runnable() {
						  public void run() {
							  Context context = getApplicationContext();
							  CharSequence text = message;
							  int duration = Toast.LENGTH_SHORT;
							  Toast toast = Toast.makeText(context, text, duration);
							  toast.show();
						  }
					  }
		);
	}

	private Runnable mLoop = new Runnable() {
		@Override
		public void run() {
			int i;
			int readSize;
			mThreadIsStopped = false;
			while(true) {
				if(mThreadIsStopped) {
					break;
				}
				synchronized (piksi) {
					readSize = piksi.getQueueStatus();
					if(readSize > 0) {
						mReadSize = readSize;
						if(mReadSize > READBUF_SIZE) {
							mReadSize = READBUF_SIZE;
						}
						piksi.read(rbuf,mReadSize);

						mHandler.post(new Runnable() {
							@Override
							public void run() {
								Log.d(TAG, HexDump.dumpHexString(rbuf));
							}
						});

					}
				}
			}
		}
	};

	private void setupUI() {
		TabHost tabHost = (TabHost)findViewById(R.id.tabHost);
		tabHost.setup();


		TabHost.TabSpec tabSpec = tabHost.newTabSpec("Piksi");
		tabSpec.setContent(R.id.piksi);
		tabSpec.setIndicator("Piksi");
		tabHost.addTab(tabSpec);

		tabSpec = tabHost.newTabSpec("Tracking");
		tabSpec.setContent(R.id.tracking);
		tabSpec.setIndicator("Tracking");
		tabHost.addTab(tabSpec);

		tabSpec = tabHost.newTabSpec("Map");
		tabSpec.setContent(R.id.map);
		tabSpec.setIndicator("Map");
		tabHost.addTab(tabSpec);

		tabSpec = tabHost.newTabSpec("Observation");
		tabSpec.setContent(R.id.observation);
		tabSpec.setIndicator("Observation");
		tabHost.addTab(tabSpec);

		Button read_button = (Button)findViewById(R.id.read_button);
		read_button.setEnabled(false);
		read_button.setOnClickListener(read_listen);
	}
}
