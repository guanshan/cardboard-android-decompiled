package com.google.vrtoolkit.cardboard;

import android.content.res.AssetManager;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class ConfigUtils {
	public static final String CARDBOARD_CONFIG_FOLDER = "Cardboard";
	public static final String CARDBOARD_DEVICE_PARAMS_FILE = "current_device_params";
	public static final String CARDBOARD_PHONE_PARAMS_FILE = "phone_params";

	public static File getConfigFile(String filename) {
		File configFolder = new File(Environment.getExternalStorageDirectory(),
				CARDBOARD_CONFIG_FOLDER);

		if (!configFolder.exists()) {
			configFolder.mkdirs();
		} else if (!configFolder.isDirectory()) {
			String str = String.valueOf(configFolder);
			throw new IllegalStateException(61 + String.valueOf(str).length()
					+ str + " already exists as a file, but is "
					+ "expected to be a directory.");
		}

		return new File(configFolder, filename);
	}

	public static InputStream openAssetConfigFile(AssetManager assetManager,
			String filename) throws IOException {
		String assetPath = new File(CARDBOARD_CONFIG_FOLDER, filename).getPath();
		return assetManager.open(assetPath);
	}
}