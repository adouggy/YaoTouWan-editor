package me.yaotouwan.post;

import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.Button;
import com.actionbarsherlock.view.*;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import me.yaotouwan.R;
import me.yaotouwan.screenrecorder.EditVideoActivity;
import me.yaotouwan.screenrecorder.SRecorderService;
import me.yaotouwan.screenrecorder.ScreenRecorder;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-24.
 */
public class RecordScreenActivity extends BaseActivity implements ScreenRecorder.ScreenRecorderListener {

    private static final int INTENT_REQUEST_CODE_CUT_VIDEO = 1;

    private String gameName;
    private String packetName;
    private Uri draftUri;
    private ScreenRecorder screenRecorder;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_screen);
        setupActionBar(R.string.record_screen_title);

        screenRecorder = new ScreenRecorder(this, this);
        packetName = getIntent().getStringExtra("package_name");
        screenRecorder.moveToBackAlert = packetName == null;
        draftUri = getIntent().getData();

        gameName = getIntent().getStringExtra("game_name");

        IntentFilter filter = new IntentFilter();
        filter.addAction(SRecorderService.ACTION_SCREEN_RECORDER_STARTED);
        filter.addAction(SRecorderService.ACTION_SCREEN_RECORDER_STOPPED);
        registerReceiver(recorderStartedReceiver, filter);

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

    public void startRecordButtonClicked(View v) {
        startGame();
    }

    public void onStartedScreenRecorder() {

    }

    boolean isWaitingForStartingRecorder;
    void startGame() {
        isWaitingForStartingRecorder = true;
        if (packetName == null) {
            moveTaskToBack(true);
        } else {
            Intent gameIntent = getPackageManager()
                    .getLaunchIntentForPackage(packetName);
            startActivity(gameIntent);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        logd("onStop");
        if (isWaitingForStartingRecorder) {
            isWaitingForStartingRecorder = false;
            screenRecorder.videoLandscape =
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            screenRecorder.videoPath = YTWHelper
                    .generateFilePathForVideoSaveWithDraftUri(draftUri);
            screenRecorder.start();
        }
    }

    public void onStoppedScreenRecorder() {
        if (isWaitingForCompletingRecorder) {
            isWaitingForCompletingRecorder = false;
            YTWHelper.killAll(SRecorderService.BUILDIN_RECORDER_NAME, false);
            hideProgressDialog();
            startActivityForResult(new Intent(this, EditVideoActivity.class)
                            .setData(Uri.parse(screenRecorder.videoPath))
                            .putExtra("draft_path", draftUri.getPath()),
                    INTENT_REQUEST_CODE_CUT_VIDEO
            );
        }
    }

    BroadcastReceiver recorderStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SRecorderService.ACTION_SCREEN_RECORDER_STARTED)) {
                onStartedScreenRecorder();
            } else if (intent.getAction().equals(SRecorderService.ACTION_SCREEN_RECORDER_STOPPED)) {
                onStoppedScreenRecorder();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INTENT_REQUEST_CODE_CUT_VIDEO) {
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

    public void videoQualityOptionOnClick(View view) {
        final Button optBtn = (Button) view;
        List<ActionSheetItem> items = new ArrayList<ActionSheetItem>();
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_high),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        screenRecorder.videoQuality = 1;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_high));
                    }
                }));
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_middle),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        screenRecorder.videoQuality = 0;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_middle));
                    }
                }));
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_low),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        screenRecorder.videoQuality = -1;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_low));
                    }
                }));
        ActionSheet.showWithItems(this, items);
    }

    public void showTouchesOptionOnClick(View view) {
        final Button optBtn = (Button) view;
        screenRecorder.showTouches = screenRecorder.showTouches > 0 ? 0 : 1;
        optBtn.setText(screenRecorder.showTouches > 0 ?
                getString(R.string.video_encoder_show_touches_option_title_on) :
                getString(R.string.video_encoder_show_touches_option_title_off));
    }

    private boolean isWaitingForCompletingRecorder;
    @Override
    protected void onStart() {
        super.onStart();

        logd("onStart");
        screenRecorder.videoPath = YTWHelper
                .prepareFilePathForVideoSaveWithDraftUri(draftUri);
        isWaitingForCompletingRecorder = true;
        if (screenRecorder.stop()) {
            showProgressDialog(R.string.stopping_screen_recorder);
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        logd("force stop recorder");
                        onStoppedScreenRecorder();
                    }
                }
            }, 3000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(recorderStartedReceiver);
    }
}