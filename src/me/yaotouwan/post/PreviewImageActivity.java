package me.yaotouwan.post;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;
import uk.co.senab.photoview.PhotoViewAttacher;

public class PreviewImageActivity extends BaseActivity {

    private PhotoViewAttacher mAttacher;

    public static Drawable placeHolder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_image);

        hideActionBar();
        enterFullscreen();

        final ImageView mImageView = (ImageView) findViewById(R.id.photo_view);
        final Uri uri = getIntent().getData();
        if (uri != null) {
            if (placeHolder != null) {
                mImageView.setImageDrawable(placeHolder);

                Point imageSize = YTWHelper.getImageSize(uri.getPath());
                if (imageSize.x > placeHolder.getBounds().right * 1.2
                        || imageSize.y > placeHolder.getBounds().bottom * 1.2) {

                    mImageView.post(new Runnable() {
                        @Override
                        public void run() {
                            mImageView.setImageURI(uri);
                        }
                    });
                }
                placeHolder = null;
            } else {
                mImageView.setImageURI(uri);
            }
        }

        // The MAGIC happens here!
        mAttacher = new PhotoViewAttacher(mImageView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Need to call clean-up
        mAttacher.cleanup();
    }
}
