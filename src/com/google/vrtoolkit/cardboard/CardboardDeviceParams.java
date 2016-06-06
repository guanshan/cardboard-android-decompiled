package com.google.vrtoolkit.cardboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.util.Base64;
import android.util.Log;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;
import com.google.vrtoolkit.cardboard.proto.CardboardDevice;
import com.google.vrtoolkit.cardboard.proto.CardboardDevice.DeviceParams;

public class CardboardDeviceParams {
	private static final String TAG = "CardboardDeviceParams";
	private static final String HTTP_SCHEME = "http";
	private static final String URI_HOST_GOOGLE_SHORT = "g.co";
	private static final String URI_HOST_GOOGLE = "google.com";
	private static final String URI_PATH_CARDBOARD_HOME = "cardboard";
	private static final String URI_PATH_CARDBOARD_CONFIG = "cardboard/cfg";
	private static final String URI_SCHEME_LEGACY_CARDBOARD = "cardboard";
	private static final String URI_HOST_LEGACY_CARDBOARD = "v1.0.0";
	private static final Uri URI_ORIGINAL_CARDBOARD_NFC = new Uri.Builder()
			.scheme(URI_PATH_CARDBOARD_HOME).authority(URI_HOST_LEGACY_CARDBOARD).build();

	private static final Uri URI_ORIGINAL_CARDBOARD_QR_CODE = new Uri.Builder()
			.scheme(HTTP_SCHEME).authority(URI_HOST_GOOGLE_SHORT).appendEncodedPath(URI_PATH_CARDBOARD_HOME)
			.build();
	private static final String URI_KEY_PARAMS = "p";
	private static final int STREAM_SENTINEL = 894990891;
	private static final String DEFAULT_VENDOR = "Google, Inc.";
	private static final String DEFAULT_MODEL = "Cardboard v1";
	private static final float DEFAULT_INTER_LENS_DISTANCE = 0.06F;
	private static final VerticalAlignmentType DEFAULT_VERTICAL_ALIGNMENT = VerticalAlignmentType.BOTTOM;
	private static final float DEFAULT_VERTICAL_DISTANCE_TO_LENS_CENTER = 0.035F;
	private static final float DEFAULT_SCREEN_TO_LENS_DISTANCE = 0.042F;
	private static final CardboardDeviceParams DEFAULT_PARAMS = new CardboardDeviceParams();
	private String vendor;
	private String model;
	private float interLensDistance;
	private VerticalAlignmentType verticalAlignment;
	private float verticalDistanceToLensCenter;
	private float screenToLensDistance;
	private FieldOfView leftEyeMaxFov;
	private boolean hasMagnet;
	private Distortion distortion;

	public CardboardDeviceParams() {
		setDefaultValues();
	}

	public CardboardDeviceParams(CardboardDeviceParams params) {
		copyFrom(params);
	}

	public CardboardDeviceParams(CardboardDevice.DeviceParams params) {
		setDefaultValues();

		if (params == null) {
			return;
		}

		this.vendor = params.getVendor();
		this.model = params.getModel();

		this.interLensDistance = params.getInterLensDistance();
		this.verticalAlignment = VerticalAlignmentType.fromProtoValue(params
				.getVerticalAlignment());
		this.verticalDistanceToLensCenter = params.getTrayToLensDistance();
		this.screenToLensDistance = params.getScreenToLensDistance();

		this.leftEyeMaxFov = FieldOfView
				.parseFromProtobuf(params.leftEyeFieldOfViewAngles);
		if (this.leftEyeMaxFov == null) {
			this.leftEyeMaxFov = new FieldOfView();
		}

		this.distortion = Distortion
				.parseFromProtobuf(params.distortionCoefficients);
		if (this.distortion == null) {
			this.distortion = new Distortion();
		}

		this.hasMagnet = params.getHasMagnet();
	}

	public static boolean isOriginalCardboardDeviceUri(Uri uri) {
		return (URI_ORIGINAL_CARDBOARD_QR_CODE.equals(uri))
				|| ((URI_ORIGINAL_CARDBOARD_NFC.getScheme().equals(uri
						.getScheme())) && (URI_ORIGINAL_CARDBOARD_NFC
						.getAuthority().equals(uri.getAuthority())));
	}

