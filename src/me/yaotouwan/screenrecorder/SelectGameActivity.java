package me.yaotouwan.screenrecorder;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;
import me.yaotouwan.R;
import me.yaotouwan.post.BaseActivity;
import me.yaotouwan.post.RecordScreenActivity;
import me.yaotouwan.util.AppPackageHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-27.
 */
public class SelectGameActivity extends BaseActivity {

    ProgressDialog mProgressDialog;

    public static List<AppPackageHelper.Game> preLoadGames;
    List<AppPackageHelper.Game> gamesInstalled;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_game);
        setupActionBar(R.string.select_game_title);

        if (preLoadGames != null) {
            gamesInstalled = preLoadGames;
            reloadData();
            preLoadGames = null;
        } else {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.select_game_waiting_tip));
            mProgressDialog.setCancelable(true);
            mProgressDialog.show();

            new AppPackageHelper().getPackages(this, new AppPackageHelper.AppPackageHelperDelegate() {
                @Override
                public void onComplete(List<AppPackageHelper.Game> games) {
                    gamesInstalled = games;
                    reloadData();

                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            });
        }
    }

    void reloadData() {
        GridView gamesView = (GridView) findViewById(R.id.grid_view);
        GameAdapter adapter = new GameAdapter(SelectGameActivity.this, gamesInstalled);
        gamesView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
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
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SelectGameActivity.this, RecordScreenActivity.class);
                    String pname = getItem(position).pname;
                    intent.putExtra("package_name", pname);
                    startActivity(intent);
                }
            });

            TextView nameView = (TextView) rowView.findViewById(R.id.game_name);
            nameView.setText(game.appname);

            return rowView;
        }

    }

    public void selectOther(View view) {
        pushActivity(RecordScreenActivity.class);
    }
}