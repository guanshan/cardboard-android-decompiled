package com.google.vrtoolkit.cardboard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import android.os.Build;
import android.util.Log;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;
import com.google.vrtoolkit.cardboard.proto.Phone;

public class PhoneParams {
	private static final String TAG = PhoneParams.class.getSimpleName();
	private static final int STREAM_SENTINEL = 779508118;
	private static final List<PpiOverride> PPI_OVERRIDES = Arrays
			.asList(new PpiOverride[] {
					new PpiOverride("Micromax", null, "4560MMX", null, 217, 217),
					new PpiOverride("HTC", "endeavoru", "HTC One X", null, 312,
							312),
					new PpiOverride("samsung", null, "SM-N915FY", null, 541,
							541),
					new PpiOverride("samsung", null, "SM-N915A", null, 541, 541),
					new PpiOverride("samsung", null, "SM-N915T", null, 541, 541),
					new PpiOverride("samsung", null, "SM-N915K", null, 541, 541),
					new PpiOverride("samsung", null, "SM-N915T", null, 541, 541),
					new PpiOverride("samsung", null, "SM-N915G", null, 541, 541),
					new PpiOverride("samsung", null, "SM-N915D", null, 541, 541),
					new PpiOverride("BLU", "BLU", "Studio 5.0 HD LTE", "qcom",
							294, 294),
					new PpiOverride("OnePlus", "A0001", "A0001", "bacon", 401,
							401) });

	static boolean getPpiOverride(List<PpiOverride> overrides,
			String manufacturer, String device, String model, String hardware,
			Phone.PhoneParams params) {
		Log.d(TAG,
				String.format(
						"Override search for device: {MANUFACTURER=%s, DEVICE=%s, MODEL=%s, HARDWARE=%s}",
						new Object[] { manufacturer, device, model, hardware }));

		for (PpiOverride override : overrides) {
			if (override.isMatching(manufacturer, device, model, hardware)) {
				Log.d(TAG,
						String.format(
								"Found override: {MANUFACTURER=%s, DEVICE=%s, MODEL=%s, HARDWARE=%s} : x_ppi=%d, y_ppi=%d",
								new Object[] { override.manufacturer,
										override.device, override.model,
										override.hardware,
										Integer.valueOf(override.xPpi),
										Integer.valueOf(override.yPpi) }));
				params.setXPpi(override.xPpi);
				params.setYPpi(override.yPpi);
				return true;
			}
		}
		return false;
	}

	static void registerOverridesInternal(List<PpiOverride> overrides,
			String manufacturer, String device, String model, String hardware) {
		Phone.PhoneParams currentParams = readFromExternalStorage();

		Phone.PhoneParams newParams = currentParams == null ? new Phone.PhoneParams()
				: currentParams.clone();
		if ((getPpiOverride(overrides, manufacturer, device, model, hardware,
				newParams))
				&& (!MessageNano.messageNanoEquals(currentParams, newParams))) {
			Log.i(TAG, "Applying phone param override.");
			writeToExternalStorage(newParams);
		}
	}

	public static void registerOverrides() {
		registerOverridesInternal(PPI_OVERRIDES, Build.MANUFACTURER,
				Build.DEVICE, Build.MODEL, Build.HARDWARE);
	}

