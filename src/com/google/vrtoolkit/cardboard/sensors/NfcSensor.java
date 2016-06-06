package com.google.vrtoolkit.cardboard.sensors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.os.Handler;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardDeviceParams;
import com.google.vrtoolkit.cardboard.PermissionUtils;

public class NfcSensor {
	private static final String TAG = "NfcSensor";
	private static final int MAX_CONNECTION_FAILURES = 1;
	private static final long NFC_POLLING_INTERVAL_MS = 250L;
	private static NfcSensor sInstance;
	private final Context context;
	private final NfcAdapter nfcAdapter;
	private final Object tagLock;
	private final List<ListenerHelper> listeners;
	private BroadcastReceiver nfcBroadcastReceiver;
	private IntentFilter[] nfcIntentFilters;
	private Ndef currentNdef;
	private Tag currentTag;
	private boolean currentTagIsCardboard;
	private Timer nfcDisconnectTimer;
	private int tagConnectionFailures;

	public static NfcSensor getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new NfcSensor(context);
		}

		return sInstance;
	}

	private NfcSensor(Context context) {
		this.context = context.getApplicationContext();
		this.listeners = new ArrayList();
		this.tagLock = new Object();

		if (PermissionUtils.hasPermission(context, "android.permission.NFC"))
			this.nfcAdapter = NfcAdapter.getDefaultAdapter(context);
		else {
			this.nfcAdapter = null;
		}

		if (this.nfcAdapter == null) {
			return;
		}

		this.nfcBroadcastReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				NfcSensor.this.onNfcIntent(intent);
			}
		};
	}

	public void addOnCardboardNfcListener(OnCardboardNfcListener listener) {
		if (listener == null) {
			return;
		}

		synchronized (this.listeners) {
			IntentFilter ndefIntentFilter;
			if (this.listeners.isEmpty()) {
				ndefIntentFilter = new IntentFilter(
						"android.nfc.action.NDEF_DISCOVERED");
				ndefIntentFilter
						.addAction("android.nfc.action.TECH_DISCOVERED");
				ndefIntentFilter.addAction("android.nfc.action.TAG_DISCOVERED");
				this.nfcIntentFilters = new IntentFilter[] { ndefIntentFilter };

				this.context.registerReceiver(this.nfcBroadcastReceiver,
						ndefIntentFilter);
			}

			for (ListenerHelper helper : this.listeners) {
				if (helper.getListener() == listener) {
					return;
				}
			}

			this.listeners.add(new ListenerHelper(listener, new Handler()));
		}
	}

	public void removeOnCardboardNfcListener(OnCardboardNfcListener listener) {
		if (listener == null) {
			return;
		}

		synchronized (this.listeners) {
			for (ListenerHelper helper : this.listeners) {
				if (helper.getListener() == listener) {
					this.listeners.remove(helper);
					break;
				}

			}

			if ((this.nfcBroadcastReceiver != null)
					&& (this.listeners.isEmpty()))
				this.context.unregisterReceiver(this.nfcBroadcastReceiver);
		}
	}

	public boolean isNfcSupported() {
		return this.nfcAdapter != null;
	}

	public boolean isNfcEnabled() {
		return (isNfcSupported()) && (this.nfcAdapter.isEnabled());
	}

	public boolean isDeviceInCardboard() {
		synchronized (this.tagLock) {
			return this.currentTagIsCardboard;
		}
	}

	public NdefMessage getTagContents() {
		synchronized (this.tagLock) {
			return this.currentNdef != null ? this.currentNdef
					.getCachedNdefMessage() : null;
		}
	}

	public NdefMessage getCurrentTagContents() throws TagLostException,
			IOException, FormatException {
		synchronized (this.tagLock) {
			return this.currentNdef != null ? this.currentNdef.getNdefMessage()
					: null;
		}
	}

	public int getTagCapacity() {
		synchronized (this.tagLock) {
			if (this.currentNdef == null) {
				throw new IllegalStateException("No NFC tag");
			}

			return this.currentNdef.getMaxSize();
		}
	}

	
	public void onResume(Activity activity) {
		if (!isNfcEnabled()) {
			return;
		}

		Intent intent = new Intent("android.nfc.action.NDEF_DISCOVERED");
		intent.setPackage(activity.getPackageName());

		PendingIntent pendingIntent = PendingIntent.getBroadcast(this.context,
				0, intent, 0);
		this.nfcAdapter.enableForegroundDispatch(activity, pendingIntent,
				this.nfcIntentFilters, (String[][]) null);
	}

	public void onPause(Activity activity) {
		if (!isNfcEnabled()) {
			return;
		}

		this.nfcAdapter.disableForegroundDispatch(activity);
	}

	public void onNfcIntent(Intent intent) {
		if (!isNfcEnabled() || intent == null
				|| !nfcIntentFilters[0].matchAction(intent.getAction())) {
			return;
		} else {
//			onNewNfcTag((Tag) intent
//					.getParcelableExtra("android.nfc.extra.TAG"));
			return;
		}
	}

	private void closeCurrentNfcTag() {
		if (this.nfcDisconnectTimer != null) {
			this.nfcDisconnectTimer.cancel();
		}

		if (this.currentNdef == null) {
			return;
		}

		try {
			this.currentNdef.close();
		} catch (IOException e) {
			Log.w("NfcSensor", e.toString());
		}

		this.currentTag = null;
		this.currentNdef = null;
		this.currentTagIsCardboard = false;
	}

	private void sendDisconnectionEvent() {
		synchronized (this.listeners) {
			for (ListenerHelper listener : this.listeners)
				listener.onRemovedFromCardboard();
		}
	}

	private boolean isCardboardNdefMessage(NdefMessage message) {
		if (message == null) {
			return false;
		}

		for (NdefRecord record : message.getRecords()) {
			if (isCardboardNdefRecord(record)) {
				return true;
			}
		}

		return false;
	}

	private boolean isCardboardNdefRecord(NdefRecord record) {
		if (record == null) {
			return false;
		}

		Uri uri = record.toUri();
		return (uri != null) && (CardboardDeviceParams.isCardboardUri(uri));
	}

	private static class ListenerHelper implements
			NfcSensor.OnCardboardNfcListener {
		private NfcSensor.OnCardboardNfcListener listener;
		private Handler handler;

		public ListenerHelper(NfcSensor.OnCardboardNfcListener listener,
				Handler handler) {
			this.listener = listener;
			this.handler = handler;
		}

		public NfcSensor.OnCardboardNfcListener getListener() {
			return this.listener;
		}

		public void onInsertedIntoCardboard(
				final CardboardDeviceParams deviceParams) {
			this.handler.post(new Runnable() {
				public void run() {
					NfcSensor.ListenerHelper.this.listener
							.onInsertedIntoCardboard(deviceParams);
				}
			});
		}

		public void onRemovedFromCardboard() {
			this.handler.post(new Runnable() {
				public void run() {
					NfcSensor.ListenerHelper.this.listener
							.onRemovedFromCardboard();
				}
			});
		}
	}

	public static abstract interface OnCardboardNfcListener {
		public abstract void onInsertedIntoCardboard(
				CardboardDeviceParams paramCardboardDeviceParams);

		public abstract void onRemovedFromCardboard();
	}
}