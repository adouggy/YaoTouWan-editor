package me.yaotouwan;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.TextView;
import me.yaotouwan.post.BaseActivity;
import me.yaotouwan.post.PostActivity;

import java.io.File;

public class MainActivity extends BaseActivity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

//        TextView tv = (TextView) findViewById(R.id.testview);
//        TextPaint tp = tv.getPaint();
//        tp.setFakeBoldText(true);
//        SpannableString str = new SpannableString("维基百科");
//        str.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        tv.setText(str);
    }

    public void startPost(View btn) {
        pushActivity(PostActivity.class, Uri.parse("/sdcard/yaotouwan/post_20140509_100901.json"));
        File path =  Environment.getExternalStoragePublicDirectory
                (Environment.DIRECTORY_DCIM);
        logd(path.getAbsolutePath());
//        pushActivity(TestEditTextInListViewActivity.class);
//        pushActivity(PostEditorActivity.class);
//        pushActivity(EditVideoActivity.class,
//                Uri.parse("/sdcard/yaotouwan/video/20140409_165049_638.mp4"));
    }

    @Override
     public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
