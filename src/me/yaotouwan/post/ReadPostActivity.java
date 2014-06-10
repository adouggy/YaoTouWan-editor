package me.yaotouwan.post;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.os.Message;
import android.util.Log;
import android.webkit.*;
import me.yaotouwan.R;
import me.yaotouwan.android.util.MyConstants;
import me.yaotouwan.screenrecorder.YoukuUploader;
import me.yaotouwan.util.YTWHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.util.EntityUtils;

/**
 * Created by jason on 14-3-18.
 * 
 * Update by ade on 14-6-9, abstract some code to helper..
 */
public class ReadPostActivity extends BaseActivity {

	ListView				postItemsListView;
	PostListViewDataSource	adapter;
	Fragment				headerFragment;
	Fragment				footerFragment;
	JSONArray				sections;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.read_post);
		setupActionBar(R.string.post_title);

		Intent intent = getIntent();
		Uri postUri = intent.getData();

		postItemsListView = (ListView) findViewById(R.id.post_items);
		adapter = new PostListViewDataSource();

		View headerView = getLayoutInflater().inflate(R.layout.read_post_header, null);
		View footerView = getLayoutInflater().inflate(R.layout.read_post_footer, null);
		postItemsListView.addHeaderView(headerView);
		postItemsListView.addFooterView(footerView);

		postItemsListView.setAdapter(adapter);
		postItemsListView.setOnScrollListener(adapter.helper);

		// FragmentTransaction fragmentTransaction =
		// getSupportFragmentManager().beginTransaction();
		// String headerFragmentClassName = getIntent()
		// .getStringExtra("read_post_header_fragment_class_name");
		// String footerFragmentClassName = getIntent()
		// .getStringExtra("read_post_footer_fragment_class_name");
		// try {
		// headerFragment =
		// (Fragment) Class.forName(headerFragmentClassName).newInstance();
		// footerFragment =
		// (Fragment) Class.forName(footerFragmentClassName).newInstance();
		// fragmentTransaction.replace(R.id.read_post_header, headerFragment);
		// fragmentTransaction.replace(R.id.read_post_footer, footerFragment);
		// } catch (InstantiationException e) {
		// } catch (IllegalAccessException e) {
		// } catch (ClassNotFoundException e) {
		// }
		// fragmentTransaction.commit();

		loadPost(postUri);
	}

	@Override
	public void onBackPressed() {
		if (adapter.helper.youkuFullscreen) {
			adapter.helper.youkuFullscreen = false;
			getRootViewGroup().removeView(adapter.helper.youkuWebView);
			adapter.helper.youkuWebViewParent.addView(adapter.helper.youkuWebView, adapter.helper.youkuWebViewIndex);
			adapter.helper.youkuWebView = null;
			adapter.helper.youkuWebViewParent = null;
			adapter.helper.youkuWebViewIndex = -1;
			exitFullscreen();
			showActionBar();
			return;
		}
		super.onBackPressed();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (adapter.helper.getWebViews() != null) {
			for (WebView webView : adapter.helper.getWebViews()) {
				webView.loadUrl("about:blank");
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		adapter.notifyDataSetChanged();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (adapter.helper.getWebViews() != null) {
			for (WebView webView : adapter.helper.getWebViews()) {
				webView.loadData("about:blank", "text/html", "UTF-8");
			}
		}
	}

	void loadPost(final Uri postUri) {
		if (postUri == null) {
			Toast.makeText(this, "No Uri passed", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		if (!HttpClientUtil.checkConnection(this)) {
			Toast.makeText(this, "没有网络，无法查看文章", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		showProgressDialog();
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void[] params) {
				try {
					HttpClient httpclient = HttpClientUtil.createInstance();
					HttpGet httpGet = new HttpGet(postUri.toString());
					HttpResponse response = httpclient.execute(httpGet);
					if (response != null) {
						String content = EntityUtils.toString(response.getEntity());
						return content;
					}
				} catch (ClientProtocolException e) {
				} catch (IOException e) {
				}
				return null;
			}

			@Override
			protected void onPostExecute(String content) {
				try {
					if (content != null) {
						if (headerFragment != null) {
							((PostHeader) headerFragment).setContent(content);
						}
						if ((footerFragment != null)) {
							((PostFooter) footerFragment).setPostUrl(postUri.toString());
						}
						JSONObject jsonObject = new JSONObject(content);
						if (jsonObject != null) {
							if (jsonObject.has("content")) {
								JSONObject contentObj = jsonObject.getJSONObject("content");
								if (contentObj.has("text")) {
									String text = contentObj.getString("text");
									try {
										sections = new JSONArray(text);
										adapter.notifyDataSetChanged();
									} catch (JSONException e) {
										try {
											JSONObject section = new JSONObject();
											section.put("text", text);
											sections = new JSONArray();
											sections.put(section);
											adapter.notifyDataSetChanged();
										} catch (JSONException e1) {
											e1.printStackTrace();
										}
									}
								}
							}
						}
					}
					hideProgressDialog();
				} catch (JSONException e) {

				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	void loadSections(JSONArray sections) {
		this.sections = sections;
	}

	public class PostListViewDataSource extends BaseAdapter {
		public PostViewHelper	helper;

		public PostListViewDataSource() {
			super();
			helper = new PostViewHelper(ReadPostActivity.this, sections, postItemsListView.getWidth(), postItemsListView);
		}

		@Override
		public int getCount() {
			if (sections == null)
				return 0;
			return sections.length();
		}

		@Override
		public JSONObject getItem(int position) {
			try {
				return sections.getJSONObject(position);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			return helper.inflateView(ReadPostActivity.this, position, convertView, parent);
		}

	}

	public interface PostHeader {
		public void setContent(String content);
	}

	public interface PostFooter {
		public void setPostUrl(String postUrl);
	}

	public static class PostViewHelper implements AbsListView.OnScrollListener {
		private JSONArray		sections;

		private Activity		mContext;
		private ListView		postItemsListView;
		List<WebView>			webViews;
		private LayoutInflater	inflater;
		private int				width;
		String					youkuPlayerHTML;

		static final int		TEXT_STYLE_HEAD		= 1;
		static final int		TEXT_STYLE_BOLD		= 2;
		static final int		TEXT_STYLE_ITALIC	= 3;
		static final int		TEXT_STYLE_QUOTE	= 4;

		public boolean					youkuFullscreen;
		public WebView					youkuWebView;
		public ViewGroup				youkuWebViewParent;
		public int						youkuWebViewIndex	= -1;

		public PostViewHelper(Activity ctx, JSONArray data, int width, ListView listview) {
			mContext = ctx;
			this.sections = data;
			this.width = width;
			this.postItemsListView = listview;
		}

		public List<WebView> getWebViews() {
			return this.webViews;
		}

		public JSONObject getItem(int position) {
			try {
				return sections.getJSONObject(position);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		String getText(int row) {
			try {
				return getItem(row).has("text") ? getItem(row).getString("text") : null;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		String getImagePath(int row) {
			try {
				return getItem(row).has("image_src") ? getItem(row).getString("image_src") : null;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		String getVideoPath(int row) {
			try {
				return getItem(row).has("video_src") ? getItem(row).getString("video_src") : null;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		Integer getTextStyle(int row) {
			try {
				return getItem(row).has("text_style") ? getItem(row).getInt("text_style") : 0;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		Point getMediaSize(int row) {
			try {
				int width = getItem(row).has("media_width") ? getItem(row).getInt("media_width") : 0;
				int height = getItem(row).has("media_height") ? getItem(row).getInt("media_height") : 0;
				if (width > 0 && height > 0)
					return new Point(width, height);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		void applyStyle(TextView textView, String text, int style) {
			if (text == null) {
				textView.setText(text);
				return;
			}

			SpannableString styledText = new SpannableString(text);

			styledText.setSpan(new TypefaceSpan("serif"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (style == TEXT_STYLE_HEAD) {
				styledText.setSpan(new RelativeSizeSpan(1.3f), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (style == TEXT_STYLE_BOLD) {
				styledText.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				styledText.setSpan(new TypefaceSpan("monospace"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (style == TEXT_STYLE_ITALIC) {
				styledText.setSpan(new StyleSpan(Typeface.ITALIC), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				styledText.setSpan(new TypefaceSpan("monospace"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (style == TEXT_STYLE_QUOTE) {
				styledText.setSpan(new QuoteSpan(Color.GRAY), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}

			textView.setText(styledText);
			textView.setMovementMethod(LinkMovementMethod.getInstance());
		}

		public View inflateView(Context ctx, final int position, View convertView, ViewGroup parent) {
			if (inflater == null)
				inflater = (LayoutInflater) ctx.getSystemService(LAYOUT_INFLATER_SERVICE);
			final View rowView;
			if (convertView == null) {
				rowView = inflater.inflate(R.layout.read_post_item, parent, false);
			} else {
				rowView = convertView;
			}
			final TextView textView = (TextView) rowView.findViewById(R.id.post_item_text_readonly);
			final String text = getText(position);
			final String imagePath = getImagePath(position);
			final String videoPath = getVideoPath(position);

			final CachedImageButton previewImageView = (CachedImageButton) rowView.findViewById(R.id.post_item_image);

			final WebView webView = (WebView) rowView.findViewById(R.id.video_webview);
			if (webView == null) {
				return convertView;
			}
			if (webViews == null || !webViews.contains(webView)) {
				if (webViews == null) {
					webViews = new ArrayList<WebView>(3);
				}
				webViews.add(webView);
				webView.getSettings().setJavaScriptEnabled(true);
				webView.getSettings().setAppCacheEnabled(true);
				webView.addJavascriptInterface(new JavaScriptInterface(), "Android");
                webView.setWebChromeClient(new WebChromeClient() {});
			}

			if (imagePath != null) {
				showView(previewImageView);
				hideView(webView);

                previewImageView.setImageWithPath(imagePath, width, scrolling, 0);
				Point imgSize = getMediaSize(position);
				if (imgSize != null) {
					if (imgSize.x > imgSize.y * 2) {
						imgSize.x = imgSize.y * 2;
					} else if (imgSize.y > imgSize.x * 2) {
						imgSize.y = imgSize.x * 2;
					}
					setViewHeight(previewImageView, width * imgSize.y / imgSize.x);
				}
				previewImageView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String imagePath = getImagePath(position);
						String videoPath = getVideoPath(position);
						if (imagePath != null) {
							PreviewImageActivity.placeHolder = previewImageView.getDrawable();
							pushActivity(PreviewImageActivity.class, Uri.parse(imagePath));
						}
					}
				});
			} else if (videoPath != null) {
				hideView(previewImageView);
				showView(webView);

				if ("youku".equals(Uri.parse(videoPath).getScheme())) {
					if (youkuPlayerHTML == null) {
						youkuPlayerHTML = YTWHelper.readAssertsTextContent(mContext, "youku_player.html");
					}
					String videoTag = youkuPlayerHTML;
					String videoID = Uri.parse(videoPath).getHost();
					videoTag = videoTag.replace("{youku_video_id}", videoID);
					videoTag = videoTag.replace("{youku_client_id}", YoukuUploader.CLIENT_ID);
					videoTag = videoTag.replace("{position}", position + "");
					webView.loadData(videoTag, "text/html", "UTF-8");
//                    Log.d("ReadPostActivity", videoTag);
					setViewHeight(webView, width * 3 / 4);
				}
			} else {
				hideView(previewImageView);
				hideView(webView);
			}

			textView.setMinLines(0);

			final Integer style = (Integer) getTextStyle(position);
			applyStyle(textView, text, style);
			if (text == null || text.length() == 0) {
				hideView(textView);
			}

			if (imagePath != null || videoPath != null) {
				textView.setTextSize(14);
			} else {
				textView.setTextSize(16);
			}
			return rowView;
		}

		boolean	scrolling;

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			scrolling = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
		}

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

		}

		protected void showView(View view) {
			view.setVisibility(View.VISIBLE);
		}

		protected void hideView(View view) {
			view.setVisibility(View.GONE);
		}

		public void pushActivity(Class<? extends Activity> activityClass, Uri data) {
			Intent intent = new Intent(mContext, activityClass);
			intent.setData(data);
			mContext.startActivity(intent);
		}

		public void setViewHeight(View view, int height) {
			ViewGroup.LayoutParams p = view.getLayoutParams();
			if (p != null) {
				p.height = height;
			}
			view.setLayoutParams(p);
		}

		protected void doTaskOnMainThread(Runnable runnable) {
			new Handler(mContext.getMainLooper()).post(runnable);
		}

		class JavaScriptInterface {
			@JavascriptInterface
			public void onVideoStart(final int position) {
				doTaskOnMainThread(new Runnable() {
					@Override
					public void run() {
						View rowView = postItemsListView.getChildAt(position + postItemsListView.getHeaderViewsCount() - postItemsListView.getFirstVisiblePosition());
						if (rowView == null)
							return;
						youkuWebView = (WebView) rowView.findViewById(R.id.video_webview);
						if (youkuWebView == null)
							return;
						youkuWebViewParent = (ViewGroup) youkuWebView.getParent();
						if (youkuWebViewParent == null)
							return;
						youkuWebViewIndex = youkuWebViewParent.indexOfChild(youkuWebView);
						youkuWebViewParent.removeView(youkuWebView);
						setViewSize(youkuWebView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
						getRootViewGroup().addView(youkuWebView);
						hideActionBar();
						enterFullscreen();
						youkuFullscreen = true;
					}
				});
			}
		}

		public void setViewSize(View view, int width, int height) {
			ViewGroup.LayoutParams p = view.getLayoutParams();
			if (p != null) {
				p.width = width;
				p.height = height;
			}
			view.setLayoutParams(p);
		}

		public void hideActionBar() {
			if (mContext.getActionBar() != null) {
				mContext.getActionBar().hide();
			}
		}

		public void enterFullscreen() {
			mContext.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			mContext.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// FIXME id..
		protected ViewGroup getRootViewGroup() {
			return (ViewGroup) mContext.findViewById(R.id.root_layout);
		}
	}
}
