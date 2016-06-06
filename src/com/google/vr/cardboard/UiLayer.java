package com.google.vr.cardboard;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class UiLayer {
	private static final String TAG = UiLayer.class.getSimpleName();
	private static final int ALIGNMENT_MARKER_LINE_WIDTH = 4;
	private static final int ICON_WIDTH_DP = 28;
	private static final float TOUCH_SLOP_FACTOR = 1.5F;
	private static final int ALIGNMENT_MARKER_LINE_COLOR = -13487566;
	private final Context context;
	private final DisplayMetrics metrics;
	private final Drawable settingsIconDrawable;
	private final Drawable backIconDrawable;
	private ImageView settingsButton;
	private ImageView backButton;
	private View alignmentMarker;
	private final RelativeLayout rootLayout;
	private volatile boolean isSettingsButtonEnabled = true;
	private volatile boolean isAlignmentMarkerEnabled = false;

	private volatile Runnable backButtonRunnable = null;

	public UiLayer(Context context) {
		if (!(context instanceof Activity)) {
			throw new RuntimeException(
					"Context is not an instance of activity: Aborting.");
		}
		this.context = context;
		this.settingsIconDrawable = decodeBitmapFromString(Base64Resources.SETTINGS_BUTTON_PNG_STRING);
		this.backIconDrawable = decodeBitmapFromString(Base64Resources.BACK_BUTTON_PNG_STRING);

		WindowManager windowManager = (WindowManager) context
				.getSystemService("window");
		Display display = windowManager.getDefaultDisplay();
		this.metrics = new DisplayMetrics();

		if (Build.VERSION.SDK_INT >= 17)
			display.getRealMetrics(this.metrics);
		else {
			display.getMetrics(this.metrics);
		}

		this.rootLayout = new RelativeLayout(context);
		initializeViews();
	}

	private Drawable decodeBitmapFromString(String bitmapString) {
		byte[] decodedBytes = Base64.decode(bitmapString, 0);

		Bitmap buttonBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0,
				decodedBytes.length);

		return new BitmapDrawable(this.context.getResources(), buttonBitmap);
	}

	private void initializeViews() {
		int iconWidthPx = (int) (ICON_WIDTH_DP * this.metrics.density);
		int touchWidthPx = (int) (iconWidthPx * TOUCH_SLOP_FACTOR);

		this.settingsButton = createButton(this.settingsIconDrawable,
				this.isSettingsButtonEnabled, new int[] { 12, 13 });

		this.settingsButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				UiUtils.launchOrInstallCardboard(v.getContext());
			}
		});
		this.rootLayout.addView(this.settingsButton);

		this.backButton = createButton(this.backIconDrawable,
				getBackButtonEnabled(), new int[] { 10, 9 });

		this.backButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Runnable runnable = UiLayer.this.backButtonRunnable;
				if (runnable != null)
					runnable.run();
			}
		});
		this.rootLayout.addView(this.backButton);

		this.alignmentMarker = new View(this.context);
		this.alignmentMarker.setBackground(new ColorDrawable(
				ALIGNMENT_MARKER_LINE_COLOR));
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
				(int) (ALIGNMENT_MARKER_LINE_WIDTH * this.metrics.density), -1);

		layoutParams.addRule(13);
		layoutParams.setMargins(0, touchWidthPx, 0, touchWidthPx);
		this.alignmentMarker.setLayoutParams(layoutParams);
		this.alignmentMarker.setVisibility(this.isAlignmentMarkerEnabled ? View.VISIBLE
				: View.GONE);
		this.rootLayout.addView(this.alignmentMarker);
	}

	private ImageView createButton(Drawable iconDrawable, boolean isEnabled,
			int[] layoutParams) {
		int iconWidthPx = (int) (ICON_WIDTH_DP * this.metrics.density);
		int touchWidthPx = (int) (iconWidthPx * TOUCH_SLOP_FACTOR);
		int padding = (touchWidthPx - iconWidthPx) / 2;

		ImageView buttonLayout = new ImageView(this.context);

		buttonLayout.setPadding(padding, padding, padding, padding);
		buttonLayout.setImageDrawable(iconDrawable);
		buttonLayout.setScaleType(ImageView.ScaleType.FIT_CENTER);
		RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(
				touchWidthPx, touchWidthPx);

		for (int layoutParam : layoutParams) {
			buttonParams.addRule(layoutParam);
		}
		buttonLayout.setLayoutParams(buttonParams);

		buttonLayout.setVisibility(isEnabled ? View.VISIBLE : View.INVISIBLE);
		return buttonLayout;
	}

	public void attachUiLayer(final ViewGroup parentView) {
		((Activity) this.context).runOnUiThread(new Runnable() {
			public void run() {
				if (parentView == null) {
					((Activity) UiLayer.this.context).addContentView(
							UiLayer.this.rootLayout,
							new RelativeLayout.LayoutParams(-1, -1));
				} else {
					parentView.addView(UiLayer.this.rootLayout);
				}
			}
		});
	}

	public void setEnabled(final boolean enabled) {
		((Activity) this.context).runOnUiThread(new Runnable() {
			public void run() {
				UiLayer.this.rootLayout.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
			}
		});
	}

	public void setSettingsButtonEnabled(final boolean enabled) {
		this.isSettingsButtonEnabled = enabled;

		((Activity) this.context).runOnUiThread(new Runnable() {
			public void run() {
				UiLayer.this.settingsButton.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
			}
		});
	}

	public void setBackButtonListener(final Runnable runnable) {
		this.backButtonRunnable = runnable;
		((Activity) this.context).runOnUiThread(new Runnable() {
			public void run() {
				UiLayer.this.backButton.setVisibility(runnable == null ? View.INVISIBLE : View.VISIBLE);
			}
		});
	}

	public void setAlignmentMarkerEnabled(final boolean enabled) {
		this.isAlignmentMarkerEnabled = enabled;
		((Activity) this.context).runOnUiThread(new Runnable() {
			public void run() {
				UiLayer.this.alignmentMarker.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
			}
		});
	}

	public boolean getSettingsButtonEnabled() {
		return this.isSettingsButtonEnabled;
	}

	public boolean getBackButtonEnabled() {
		return this.backButtonRunnable != null;
	}

	public boolean getAlignmentMarkerEnabled() {
		return this.isAlignmentMarkerEnabled;
	}
}