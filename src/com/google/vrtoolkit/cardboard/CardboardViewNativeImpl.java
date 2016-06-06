package com.google.vrtoolkit.cardboard;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.MotionEvent;

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.cardboard.UiLayer;

@UsedByNative
public class CardboardViewNativeImpl implements CardboardViewApi {
	private static final String TAG = CardboardViewNativeImpl.class
			.getSimpleName();
	private RendererHelper rendererHelper;
	private HeadMountedDisplayManager hmdManager;
	private CountDownLatch shutdownLatch;
	private DisplaySynchronizer displaySynchronizer;
	private UiLayer uiLayer;
	private boolean convertTapIntoTrigger = true;
	private int cardboardTriggerCount = 0;
	private Runnable cardboardTriggerListener;
	private Handler cardboardTriggerListenerHandler;
	private Runnable cardboardBackListener;
	private Handler cardboardBackListenerHandler;
	private final GLSurfaceView glSurfaceView;
	private boolean vrMode = true;
	private volatile boolean restoreGLStateEnabled = true;
	private volatile boolean distortionCorrectionEnabled = true;
	private volatile boolean lowLatencyModeEnabled = false;
	private volatile boolean chromaticAberrationCorrectionEnabled = false;
	private volatile boolean vignetteEnabled = true;

	private volatile boolean electronicDisplayStabilizationEnabled = false;
	private volatile boolean uiLayerAlignmentMarkerEnabled = true;
	private volatile boolean uiLayerAttached = false;
	private final long nativeCardboardView;

	public CardboardViewNativeImpl(Context context, GLSurfaceView view) {
		this.hmdManager = new HeadMountedDisplayManager(context);
		ScreenParams screenParams = this.hmdManager.getHeadMountedDisplay()
				.getScreenParams();
		String nativeLibrary;
		try {
			String proxyClassName = String.valueOf(
					getClass().getPackage().getName()).concat(".NativeProxy");
			Class proxyClass = Class.forName(proxyClassName);
			Field proxyLibField = proxyClass.getDeclaredField("PROXY_LIBRARY");
			nativeLibrary = (String) proxyLibField.get(null);
		} catch (Exception e) {
			Log.d(TAG, "NativeProxy not found");
			nativeLibrary = "vrtoolkit";

		}
		Log.d(TAG, "Loading native library " + nativeLibrary);
		System.loadLibrary(nativeLibrary);
		Log.d(TAG, "Native library loaded");
		nativeSetApplicationState(getClass().getClassLoader(),
				context.getApplicationContext());
		glSurfaceView = view;
		rendererHelper = new RendererHelper();
		displaySynchronizer = new DisplaySynchronizer();
		uiLayer = new UiLayer(context);
		nativeCardboardView = nativeInit(
				screenParams.getWidth(),
				screenParams.getHeight(),
				screenParams.getWidthMeters() / (float) screenParams.getWidth(),
				screenParams.getHeightMeters()
						/ (float) screenParams.getHeight(),
				screenParams.getBorderSizeMeters(),
				displaySynchronizer.retainNativeDisplaySynchronizer());
		cardboardTriggerListenerHandler = new Handler(Looper.getMainLooper());
		cardboardBackListenerHandler = new Handler(Looper.getMainLooper());
	}

	protected void finalize() throws Throwable {
		try {
			nativeDestroy(this.nativeCardboardView);

			super.finalize();
		} finally {
			super.finalize();
		}
	}

	public GLSurfaceView.Renderer setRenderer(CardboardView.Renderer renderer) {
		if (renderer == null) {
			return null;
		}

		this.rendererHelper.setRenderer(renderer);
		return this.rendererHelper;
	}

	public GLSurfaceView.Renderer setRenderer(
			CardboardView.StereoRenderer renderer) {
		if (renderer == null) {
			return null;
		}

		this.rendererHelper.setRenderer(renderer);
		return this.rendererHelper;
	}