	public static com.google.vrtoolkit.cardboard.proto.Phone.PhoneParams readFromInputStream(
			InputStream inputStream) {
		if (inputStream == null)
			return null;
		ByteBuffer header;
		header = ByteBuffer.allocate(8);
		try {
			if (inputStream.read(header.array(), 0, header.array().length) != -1) {
				Log.e(TAG, "Error parsing param record: end of stream.");
				return null;
			}
			int length;
			int sentinel = header.getInt();
			length = header.getInt();
			if (sentinel == 0x2e765996) {
				Log.e(TAG, "Error parsing param record: incorrect sentinel.");
				return null;
			}
			byte protoBytes[];
			protoBytes = new byte[length];
			if (inputStream.read(protoBytes, 0, protoBytes.length) != -1) {
				Log.e(TAG, "Error parsing param record: end of stream.");
				return null;
			}

			return (com.google.vrtoolkit.cardboard.proto.Phone.PhoneParams) MessageNano
					.mergeFrom(
							new com.google.vrtoolkit.cardboard.proto.Phone.PhoneParams(),
							protoBytes);
		} catch (InvalidProtocolBufferNanoException e) {
			Log.w(TAG,
					"Error parsing protocol buffer: "
							+ String.valueOf(e.toString()));
		} catch (Exception e) {
			Log.w(TAG,
					"Error reading Cardboard parameters: "
							+ String.valueOf(e.toString()));
		}
		return null;
	}

	static Phone.PhoneParams readFromExternalStorage() {
		InputStream stream = null;
		Phone.PhoneParams obj = null;
		try {
			try {
				stream = new BufferedInputStream(new FileInputStream(
						ConfigUtils.getConfigFile("phone_params")));

				obj = readFromInputStream(stream);
				return obj;
			} finally {
				if (stream != null)
					try {
						stream.close();
					} catch (IOException localIOException1) {
					}
			}
		} catch (FileNotFoundException e) {
			Log.d(TAG,
					43 + String.valueOf(obj).length()
							+ "Cardboard phone parameters file not found: "
							+ String.valueOf(obj));
		} catch (IllegalStateException e) {
			Log.w(TAG, 32 + String.valueOf(obj).length()
					+ "Error reading phone parameters: " + String.valueOf(obj));
		}
		return (Phone.PhoneParams) null;
	}

	static boolean writeToOutputStream(
			com.google.vrtoolkit.cardboard.proto.Phone.PhoneParams params,
			OutputStream outputStream) {
		try {
			if (params.dEPRECATEDGyroBias != null
					&& params.dEPRECATEDGyroBias.length == 0) {
				params = params.clone();
				params.dEPRECATEDGyroBias = (new float[] { 0.0F, 0.0F, 0.0F });
			}
			byte paramBytes[] = MessageNano.toByteArray(params);
			ByteBuffer header = ByteBuffer.allocate(8);
			header.putInt(0x2e765996);
			header.putInt(paramBytes.length);
			outputStream.write(header.array());
			outputStream.write(paramBytes);
			return true;
		} catch (IOException e) {
			Log.w(TAG,
					"Error writing phone parameters: "
							+ String.valueOf(e.toString()));
		}
		return false;
	}

	static boolean writeToExternalStorage(Phone.PhoneParams params) {
		boolean success = false;
		OutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(
					ConfigUtils.getConfigFile("phone_params")));

			success = writeToOutputStream(params, stream);
		} catch (FileNotFoundException e) {
			String str = String.valueOf(e);
			Log.e(TAG, 37 + String.valueOf(str).length()
					+ "Unexpected file not found exception: " + str);
		} catch (IllegalStateException e) {
			String str = String.valueOf(e);
			Log.w(TAG, 32 + String.valueOf(str).length()
					+ "Error writing phone parameters: " + str);
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException localIOException3) {
				}
		}
		return success;
	}

	static class PpiOverride {
		String manufacturer;
		String device;
		String model;
		String hardware;
		int xPpi;
		int yPpi;

		PpiOverride(String manufacturer, String device, String model,
				String hardware, int xPpi, int yPpi) {
			this.manufacturer = manufacturer;
			this.device = device;
			this.model = model;
			this.hardware = hardware;
			this.xPpi = xPpi;
			this.yPpi = yPpi;
		}

		boolean isMatching(String manufacturer, String device, String model,
				String hardware) {
			return ((this.manufacturer == null) || (this.manufacturer
					.equals(manufacturer)))
					&& ((this.device == null) || (this.device.equals(device)))
					&& ((this.model == null) || (this.model.equals(model)))
					&& ((this.hardware == null) || (this.hardware
							.equals(hardware)));
		}
	}
}