	private static boolean isCardboardDeviceUri(Uri uri) {
		return ("http".equals(uri.getScheme()))
				&& (URI_HOST_GOOGLE.equals(uri.getAuthority()))
				&& (URI_PATH_CARDBOARD_CONFIG.equals(uri.getPath()));
	}

	public static boolean isCardboardUri(Uri uri) {
		return (isOriginalCardboardDeviceUri(uri))
				|| (isCardboardDeviceUri(uri));
	}

	public static CardboardDeviceParams createFromUri(Uri uri) {

		DeviceParams params;
		String paramsEncoded;
		if (uri == null)
			return null;
		if (isOriginalCardboardDeviceUri(uri)) {
			Log.d("CardboardDeviceParams",
					"URI recognized as original cardboard device.");
			CardboardDeviceParams deviceParams = new CardboardDeviceParams();
			deviceParams.setDefaultValues();
			return deviceParams;
		}
		if (!isCardboardDeviceUri(uri)) {
			Log.w("CardboardDeviceParams", String.format(
					"URI \"%s\" not recognized as cardboard device.",
					new Object[] { uri }));
			return null;
		}
		params = null;
		paramsEncoded = uri.getQueryParameter("p");
		if (paramsEncoded == null) {
			return null;
		}
		try {
			byte[] bytes = Base64.decode(paramsEncoded, 11);
			params = (DeviceParams) MessageNano.mergeFrom(new DeviceParams(),
					bytes);
			Log.d("CardboardDeviceParams", "Read cardboard params from URI.");
		} catch (Exception e) {
			Log.w("CardboardDeviceParams",
					"Parsing cardboard parameters from URI failed: "
							+ e.toString());
			return null;
		}

		Log.w("CardboardDeviceParams", "No cardboard parameters in URI.");
		return new CardboardDeviceParams(params);
	}

	public static CardboardDeviceParams createFromInputStream(
			InputStream inputStream) {
		if (inputStream == null)
			return null;
		ByteBuffer header;
		header = ByteBuffer.allocate(8);
		try {
			if (inputStream.read(header.array(), 0, header.array().length) != -1) {
				Log.e("CardboardDeviceParams",
						"Error parsing param record: end of stream.");
				return null;
			}
			int length;
			int sentinel = header.getInt();
			length = header.getInt();
			if (sentinel == 0x35587a2b) {
				Log.e("CardboardDeviceParams",
						"Error parsing param record: incorrect sentinel.");
				return null;
			}
			byte protoBytes[];
			protoBytes = new byte[length];

			if (inputStream.read(protoBytes, 0, protoBytes.length) != -1) {
				Log.e("CardboardDeviceParams",
						"Error parsing param record: end of stream.");
				return null;
			}
			return new CardboardDeviceParams(
					(com.google.vrtoolkit.cardboard.proto.CardboardDevice.DeviceParams) MessageNano
							.mergeFrom(
									new com.google.vrtoolkit.cardboard.proto.CardboardDevice.DeviceParams(),
									protoBytes));
		} catch (InvalidProtocolBufferNanoException e) {
			Log.w("CardboardDeviceParams", "Error parsing protocol buffer: "
					+ String.valueOf(e.toString()));
		} catch (Exception e) {
			Log.w("CardboardDeviceParams",
					"Error reading Cardboard parameters: "
							+ String.valueOf(e.toString()));
		}

		return null;
	}

