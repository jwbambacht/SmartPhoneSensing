package com.example.whereami;

import android.util.Log;

public class StepDetector {

    private static final int ACCEL_RING_SIZE = 50;
    private static final int VEL_RING_SIZE = 10;
    private static final int STEP_DELAY_NS = 250000000;

    private int accelRingCounter = 0;
    private float[] accelRingZ = new float[ACCEL_RING_SIZE];
    private int velRingCounter = 0;
    private float[] velRing = new float[VEL_RING_SIZE];
    private long lastStepTimeNs = 0;
    private float oldVelocityEstimate = 0;

    private StepListener listener;

    public void registerListener(StepListener listener) {
        this.listener = listener;
    }


    public void updateAcceleration(long timeNs, float x, float y, float z) {
        float[] currentAcceleration = new float[3];
        currentAcceleration[0] = x;
        currentAcceleration[1] = y;
        currentAcceleration[2] = z;

        // First step is to update our guess of where the global z vector is.
        accelRingCounter++;
        accelRingZ[accelRingCounter % ACCEL_RING_SIZE] = currentAcceleration[2];

        float[] zAxis = new float[3];
        zAxis[2] = sum(accelRingZ) / Math.min(accelRingCounter, ACCEL_RING_SIZE);

        float normalization_factor = norm(zAxis);
        zAxis[2] = zAxis[2] / normalization_factor;

        float currentZ = dot(zAxis, currentAcceleration) - normalization_factor;
        velRingCounter++;
        velRing[velRingCounter % VEL_RING_SIZE] = currentZ;

        float velocityEstimate = sum(velRing);
        float STEP_THRESHOLD = listener.getSensitivity();

        if (velocityEstimate > STEP_THRESHOLD && oldVelocityEstimate <= STEP_THRESHOLD && (timeNs - lastStepTimeNs > STEP_DELAY_NS)) {
            listener.step(timeNs);
            int direction = listener.getDirection();
            listener.moveParticles(direction);

            lastStepTimeNs = timeNs;
        }
        oldVelocityEstimate = velocityEstimate;
    }

    public static float sum(float[] array) {
        float sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }

    public static float norm(float[] array) {
        float norm = 0;
        for (int i = 0; i < array.length; i++) {
            norm += array[i] * array[i];
        }
        return (float) Math.sqrt(norm);
    }


    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

}
