package com.arkletech.networksetting;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final int PERMISSION_ACCESS_COARSE_LOCATION = 100;
    private final String LOG_TAG = "CustomNetwork";

    private String mConnectWifiSSID = "blizzard";

    private ScanResult theOneAP=null;

    private AlertDialog mWifiAlertDialog=null;
    private Spinner mWifiListSpinner=null;

    private List<String> mWifiScannedList;

    private Button mDhcpRefreshButtonView;
    private Button mStaticRefreshButtonView;
    private TextView mApSSIDView;
    private TextView mDhcpIpAddressTextView;
    private TextView mDhcpGatewayTextView;
    private TextView mDhcpMaskTextView;
    private TextView mDhcpDns1TextView;
    private TextView mDhcpDns2TextView;

    private TextView mStaticIpAddressTextView;
    private TextView mStaticGatewayTextView;
    private TextView mStaticMaskTextView;
    private TextView mStaticDns1TextView;
    private TextView mStaticDns2TextView;

    private Button mTransferButton;
    private Button mUpdateButton;

    private int mMaskBits;

    private BroadcastReceiver mScanResultsReceiver=null;
    private boolean mIsScanResultsReceiverRegistered=false;
    private BroadcastReceiver mNetworkStateChangedReceiver=null;
    private boolean mIsNetworkStateChangedReceiverRegistered=false;

    private WifiManager mWifiManager;

    private String mWifiUser=null, mWifiPass=null;