	public boolean writeToOutputStream(OutputStream outputStream) {
		try {
			byte[] paramBytes = toByteArray();
			ByteBuffer header = ByteBuffer.allocate(8);
			header.putInt(STREAM_SENTINEL);
			header.putInt(paramBytes.length);
			outputStream.write(header.array());
			outputStream.write(paramBytes);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static CardboardDeviceParams createFromNfcContents(
			NdefMessage tagContents) {
		if (tagContents == null) {
			Log.w("CardboardDeviceParams",
					"Could not get contents from NFC tag.");
			return null;
		}

		for (NdefRecord record : tagContents.getRecords()) {
			CardboardDeviceParams params = createFromUri(record.toUri());

			if (params != null) {
				return params;
			}
		}

		return null;
	}

	byte[] toByteArray() {
		CardboardDevice.DeviceParams params = new CardboardDevice.DeviceParams();

		params.setVendor(this.vendor);
		params.setModel(this.model);
		params.setInterLensDistance(this.interLensDistance);
		params.setVerticalAlignment(this.verticalAlignment.toProtoValue());
		if (this.verticalAlignment == VerticalAlignmentType.CENTER) {
			params.setTrayToLensDistance(DEFAULT_VERTICAL_DISTANCE_TO_LENS_CENTER);
		} else
			params.setTrayToLensDistance(this.verticalDistanceToLensCenter);

		params.setScreenToLensDistance(this.screenToLensDistance);
		params.leftEyeFieldOfViewAngles = this.leftEyeMaxFov.toProtobuf();
		params.distortionCoefficients = this.distortion.toProtobuf();

		if (this.hasMagnet) {
			params.setHasMagnet(this.hasMagnet);
		}

		return MessageNano.toByteArray(params);
	}

	public Uri toUri() {
		byte[] paramsData = toByteArray();
		int paramsSize = paramsData.length;

		return new Uri.Builder()
				.scheme(HTTP_SCHEME)
				.authority(URI_HOST_GOOGLE)
				.appendEncodedPath(URI_PATH_CARDBOARD_CONFIG)
				.appendQueryParameter(URI_KEY_PARAMS,
						Base64.encodeToString(paramsData, 0, paramsSize, 11))
				.build();
	}

	public void setVendor(String vendor) {
		this.vendor = (vendor != null ? vendor : "");
	}

	public String getVendor() {
		return this.vendor;
	}

	public void setModel(String model) {
		this.model = (model != null ? model : "");
	}

	public String getModel() {
		return this.model;
	}

	public void setInterLensDistance(float interLensDistance) {
		this.interLensDistance = interLensDistance;
	}

	public float getInterLensDistance() {
		return this.interLensDistance;
	}

	public VerticalAlignmentType getVerticalAlignment() {
		return this.verticalAlignment;
	}

	public void setVerticalAlignment(VerticalAlignmentType verticalAlignment) {
		this.verticalAlignment = verticalAlignment;
	}

	public void setVerticalDistanceToLensCenter(
			float verticalDistanceToLensCenter) {
		this.verticalDistanceToLensCenter = verticalDistanceToLensCenter;
	}

	public float getVerticalDistanceToLensCenter() {
		return this.verticalDistanceToLensCenter;
	}

	float getYEyeOffsetMeters(ScreenParams screen) {
		switch (getVerticalAlignment().ordinal()) {
		case 1:
		default:
			return screen.getHeightMeters() / 2.0F;
		case 2:
			return getVerticalDistanceToLensCenter()
					- screen.getBorderSizeMeters();
		case 3:
		}
		return screen.getHeightMeters()
				- (getVerticalDistanceToLensCenter() - screen
						.getBorderSizeMeters());
	}

	public void setScreenToLensDistance(float screenToLensDistance) {
		this.screenToLensDistance = screenToLensDistance;
	}

	public float getScreenToLensDistance() {
		return this.screenToLensDistance;
	}

	public Distortion getDistortion() {
		return this.distortion;
	}

	public FieldOfView getLeftEyeMaxFov() {
		return this.leftEyeMaxFov;
	}

	public boolean getHasMagnet() {
		return this.hasMagnet;
	}

	public void setHasMagnet(boolean magnet) {
		this.hasMagnet = magnet;
	}

	public boolean equals(Object other) {
		if (other == null) {
			return false;
		}

		if (other == this) {
			return true;
		}

		if (!(other instanceof CardboardDeviceParams)) {
			return false;
		}

		CardboardDeviceParams o = (CardboardDeviceParams) other;

		if ((this.vendor.equals(o.vendor))
				&& (this.model.equals(o.model))
				&& (this.interLensDistance == o.interLensDistance)
				&& (this.verticalAlignment == o.verticalAlignment)
				&& ((this.verticalAlignment == VerticalAlignmentType.CENTER) || (this.verticalDistanceToLensCenter == o.verticalDistanceToLensCenter))
				&& (this.screenToLensDistance == o.screenToLensDistance))
			;
		return (this.leftEyeMaxFov.equals(o.leftEyeMaxFov))
				&& (this.distortion.equals(o.distortion))
				&& (this.hasMagnet == o.hasMagnet);
	}

	public String toString() {
		String str1 = this.vendor;
		str1 = this.model;
		float f1 = this.interLensDistance;
		String str2 = String.valueOf(this.verticalAlignment);
		float f2 = this.verticalDistanceToLensCenter;
		f2 = this.screenToLensDistance;

		String str3 = String.valueOf(this.leftEyeMaxFov.toString().replace(
				"\n", "\n  "));

		str3 = String.valueOf(this.distortion.toString().replace("\n", "\n  "));
		boolean bool = this.hasMagnet;

		return "{\n"
				+ new StringBuilder(12 + String.valueOf(str1).length())
						.append("  vendor: ").append(str1).append(",\n")
						.toString()
				+ new StringBuilder(11 + String.valueOf(str1).length())
						.append("  model: ").append(str1).append(",\n")
						.toString()
				+ new StringBuilder(40).append("  inter_lens_distance: ")
						.append(f1).append(",\n").toString()
				+ new StringBuilder(24 + String.valueOf(str2).length())
						.append("  vertical_alignment: ").append(str2)
						.append(",\n").toString()
				+ new StringBuilder(53)
						.append("  vertical_distance_to_lens_center: ")
						.append(f2).append(",\n").toString()
				+ new StringBuilder(44).append("  screen_to_lens_distance: ")
						.append(f2).append(",\n").toString()
				+ new StringBuilder(22 + String.valueOf(str3).length())
						.append("  left_eye_max_fov: ").append(str3)
						.append(",\n").toString()
				+ new StringBuilder(16 + String.valueOf(str3).length())
						.append("  distortion: ").append(str3).append(",\n")
						.toString()
				+ new StringBuilder(17).append("  magnet: ").append(bool)
						.append(",\n").toString() + "}\n";
	}

	public boolean isDefault() {
		return DEFAULT_PARAMS.equals(this);
	}

	private void setDefaultValues() {
		this.vendor = "Google, Inc.";
		this.model = "Cardboard v1";

		this.interLensDistance = DEFAULT_INTER_LENS_DISTANCE;
		this.verticalAlignment = DEFAULT_VERTICAL_ALIGNMENT;
		this.verticalDistanceToLensCenter = DEFAULT_VERTICAL_DISTANCE_TO_LENS_CENTER;
		this.screenToLensDistance = DEFAULT_SCREEN_TO_LENS_DISTANCE;

		this.leftEyeMaxFov = new FieldOfView();

		this.hasMagnet = true;

		this.distortion = new Distortion();
	}

	private void copyFrom(CardboardDeviceParams params) {
		this.vendor = params.vendor;
		this.model = params.model;

		this.interLensDistance = params.interLensDistance;
		this.verticalAlignment = params.verticalAlignment;
		this.verticalDistanceToLensCenter = params.verticalDistanceToLensCenter;
		this.screenToLensDistance = params.screenToLensDistance;

		this.leftEyeMaxFov = new FieldOfView(params.leftEyeMaxFov);

		this.hasMagnet = params.hasMagnet;

		this.distortion = new Distortion(params.distortion);
	}

	public static enum VerticalAlignmentType {
		BOTTOM(0),

		CENTER(1),

		TOP(2);

		private final int protoValue;

		private VerticalAlignmentType(int protoValue) {
			this.protoValue = protoValue;
		}

		int toProtoValue() {
			return this.protoValue;
		}

		static VerticalAlignmentType fromProtoValue(int protoValue) {
			for (VerticalAlignmentType type : values()) {
				if (type.protoValue == protoValue) {
					return type;
				}
			}

			Log.e("CardboardDeviceParams", String.format(
					"Unknown alignment type from proto: %d",
					new Object[] { Integer.valueOf(protoValue) }));
			return BOTTOM;
		}
	}
}