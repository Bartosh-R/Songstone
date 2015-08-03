package bartoshr.songstone;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nononsenseapps.filepicker.FilePickerActivity;

import bartoshr.songstone.Intefaces.OnItemClickListener;


public class MainActivity extends AppCompatActivity implements OnItemClickListener, ServiceConnection{

    private static final String TAG = "MainActivity";
    private static final String PANEL_FRAGMENT_TAG = "PANEL_FRAGMENT_TAG";

    //Preferences
    private SharedPreferences preferences;
    private static final String PREFERENCES_NAME = "PREFERENCES_NAME";
    private static final String PREFERENCES_DIR = "PREFERENCES_DIR";

    // Constans
    private static final int FILE_CODE = 1;
    public static final String BUNDLE_TITLE = "BUNDLE_TITLE";

    SongsFinder finder;
    String songDirecory;

    // Views
    private Toolbar toolbar;
    private TextView emptyView;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private LinearLayout parentView;
    private DrawerLayout drawerLayout;
    NavigationView mNavigationView;

    //Services
    private SongService songService;
    private Intent songIntent;

    Receiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFERENCES_NAME, AppCompatActivity.MODE_PRIVATE);
        songDirecory = preferences.getString(PREFERENCES_DIR, /*"/storage/"*/ Environment.getExternalStorageDirectory().getPath());

        finder = new SongsFinder(songDirecory);
        receiver = new Receiver();

        // Setting views
        setUpToolbar();
        setUpNavDrawer();

        parentView = (LinearLayout) findViewById(R.id.parentView);
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {

                menuItem.setChecked(true);
                switch (menuItem.getItemId()) {
                    case R.id.navigation_item_1:
                        Snackbar.make(parentView, "Delete", Snackbar.LENGTH_SHORT).show();
                        return true;
                    case R.id.navigation_item_2:
                        Snackbar.make(parentView, "Add", Snackbar.LENGTH_SHORT).show();
                        return true;
                    default:
                        return true;
                }
            }
        });


        emptyView = (TextView) findViewById(R.id.emptyView);
        mRecyclerView = (RecyclerView) findViewById(R.id.songsview);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new SongAdapter(this, finder.songs, this);
        mRecyclerView.setAdapter(mAdapter);


        Log.i(TAG, "onCreate()");
        startService();

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (finder.songs.isEmpty()) {
            mRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
        else {
            mRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
        this.bindService(songIntent, this, Context.BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SongService.ACTION_REFRESH_VIEW);
        registerReceiver(receiver, intentFilter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            /*case R.id.action_settings:
                startFilePicker();
                break;*/
            case R.id.action_bluetooth:
                toggleBluetooth();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // Method started when need to chaage title
    public void updateView(String title){

        Fragment f = getFragmentManager().findFragmentByTag(PANEL_FRAGMENT_TAG);

        if (f != null) {
            getFragmentManager().popBackStack();
        }

        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);

        PanelFragment fragment = (PanelFragment) PanelFragment.instantiate(this, PanelFragment.class.getName());
        fragment.setArguments(bundle);

        fragment.setAnimationChangedListener(new PanelFragment.OnAnimationChanged() {

            @Override
            public void onAnimationEnded() {
                mRecyclerView.setPadding(0,0,0, 115);
            }

            @Override
            public void onAnimationStarted() {
                if(mRecyclerView.getPaddingBottom() != 0)
                    mRecyclerView.setPadding(0,0,0,0);
            }
        });

        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.slide_up,
                        R.animator.slide_down,
                        R.animator.slide_up,
                        R.animator.slide_down)
                .add(R.id.screenLayout, fragment,
                        PANEL_FRAGMENT_TAG
                ).addToBackStack(null).commit();

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {

                Uri uri = data.getData();
                preferences.edit().putString(PREFERENCES_DIR, uri.getPath()).commit();
                Snackbar.make(parentView, "Directory changed :  " +uri.getPath(), Snackbar.LENGTH_SHORT).show();
               // Toast.makeText(this, uri.getPath(), Toast.LENGTH_LONG).show();
        }
    }

    public void startFilePicker(){
        Intent i = new Intent(this, FilePickerActivity.class);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);

        // Configure initial directory like so
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, "/storage");
        startActivityForResult(i, FILE_CODE);
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "Service Connected");

        SongService.MusicBinder binder = (SongService.MusicBinder)service;

        // Here's where we finally create the MusicService
        songService = binder.getService();
        songService.setList(finder.songs);
        songService.musicBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i(TAG, "Service Disconected");
        songService.musicBound = false;
    }


    public void startService(){
        songIntent = new Intent(this, SongService.class);
        this.startService(songIntent);
    }


    // Invoke when List item is clicked
    @Override
    public void onItemClick(int position) {
        LocalBroadcastManager local = LocalBroadcastManager.getInstance(getApplicationContext());
        Intent broadcastIntent = new Intent(SongService.BROADCAST_ORDER);
        broadcastIntent.putExtra(SongService.BROADCAST_EXTRA_GET_ORDER, SongService.ACTION_PLAY);
        broadcastIntent.putExtra(SongService.BROADCAST_EXTRA_GET_POSITION, position);
        local.sendBroadcast(broadcastIntent);
    }


    public void toggleBluetooth()
    {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            Snackbar.make(parentView, "Bluetooth not present", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if(bluetoothAdapter.isEnabled()) bluetoothAdapter.disable();
        else bluetoothAdapter.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        unbindService(this);
        super.onStop();
    }


    class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra(SongService.TITLE_KEY);
            updateView(title);
        }
    }

    private void setUpToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    private void setUpNavDrawer() {
        drawerLayout = (DrawerLayout) findViewById(R.id.nav_drawer);
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(
                this,  drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        drawerLayout.setDrawerListener(mDrawerToggle);
        if (toolbar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            mDrawerToggle.syncState();
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
    }

}