/*
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }

    };
*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        // HACK: Have to do this here to get the Scan Wifi working (bug in Android?!)
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mTransferButton = (Button) findViewById(R.id.bt_transfer);
        mTransferButton.setOnClickListener(this);

        mUpdateButton = (Button) findViewById(R.id.bt_static_update);
        mUpdateButton.setOnClickListener(this);
        mUpdateButton.setEnabled(false);

        mWifiAlertDialog = createWifiCredentialAlertDialog(this);

//        mWifiListSpinner = (Spinner) mWifiAlertDialog.findViewById(R.id.sp_wifi_list);

//        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
//        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        // If the network is already configured, then may need to "forget" the network if this app did not created the network
        if (!isNetworkConfigured(mConnectWifiSSID)) {
            mScanResultsReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LOG_TAG, "ScanResults:onReceive(): " + intent.getAction() + ", Wifi Status: " + mWifiManager.getWifiState());
                    if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                        if (mIsScanResultsReceiverRegistered) {
                            unregisterReceiver(mScanResultsReceiver);
                            mIsScanResultsReceiverRegistered = false;
                        }
                        List<ScanResult> scanResultList = mWifiManager.getScanResults();
                        Log.d(LOG_TAG, "BroadcastReceiver::scanResultList.size()=" + scanResultList.size());
                        Log.d(LOG_TAG, "BroadcastReceiver::scanResultList=" + scanResultList);
                        int level = -10000;
                        mWifiScannedList = new ArrayList<String>();
                        for (ScanResult result : scanResultList) {
                            if (!mWifiScannedList.contains(result.SSID) && result.capabilities.contains("EAP"))
                                mWifiScannedList.add(result.SSID);
                            // result.SSID does not include the quotes "" around the AP name
                            if (result.SSID.equals(mConnectWifiSSID)) {
                                if (result.level > level) {
                                    level = result.level;
                                    theOneAP = result;
                                }
                            }
                        }
                        Log.d(LOG_TAG, "Scanned List (with EAP): "+mWifiScannedList);

                        if (theOneAP != null) {
                            Log.d(LOG_TAG, "Interested AP:");
                            Log.d(LOG_TAG, "    SSID=" + theOneAP.SSID);
                            Log.d(LOG_TAG, "    BSSID=" + theOneAP.BSSID);
                            Log.d(LOG_TAG, "    capabilities=" + theOneAP.capabilities);
                            Log.d(LOG_TAG, "    centerFreq0=" + theOneAP.centerFreq0);
                            Log.d(LOG_TAG, "    centerFreq1=" + theOneAP.centerFreq1);
                            Log.d(LOG_TAG, "    channelWidth=" + theOneAP.channelWidth);
                            Log.d(LOG_TAG, "    frequency=" + theOneAP.frequency);
                            Log.d(LOG_TAG, "    level=" + theOneAP.level);
                            Log.d(LOG_TAG, "    operatorFriendlyName=" + theOneAP.operatorFriendlyName);
                            Log.d(LOG_TAG, "    passpoint=" + theOneAP.isPasspointNetwork());
                            // show it
                            if (!isNetworkConfigured(mConnectWifiSSID) && mWifiAlertDialog != null) {
                                ArrayAdapter<String> adapter;
                                adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.spinner_item, mWifiScannedList);
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                mWifiListSpinner.setAdapter(adapter);
                                mWifiListSpinner.setSelection(adapter.getPosition(mConnectWifiSSID));
                                mWifiAlertDialog.show();
                            }
                        } else {
                            Log.e(LOG_TAG, "ERROR: did not find the AP with SSID=\"" + mConnectWifiSSID + "\"!!");
                        }
                    }
                }
            };

            mNetworkStateChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LOG_TAG, "NetworkStateChanged:onReceive(): "+intent.getAction()+", Wifi Status: "+mWifiManager.getWifiState());
                    if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                        if (mWifiManager.getConnectionInfo().getIpAddress() != 0) {
                            updateNetworkInfo(true);
                            mUpdateButton.setEnabled(true);
                        }
                    }
                }
            };

            registerReceiver(mScanResultsReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            mIsScanResultsReceiverRegistered = true;
            registerReceiver(mNetworkStateChangedReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
            mIsNetworkStateChangedReceiverRegistered = true;
            if (mWifiManager.startScan() == false) {
                Log.e(LOG_TAG, "ERROR: Call StartScan() failed!!");
            }
        }
        else {  // network already configured
            mUpdateButton.setEnabled(true);
        }

        mDhcpRefreshButtonView = (Button) findViewById(R.id.bt_dhcp_refresh);
        mDhcpRefreshButtonView.setOnClickListener(this);
        mApSSIDView = (TextView)findViewById(R.id.et_dhcp_ssid);
        mDhcpIpAddressTextView = (TextView)findViewById(R.id.et_dhcp_ip_address);
        mDhcpGatewayTextView = (TextView) findViewById(R.id.et_dhcp_gateway);
        mDhcpMaskTextView = (TextView) findViewById(R.id.et_dhcp_mask);
        mDhcpDns1TextView = (TextView) findViewById(R.id.et_dhcp_dns_1);
        mDhcpDns2TextView = (TextView) findViewById(R.id.et_dhcp_dns_2);


        mStaticRefreshButtonView = (Button) findViewById(R.id.bt_static_refresh);
        mStaticRefreshButtonView.setOnClickListener(this);
        mStaticIpAddressTextView = (TextView)findViewById(R.id.et_static_ip_address);
        mStaticGatewayTextView = (TextView) findViewById(R.id.et_static_gateway);
        mStaticMaskTextView = (TextView) findViewById(R.id.et_static_mask);
        mStaticDns1TextView = (TextView) findViewById(R.id.et_static_dns_1);
        mStaticDns2TextView = (TextView) findViewById(R.id.et_static_dns_2);

        updateNetworkInfo(true);

        // Hard code the settings for now
        mStaticDns1TextView.setText("10.157.100.53");
        ((CheckBox)findViewById(R.id.cb_update_static_dns1)).setChecked(true);
        mStaticMaskTextView.setText("255.255.252.0");
        ((CheckBox)findViewById(R.id.cb_update_mask)).setChecked(false);
        ((CheckBox)findViewById(R.id.cb_update_static_mask)).setChecked(true);
        // ^^^^^^^^^^^^^^

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

//        getWifiNetworkInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mScanResultsReceiver != null && mIsScanResultsReceiverRegistered) {
            unregisterReceiver(mScanResultsReceiver);
            mIsScanResultsReceiverRegistered = false;
        }
        if (mNetworkStateChangedReceiver != null && mIsNetworkStateChangedReceiverRegistered) {
            unregisterReceiver(mNetworkStateChangedReceiver);
            mIsNetworkStateChangedReceiverRegistered = false;
        }
        if (mWifiAlertDialog != null) {
            mWifiAlertDialog.dismiss();
            mWifiAlertDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mScanResultsReceiver != null && !mIsScanResultsReceiverRegistered) {
            registerReceiver(mScanResultsReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            mIsScanResultsReceiverRegistered = true;
        }
        if (mNetworkStateChangedReceiver != null && !mIsNetworkStateChangedReceiverRegistered) {
            registerReceiver(mNetworkStateChangedReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
            mIsNetworkStateChangedReceiverRegistered = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(LOG_TAG, "onRequestPermissionsResult():requestCode="+requestCode+", permissions="+permissions+", grantResults="+grantResults);
        switch (requestCode) {
            case PERMISSION_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                } else {
                    // permission denied, boo!
                }
                break;
            default:
                break;
        }
    }

    private void updateNetworkInfo(boolean forDhcp) {
        DhcpInfo info = mWifiManager.getDhcpInfo();

        String ssid = mWifiManager.getConnectionInfo().getSSID();
        int ipAddress = info.ipAddress;
        int dns1 = info.dns1;
        int dns2 = info.dns2;
        int gateway = info.gateway;
        int mask = info.netmask;
        int serverIp = info.serverAddress;

        mMaskBits = countBits(mask);

        Log.d(LOG_TAG, "ipAddress="+intToIp(ipAddress)+", gateway="+intToIp(gateway)+", mask="+intToIp(mask)+", dns1="+intToIp(dns1)+", dns2="+intToIp(dns2)+", serverIp="+intToIp(serverIp)+", mMaskBits="+mMaskBits);

        if (forDhcp == true) {
            mApSSIDView.setText(ssid);
            mDhcpIpAddressTextView.setText(intToIp(ipAddress));
            mDhcpGatewayTextView.setText(intToIp(gateway));
            mDhcpMaskTextView.setText(intToIp(mask));
            mDhcpDns1TextView.setText(intToIp(dns1));
            mDhcpDns2TextView.setText(intToIp(dns2));
        } else {
            mStaticIpAddressTextView.setText(intToIp(ipAddress));
            mStaticGatewayTextView.setText(intToIp(gateway));
            mStaticMaskTextView.setText(intToIp(mask));
            mStaticDns1TextView.setText(intToIp(dns1));
            mStaticDns2TextView.setText(intToIp(dns2));
        }
    }

    /**
     * Convert int IP adress to String
     * cf. http://teneo.wordpress.com/2008/12/23/java-ip-address-to-integer-and-back/
     */
    private String intToIp(int i) {
        return ( i & 0xFF) + "." +
                (( i >> 8 ) & 0xFF) + "." +
                (( i >> 16 ) & 0xFF) + "." +
                (( i >> 24 ) & 0xFF);
    }

    private int ipToInt(String address) {
        int result = 0;
        int shiftBits = 0;
        for(String part : address.split(Pattern.quote("."))) {
            result |= (Integer.parseInt(part) << shiftBits);
            shiftBits += 8;
        }
        Log.d(LOG_TAG, "ipToInt(\""+address+"\")="+Integer.toHexString(result));
        return result;
    }

    private int countBits(int i) {
        int cnt=0;
        for (cnt=0; (i & 1) != 0; cnt++)
            i >>= 1;
        return cnt;
    }

    boolean isNetworkConfigured(String ssid) {
        boolean configured = false;
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();

        for (WifiConfiguration wifiConfiguration : configs) {
            if (wifiConfiguration.SSID.contains(ssid)) {
                Log.d(LOG_TAG, "\""+ssid+"\" is Configured Networks!");
                configured = true;
            }
        }

        return configured;
    }

    void getWifiNetworkInfo(String ssid) {
        ScanResult scanResult=null;
        //get the current wifi configuration
        //       WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wc = null;

        if (!mWifiManager.isWifiEnabled()) {
            Log.v(LOG_TAG, "Wifi is not enabled, enable it...");
            mWifiManager.setWifiEnabled(true);
        }

/*
        mWifiManager.startScan();
        List<ScanResult> results = mWifiManager.getScanResults();
        Log.d(LOG_TAG, "ScanResults="+results);

        for (ScanResult result : results) {
            Log.d(LOG_TAG, "ScanResult: "+result.SSID);
        }
*/

        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();

        for (WifiConfiguration wifiConfiguration : configs) {
            Log.d(LOG_TAG, "ConfiguredNetworks ("+configs.size()+"): "+wifiConfiguration.SSID);
            if (wifiConfiguration.SSID.equals(ssid)) {
                Log.d(LOG_TAG, "  "+wifiConfiguration.SSID+" securities:");
                Log.d(LOG_TAG, "    security: allowedKeyManagement="+wifiConfiguration.allowedKeyManagement);
                Log.d(LOG_TAG, "    security: allowedGroupCiphers="+wifiConfiguration.allowedGroupCiphers);
                Log.d(LOG_TAG, "    security: allowedAuthAlgorithms="+wifiConfiguration.allowedAuthAlgorithms);
                Log.d(LOG_TAG, "    security: allowedProtocols="+wifiConfiguration.allowedProtocols);
                Log.d(LOG_TAG, "    security: allowedPairwiseCiphers="+wifiConfiguration.allowedPairwiseCiphers);
                Log.d(LOG_TAG, "    security: getCaCertificate="+wifiConfiguration.enterpriseConfig.getCaCertificate());
                Log.d(LOG_TAG, "    security: getAnonymousIdentity="+wifiConfiguration.enterpriseConfig.getAnonymousIdentity());
                Log.d(LOG_TAG, "    security: getClientCertificate="+wifiConfiguration.enterpriseConfig.getClientCertificate());
                Log.d(LOG_TAG, "    security: getPhase2Method="+wifiConfiguration.enterpriseConfig.getPhase2Method());
                Log.d(LOG_TAG, "    security: getIdentity="+wifiConfiguration.enterpriseConfig.getIdentity());
                Log.d(LOG_TAG, "    security: getPassword="+wifiConfiguration.enterpriseConfig.getPassword());
                Log.d(LOG_TAG, "    security: getEapMethod="+wifiConfiguration.enterpriseConfig.getEapMethod());
                Log.d(LOG_TAG, "    security: getPlmn="+wifiConfiguration.enterpriseConfig.getPlmn());
                Log.d(LOG_TAG, "    security: getRealm="+wifiConfiguration.enterpriseConfig.getRealm());
            }
        }
    }

    AlertDialog createWifiCredentialAlertDialog(Context context) {
        LayoutInflater li = LayoutInflater.from(context);
        final View dialogView = li.inflate(R.layout.wifi_login_dialog, null);
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle("Enter Wifi Credential for");
        alertDialogBuilder.setView(dialogView);
        alertDialogBuilder.setCancelable(false);
        mWifiListSpinner = (Spinner) dialogView.findViewById(R.id.sp_wifi_list);
//        View v = mWifiListSpinner.getSelectedView();
//        ((TextView)v).setTextColor(Color.WHITE);
        alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(LOG_TAG, "Got the Wifi Login ...");
                EditText username = (EditText) dialogView.findViewById(R.id.et_wifi_username);
                mWifiUser = username.getText().toString();
                EditText password = (EditText) dialogView.findViewById(R.id.et_wifi_password);
                mWifiPass = password.getText().toString();
                mConnectWifiSSID = ((Spinner) dialogView.findViewById(R.id.sp_wifi_list)).getSelectedItem().toString();
                Log.d(LOG_TAG, "Got Wifi Info: username="+mWifiUser+", password length="+mWifiPass.length()+", Selected SSID="+mConnectWifiSSID);
                dialog.dismiss();
                if (mWifiUser != null && mWifiPass != null & mWifiUser.length() > 0 && mWifiPass.length() > 7) {
                    if (addNetworkAndActivate(theOneAP, mWifiUser, mWifiPass) == true) {
                        getWifiNetworkInfo(mConnectWifiSSID);
                    }
                    else {
                        Log.w(LOG_TAG, "WARNING: Can't add & activate network");
                        finish();
                    }
                }
                else {
                    Log.w(LOG_TAG, "WARNING: No Wifi Credential entered to setup network!");
                    finish();
                }
            }
        });
        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        // create alert dialog
        return alertDialogBuilder.create();
    }

    @Override
    public void onClick(View view) {
        Log.d(LOG_TAG, "onClick():View="+view.getId());
        switch (view.getId()) {
            case R.id.bt_dhcp_refresh:
                updateNetworkInfo(true);
                break;
            case R.id.bt_static_refresh:
                updateNetworkInfo(false);
                break;
            case R.id.bt_transfer:
                if (((CheckBox)findViewById(R.id.cb_update_ip)).isChecked()) {
                    mStaticIpAddressTextView.setText(mDhcpIpAddressTextView.getText());
                }
                if (((CheckBox)findViewById(R.id.cb_update_gateway)).isChecked()) {
                    mStaticGatewayTextView.setText(mDhcpGatewayTextView.getText());
                }
                if (((CheckBox)findViewById(R.id.cb_update_mask)).isChecked()) {
                    mStaticMaskTextView.setText(mDhcpMaskTextView.getText());
                }
                if (((CheckBox)findViewById(R.id.cb_update_dns1)).isChecked()) {
                    mStaticDns1TextView.setText(mDhcpDns1TextView.getText());
                }
                if (((CheckBox)findViewById(R.id.cb_update_dns2)).isChecked()) {
                    mStaticDns2TextView.setText(mDhcpDns2TextView.getText());
                }
                break;
            case R.id.bt_static_update:
                //setIpAssignment();
                //Object ipAssignment = getEnumValue("android.net.IpConfiguration$IpAssignment", "STATIC");
                String address, gateway, mask, dns1, dns2;
                address = mDhcpIpAddressTextView.getText().toString();
                gateway = mDhcpGatewayTextView.getText().toString();
                mask = mDhcpMaskTextView.getText().toString();
                dns1 = mDhcpDns1TextView.getText().toString();
                dns2 = mDhcpDns2TextView.getText().toString();
                if (((CheckBox)findViewById(R.id.cb_update_static_ip)).isChecked() && mStaticIpAddressTextView.getText().length() > 0) {
                    address = mStaticIpAddressTextView.getText().toString();
                }
                if (((CheckBox)findViewById(R.id.cb_update_static_gateway)).isChecked() && mStaticGatewayTextView.getText().length() > 0) {
                    gateway = mStaticGatewayTextView.getText().toString();
                }
                if (((CheckBox)findViewById(R.id.cb_update_static_mask)).isChecked() && mStaticMaskTextView.getText().length() > 0) {
                    mask = mStaticMaskTextView.getText().toString();
                    mMaskBits = countBits(ipToInt(mask));
                }
                if (((CheckBox)findViewById(R.id.cb_update_static_dns1)).isChecked() && mStaticDns1TextView.getText().length() > 0) {
                    dns1 = mStaticDns1TextView.getText().toString();
                }
                if (((CheckBox)findViewById(R.id.cb_update_static_dns2)).isChecked() && mStaticDns2TextView.getText().length() > 0) {
                    dns2 = mStaticDns2TextView.getText().toString();
                }


                if (!address.equals(mDhcpIpAddressTextView.getText().toString()) ||
                        !gateway.equals(mDhcpGatewayTextView.getText().toString()) ||
                        !mask.equals(mDhcpMaskTextView.getText().toString()) ||
                        !dns1.equals(mDhcpDns1TextView.getText().toString()) ||
                        !dns2.equals(mDhcpDns2TextView.getText().toString())
                        ) {
                    Log.d(LOG_TAG, "Setting(s) changed, need to update ...");
//                    updateWifiNetwork(false, address, gateway, dns1, dns2, mMaskBits);
                    changeWifiConfiguration(false, address, mMaskBits, dns1, dns2, gateway);
                }
                break;
            default:
                break;
        }
    }

