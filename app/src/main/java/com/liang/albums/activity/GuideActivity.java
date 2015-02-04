package com.liang.albums.activity;

import android.app.AlarmManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.liang.albums.R;
import com.liang.albums.adapter.GuidePagerAdapter;
import com.liang.albums.adapter.WifiListAdapter;
import com.liang.albums.util.AccessPoint;
import com.liang.albums.view.WifiPasswordDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by liang on 15/1/29.
 */
public class GuideActivity extends FragmentActivity implements ViewPager.OnPageChangeListener {
    private static final String TAG = "GuideActivity";

    private ViewPager mViewPager;
    private List<View> mPageList;

    private ArrayAdapter<String> mTimeZoneAdapter;
    private Spinner mTimeZoneSpinner;


    // for wifi settings
    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private AccessPoint mSelectedAccessPoint;
    private static final int INVALID_NETWORK_ID = -1;
    private static final String EXTRA_HUB_NAME = "user_name";
    private static final String EXTRA_HUB_PASSWORD = "user_password";
    private Scanner mScanner;
    private NetworkInfo.DetailedState mLastState;
    private WifiInfo mLastInfo;
    private int mLastPriority;
    private boolean mResetNetworks = false;
    private boolean mFilterNetwork = false;
    private WifiListAdapter mWifiListAdapter;

    private WifiManager mWifiManager;
    private List<AccessPoint> mAccessPoints;

    public GuideActivity(){
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(intent);
            }
        };
        mScanner = new Scanner();
        mAccessPoints = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        mViewPager = (ViewPager) findViewById(R.id.viewpager_guide);
        mPageList = initPageList();
        mViewPager.setAdapter(new GuidePagerAdapter(mPageList));

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        updateAccessPoints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mReceiver);
        mScanner.pause();

        if (mResetNetworks) {
            enableNetworks();
        }
    }

    private List<View> initPageList(){
        // To init language page and wifi selection page
        List<View> list = new ArrayList<>();

        LayoutInflater inflater = LayoutInflater.from(this);
        View pageView1 = inflater.inflate(R.layout.page_item_language, null);
        View pageView2 = inflater.inflate(R.layout.page_item_wifi, null);
        View pageView3 = inflater.inflate(R.layout.page_item_datetime, null);

        // language page
        Button btnNext = (Button)pageView1.findViewById(R.id.btn_pager_next);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewPager.setCurrentItem(1);
            }
        });

        // wifi page
        ListView listView = (ListView) pageView2.findViewById(R.id.listView_page_wifi);
        mWifiListAdapter = new WifiListAdapter(this,mAccessPoints);
        listView.setAdapter(mWifiListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AccessPoint point = (AccessPoint) mWifiListAdapter.getItem(position);
                WifiPasswordDialog dialog = new WifiPasswordDialog(GuideActivity.this, point, mAccessPoints, mWifiManager);
                dialog.show();
            }
        });
        Button btnGoAhead = (Button)pageView2.findViewById(R.id.btn_pager_next);
        btnGoAhead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewPager.setCurrentItem(2);
            }
        });


        // time settings
        TextView tvDone = (TextView)pageView3.findViewById(R.id.text_pager_done);
        tvDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(GuideActivity.this, MainActivity.class));
                GuideActivity.this.finish();
            }
        });

        mTimeZoneSpinner = (Spinner)pageView3.findViewById(R.id.spinner_pager_time);
        mTimeZoneAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, TimeZone.getAvailableIDs());
        mTimeZoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTimeZoneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            Calendar current = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm:ss");
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedId = (String) (parent
                        .getItemAtPosition(position));
                TimeZone timezone = TimeZone.getTimeZone(selectedId);
                String TimeZoneName = timezone.getDisplayName();

                int TimeZoneOffset = timezone.getRawOffset()
                        / (60 * 1000);

                int hrs = TimeZoneOffset / 60;
                int mins = TimeZoneOffset % 60;
                long miliSeconds = current.getTimeInMillis();
                miliSeconds = miliSeconds + timezone.getRawOffset();

                Date resultdate = new Date(miliSeconds);
                Log.d(TAG, sdf.format(resultdate));

                AlarmManager am = (AlarmManager)getApplication().getSystemService(Service.ALARM_SERVICE);
                am.setTimeZone(timezone.getID());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mTimeZoneSpinner.setAdapter(mTimeZoneAdapter);


        list.add(pageView1);
        list.add(pageView2);
        list.add(pageView3);

        return list;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    //  for wifi settings
    private void handleEvent(Intent intent) {
        String action = intent.getAction();
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN));
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            updateAccessPoints();
        } else if (WifiManager.NETWORK_IDS_CHANGED_ACTION.equals(action)) {
            if (mSelectedAccessPoint != null && mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                mSelectedAccessPoint = null;
            }
            updateAccessPoints();
        } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            updateConnectionState(WifiInfo.getDetailedStateOf((SupplicantState)
                    intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)));
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            updateConnectionState(((NetworkInfo) intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_INFO)).getDetailedState());
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateConnectionState(null);

