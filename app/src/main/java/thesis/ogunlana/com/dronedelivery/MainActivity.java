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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
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

public class MainActivity extends FragmentActivity implements View.OnClickListener,
        GoogleMap.OnMapClickListener, OnMapReadyCallback, ValueEventListener {

    protected static final String TAG = "MainActivity";
    // Drone Locations
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
            new String[]{"1903 Courtyard", "40.346023", "-74.657142"},
            new String[]{"Rugby Field Drop", "40.338802", "-74.643799"},
            new String[]{"Poe Field Drop", "40.343500", "-74.653576"}
    };
    private static String[][] commandLocations = new String[][]{
            new String[]{"Frist", "40.346355", "-74.654842"},
            new String[]{"PSafe", "40.342877", "-74.656820"},
            new String[]{"Rugby Field", "40.338281", "-74.643868"},
            new String[]{"Poe Field", "40.343278", "-74.653905"}
    };
    private final String[] shapes = {"LINE", "TRIANGLE", "SQUARE", "ZIGZAG"};

    //==============================================================================================
    //
    // VARIABLES
    //
    //==============================================================================================
    // UI Variables
    private Button locate, add, clear, commandLoc, config, returnHome, start, clearRequest;
    private TextView destinationText, confirmationIdText;
    private boolean isAdd = false, isStart = false;
    private int idCount = 0;
    private boolean destAdded = false;
    private String commandLocText = "Frist";

    // Google Map Variables
    private LatLng droneLocation = new LatLng(29.650744, -98.427593);
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;
    private GoogleMap gMap;

    // Default Drone Settings
    private float altitude = 40.0f;
    private float mSpeed = 10.0f;

    // Drone Control Variables
    private List<Waypoint> waypointList = new ArrayList<>();
    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private boolean returning = false;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    // Firebase Variables
    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference mRootReference = firebaseDatabase.getReference();
    private DatabaseReference deliveryRequest = mRootReference.child("users");
    private HashMap<String, String> currentRequest;

    //==============================================================================================
    //
    // ANDROID APP NECESSARY DEFAULT METHODS
    //
    //==============================================================================================
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

    public void onReturn(View view){ this.finish(); }

    //==============================================================================================
    //
    // HELPER METHODS
    //
    //==============================================================================================

    // find midpoint between two points on earth
    public static LatLng midPoint(double lat1,double lon1,double lat2,double lon2){

        double dLon = Math.toRadians(lon2 - lon1);

        //convert to radians
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        lon1 = Math.toRadians(lon1);

        double Bx = Math.cos(lat2) * Math.cos(dLon);
        double By = Math.cos(lat2) * Math.sin(dLon);
        double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2), Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx) + By * By));
        double lon3 = lon1 + Math.atan2(By, Math.cos(lat1) + Bx);

        //print out in degrees
        //System.out.println(Math.toDegrees(lat3) + " " + Math.toDegrees(lon3));
        return new LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3));
    }

    // Helper Method for printing toast messages
    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Convert String location into LatLng location
    private LatLng getPointFromString(String destText, String[][] locations) {
        //Log.d(TAG, "getPointFromString: " + destText);
        for(String[] loc : locations) {
            if(destText.equals(loc[0])) {
                return new LatLng(Double.parseDouble(loc[1]), Double.parseDouble(loc[2]));
            }
        }
        return new LatLng(0,0);
    }

    // check string contains only an integer
    private String nulltoIntegerDefault(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }
    // helper for nulltoIntegerDefault
    private boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    // Check valid GPS Coordinate
    private static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180)
                && (latitude != 0f && longitude != 0f);
    }

    // inside designated area near the dropLocation?
    private boolean closeEnoughForHumanID(LatLng drop, LatLng drone) {
        double range = 0.000005;

        double longDif = drop.longitude - drone.longitude;
        longDif = (longDif > 0) ? longDif : -1*longDif;
        double latDif = drop.latitude - drone.latitude;
        latDif = (latDif > 0) ? latDif : -1*latDif;
        if(longDif < range && latDif < range)
            return true;
        return false;
    }

    // Handle enable and disabling add button
    private void enableDisableAdd(){
        if (!isAdd) {
            isAdd = true;
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    // Initialize all UI variables
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

    // Clear all images (markers & package image) on Google Map
    private void clearMap() {
        returning = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gMap.clear();
            }
        });
    }

    // Make thread sleep
    private void sleep(int secs) {
        try
        {
            Thread.sleep(secs);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    // Update student request node in firebase database
    private void createRequestNode(String netid, String code, String identity, String droplocation, double lat, double lng, String time) {
        netid = netid.replaceAll("\\.","");
        deliveryRequest.child(netid).child("code").setValue(code);
        deliveryRequest.child(netid).child("netid").setValue(netid);
        deliveryRequest.child(netid).child("identity").setValue(identity);
        deliveryRequest.child(netid).child("droplocation").setValue(droplocation);
        deliveryRequest.child(netid).child("dronelocationlat").setValue(Double.toString(lat));
        deliveryRequest.child(netid).child("dronelocationlng").setValue(Double.toString(lng));
        deliveryRequest.child(netid).child("time").setValue(time);
        deliveryRequest.child(netid).child("shape").setValue("null");
    }

    // Add marker to google map
    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    //==============================================================================================
    //
    // MAIN CODE
    //
    //==============================================================================================

    // onCreate to initialize MainActivity
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
                }, 1);
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

    // Handle All Button Clicks
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
                clearMap();
                destAdded = false;
                if (isAdd) {
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
                if(isStart) {
                    stopWaypointMission();
                }
                else {
                    idCount++;
                    startWaypointMission();
                }
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
            case R.id.rugbyBtn: {
                commandLocText = "Rugby Field";
                commandLoc.setText("COMMAND LOCATION: Rugby");
                break;
            }
            default:
                break;
        }
    }

    // Center google map camera on droneLocation
    private void cameraUpdate(){
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(droneLocation, zoomlevel);
        gMap.moveCamera(cu);
    }

    // Set next waypoint to user's location and add marker to map
    private void addDestination() {
        // no requests present currently in firebase
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
        if (isAdd && !destAdded){
            destAdded = true;
            markWaypoint(point);
            LatLng commandBase = getPointFromString(commandLocText, commandLocations);

            // create a waypoint at halfway point (single waypoint dist cannot be > 500meters)
            /*Waypoint mWaypoint = new Waypoint((point.latitude + commandBase.latitude)/2.0,
                    (point.longitude + commandBase.longitude)/2.0, altitude);*/
            LatLng halfPoint = midPoint(droneLocation.latitude, droneLocation.longitude, point.latitude, point.longitude);
            Waypoint mWaypoint = new Waypoint(halfPoint.latitude, halfPoint.longitude, altitude);
            Waypoint mWaypoint2 = new Waypoint(point.latitude, point.longitude, altitude);

            //Add Waypoints to Waypoint arraylist
            waypointMissionBuilder = (waypointMissionBuilder == null) ? new WaypointMission.Builder() : waypointMissionBuilder;
            waypointList.add(mWaypoint);
            waypointList.add(mWaypoint2);
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            if(returning)
                mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
            else
                mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        }else{
            setResultToToast("Cannot Add Waypoint");
        }
        if(!currentRequest.get("code").equals("null"))
            return;
        // generate a 4 digit delivery code (to be added to user's package)
        int idCode = (int)Math.round(1000 + Math.random()*9000);
        confirmationIdText.setText("Confirmation Code: " + (int)idCode);

        createRequestNode(currentRequest.get("netid"), String.valueOf(idCode), currentRequest.get("identity"),
                currentRequest.get("droplocation"), droneLocation.latitude, droneLocation.longitude, Long.toString(System.currentTimeMillis()));
    }

    // Update the drone location on the google map
    // Update the drone location in the firebase database
    // begin humanID phase if drone is close enough to intended destination
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
        if(currentRequest != null && currentRequest.containsKey("droplocation") && droneLocation != null) {
            LatLng droplocation = getPointFromString(currentRequest.get("droplocation"), pickUpSpots);
            if (closeEnoughForHumanID(droplocation, droneLocation) && idCount < 2) {
                idCount++;
                humanID(droneLocation);
            }
        }
    }

    // Handle configuration setting of speed and altitude set by command and then upload mission
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
        if(returning)
            mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
        else
            mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
        mHeadingMode = WaypointMissionHeadingMode.AUTO;

        new AlertDialog.Builder(this)
            .setTitle("").setView(wayPointSettings)
            .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int id) {

                    String altitudeString = wpAltitude_TV.getText().toString();
                    altitude = Integer.parseInt(nulltoIntegerDefault(altitudeString));
                    // drone always higher than lewis library (3rd highest building on campus)
                    altitude = Math.max(altitude, 40.0f);
                    configWayPointMission();
                    uploadWayPointMission();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            }).create().show();
    }

    // Set next waypoint to command's home base location and add marker to map
    private void returnDroneHome() {
        LatLng commandBase = getPointFromString(commandLocText, commandLocations);
        markWaypoint(commandBase);
        LatLng halfPoint = midPoint(droneLocation.latitude, droneLocation.longitude, commandBase.latitude, commandBase.longitude);
        Waypoint mWaypoint = new Waypoint(halfPoint.latitude, halfPoint.longitude, altitude);
        Waypoint mWaypoint2 = new Waypoint(commandBase.latitude, commandBase.longitude, altitude);
        //Add Waypoints to Waypoint arraylist but only one point should be stored
        waypointMissionBuilder = (waypointMissionBuilder == null) ? new WaypointMission.Builder() : waypointMissionBuilder;
        waypointList.add(mWaypoint);
        waypointList.add(mWaypoint2);
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        returning = true;
        mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
        setResultToToast("Return Home Mission Created");
    }

    // End the current waypoint mission
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
                if(error != null)
                    Log.d("mission stop", error.getDescription());
            }
        });
    }

    // Start the current waypoint mission
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
                if(error != null)
                    Log.d("Mission Start", error.getDescription());
            }
        });
    }

    // Clear a student request from the firebase database (after request is completed ideally)
    private void clearRequest() {
        // delete request from database
        Log.d(TAG, "clearRequest: ");

        confirmationIdText.setText("Confirmation Code: Press 'ADD' to create");
        destinationText.setText("No Location Received");

        if(currentRequest == null) {
            return;
        }
        if(deliveryRequest != null && currentRequest.containsKey("netid")) {
            deliveryRequest.child(currentRequest.get("netid")).setValue(null);
            currentRequest = null;
        }
        else
            setResultToToast("Not Cleared Properly");
    }

    // Choose command base's return location
    private void chooseCommandLocation() {
        LinearLayout commandLocationSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.command_choice_activity, null);
        Button psafeBtn = (Button) commandLocationSettings.findViewById(R.id.psafeBtn);
        Button fristBtn = (Button) commandLocationSettings.findViewById(R.id.fristBtn);
        Button rugbyBtn = (Button) commandLocationSettings.findViewById(R.id.rugbyBtn);
        psafeBtn.setOnClickListener(this);
        fristBtn.setOnClickListener(this);
        rugbyBtn.setOnClickListener(this);

        new AlertDialog.Builder(this)
                .setTitle("").setView(commandLocationSettings)
                .setPositiveButton("Done",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                    }
                }).create().show();
    }

    // Begin randomized custom flight path
    // Drone flies in 1 of 4 shapes (line, triangle, square, zigzag)
    private void humanID(LatLng location) {
        stopWaypointMission();
        LEDsSettings.Builder ledSettingsBuilder = new LEDsSettings.Builder();
        ledSettingsBuilder.frontLEDsOn(true);
        ledSettingsBuilder.rearLEDsOn(true);
        mFlightController.setLEDsEnabledSettings(ledSettingsBuilder.build(), new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if(error == null && deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid")) {
                    deliveryRequest.child(currentRequest.get("netid")).child("lights").setValue("ALLON");
                }
            }
        });

        waypointMissionBuilder = (waypointMissionBuilder == null) ? new WaypointMission.Builder() : waypointMissionBuilder;
        int choice = (int)(Math.random() * shapes.length);
        //int choice = 3;
        updateWaypoints(choice, location);
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size()).repeatTimes(3);


        sleep(1000);
        configWayPointMission();
        sleep(1000);
        uploadWayPointMission();
        sleep(1500);
        startWaypointMission();

        if(deliveryRequest != null && currentRequest != null  && currentRequest.containsKey("netid")) {
            deliveryRequest.child(currentRequest.get("netid")).child("shape").setValue(shapes[choice]);
        }
        else
            setResultToToast("Shape not set");
    }

    // Flight paths for the humanID function
    private void updateWaypoints(int x, LatLng loc) {
        waypointList.clear();
        clearMap();
        switch (x) {
            case 0: // line path
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markWaypoint(new LatLng(loc.latitude, (loc.longitude + 0.0001)));
                        markWaypoint(new LatLng(loc.latitude, (loc.longitude - 0.0001)));
                    }
                });
                waypointList.add(new Waypoint(loc.latitude, (loc.longitude + 0.0001), altitude));
                waypointList.add(new Waypoint(loc.latitude, (loc.longitude - 0.0001), altitude));
                break;
            case 1: // triangle path
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markWaypoint(new LatLng((loc.latitude + 0.0000433), (loc.longitude + 0.000025)));
                        markWaypoint(new LatLng((loc.latitude - 0.0000433), (loc.longitude + 0.000025)));
                        markWaypoint(new LatLng(loc.latitude, (loc.longitude - 0.00005)));
                    }
                });
                waypointList.add(new Waypoint((loc.latitude + 0.0000433), (loc.longitude + 0.000025), altitude));
                waypointList.add(new Waypoint((loc.latitude - 0.0000433), (loc.longitude + 0.000025), altitude));
                waypointList.add(new Waypoint(loc.latitude, (loc.longitude - 0.00005), altitude));
                break;
            case 2: // square path
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markWaypoint(new LatLng(loc.latitude, (loc.longitude + 0.0001)));
                        markWaypoint(new LatLng((loc.latitude + 0.0001), loc.longitude));
                        markWaypoint(new LatLng(loc.latitude, (loc.longitude - 0.0001)));
                        markWaypoint(new LatLng((loc.latitude - 0.0001), loc.longitude));
                    }
                });
                waypointList.add(new Waypoint(loc.latitude, (loc.longitude + 0.0001), altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0001), loc.longitude, altitude));
                waypointList.add(new Waypoint(loc.latitude, (loc.longitude - 0.0001), altitude));
                waypointList.add(new Waypoint((loc.latitude - 0.0001), loc.longitude, altitude));
                break;
            case 3: // zigzag
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markWaypoint(new LatLng(loc.latitude, loc.longitude));
                        markWaypoint(new LatLng((loc.latitude + 0.0001), loc.longitude));
                        markWaypoint(new LatLng((loc.latitude + 0.0001), (loc.longitude + 0.0001)));
                        markWaypoint(new LatLng((loc.latitude + 0.0002), (loc.longitude + 0.0001)));
                    }
                });
                waypointList.add(new Waypoint(loc.latitude, loc.longitude, altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0001), loc.longitude, altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0001), (loc.longitude + 0.0001), altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0002), (loc.longitude + 0.0001), altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0001), (loc.longitude + 0.0001), altitude));
                waypointList.add(new Waypoint((loc.latitude + 0.0001), loc.longitude, altitude));
                break;
            default:
                setResultToToast("choice error");
        }
    }

    @Override
    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
        HashMap<String, HashMap<String, String>> users;
        if(dataSnapshot.getKey() != null) {
            users = (HashMap<String, HashMap<String, String>>) dataSnapshot.getValue();
            double oldest = Double.MAX_VALUE;
            for (Map.Entry<String, HashMap<String, String>> entry : users.entrySet()) {
                HashMap<String, String> user = entry.getValue();
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
                        String codeTemp, shapeTemp, latTemp, lngTemp, netidTemp, idenTemp, timeTemp;
                        try{codeTemp = user.get("code");}catch (Exception e){codeTemp = "null";}
                        try{shapeTemp = user.get("shape");}catch (Exception e){shapeTemp = "null";}
                        try{latTemp = user.get("dronelocationlat");}catch (Exception e){latTemp = "null";}
                        try{lngTemp = user.get("dronelocationlng");}catch (Exception e){lngTemp = "null";}
                        try{netidTemp = user.get("netid");}catch (Exception e){netidTemp = "null";}
                        try{idenTemp = user.get("identity");}catch (Exception e){idenTemp = "null";}
                        try{timeTemp = timeConvert(user.get("time"));}catch (Exception e){timeTemp = "null";}
                        String userInfo = String.format("Code: %s Shape: %s\nLatitude: %s\nLongitude: %s\nNetID: %s\nIdentity: : %s\nTime: %s",
                                codeTemp, shapeTemp, latTemp, lngTemp, netidTemp, idenTemp, timeTemp);
                        destinationText.setText(userInfo);
                    }
                    if (user.get("netid").equals(currentRequest.get("netid")) && currentRequest.get("code").equals("0000")) {
                        confirmationIdText.setText("Package Recieved By Student");
                        mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                        returning = true;
                    }

                    if (user.containsKey("shape") && user.get("shape").equals("CONFIRMED")) {
                        deliveryRequest.child(currentRequest.get("netid")).child("shape").setValue("LANDING");
                        stopWaypointMission();
                        sleep(3000);
                        land();
                    } else if (user.containsKey("shape") && user.get("shape").equals("UNCONFIRMED")) {
                        stopWaypointMission();
                        setResultToToast("User failed to verify. Return Home.");
                    }
                }
            }
        }
    }

    // land the drone and end mission
    private void land() {
        clearMap();
        waypointList.clear();
        LatLng location = getPointFromString(currentRequest.get("droplocation"), pickUpSpots);
        markWaypoint(location);
        //LatLng halfPoint = midPoint(droneLocation.latitude, droneLocation.longitude, location.latitude, location.longitude);
        Waypoint mWaypoint = new Waypoint(location.latitude, (location.longitude+0.00001), altitude);
        Waypoint mWaypoint2 = new Waypoint(location.latitude, location.longitude, altitude);
        //Add Waypoints to Waypoint arraylist but only one point should be stored
        waypointMissionBuilder = (waypointMissionBuilder == null) ? new WaypointMission.Builder() : waypointMissionBuilder;
        waypointList.add(mWaypoint);
        waypointList.add(mWaypoint2);
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size()).repeatTimes(0);
        mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;

        sleep(1000);
        configWayPointMission();
        sleep(1000);
        uploadWayPointMission();
        sleep(1500);
        startWaypointMission();
    }

    //==============================================================================================
    //
    // Necessary DJI methods
    //
    //==============================================================================================

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    // login to user account
    private void loginAccount(){
        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:" + error.getDescription());
                    }
                });
    }

    // Initialize flight controller and constantly update drone location
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
            setResultToToast("controller error");
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    // Remove listener
    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    // Waypoint listener
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

    //==============================================================================================
    //
    // Necessary Google Map methods
    //
    //==============================================================================================

    // Get current waypoint operator instance
    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null){
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    // map listener
    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object

    }

    // Don't allow user to add waypoints through the map
    @Override
    public void onMapClick(LatLng point) {
        setResultToToast("Cannot Add Waypoint Manually");
    }

    // initialize map
    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
    }

    //==============================================================================================
    //
    // Waypoint Helper Methods
    //
    //==============================================================================================

    // Convert epoch to regular date/time
    private String timeConvert(String epoch) {
        if (epoch.equals("null"))
            return "null";
        Date date = new Date(Long.parseLong(epoch));
        DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        String formatted = format.format(date);
        return formatted;
    }

    // Configure waypoint mission
    private void configWayPointMission(){
        waypointMissionBuilder = (waypointMissionBuilder == null) ? new WaypointMission.Builder() : waypointMissionBuilder;
        waypointMissionBuilder.finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

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

    // Upload waypoint mission
    private void uploadWayPointMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                    Log.d("upload", error.getDescription());
                }
            }
        });
    }

    //==============================================================================================
    //
    // Firebase Helper Methods
    //
    //==============================================================================================

    @Override
    public void onCancelled(@NonNull DatabaseError databaseError) { }

    protected void onStart() {
        super.onStart();
        deliveryRequest.addValueEventListener(this);
    }
}
