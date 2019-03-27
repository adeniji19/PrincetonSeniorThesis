package thesis.ogunlana.com.dronedelivery;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback, ValueEventListener {

    protected static final String TAG = "GPSActivity";
    private static String[][] pickUpSpots = new String[][]{
            new String[]{"Scully Courtyard", "40.344517", "-74.654794"},
            new String[]{"Yoseloff Courtyard", "40.344065", "-74.656240"},
            new String[]{"1963 Courtyard", "40.343804", "-74.657956"},
            new String[]{"Fisher Hall Courtyard", "40.344387", "-74.658086"},
            new String[]{"Behind Spellman", "40.344788", "-74.659599"},
            new String[]{"Pyne Courtyard", "40.345447", "-74.659644"},
            new String[]{"Henry Courtyard", "40.346085", "-74.660556"},
            new String[]{"Lockhart/Foulke Courtyard", "40.346832", "-74.660791"},
            new String[]{"Little Courtyard", "40.347043", "-74.659890"},
            new String[]{"Mathey Courtard", "40.347747", "-74.661598"},
            new String[]{"Rockefeller Courtyard", "40.348558", "-74.661565"},
            new String[]{"Hamilton Courtyard", "40.348329", "-74.662161"},
            new String[]{"Wilson Courtyard", "40.345336", "-74.656016"},
            new String[]{"1903 Courtyard", "40.346023", "-74.657142"}
    };
    private static String[][] commandLocations = new String[][]{
            new String[]{"Frist", "40.346355", "-74.654842"},
            new String[]{"PSafe", "40.342877", "-74.656820"}
    };

    private GoogleMap gMap;

    private Button locate, add, clear, commandLoc;
    private Button config, returnHome, start, clearRequest;
    private TextView destinationText, confirmationIdText;

    private boolean isAdd = false;
    private boolean isStart = false;
    private boolean destAdded = false;
    private String commandLocText = "Frist";
    private HashMap<String, String> currentRequest;

    private LatLng droneLocation = new LatLng(29.650744, -98.427593);
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 40.0f;
    private float mSpeed = 10.0f;

    private Timer sendVirtualStickDataTimer = new Timer();
    private SendVirtualStickDataTask sendVirtualStickDataTask;
    private final String[] shapes = {"SQUARE", "TRIANGLE", "LINE", "SPIN"};

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference mRootReference = firebaseDatabase.getReference();
    private DatabaseReference deliveryRequest = mRootReference.child("users");

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        returnHome = (Button) findViewById(R.id.returnHome);
        start = (Button) findViewById(R.id.start);
        clearRequest = (Button) findViewById(R.id.clearRequest);
        commandLoc = (Button) findViewById(R.id.commandLocationBtn);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        returnHome.setOnClickListener(this);
        start.setOnClickListener(this);
        clearRequest.setOnClickListener(this);
        commandLoc.setOnClickListener(this);

        destinationText = (TextView) findViewById(R.id.destinationText);
        confirmationIdText = (TextView) findViewById(R.id.confirmationId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        //Register BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    double droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    double droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    droneLocation = new LatLng(droneLocationLat, droneLocationLng);
                    updateDroneLocation();
                }
            });
        }
        else
        {
            Toast.makeText(this, "PROBLEM", Toast.LENGTH_LONG);
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) { }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) { }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) { }

        @Override
        public void onExecutionStart() { }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null){
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object

    }

    @Override
    public void onMapClick(LatLng point) {
        setResultToToast("Cannot Add Waypoint Manually");
    }

    private static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(droneLocation);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.box_package));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocation.latitude, droneLocation.longitude)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
        if(deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid") && droneLocation != null) {
            deliveryRequest.child(currentRequest.get("netid")).child("dronelocationlat").setValue(Double.toString(droneLocation.latitude));
            deliveryRequest.child(currentRequest.get("netid")).child("dronelocationlng").setValue(Double.toString(droneLocation.longitude));
        }
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate();
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                addDestination();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                destAdded = false;
                if (isAdd == true) {
                    isAdd = false;
                }
                if(waypointList != null && waypointMissionBuilder != null) {
                    waypointList.clear();
                    waypointMissionBuilder.waypointList(waypointList);
                    updateDroneLocation();
                }
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.returnHome:{
                returnDroneHome();
                break;
            }
            case R.id.start:{
                if(isStart)
                    stopWaypointMission();
                else
                    startWaypointMission();
                break;
            }
            case R.id.clearRequest:{
                clearRequest();
                break;
            }
            case R.id.commandLocationBtn: {
                chooseCommandLocation();
                break;
            }
            case R.id.psafeBtn: {
                commandLocText = "PSafe";
                commandLoc.setText("COMMAND LOCATION: PSAFE");
                break;
            }
            case R.id.fristBtn: {
                commandLocText = "Frist";
                commandLoc.setText("COMMAND LOCATION: FRIST");
                break;
            }
            default:
                break;
        }
    }

    private void clearRequest() {
        // delete request from database
        Log.d(TAG, "clearRequest: ");

        confirmationIdText.setText("Confirmation Code: Press 'ADD' to create");
        destinationText.setText("No Location Received");

        if(currentRequest == null) {
            return;
        }
        if(deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid")) {
            deliveryRequest.child(currentRequest.get("netid")).setValue(null);
            currentRequest = null;
        }
        else
            setResultToToast("Not Cleared Properly");
    }

    private void chooseCommandLocation() {
        LinearLayout commandLocationSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.command_choice_activity, null);
        Button psafeBtn = (Button) commandLocationSettings.findViewById(R.id.psafeBtn);
        Button fristBtn = (Button) commandLocationSettings.findViewById(R.id.fristBtn);
        psafeBtn.setOnClickListener(this);
        fristBtn.setOnClickListener(this);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(commandLocationSettings)
                .setPositiveButton("Done",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })
                .create()
                .show();
    }

    private void returnDroneHome() {
        LatLng commandBase = getPointFromString(commandLocText, commandLocations);
        markWaypoint(commandBase);
        Waypoint mWaypoint = new Waypoint(commandBase.latitude, commandBase.longitude, altitude);
        Waypoint mWaypoint2 = new Waypoint(commandBase.latitude, (commandBase.longitude + 0.000025), altitude);
        //Add Waypoints to Waypoint arraylist but only one point should be stored
        if (waypointMissionBuilder != null) {
            waypointList.add(mWaypoint);
            waypointList.add(mWaypoint2);
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        }else
        {
            waypointMissionBuilder = new WaypointMission.Builder();
            waypointList.add(mWaypoint);
            waypointList.add(mWaypoint2);
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        }
        setResultToToast("Return Home Mission Created");
    }

    private void addDestination() {
        if(currentRequest == null) {
            destinationText.setText("No Location Received");
            setResultToToast("No Active Drone Request");
            return;
        }
        LatLng point = getPointFromString(currentRequest.get("droplocation"), pickUpSpots);
        if(point.latitude == 0 && point.longitude == 0) {
            Log.d(TAG, "addDestination: location not found!");
            return;
        }
        if (isAdd == true && destAdded == false){
            destAdded = true;
            markWaypoint(point);
            Waypoint mWaypoint = null;
            Waypoint mWaypoint2 = null;
            LatLng commandBase = getPointFromString(commandLocText, commandLocations);
            if( latlngToMeters(commandBase.latitude, commandBase.longitude, point.latitude, point.longitude) >= 400 )
            {
                mWaypoint = new Waypoint((point.latitude + commandBase.latitude)/2, (point.longitude + commandBase.longitude)/2, altitude);
                mWaypoint2 = new Waypoint(point.latitude, (point.longitude), altitude);
            }
            else // wapoints can be made of the distance is less than 500 meters
            {
                mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
                mWaypoint2 = new Waypoint(point.latitude, (point.longitude + 0.000025), altitude);
            }
            //Add Waypoints to Waypoint arraylist but only one point should be stored
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointList.add(mWaypoint2);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointList.add(mWaypoint2);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        }else{
            setResultToToast("Cannot Add Waypoint");
        }
        if(!currentRequest.get("code").equals("null"))
            return;
        int idCode = (int)Math.round(1000 + Math.random()*9000); // create a 4 digit code
        confirmationIdText.setText("Confirmation Code: " + (int)idCode);

        createRequestNode(currentRequest.get("netid"), String.valueOf(idCode), currentRequest.get("identity"),
                currentRequest.get("droplocation"), droneLocation.latitude, droneLocation.longitude, Long.toString(System.currentTimeMillis()));
    }

    private double latlngToMeters(double lat1, double lon1, double lat2, double lon2){  // generally used geo measurement function
        double R = 6378.137; // Radius of earth in KM
        double dLat = lat2 * Math.PI / 180 - lat1 * Math.PI / 180;
        double dLon = lon2 * Math.PI / 180 - lon1 * Math.PI / 180;
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c;
        return d * 1000; // meters
    }


    private LatLng getPointFromString(String destText, String[][] locations) {
        Log.d(TAG, "getPointFromString: " + destText);
        for(String[] loc : locations) {
            if(destText.equals(loc[0])) {
                return new LatLng(Double.parseDouble(loc[1]), Double.parseDouble(loc[2]));
            }
        }
        return new LatLng(0,0);
    }

    private void cameraUpdate(){
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(droneLocation, zoomlevel);
        gMap.moveCamera(cu);

    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            //add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }

        });

        // have drone hover in order to do custom human ID verification prior to land
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO;
        // insert custom landing + human ID here

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        // drone always higher than lewis library (3rd highest building on campus)
                        altitude = Math.max(altitude, 40.0f);
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                        uploadWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void configWayPointMission(){

        if (waypointMissionBuilder == null){
            Log.d(TAG, "configWayPointMission: null");
            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            Log.d(TAG, "configWayPointMission: not null");
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){
            Log.d(TAG, "configWayPointMission: " + waypointMissionBuilder.getWaypointList().size());
            for (int i=0; i<waypointMissionBuilder.getWaypointList().size(); i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
            }

            setResultToToast("Set Waypoint attitude successfully");
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
            Log.d(TAG, "configWayPointMission: " + error.getDescription());
        }
    }

    private void uploadWayPointMission(){

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    private void startWaypointMission(){

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if(error == null)
                {
                    isStart = true;
                    start.setText("Stop");
                }
                else
                {
                    isStart = false;
                    start.setText("Start");
                }
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
        // THIS FEELS LIKE THE WRONG PLACE TO DO THIS CHECK. VERIFY LATER
        // Turns all lights off once mission completed in theory
        if(waypointMissionBuilder.isMissionComplete()) {
            humanID();
        }
    }

    private void humanID() {
        LEDsSettings.Builder ledSettingsBuilder = new LEDsSettings.Builder();
        ledSettingsBuilder.frontLEDsOn(false);
        ledSettingsBuilder.rearLEDsOn(false);
        mFlightController.setLEDsEnabledSettings(ledSettingsBuilder.build(), new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("LEDs Turned Off: " + (error == null ? "Successfully" : error.getDescription()));
                if(error == null) {
                    // do some checking stuff involving firebase and user
                    if(deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid")) {
                        deliveryRequest.child(currentRequest.get("netid")).child("lights").setValue("ON");
                    }
                    else
                        setResultToToast("lights not changed");
                }
            }
        });

        sendVirtualStickDataTimer = (sendVirtualStickDataTimer == null) ? new Timer() : sendVirtualStickDataTimer;
        int choice = (int)(Math.random() * 4);
        sendVirtualStickDataTask = new SendVirtualStickDataTask(shapes[choice], mFlightController);
        sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, 100);
        if(deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid")) {
            deliveryRequest.child(currentRequest.get("netid")).child("shape").setValue("SQUARE");
        }
        else
            setResultToToast("Shape not set");
    }

    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if(error == null)
                {
                    isStart = false;
                    start.setText("Start");
                }
                else
                {
                    isStart = true;
                    start.setText("Stop");
                }
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
    }

    private void createRequestNode(String netid, String code, String identity, String droplocation, double lat, double lng, String time) {
        netid = netid.replaceAll("\\.","");
        deliveryRequest.child(netid).child("code").setValue(code);
        deliveryRequest.child(netid).child("netid").setValue(netid);
        deliveryRequest.child(netid).child("identity").setValue(identity);
        deliveryRequest.child(netid).child("droplocation").setValue(droplocation);
        deliveryRequest.child(netid).child("dronelocationlat").setValue(Double.toString(lat));
        deliveryRequest.child(netid).child("dronelocationlng").setValue(Double.toString(lng));
        deliveryRequest.child(netid).child("time").setValue(time);
    }

    @Override
    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
        Log.d("DRONE", "CHANGED");
        HashMap<String, HashMap<String, String>> users;

        if(dataSnapshot.getKey() != null) {
            users = (HashMap<String, HashMap<String, String>>) dataSnapshot.getValue();
            double oldest = Double.MAX_VALUE;
            for (Map.Entry<String, HashMap<String, String>> entry : users.entrySet()) {
                HashMap<String, String> user = entry.getValue();
                Log.d(TAG, "onDataChange: " + currentRequest);
                Log.d(TAG, "onDataChange: " + user);
                if(user != null && user.containsKey("netid") && user.containsKey("code") && user.containsKey("identity") && user.containsKey("droplocation")) {
                    if (currentRequest == null &&  (user.get("netid").equals("emptyuser") || user.get("netid").equals("emptyuser2"))) {
                        currentRequest = null;
                        continue;
                    } else if (currentRequest == null) {
                        currentRequest = user;
                        oldest = Double.parseDouble(currentRequest.get("time"));
                    }

                    if (user.get("netid").equals(currentRequest.get("netid")) && (Double.parseDouble(user.get("time")) < oldest)) {
                            currentRequest = user;
                            oldest = Double.parseDouble(user.get("time"));
                            destinationText.setText(user.toString());
                    }
                    if (user.get("netid").equals(currentRequest.get("netid")) && currentRequest.get("code").equals("0000")) {
                        confirmationIdText.setText("Package Recieved By Student");
                    }
                }
            }
        }
    }

    @Override
    public void onCancelled(@NonNull DatabaseError databaseError) {

    }

    protected void onStart() {
        super.onStart();
        deliveryRequest.addValueEventListener(this);
    }
}