	public void getCurrentEyeParams(HeadTransform head, Eye leftEye,
			Eye rightEye, Eye monocular, Eye leftEyeNoDistortionCorrection,
			Eye rightEyeNoDistortionCorrection) {
		this.rendererHelper.getCurrentEyeParams(head, leftEye, rightEye,
				monocular, leftEyeNoDistortionCorrection,
				rightEyeNoDistortionCorrection);
	}

	public void setVRModeEnabled(boolean enabled) {
		this.vrMode = enabled;
		this.rendererHelper.setVRModeEnabled(enabled);
	}

	public boolean getVRMode() {
		return this.vrMode;
	}

	public void setAlignmentMarkerEnabled(final boolean enabled) {
		this.uiLayerAlignmentMarkerEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetUiLayerAlignmentMarkerEnabled(
								CardboardViewNativeImpl.this.nativeCardboardView,
								enabled);
			}
		});
	}

	public boolean getAlignmentMarkerEnabled() {
		return this.uiLayerAlignmentMarkerEnabled;
	}

	public void setSettingsButtonEnabled(boolean enabled) {
		this.uiLayer.setSettingsButtonEnabled(enabled);
	}

	public boolean getSettingsButtonEnabled() {
		return this.uiLayer.getSettingsButtonEnabled();
	}

	public void setOnCardboardBackButtonListener(Runnable listener) {
		this.uiLayer.setBackButtonListener(listener);
	}

	public boolean getCardboardBackButtonEnabled() {
		return this.uiLayer.getBackButtonEnabled();
	}

	public HeadMountedDisplay getHeadMountedDisplay() {
		return this.hmdManager.getHeadMountedDisplay();
	}

	public void setRestoreGLStateEnabled(final boolean enabled) {
		this.restoreGLStateEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this.nativeSetRestoreGLStateEnabled(
						CardboardViewNativeImpl.this.nativeCardboardView,
						enabled);
			}
		});
	}

	public boolean getRestoreGLStateEnabled() {
		return this.restoreGLStateEnabled;
	}

	public void setChromaticAberrationCorrectionEnabled(final boolean enabled) {
		this.chromaticAberrationCorrectionEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetChromaticAberrationCorrectionEnabled(
								CardboardViewNativeImpl.this.nativeCardboardView,
								enabled);
			}
		});
	}

	public boolean getChromaticAberrationCorrectionEnabled() {
		return this.chromaticAberrationCorrectionEnabled;
	}

	public void setVignetteEnabled(final boolean enabled) {
		this.vignetteEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this.nativeSetVignetteEnabled(
						CardboardViewNativeImpl.this.nativeCardboardView,
						enabled);
			}
		});
	}

	public boolean getVignetteEnabled() {
		return this.vignetteEnabled;
	}

	public void setElectronicDisplayStabilizationEnabled(boolean enabled) {
		this.electronicDisplayStabilizationEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetElectronicDisplayStabilizationEnabled(
								CardboardViewNativeImpl.this.nativeCardboardView,
								CardboardViewNativeImpl.this.electronicDisplayStabilizationEnabled);
			}
		});
	}

	public boolean getElectronicDisplayStabilizationEnabled() {
		return this.electronicDisplayStabilizationEnabled;
	}

	public void setNeckModelEnabled(boolean enabled) {
		nativeSetNeckModelEnabled(this.nativeCardboardView, enabled);
	}

	public float getNeckModelFactor() {
		return nativeGetNeckModelFactor(this.nativeCardboardView);
	}

	public void setNeckModelFactor(float factor) {
		nativeSetNeckModelFactor(this.nativeCardboardView, factor);
	}

	public void setGyroBiasEstimationEnabled(boolean enabled) {
		nativeSetGyroBiasEstimationEnabled(this.nativeCardboardView, enabled);
	}

	public boolean getGyroBiasEstimationEnabled() {
		return nativeGetGyroBiasEstimationEnabled(this.nativeCardboardView);
	}

	public void resetHeadTracker() {
		nativeResetHeadTracker(this.nativeCardboardView);
	}

	public void updateCardboardDeviceParams(
			CardboardDeviceParams cardboardDeviceParams) {
		if (this.hmdManager.updateCardboardDeviceParams(cardboardDeviceParams))
			setCardboardDeviceParams(getCardboardDeviceParams());
	}

	public CardboardDeviceParams getCardboardDeviceParams() {
		return this.hmdManager.getHeadMountedDisplay()
				.getCardboardDeviceParams();
	}

	public void updateScreenParams(ScreenParams screenParams) {
		if (this.hmdManager.updateScreenParams(screenParams))
			setScreenParams(getScreenParams());
	}

	public ScreenParams getScreenParams() {
		return this.hmdManager.getHeadMountedDisplay().getScreenParams();
	}

	public float getInterpupillaryDistance() {
		return getCardboardDeviceParams().getInterLensDistance();
	}

	public void setDistortionCorrectionEnabled(final boolean enabled) {
		this.distortionCorrectionEnabled = enabled;
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetDistortionCorrectionEnabled(
								CardboardViewNativeImpl.this.nativeCardboardView,
								enabled);
			}
		});
	}

	public boolean getDistortionCorrectionEnabled() {
		return this.distortionCorrectionEnabled;
	}

	public void setLowLatencyModeEnabled(boolean enabled) {
		this.lowLatencyModeEnabled = enabled;
	}

	public boolean getLowLatencyModeEnabled() {
		return this.lowLatencyModeEnabled;
	}

	public void undistortTexture(final int inputTexture) {
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this.nativeUndistortTexture(
						CardboardViewNativeImpl.this.nativeCardboardView,
						inputTexture);
			}
		});
	}

	public void renderUiLayer() {
		if (!this.uiLayerAttached) {
			this.uiLayer.attachUiLayer(null);
			this.uiLayerAttached = true;
		}

		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeDrawUiLayer(CardboardViewNativeImpl.this.nativeCardboardView);
			}
		});
	}

	public void setDistortionCorrectionScale(final float scale) {
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetDistortionCorrectionScale(
								CardboardViewNativeImpl.this.nativeCardboardView,
								scale);
			}
		});
	}

	public void onResume() {
		this.hmdManager.onResume();
		setScreenParams(getScreenParams());
		setCardboardDeviceParams(getCardboardDeviceParams());
		nativeStartTracking(this.nativeCardboardView);
	}

	public void onPause() {
		this.hmdManager.onPause();
		nativeStopTracking(this.nativeCardboardView);
	}

	public void onDetachedFromWindow() {
		if (shutdownLatch != null) {
			return;
		}
		try {
			shutdownLatch = new CountDownLatch(1);
			rendererHelper.shutdown();
			shutdownLatch.await();
		} catch (InterruptedException e) {
			Log.e(TAG, "Interrupted during shutdown: " + e.toString());
		}
		shutdownLatch = null;
	}

	public boolean onTouchEvent(MotionEvent e) {
		if (e.getActionMasked() == 0) {
			onCardboardTrigger();
			return true;
		}
		return false;
	}

	public synchronized void setOnCardboardTriggerListener(Runnable listener) {
		this.cardboardTriggerListener = listener;
	}

	public synchronized void setOnCardboardBackListener(Runnable listener) {
		this.cardboardBackListener = listener;
	}

	public void setConvertTapIntoTrigger(boolean enabled) {
		this.convertTapIntoTrigger = enabled;
	}

	public boolean getConvertTapIntoTrigger() {
		return this.convertTapIntoTrigger;
	}

	public boolean handlesMagnetInput() {
		return true;
	}

	public void runOnCardboardTriggerListener() {
		synchronized (this) {
			if (this.cardboardTriggerListener == null) {
				return;
			}
			if (this.cardboardTriggerListenerHandler.getLooper().getThread() == Thread
					.currentThread()) {
				this.cardboardTriggerListener.run();
			} else {
				this.cardboardTriggerListenerHandler.post(new Runnable() {
					public void run() {
						synchronized (CardboardViewNativeImpl.this) {
							if (CardboardViewNativeImpl.this.cardboardTriggerListener != null)
								CardboardViewNativeImpl.this.cardboardTriggerListener
										.run();
						}
					}
				});
			}
		}
	}

	public void runOnCardboardBackListener() {
		synchronized (this) {
			if (this.cardboardBackListener == null) {
				return;
			}
			if (this.cardboardBackListenerHandler.getLooper().getThread() == Thread
					.currentThread()) {
				this.cardboardBackListener.run();
			} else {
				this.cardboardBackListenerHandler.post(new Runnable() {
					public void run() {
						synchronized (CardboardViewNativeImpl.this) {
							if (CardboardViewNativeImpl.this.cardboardBackListener != null)
								CardboardViewNativeImpl.this.cardboardBackListener
										.run();
						}
					}
				});
			}
		}
	}

	@UsedByNative
	private void onCardboardTrigger() {
		if (this.convertTapIntoTrigger)
			runOnCardboardTriggerListener();
	}

	@UsedByNative
	private void onCardboardBack() {
		runOnCardboardBackListener();
	}

	private void queueEvent(Runnable r) {
		this.glSurfaceView.queueEvent(r);
	}

	private void setCardboardDeviceParams(final CardboardDeviceParams newParams) {
		CardboardDeviceParams deviceParams = new CardboardDeviceParams(
				newParams);
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this.nativeSetCardboardDeviceParams(
						CardboardViewNativeImpl.this.nativeCardboardView,
						newParams.toByteArray());
			}
		});
	}

	private void setScreenParams(ScreenParams newParams) {
		final ScreenParams screenParams = new ScreenParams(newParams);
		queueEvent(new Runnable() {
			public void run() {
				CardboardViewNativeImpl.this
						.nativeSetScreenParams(
								CardboardViewNativeImpl.this.nativeCardboardView,
								screenParams.getWidth(),
								screenParams.getHeight(),
								screenParams.getWidthMeters()
										/ screenParams.getWidth(),
								screenParams.getHeightMeters()
										/ screenParams.getHeight(),
								screenParams.getBorderSizeMeters());
			}
		});
	}

	private static native long nativeSetApplicationState(
			ClassLoader paramClassLoader, Context paramContext);

	private native long nativeInit(int paramInt1, int paramInt2,
			float paramFloat1, float paramFloat2, float paramFloat3,
			long paramLong);

	private native void nativeDestroy(long paramLong);

	private native void nativeSetRenderer(long paramLong,
			CardboardView.Renderer paramRenderer);

	private native void nativeSetStereoRenderer(long paramLong,
			CardboardView.StereoRenderer paramStereoRenderer);

	private native void nativeOnSurfaceCreated(long paramLong);

	private native void nativeOnSurfaceChanged(long paramLong, int paramInt1,
			int paramInt2);

	private native void nativeOnDrawFrame(long paramLong);

	private native void nativeGetCurrentEyeParams(long paramLong,
			HeadTransform paramHeadTransform, Eye paramEye1, Eye paramEye2,
			Eye paramEye3, Eye paramEye4, Eye paramEye5);

	private native void nativeStartTracking(long paramLong);

	private native void nativeStopTracking(long paramLong);

	private native void nativeResetHeadTracker(long paramLong);

	private native void nativeSetNeckModelEnabled(long paramLong,
			boolean paramBoolean);

	private native float nativeGetNeckModelFactor(long paramLong);

	private native void nativeSetNeckModelFactor(long paramLong,
			float paramFloat);

	private native void nativeSetGyroBiasEstimationEnabled(long paramLong,
			boolean paramBoolean);

	private native boolean nativeGetGyroBiasEstimationEnabled(long paramLong);

	private native void nativeSetCardboardDeviceParams(long paramLong,
			byte[] paramArrayOfByte);

	private native void nativeSetScreenParams(long paramLong, int paramInt1,
			int paramInt2, float paramFloat1, float paramFloat2,
			float paramFloat3);

	private native void nativeSetVRModeEnabled(long paramLong,
			boolean paramBoolean);

	private native void nativeSetDistortionCorrectionEnabled(long paramLong,
			boolean paramBoolean);

	private native void nativeSetDistortionCorrectionScale(long paramLong,
			float paramFloat);

	private native void nativeSetRestoreGLStateEnabled(long paramLong,
			boolean paramBoolean);

	private native void nativeSetChromaticAberrationCorrectionEnabled(
			long paramLong, boolean paramBoolean);

	private native void nativeSetVignetteEnabled(long paramLong,
			boolean paramBoolean);

	private native void nativeSetElectronicDisplayStabilizationEnabled(
			long paramLong, boolean paramBoolean);

	private native void nativeUndistortTexture(long paramLong, int paramInt);

	private native void nativeDrawUiLayer(long paramLong);

	private native void nativeSetUiLayerAlignmentMarkerEnabled(long paramLong,
			boolean paramBoolean);

	private static class TraceCompat {
		static void beginSection(String sectionName) {
			if (Build.VERSION.SDK_INT < 18) {
				return;
			}
			Trace.beginSection(sectionName);
		}

		static void endSection() {
			if (Build.VERSION.SDK_INT < 18) {
				return;
			}
			Trace.endSection();
		}
	}

	private class RendererHelper implements GLSurfaceView.Renderer {
		private CardboardView.Renderer renderer;
		private CardboardView.StereoRenderer stereoRenderer;
		private HeadMountedDisplay hmd;
		private boolean vrMode;
		private boolean surfaceCreated;
		private boolean invalidSurfaceSizeWarningShown;
		private EGLDisplay eglDisplay;

		public RendererHelper() {
			this.hmd = new HeadMountedDisplay(
					CardboardViewNativeImpl.this.getHeadMountedDisplay());
			this.vrMode = CardboardViewNativeImpl.this.vrMode;
		}

		public void setRenderer(CardboardView.Renderer renderer) {
			this.renderer = renderer;
			CardboardViewNativeImpl.this.nativeSetRenderer(
					CardboardViewNativeImpl.this.nativeCardboardView, renderer);
		}

		public void setRenderer(CardboardView.StereoRenderer stereoRenderer) {
			this.stereoRenderer = stereoRenderer;
			CardboardViewNativeImpl.this.nativeSetStereoRenderer(
					CardboardViewNativeImpl.this.nativeCardboardView,
					stereoRenderer);
		}

		public void shutdown() {
			CardboardViewNativeImpl.this.queueEvent(new Runnable() {
				public void run() {
					if ((renderer != null)
							&& (CardboardViewNativeImpl.RendererHelper.this.surfaceCreated)) {
						surfaceCreated = false;
						CardboardViewNativeImpl.RendererHelper.this
								.callOnRendererShutdown();
					}

					CardboardViewNativeImpl.this.shutdownLatch.countDown();
				}
			});
		}

		public void setVRModeEnabled(final boolean enabled) {
			CardboardViewNativeImpl.this.queueEvent(new Runnable() {
				public void run() {
					if (CardboardViewNativeImpl.RendererHelper.this.vrMode == enabled) {
						return;
					}

					vrMode = enabled;
					CardboardViewNativeImpl.this.nativeSetVRModeEnabled(
							CardboardViewNativeImpl.this.nativeCardboardView,
							enabled);

					CardboardViewNativeImpl.RendererHelper.this
							.onSurfaceChanged(
									(GL10) null,
									CardboardViewNativeImpl.RendererHelper.this.hmd
											.getScreenParams().getWidth(),
									CardboardViewNativeImpl.RendererHelper.this.hmd
											.getScreenParams().getHeight());
				}
			});
		}

		public void getCurrentEyeParams(final HeadTransform head,
				final Eye leftEye, final Eye rightEye, final Eye monocular,
				final Eye leftEyeNoDistortionCorrection,
				final Eye rightEyeNoDistortionCorrection) {
			try {
				final CountDownLatch finished;
				finished = new CountDownLatch(1);
				queueEvent(new Runnable() {

					public void run() {
						nativeGetCurrentEyeParams(nativeCardboardView, head,
								leftEye, rightEye, monocular,
								leftEyeNoDistortionCorrection,
								rightEyeNoDistortionCorrection);
						finished.countDown();
					}

				});
				finished.await();
			} catch (InterruptedException e) {
				Log.e(CardboardViewNativeImpl.TAG,
						"Interrupted while reading frame params: "
								+ String.valueOf(e.toString()));
			}

		}

		public void onDrawFrame(GL10 gl) {
			if (((this.renderer == null) && (this.stereoRenderer == null))
					|| (!this.surfaceCreated)) {
				return;
			}

			long nextVSync = 0L;

			if (CardboardViewNativeImpl.this.lowLatencyModeEnabled) {
				CardboardViewNativeImpl.TraceCompat.beginSection("Sync");
				nextVSync = CardboardViewNativeImpl.this.displaySynchronizer
						.syncToNextVsync();
				CardboardViewNativeImpl.TraceCompat.endSection();
			}

			CardboardViewNativeImpl.TraceCompat.beginSection("Render");
			CardboardViewNativeImpl.this
					.nativeOnDrawFrame(CardboardViewNativeImpl.this.nativeCardboardView);
			CardboardViewNativeImpl.TraceCompat.endSection();

			if (Build.VERSION.SDK_INT < 17) {
				return;
			}

			if (CardboardViewNativeImpl.this.lowLatencyModeEnabled) {
				if (Build.VERSION.SDK_INT < 19) {
					EGL14.eglSwapInterval(this.eglDisplay, 0);
				} else {
					EGLSurface surface = EGL14.eglGetCurrentSurface(12377);

					EGLExt.eglPresentationTimeANDROID(this.eglDisplay, surface,
							nextVSync - 1000000L);
				}
			} else
				EGL14.eglSwapInterval(this.eglDisplay, 1);
		}

		public void onSurfaceChanged(GL10 gl, int width, int height) {
			if (((this.renderer == null) && (this.stereoRenderer == null))
					|| (!this.surfaceCreated)) {
				return;
			}

			ScreenParams screen = this.hmd.getScreenParams();
			if ((this.vrMode)
					&& ((width != screen.getWidth()) || (height != screen
							.getHeight()))) {
				if (!this.invalidSurfaceSizeWarningShown) {
					int i = screen.getWidth();
					int j = screen.getHeight();

					Log.e(CardboardViewNativeImpl.TAG, 134 + "Surface size "
							+ width + "x" + height
							+ " does not match the expected screen size " + i
							+ "x" + j + ". Stereo rendering might feel off.");
				}

				this.invalidSurfaceSizeWarningShown = true;
			} else {
				this.invalidSurfaceSizeWarningShown = false;
			}

			CardboardViewNativeImpl.this.nativeOnSurfaceChanged(
					CardboardViewNativeImpl.this.nativeCardboardView, width,
					height);

			callOnSurfaceChanged(width, height);
		}

		private void callOnSurfaceCreated(EGLConfig config) {
			CardboardViewNativeImpl.this
					.nativeOnSurfaceCreated(CardboardViewNativeImpl.this.nativeCardboardView);

			if (this.renderer != null)
				this.renderer.onSurfaceCreated(config);
			else if (this.stereoRenderer != null)
				this.stereoRenderer.onSurfaceCreated(config);
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			if ((this.renderer == null) && (this.stereoRenderer == null)) {
				return;
			}

			this.surfaceCreated = true;
			if (!CardboardViewNativeImpl.this.uiLayerAttached) {
				CardboardViewNativeImpl.this.uiLayer.attachUiLayer(null);
				uiLayerAttached = true;
			}

			if (Build.VERSION.SDK_INT > 16) {
				this.eglDisplay = EGL14.eglGetCurrentDisplay();
			}

			callOnSurfaceCreated(config);
		}

		private void callOnSurfaceChanged(int width, int height) {
			if (this.renderer != null) {
				this.renderer.onSurfaceChanged(width, height);
			} else if (this.stereoRenderer != null)
				if (this.vrMode) {
					this.stereoRenderer.onSurfaceChanged(width / 2, height);
				} else
					this.stereoRenderer.onSurfaceChanged(width, height);
		}

		private void callOnRendererShutdown() {
			if (this.renderer != null)
				this.renderer.onRendererShutdown();
			else if (this.stereoRenderer != null)
				this.stereoRenderer.onRendererShutdown();
		}
	}
}