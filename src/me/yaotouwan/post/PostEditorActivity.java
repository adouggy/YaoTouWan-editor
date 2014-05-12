package me.yaotouwan.post;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

/**
 * Created by jason on 14-4-10.
 */
public class PostEditorActivity extends BaseActivity {

    EditText editor;
    ImageView imageView;
    String imagePath;
    Bitmap croppedPreviewImage;
    String originalText;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        menuResId = R.menu.post_editor_actions;

        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            bar.setTitle(R.string.cancel);
        }

        Uri previewImageUri = getIntent().getData();
        if (previewImageUri != null) {
            setContentView(R.layout.post_editor_image);

            imageView = (ImageView) findViewById(R.id.post_editor_dialog_preview);
            imagePath = previewImageUri.getPath();
            imageView.setImageDrawable(Drawable.createFromPath(imagePath));
        } else {
            setContentView(R.layout.post_editor);
        }

        editor = (EditText) findViewById(R.id.post_editor_dialog_text);
        originalText = getIntent().getStringExtra("text");
        if (originalText == null) originalText = "";
        if (originalText.length() > 0) {
            editor.setText(originalText);
            editor.setSelection(originalText.length());
        }

        if (previewImageUri != null) {
            editor.setHint(R.string.post_image_desc_hint);
        } else {
            editor.setHint(R.string.post_section_hint);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        layoutSubviews();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            hideSoftKeyboard();
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void layoutSubviews() {
        if (imagePath != null) {
            Bitmap previewImage  = BitmapFactory.decodeFile(imagePath);
            double imageAspect = previewImage.getWidth() * 1.0 / previewImage.getHeight();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(editor.getWidth(),
                    (int) (editor.getWidth() / imageAspect));
            imageView.setLayoutParams(p);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    public void onFinishClick(MenuItem menuItem) {
        hideSoftKeyboard();

        Intent intent = new Intent();
        intent.putExtra("text",
                editor.getText() != null ? editor.getText().toString() : "");
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // todo add save item
        String currentText = editor.getText() != null ? editor.getText().toString() : null;
        if (currentText == null) currentText = "";
        if (!currentText.equals(originalText)) {
            YTWHelper.confirm(this, getString(R.string.post_editor_giveup_change), new Dialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    protected void onKeyboardShow() {
        if (imagePath != null) {
            imageView.setVisibility(View.GONE);

            Bitmap previewImage  = BitmapFactory.decodeFile(imagePath);
            double imageAspect = previewImage.getWidth() * 1.0 / previewImage.getHeight();
            double editorAspect = editor.getWidth() * 1.0 / editor.getHeight();
            if (imageAspect - editorAspect > 0.1) {
                Rect r = new Rect();
                r.left = (int) (previewImage.getWidth() / 2 - previewImage.getHeight() * editorAspect / 2);
                r.right = (int) (previewImage.getWidth() / 2 + previewImage.getHeight() * editorAspect / 2);
                r.top = 0;
                r.bottom = r.top + previewImage.getHeight();
                croppedPreviewImage = Bitmap.createBitmap(previewImage, r.left, r.top, r.width(), r.height());
            } else if (imageAspect - editorAspect < 0.1) {
                Rect r = new Rect();
                r.top = (int) (previewImage.getHeight() / 2 - previewImage.getWidth() / editorAspect / 2);
                r.bottom = (int) (previewImage.getHeight() / 2 + previewImage.getWidth() / editorAspect / 2);
                r.left = 0;
                r.right = previewImage.getWidth();
                croppedPreviewImage = Bitmap.createBitmap(previewImage, r.left, r.top, r.width(), r.height());
            }
            if (croppedPreviewImage != null) {
                editor.setBackgroundDrawable(new BitmapDrawable(croppedPreviewImage));
            } else {
                editor.setBackgroundDrawable(new BitmapDrawable(previewImage));
            }
        }
    }
}