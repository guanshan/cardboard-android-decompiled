package com.google.vrtoolkit.cardboard.sensors.internal;

public class GyroscopeBiasEstimator {
	private static final float ACCEL_LOWPASS_FREQ = 1.0F;
	private static final float GYRO_LOWPASS_FREQ = 10.0F;
	private static final float GYRO_BIAS_LOWPASS_FREQ = 0.15F;
	private static final int NUM_GYRO_BIAS_SAMPLES_THRESHOLD = 30;
	private static final int NUM_GYRO_BIAS_SAMPLES_INITIAL_SMOOTHING = 100;
	private LowPassFilter accelLowPass;
	private LowPassFilter gyroLowPass;
	private LowPassFilter gyroBiasLowPass;
	private static final float ACCEL_DIFF_STATIC_THRESHOLD = 0.5F;
	private static final float GYRO_DIFF_STATIC_THRESHOLD = 0.008F;
	private Vector3d smoothedGyroDiff;
	private Vector3d smoothedAccelDiff;
	private static final float GYRO_FOR_BIAS_THRESHOLD = 0.35F;
	private static final int IS_STATIC_NUM_FRAMES_THRESHOLD = 10;
	private IsStaticCounter isAccelStatic;
	private IsStaticCounter isGyroStatic;

	public GyroscopeBiasEstimator() {
		reset();
	}

	public void reset() {
		this.smoothedGyroDiff = new Vector3d();
		this.smoothedAccelDiff = new Vector3d();
		this.accelLowPass = new LowPassFilter(ACCEL_LOWPASS_FREQ);
		this.gyroLowPass = new LowPassFilter(GYRO_LOWPASS_FREQ);
		this.gyroBiasLowPass = new LowPassFilter(GYRO_BIAS_LOWPASS_FREQ);
		this.isAccelStatic = new IsStaticCounter(IS_STATIC_NUM_FRAMES_THRESHOLD);
		this.isGyroStatic = new IsStaticCounter(IS_STATIC_NUM_FRAMES_THRESHOLD);
	}

	public void processGyroscope(Vector3d gyro, long sensorTimestampNs) {
		this.gyroLowPass.addSample(gyro, sensorTimestampNs);
		Vector3d.sub(gyro, this.gyroLowPass.getFilteredData(),
				this.smoothedGyroDiff);

		this.isGyroStatic
				.appendFrame(this.smoothedGyroDiff.length() < GYRO_DIFF_STATIC_THRESHOLD);

		if ((this.isGyroStatic.isRecentlyStatic())
				&& (this.isAccelStatic.isRecentlyStatic()))
			updateGyroBias(gyro, sensorTimestampNs);
	}

	public void processAccelerometer(Vector3d accel, long sensorTimestampNs) {
		this.accelLowPass.addSample(accel, sensorTimestampNs);
		Vector3d.sub(accel, this.accelLowPass.getFilteredData(),
				this.smoothedAccelDiff);
		this.isAccelStatic.appendFrame(this.smoothedAccelDiff.length() < ACCEL_DIFF_STATIC_THRESHOLD);
	}

	public void getGyroBias(Vector3d result) {
		if (this.gyroBiasLowPass.getNumSamples() < NUM_GYRO_BIAS_SAMPLES_THRESHOLD) {
			result.setZero();
		} else {
			result.set(this.gyroBiasLowPass.getFilteredData());

			double rampUpRatio = Math.min(1.0D,
					(this.gyroBiasLowPass.getNumSamples() - NUM_GYRO_BIAS_SAMPLES_THRESHOLD) / NUM_GYRO_BIAS_SAMPLES_INITIAL_SMOOTHING);

			result.scale(rampUpRatio);
		}
	}

	private void updateGyroBias(Vector3d gyro, long sensorTimestampNs) {
		if (gyro.length() >= GYRO_FOR_BIAS_THRESHOLD) {
			return;
		}

		double updateWeight = Math.max(0.0D,
				1.0D - gyro.length() / GYRO_FOR_BIAS_THRESHOLD);
		updateWeight *= updateWeight;
		this.gyroBiasLowPass.addWeightedSample(
				this.gyroLowPass.getFilteredData(), sensorTimestampNs,
				updateWeight);
	}

	private static class IsStaticCounter {
		private final int minStaticFrames;
		private int consecutiveIsStatic;

		IsStaticCounter(int minStaticFrames) {
			this.minStaticFrames = minStaticFrames;
		}

		void appendFrame(boolean isStatic) {
			if (!isStatic)
				this.consecutiveIsStatic = 0;
			else
				this.consecutiveIsStatic += 1;
		}

		boolean isRecentlyStatic() {
			return this.consecutiveIsStatic >= this.minStaticFrames;
		}
	}
}