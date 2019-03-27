package thesis.ogunlana.com.dronedelivery;

import android.util.Log;

import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;

public class SendVirtualStickDataTask extends TimerTask {
    private final String TAG = "SendVirtualStickDataTask";
    private String shape;
    private FlightController flightController;
    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;
    private long t0;

    public SendVirtualStickDataTask(String shape, FlightController flightController) {
        this.shape = shape;
        this.flightController = flightController;
        resetVals();
    }

    @Override
    public void run() {
        if (flightController != null) {
            if(shape.equals("SPIN")) {
                spin();
            }
            else if(shape.equals("LINE")) {
                line();
            }
            else if(shape.equals("TRIANGLE")) {
                triangle();
            }
            else if(shape.equals("SQUARE")){
                square();
            }
        }
        else {
            Log.d(TAG, "flightController is null for shape: " + shape);
        }
    }

    // Function for making the drone spin in a circle for 6 seconds
    private void spin() {
        resetVals();
        if (System.currentTimeMillis() - t0 < 6000) {
            yaw = (float) 2;
            flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {

                        }
                    });
        } else {
            cancel();
        }
    }

    // Function for flying drone back and forth in straight line 3 times
    private void line() {
        resetVals();
        float speed = 3;
        for(int iterations = 0; iterations < 3; iterations++) {
            t0 = System.currentTimeMillis();
            if (System.currentTimeMillis() - t0 < 2000) {
                pitch = speed;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 4000) {
                pitch = -1*speed;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else {
                cancel();
            }
        }
    }

    // Function for flying drone in triangle shape 3 times
    private void triangle() {
        for(int iterations = 0; iterations < 3; iterations++) {
            resetVals();
            t0 = System.currentTimeMillis();
            if (System.currentTimeMillis() - t0 < 2000) {
                roll = 3;
                pitch = 0;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if(System.currentTimeMillis() - t0 < 2500) {
                resetVals();
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 4500) {
                roll = (float)-1.5;
                pitch = (float)(-3*Math.sqrt(3)/2);
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if(System.currentTimeMillis() - t0 < 5000) {
                resetVals();
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 7000) {
                roll = (float)-1.5;
                pitch = (float)(3*Math.sqrt(3)/2);
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else {
                cancel();
            }
        }
    }

    // Function for flying drone in square shape
    private void square() {
        resetVals();
        float speed = 3;
        for(int iterations = 0; iterations < 3; iterations++) {
            resetVals();
            t0 = System.currentTimeMillis();
            if (System.currentTimeMillis() - t0 < 2000) {
                roll = speed;
                pitch = 0;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 2500) {
                resetVals();
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 4500) {
                roll = 0;
                pitch = -speed;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 5000) {
                resetVals();
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 7000) {
                roll = -speed;
                pitch = 0;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 7500) {
                resetVals();
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else if (System.currentTimeMillis() - t0 < 9500) {
                roll = 0;
                pitch = speed;
                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch, roll, yaw, throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            } else {
                cancel();
            }
        }
    }

    // Function for resetting all values to 0
    private void resetVals() {
        pitch = 0;
        roll = 0;
        yaw = 0;
        throttle = 0;
        t0 = System.currentTimeMillis();
    }
}