//////////////////////////////////
/*
    public static void setIpAssignment(String assign , WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException{
        setEnumField(wifiConf, assign, "ipAssignment");
    }

    public static void setIpAddress(InetAddress addr, int prefixLength, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            NoSuchMethodException, ClassNotFoundException, InstantiationException, InvocationTargetException {
        Object linkProperties = getField(wifiConf, "linkProperties");
        if(linkProperties == null)return;
        Class laClass = Class.forName("android.net.LinkAddress");
        Constructor laConstructor = laClass.getConstructor(new Class[]{InetAddress.class, int.class});
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);

        ArrayList mLinkAddresses = (ArrayList)getDeclaredField(linkProperties, "mLinkAddresses");
        mLinkAddresses.clear();
        mLinkAddresses.add(linkAddress);
    }

    public static void setGateway(InetAddress gateway, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
            ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException{
        Object linkProperties = getField(wifiConf, "linkProperties");
        if(linkProperties == null)return;
        Class routeInfoClass = Class.forName("android.net.RouteInfo");
        Constructor routeInfoConstructor = routeInfoClass.getConstructor(new Class[]{InetAddress.class});
        Object routeInfo = routeInfoConstructor.newInstance(gateway);

        ArrayList mRoutes = (ArrayList)getDeclaredField(linkProperties, "mRoutes");
        mRoutes.clear();
        mRoutes.add(routeInfo);
    }

    public static void setDNS(InetAddress dns, WifiConfiguration wifiConf, boolean bAdd)
            throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException{
        Object linkProperties = getField(wifiConf, "linkProperties");
        if(linkProperties == null)return;

        ArrayList<InetAddress> mDnses = (ArrayList<InetAddress>)getDeclaredField(linkProperties, "mDnses");
        if (!bAdd)
            mDnses.clear(); //or add a new dns address , here I just want to replace DNS1
        mDnses.add(dns);
    }

    public static Object getField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    public static Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    public static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }

    void updateWifiNetwork(boolean setDhcp, String address, String gateway, String dns1, String dns2, int mask_bits) {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(!wm.isWifiEnabled()) {
            // wifi is disabled
            Log.d(LOG_TAG, "Wifi is disabled!!");
            return;
        }
        // get the current wifi configuration
        WifiConfiguration wifiConf = null;
        WifiInfo connectionInfo = wm.getConnectionInfo();
        List<WifiConfiguration> configuredNetworks = wm.getConfiguredNetworks();
        if(configuredNetworks != null) {
            for (WifiConfiguration conf : configuredNetworks){
                if (conf.networkId == connectionInfo.getNetworkId()){
                    wifiConf = conf;
                    break;
                }
            }
        }
        if(wifiConf == null) {
            // wifi is not connected
            return;
        }

        if (setDhcp == true) {
            try {
                setIpAssignment("DHCP", wifiConf); //or "DHCP" for dynamic setting
                wm.addNetwork(wifiConf);
                wm.updateNetwork(wifiConf); //apply the setting
                wm.saveConfiguration(); //Save it
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                setIpAssignment("STATIC", wifiConf); //or "DHCP" for dynamic setting
                setIpAddress(InetAddress.getByName(address), mask_bits, wifiConf);
                setGateway(InetAddress.getByName(gateway), wifiConf);
                setDNS(InetAddress.getByName(dns1), wifiConf, false);
                setDNS(InetAddress.getByName(dns2), wifiConf, true);
                wm.updateNetwork(wifiConf); //apply the setting
                wm.saveConfiguration(); //Save it
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
*/
//////////////////////////////////

    void changeWifiConfiguration(boolean dhcp, String ip, int prefix, String dns1, String dns2, String gateway) {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(!wm.isWifiEnabled()) {
            // wifi is disabled
            Log.d(LOG_TAG, "Wifi is disabled!!");
            return;
        }
        // get the current wifi configuration
        WifiConfiguration wifiConf = null;
        WifiInfo connectionInfo = wm.getConnectionInfo();
        List<WifiConfiguration> configuredNetworks = wm.getConfiguredNetworks();
        if(configuredNetworks != null) {
            for (WifiConfiguration conf : configuredNetworks){
                if (conf.networkId == connectionInfo.getNetworkId()){
                    wifiConf = conf;
                    break;
                }
            }
        }
        if(wifiConf == null) {
            // wifi is not connected
            return;
        }
        try {
            Class<?> ipAssignment = wifiConf.getClass().getMethod("getIpAssignment").invoke(wifiConf).getClass();
            Object staticConf = wifiConf.getClass().getMethod("getStaticIpConfiguration").invoke(wifiConf);
            if(dhcp) {
                wifiConf.getClass().getMethod("setIpAssignment", ipAssignment).invoke(wifiConf, Enum.valueOf((Class<Enum>) ipAssignment, "DHCP"));
                if(staticConf != null) {
                    staticConf.getClass().getMethod("clear").invoke(staticConf);
                }
            } else {
                wifiConf.getClass().getMethod("setIpAssignment", ipAssignment).invoke(wifiConf, Enum.valueOf((Class<Enum>) ipAssignment, "STATIC"));
                if(staticConf == null) {
                    Class<?> staticConfigClass = Class.forName("android.net.StaticIpConfiguration");
                    staticConf = staticConfigClass.newInstance();
                }
                // STATIC IP AND MASK PREFIX
                Constructor<?> laConstructor = LinkAddress.class.getConstructor(InetAddress.class, int.class);
                LinkAddress linkAddress = (LinkAddress) laConstructor.newInstance(InetAddress.getByName(ip), prefix);
                Log.d(LOG_TAG, "linkAddress="+linkAddress);
                staticConf.getClass().getField("ipAddress").set(staticConf, linkAddress);
                // GATEWAY
                staticConf.getClass().getField("gateway").set(staticConf, InetAddress.getByName(gateway));
                Log.d(LOG_TAG, "InetAddress.getByName(gateway)="+InetAddress.getByName(gateway));
                // DNS
                List<InetAddress> dnsServers = (List<InetAddress>) staticConf.getClass().getField("dnsServers").get(staticConf);
                Log.d(LOG_TAG, "dnsServers="+dnsServers);
                dnsServers.clear();
                dnsServers.add(InetAddress.getByName(dns1));
                dnsServers.add(InetAddress.getByName(dns2));
                Log.d(LOG_TAG, "dnsServers="+dnsServers);
                // apply the new static configuration
                wifiConf.getClass().getMethod("setStaticIpConfiguration", staticConf.getClass()).invoke(wifiConf, staticConf);
            }
            int netId = wm.updateNetwork(wifiConf);
            Log.d(LOG_TAG, "wifiConf.allowedKeyManagement="+wifiConf.allowedKeyManagement+", wm.updateNetwork() returns: "+netId);
            if (netId != -1) {
                boolean isDisconnected =  wm.disconnect();
                boolean configSaved = wm.saveConfiguration(); //Save it
                boolean isEnabled = wm.enableNetwork(wifiConf.networkId, true);
                // reconnect with the new static IP
                boolean isReconnected = wm.reconnect();
                Log.d(LOG_TAG, "isEnabled="+isEnabled+", configSaved="+configSaved+", isReconnected="+isReconnected);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

//////////////////////////////////

    WifiConfiguration GetCurrentWifiConfiguration(WifiManager manager)
    {
        if (!manager.isWifiEnabled())
            return null;

        List<WifiConfiguration> configurationList = manager.getConfiguredNetworks();
        WifiConfiguration configuration = null;
        int cur = manager.getConnectionInfo().getNetworkId();
        for (int i = 0; i < configurationList.size(); ++i)
        {
            WifiConfiguration wifiConfiguration = configurationList.get(i);
            if (wifiConfiguration.networkId == cur)
                configuration = wifiConfiguration;
        }

        return configuration;
    }

    private boolean addNetworkAndActivate(ScanResult scanResult, String identity, String password) {
//        ScanResult scanResult=null;
        //get the current wifi configuration
//        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wc = null;
        int networkId = -1;
//        mWifiManager.startScan();
// get list of the results in object format ( like an array )
//        List<ScanResult> results = wifiManager.getScanResults();

// loop that goes through list
/*
        for (ScanResult result : results) {
            Log.d(LOG_TAG, "ScanResult: "+result.SSID);
            if (result.SSID.equals(ssid)) {
                scanResult = result;
                break;
            }
        }
*/

        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();

        for (WifiConfiguration wifiConfiguration : configs) {
            Log.d(LOG_TAG, "ConfiguredNetworks: "+wifiConfiguration.SSID);
            try {
                if (wifiConfiguration.SSID.equals("\"" + scanResult.SSID + "\"")) {
                    wc = wifiConfiguration;
                    networkId = wc.networkId;
                    break;
                }
            } catch (Exception e) {
                Log.v(LOG_TAG, "Caught Exception: "+e.toString());
            }
        }

        // not configured, create new
        if (wc == null) {
            Log.d(LOG_TAG, "Creating new wifi network...");
            wc = new WifiConfiguration();

            ConfigurationSecuritiesV8 conf = new ConfigurationSecuritiesV8();
            Log.d(LOG_TAG, "Security Type="+conf.getDisplaySecirityString(scanResult));
            Log.d(LOG_TAG, "conf.getScanResultSecurity(scanResult)="+conf.getScanResultSecurity(scanResult));
            conf.setupSecurity(wc, conf.getScanResultSecurity(scanResult), identity, password);
            wc.SSID = "\"" + scanResult.SSID + "\"";
    /*Priority*/
            wc.priority = 40;
    /*Enable Hidden SSID*/
            wc.hiddenSSID = false;

            networkId = mWifiManager.addNetwork(wc);

            if (networkId == -1) {
                Log.w(LOG_TAG, "ERROR: wifiManager.addNetwork() failed!!");
                return false;
            }

            if (!mWifiManager.saveConfiguration()) {
                Log.w(LOG_TAG, "ERROR: wifiManager.saveConfiguration() failed!!");
                return false;
            }
        }

        if (networkId == -1) {
            return false;
        }

        boolean active = mWifiManager.enableNetwork(networkId, true);
        Log.d(LOG_TAG, "wifiManager.enableNetwork(" + networkId + ", true) = " + active);

        return active;
    }

}

////////////////////// Classes:

class ConfigurationSecuritiesV8 extends ConfigurationSecurities {

    final static String LOG_TAG = "CustomNetwork";

    static final int SECURITY_NONE = 0;
    static final int SECURITY_WEP = 1;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_EAP = 3;

    enum PskType {
        UNKNOWN, WPA, WPA2, WPA_WPA2
    }

    private static final String TAG = "ConfigurationSecuritiesV14";

    private static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) || config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
/*
            Log.d(LOG_TAG,"security: allowedGroupCiphers="+config.allowedGroupCiphers);
            Log.d(LOG_TAG,"security: allowedAuthAlgorithms="+config.allowedAuthAlgorithms);
            Log.d(LOG_TAG,"security: allowedProtocols="+config.allowedProtocols);
            Log.d(LOG_TAG,"security: allowedPairwiseCiphers="+config.allowedPairwiseCiphers);
//            WifiConfiguration.PairwiseCipher.
*/
            return SECURITY_EAP;
        }
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    @Override
    public String getWifiConfigurationSecurity(WifiConfiguration wifiConfig) {
        return String.valueOf(getSecurity(wifiConfig));
    }

    @Override
    public String getScanResultSecurity(ScanResult scanResult) {
        return String.valueOf(getSecurity(scanResult));
    }

    @Override
    public void setupSecurity(WifiConfiguration config, String security, String identity, String password) {
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();

        final int sec = security == null ? SECURITY_NONE : Integer.valueOf(security);
        final int passwordLen = password == null ? 0 : password.length();
        Log.d(LOG_TAG, "setupSecurity()::security type="+sec);
        switch (sec) {
            case SECURITY_NONE:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;

            case SECURITY_WEP:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                if (passwordLen != 0) {
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((passwordLen == 10 || passwordLen == 26 || passwordLen == 58) && password.matches("[0-9A-Fa-f]*")) {
                        config.wepKeys[0] = password;
                    } else {
                        config.wepKeys[0] = '"' + password + '"';
                    }
                }
                break;

            case SECURITY_PSK:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                if (passwordLen != 0) {
                    if (password.matches("[0-9A-Fa-f]{64}")) {
                        config.preSharedKey = password;
                    } else {
                        config.preSharedKey = '"' + password + '"';
                    }
                }
                break;

            case SECURITY_EAP:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);

//                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
//                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);

                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

                WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
                enterpriseConfig.setIdentity(identity);
                enterpriseConfig.setPassword(password);
                enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.PEAP);
                config.enterpriseConfig = enterpriseConfig;
                break;

            default:
                Log.d(LOG_TAG, "Invalid security type: " + sec);
        }

        // config.proxySettings = mProxySettings;
        // config.ipAssignment = mIpAssignment;
        // config.linkProperties = new LinkProperties(mLinkProperties);

    }

    private static PskType getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return PskType.WPA_WPA2;
        } else if (wpa2) {
            return PskType.WPA2;
        } else if (wpa) {
            return PskType.WPA;
        } else {
            Log.d(LOG_TAG, "Received abnormal flag string: " + result.capabilities);
            return PskType.UNKNOWN;
        }
    }

    @Override
    public String getDisplaySecirityString(final ScanResult scanResult) {
        final int security = getSecurity(scanResult);
        if (security == SECURITY_PSK) {
            switch (getPskType(scanResult)) {
                case WPA:
                    return "WPA";
                case WPA_WPA2:
                case WPA2:
                    return "WPA2";
                default:
                    return "?";
            }
        } else {
            switch (security) {
                case SECURITY_NONE:
                    return "OPEN";
                case SECURITY_WEP:
                    return "WEP";
                case SECURITY_EAP:
                    return "EAP";
            }
        }

        return "?";
    }

    @Override
    public boolean isOpenNetwork(String security) {
        return String.valueOf(SECURITY_NONE).equals(security);
    }
}

//And

abstract class ConfigurationSecurities {
    /**
     * @return The security of a given {@link WifiConfiguration}.
     */
    public abstract String getWifiConfigurationSecurity(WifiConfiguration wifiConfig);

    /**
     * @return The security of a given {@link ScanResult}.
     */
    public abstract String getScanResultSecurity(ScanResult scanResult);

    /**
     * Fill in the security fields of WifiConfiguration config.
     *
     * @param config   The object to fill.
     * @param security If is OPEN, password is ignored.
     * @param password Password of the network if security is not OPEN.
     */
    public abstract void setupSecurity(WifiConfiguration config, String security, final String identity, final String password);

    public abstract String getDisplaySecirityString(final ScanResult scanResult);

    public abstract boolean isOpenNetwork(final String security);

    public static ConfigurationSecurities newInstance() {
//      if (Version.SDK < 8) {
//          return new ConfigurationSecuritiesOld();
//      } else {
        return new ConfigurationSecuritiesV8();
//      }
    }
}