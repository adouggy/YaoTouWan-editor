package me.yaotouwan.screenrecorder;

import android.app.ProgressDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import me.yaotouwan.R;
import me.yaotouwan.post.BaseActivity;
import me.yaotouwan.post.RecordScreenActivity;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.AppPackageHelper;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-27.
 */
public class SelectGameActivity extends BaseActivity
        implements ScreenRecorder.ScreenRecorderListener {

    private static final int INTENT_REQUEST_CODE_RECORD_SCREEN = 1;
    private static final int INTENT_REQUEST_CODE_CUT_VIDEO = 2;

    public static List<AppPackageHelper.Game> preLoadGames;
    List<AppPackageHelper.Game> gamesInstalled;

    GridView gamesView;
    int thumbnailSize;

    private String gameName;
    private String packetName;
    private Uri draftUri;
    private ScreenRecorder screenRecorder;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_game);
        setupActionBar(R.string.select_game_title);

        gamesView = (GridView) findViewById(R.id.root_layout);
        int space = dpToPx(20);
        int width = getWindowSize().x / 4 - space * 2;
        gamesView.setColumnWidth(width);
        gamesView.setHorizontalSpacing(space);
        gamesView.setVerticalSpacing(space);
        thumbnailSize = width;

        if (!YTWHelper.hasBuildinScreenRecorder()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SRecorderService.ACTION_SCREEN_RECORDER_STARTED);
            filter.addAction(SRecorderService.ACTION_SCREEN_RECORDER_STOPPED);
            registerReceiver(mBroadcastReceiver, filter);
        }

        draftUri = getIntent().getData();
        screenRecorder = new ScreenRecorder(this, this);
        screenRecorder.videoPath = YTWHelper
                .prepareFilePathForVideoSaveWithDraftUri(draftUri);
    }

    @Override
    protected void onStart() {
        super.onStart();

        logd("onStart");
        // try to stop recorder
        if (!YTWHelper.hasBuildinScreenRecorder()) {
            if (screenRecorder.stop()) {
                showProgressDialog(R.string.stopping_screen_recorder);
                if (preLoadGames != null) {
                    gamesInstalled = preLoadGames;
                    reloadData();
                    preLoadGames = null;
                }
                new Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            logd("force stop recorder");
                            onStoppedScreenRecorder();
                        }
                    }
                }, 3000);
                return;
            }
        }

        if (preLoadGames != null) {
            gamesInstalled = preLoadGames;
            reloadData();
            preLoadGames = null;
        } else {
            // todo
//            if (true) return ;
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.select_game_waiting_tip));
            mProgressDialog.setCancelable(true);
            mProgressDialog.show();
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    new AppPackageHelper().loadCachedGames(SelectGameActivity.this,
                            new AppPackageHelper.AppPackageHelperDelegate() {
                                @Override
                                public void onComplete(List<AppPackageHelper.Game> games) {
                                    if (games != null) {
                                        gamesInstalled = games;
                                        reloadData();
                                    } else {
                                        Toast.makeText(SelectGameActivity.this,
                                                R.string.select_game_load_failed_message,
                                                Toast.LENGTH_LONG).show();
                                        finish();
                                    }
                                }
                            });
                }
            });

            new AppPackageHelper().loadGames(this,
                    new AppPackageHelper.AppPackageHelperDelegate() {
                        @Override
                        public void onComplete(List<AppPackageHelper.Game> games) {

                            if (games != null) {
                                gamesInstalled = games;
                                reloadData();

                                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                                    mProgressDialog.dismiss();
                                    mProgressDialog = null;
                                }
                            } else {
                                new AppPackageHelper().loadCachedGames(SelectGameActivity.this,
                                        new AppPackageHelper.AppPackageHelperDelegate() {
                                            @Override
                                            public void onComplete(List<AppPackageHelper.Game> games) {
                                                if (games != null) {
                                                    gamesInstalled = games;
                                                    reloadData();
                                                } else {
                                                    Toast.makeText(SelectGameActivity.this,
                                                            R.string.select_game_load_failed_message,
                                                            Toast.LENGTH_LONG).show();
                                                    finish();
                                                }
                                            }
                                        });
                            }
                        }
                    }
            );
        }
    }

    void reloadData() {
        GameAdapter adapter = new GameAdapter(SelectGameActivity.this, gamesInstalled);
        gamesView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);

        SubMenu subMenu = menu.addSubMenu(0, 1, 3, "更多");
        MenuItem subMenuItem = subMenu.getItem();
        subMenuItem.setIcon(R.drawable.actionbar_overflow);
        subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        subMenu.add(0, 2, 0, getString(R.string.video_encoder_quality_option_title_default));
        MenuItem menuItemQualityOption = subMenu.getItem(0);
        menuItemQualityOption.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onClickVideoQualityOption(item);
                return true;
            }
        });

        int showTouches = 0;
        try {
            showTouches = Settings.System.getInt(
                    getContentResolver(), "show_touches");
        } catch (Settings.SettingNotFoundException e) {}
        subMenu.add(0, 3, 0, getString((showTouches > 0
                ? R.string.video_encoder_show_touches_option_title_on
                : R.string.video_encoder_show_touches_option_title_off)));
        MenuItem menuItemShowTouches = subMenu.getItem(1);
        menuItemShowTouches.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onClickShowTouchesOption(item);
                return true;
            }
        });

        return ret;
    }

    public void onClickVideoQualityOption(final MenuItem item) {
        List<ActionSheetItem> actionSheetItems = new ArrayList<ActionSheetItem>(3);
        actionSheetItems.add(new ActionSheetItem(
                getString(R.string.video_encoder_quality_option_high),

                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        screenRecorder.videoQuality = 1;
                        item.setTitle(R.string.video_encoder_quality_option_title_high);
                    }
                }));
        actionSheetItems.add(new ActionSheetItem(
                getString(R.string.video_encoder_quality_option_middle),

                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        screenRecorder.videoQuality = 0;
                        item.setTitle(R.string.video_encoder_quality_option_title_middle);
                    }
                }));
        actionSheetItems.add(new ActionSheetItem(
                getString(R.string.video_encoder_quality_option_low),

                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        screenRecorder.videoQuality = -1;
                        item.setTitle(R.string.video_encoder_quality_option_title_low);
                    }
                }));
        ActionSheet.showWithItems(this, actionSheetItems);
    }

    public void onClickShowTouchesOption(final MenuItem item) {
        List<ActionSheetItem> actionSheetItems = new ArrayList<ActionSheetItem>(3);
        actionSheetItems.add(new ActionSheetItem(
                getString(R.string.video_encoder_show_touches_option_on),

                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        screenRecorder.showTouches = 1;
                        item.setTitle(R.string.video_encoder_show_touches_option_title_on);
                    }
                }));
        actionSheetItems.add(new ActionSheetItem(
                getString(R.string.video_encoder_show_touches_option_off),

                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        screenRecorder.showTouches = 0;
                        item.setTitle(R.string.video_encoder_show_touches_option_title_off);
                    }
                }));
        ActionSheet.showWithItems(this, actionSheetItems);
    }

    public class GameAdapter extends BaseAdapter {
        private Context mContext;

        // Keep all Images in array
        public List<AppPackageHelper.Game> mGames;

        // Constructor
        public GameAdapter(Context c, List<AppPackageHelper.Game> games){
            mContext = c;
            mGames = games;
        }

        @Override
        public int getCount() {
            return mGames.size();
        }

        @Override
        public AppPackageHelper.Game getItem(int position) {
            return mGames.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        private LayoutInflater inflater;

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (inflater == null)
                inflater = (LayoutInflater) mContext.getSystemService(LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.select_game_item, parent, false);
            assert rowView != null;

            ImageButton icon = (ImageButton) rowView.findViewById(R.id.game_icon);
            AppPackageHelper.Game game = getItem(position);
            icon.setImageDrawable(game.icon);
            setViewSize(icon, thumbnailSize, thumbnailSize);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    packetName = getItem(position).pname;
                    gameName = getItem(position).appname;
                    if (YTWHelper.hasBuildinScreenRecorder()) {
                        Intent intent = new Intent(SelectGameActivity.this, RecordScreenActivity.class);
                        intent.setData(draftUri);
                        if (gameName != null) {
                            intent.putExtra("package_name", packetName);
                            intent.putExtra("game_name", gameName);
                        }
                        startActivityForResult(intent, INTENT_REQUEST_CODE_RECORD_SCREEN);
                    } else {
                        startGame();
                    }
                }
            });

            TextView nameView = (TextView) rowView.findViewById(R.id.game_name);
            nameView.setText(game.appname);
            setViewWidth(nameView, thumbnailSize);

            return rowView;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!YTWHelper.hasBuildinScreenRecorder()) {
            unregisterReceiver(mBroadcastReceiver);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INTENT_REQUEST_CODE_RECORD_SCREEN) {
            setResult(RESULT_OK, data);
            finish();
        } else if (requestCode == INTENT_REQUEST_CODE_CUT_VIDEO) {
            if (resultCode == RESULT_OK) {
                if (screenRecorder.videoPath != null
                        && !data.getData().getPath().equals(screenRecorder.videoPath)) {
                    // 产生了一个新的文件，于是把旧文件删除
                    new File(screenRecorder.videoPath).delete();
                }
                data.putExtra("game_name", gameName);
                setResult(RESULT_OK, data);
            } else {
                if (screenRecorder.videoPath != null)
                    new File(screenRecorder.videoPath).delete();
                setResult(RESULT_CANCELED);
            }
            finish();
        }
    }

    public void onStartedScreenRecorder() {

    }

    public void startGame() {
        isWaitingForStartRecorder = true;
        if (packetName == null || packetName.length() == 0) {
            moveTaskToBack(true);
        } else {
            Intent gameIntent = getPackageManager()
                    .getLaunchIntentForPackage(packetName);
            startActivity(gameIntent);
        }
    }

    boolean isWaitingForStartRecorder;
    @Override
    protected void onStop() {
        super.onStop();
        if (isWaitingForStartRecorder) {
            screenRecorder.start();
            isWaitingForStartRecorder = false;
        }
    }

    public void onStoppedScreenRecorder() {
        hideProgressDialog();
        startActivityForResult(new Intent(this, EditVideoActivity.class)
                        .setData(Uri.parse(screenRecorder.videoPath))
                        .putExtra("draft_path", draftUri.getPath())
                        .putExtra("rotate", estimatedRotateInGame),
                INTENT_REQUEST_CODE_CUT_VIDEO);
    }

    int estimatedRotateInGame;
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SRecorderService.ACTION_SCREEN_RECORDER_STARTED)) {
                onStartedScreenRecorder();
            } else if (intent.getAction().equals(SRecorderService.ACTION_SCREEN_RECORDER_STOPPED)) {
                estimatedRotateInGame = intent.getIntExtra("orientation", 0);
                onStoppedScreenRecorder();
            }
        }
    };
}