//            if (!TextUtils.isEmpty(mLastHubUser)) {
//                Message msg = mHandler.obtainMessage(0);
//                Bundle data = new Bundle();
//                data.putString(EXTRA_HUB_NAME, mLastHubUser);
//                data.putString(EXTRA_HUB_PASSWORD, mLastHubPwd);
//                msg.setData(data);
//                mHandler.sendMessage(msg);

//                mLastHubUser = null;
//                mLastHubPwd = null;
//            }
        }
    }

    private void updateWifiState(int state) {
        if (state == WifiManager.WIFI_STATE_ENABLED) {
            mScanner.resume();
            updateAccessPoints();
        } else {
            mScanner.pause();
            mAccessPoints.clear();
        }
    }

    private void updateAccessPoints() {
        List<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();

        if (!mFilterNetwork && configs != null) {
            mLastPriority = 0;
            for (WifiConfiguration config : configs) {
                if (config.priority > mLastPriority) {
                    mLastPriority = config.priority;
                }

                // Shift the status to make enableNetworks() more efficient.
                if (config.status == WifiConfiguration.Status.CURRENT) {
                    config.status = WifiConfiguration.Status.ENABLED;
                } else if (mResetNetworks && config.status == WifiConfiguration.Status.DISABLED) {
                    config.status = WifiConfiguration.Status.CURRENT;
                }

                AccessPoint accessPoint = new AccessPoint(this, config);
                accessPoint.update(mLastInfo, mLastState);
                accessPoints.add(accessPoint);
            }
        }

        List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0
                        || result.capabilities.contains("[IBSS]")) {
                    continue;
                }

                if (mFilterNetwork
                        && (AccessPoint.getSecurity(result) != AccessPoint.SECURITY_NONE
                        || result.frequency == 0)) {
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : accessPoints) {
                    if (accessPoint.update(result)) {
                        found = true;
                    }
                }

                if (!found) {
                    accessPoints.add(new AccessPoint(this, result));
                }
            }
        }

        mAccessPoints.clear();
        for (AccessPoint accessPoint : accessPoints) {
            mAccessPoints.add(accessPoint);
        }
        mWifiListAdapter.notifyDataSetChanged();
    }

    private void updateConnectionState(NetworkInfo.DetailedState state) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }

        if (state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {
            mScanner.pause();
        } else {
            mScanner.resume();
        }

        mLastInfo = mWifiManager.getConnectionInfo();
        if (state != null) {
            mLastState = state;
        }

        for (int i = mAccessPoints.size() - 1; i >= 0; --i) {
            mAccessPoints.get(i).update(mLastInfo, mLastState);
        }

        if (mResetNetworks && (state == NetworkInfo.DetailedState.CONNECTED ||
                state == NetworkInfo.DetailedState.DISCONNECTED || state == NetworkInfo.DetailedState.FAILED)) {
            updateAccessPoints();
            enableNetworks();
        }
    }

    private class Scanner extends Handler {
        private int mRetry = 0;

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void pause() {
            mRetry = 0;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mWifiManager.startScan()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                return;
            }

            sendEmptyMessageDelayed(0, 6000);
        }
    }

    private void enableNetworks() {
        for (int i = mAccessPoints.size() - 1; i >= 0; --i) {
            WifiConfiguration config =  mAccessPoints.get(i).getConfig();
            if (config != null && config.status != WifiConfiguration.Status.ENABLED) {
                mWifiManager.enableNetwork(config.networkId, false);
            }
        }

        mResetNetworks = false;
    }
}
