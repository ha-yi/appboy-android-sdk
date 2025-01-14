package com.braze.ui.inappmessage.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.braze.support.BrazeLogger;
import com.braze.ui.inappmessage.BrazeInAppMessageManager;
import com.braze.ui.inappmessage.listeners.IWebViewClientStateListener;
import com.braze.ui.inappmessage.utils.InAppMessageViewUtils;
import com.braze.ui.inappmessage.utils.InAppMessageWebViewClient;
import com.braze.ui.support.ViewUtils;

public abstract class InAppMessageHtmlBaseView extends RelativeLayout implements IInAppMessageView {
  private static final String TAG = BrazeLogger.getBrazeLogTag(InAppMessageHtmlBaseView.class);
  private static final String HTML_MIME_TYPE = "text/html";
  private static final String HTML_ENCODING = "utf-8";
  private static final String FILE_URI_SCHEME_PREFIX = "file://";
  /**
   * A url for the {@link WebView} to load when display is finished.
   */
  private static final String FINISHED_WEBVIEW_URL = "about:blank";

  /**
   * @deprecated Please use {@link #BRAZE_BRIDGE_PREFIX} instead. Deprecated since 4/27/21
   */
  public static final String APPBOY_BRIDGE_PREFIX = "appboyInternalBridge";
  public static final String BRAZE_BRIDGE_PREFIX = "brazeInternalBridge";

  protected WebView mMessageWebView;
  private InAppMessageWebViewClient mInAppMessageWebViewClient;
  private boolean mIsFinished = false;

  public InAppMessageHtmlBaseView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Should be called when the held {@link WebView} of this class is
   * done displaying its message. Future calls to
   * {@link #getMessageWebView()} will return null afterwards.
   */
  public void finishWebViewDisplay() {
    BrazeLogger.d(TAG, "Finishing WebView display");
    // Note that WebView.destroy() is not called here since that
    // causes immense issues with the system's own closing of
    // the WebView after we're done with it.
    mIsFinished = true;
    if (mMessageWebView != null) {
      mMessageWebView.loadUrl(FINISHED_WEBVIEW_URL);
      mMessageWebView.onPause();
      mMessageWebView.removeAllViews();
      mMessageWebView = null;
    }
  }

  /**
   * @return The {@link WebView} displaying the HTML content of this in-app message.
   */
  @SuppressLint({"SetJavaScriptEnabled"})
  public WebView getMessageWebView() {
    if (mIsFinished) {
      BrazeLogger.w(TAG, "Cannot return the WebView for an already finished message");
      return null;
    }
    final int webViewViewId = getWebViewViewId();
    if (webViewViewId == 0) {
      BrazeLogger.d(TAG, "Cannot find WebView. getWebViewViewId() returned 0.");
      return null;
    }
    if (mMessageWebView != null) {
      return mMessageWebView;
    }
    mMessageWebView = findViewById(webViewViewId);
    if (mMessageWebView == null) {
      BrazeLogger.d(TAG, "findViewById for " + webViewViewId + " returned null. Returning null for WebView.");
      return null;
    }
    WebSettings webSettings = mMessageWebView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webSettings.setUseWideViewPort(true);
    webSettings.setLoadWithOverviewMode(true);
    webSettings.setDisplayZoomControls(false);
    webSettings.setDomStorageEnabled(true);
    // Needed since locally downloaded assets are under `file://` schemes
    webSettings.setAllowFileAccess(true);
    // This enables hardware acceleration if the manifest also has it defined.
    // If not defined, then the layer type will fallback to software.
    mMessageWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    mMessageWebView.setBackgroundColor(Color.TRANSPARENT);

    try {
      // Note that this check is OS version agnostic since the Android WebView can be
      // updated independently
      if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
          && ViewUtils.isDeviceInNightMode(getContext())) {
        WebSettingsCompat.setForceDark(webSettings,
            WebSettingsCompat.FORCE_DARK_ON);
      }

      if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
        WebSettingsCompat.setForceDarkStrategy(webSettings,
            WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
      }
    } catch (Throwable e) {
      BrazeLogger.e(TAG, "Failed to set dark mode WebView settings", e);
    }

    // Set the client for console logging. See https://developer.android.com/guide/webapps/debugging.html
    mMessageWebView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onConsoleMessage(ConsoleMessage cm) {
        BrazeLogger.d(TAG, "Braze HTML In-app Message log. Line: " + cm.lineNumber()
            + ". SourceId: " + cm.sourceId()
            + ". Log Level: " + cm.messageLevel()
            + ". Message: " + cm.message());
        return true;
      }

      @Nullable
      @Override
      public Bitmap getDefaultVideoPoster() {
        // This bitmap is used to eliminate the default black & white
        // play icon used as the default poster.
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
      }
    });
    return mMessageWebView;
  }

  public View getMessageClickableView() {
    return this;
  }

  /**
   * Loads the WebView using an html string and local file resource url. This url should be a path
   * to a file on the local filesystem.
   *
   * @param htmlBody          Html text encoded in utf-8
   * @param assetDirectoryUrl path to the local assets file
   */
  public void setWebViewContent(String htmlBody, String assetDirectoryUrl) {
    getMessageWebView().loadDataWithBaseURL(FILE_URI_SCHEME_PREFIX + assetDirectoryUrl + "/", htmlBody, HTML_MIME_TYPE, HTML_ENCODING, null);
  }

  /**
   * Loads the WebView using just an html string.
   *
   * @param htmlBody Html text encoded in utf-8
   */
  public void setWebViewContent(String htmlBody) {
    // File URIs must be loaded with this "file://" scheme
    // since our html might have mixed http/data/file content
    // See https://developer.android.com/reference/android/webkit/WebView#loadData(java.lang.String,%20java.lang.String,%20java.lang.String)
    getMessageWebView().loadDataWithBaseURL(FILE_URI_SCHEME_PREFIX + "/", htmlBody, HTML_MIME_TYPE, HTML_ENCODING, null);
  }

  public void setInAppMessageWebViewClient(InAppMessageWebViewClient inAppMessageWebViewClient) {
    getMessageWebView().setWebViewClient(inAppMessageWebViewClient);
    mInAppMessageWebViewClient = inAppMessageWebViewClient;
  }

  public void setHtmlPageFinishedListener(IWebViewClientStateListener listener) {
    if (mInAppMessageWebViewClient != null) {
      mInAppMessageWebViewClient.setWebViewClientStateListener(listener);
    }
  }

  /**
   * Html in-app messages can alternatively be closed by the back button.
   * <p>
   * Note: If the internal WebView has focus instead of this view, back button events on html
   * in-app messages are handled separately in {@link InAppMessageWebView#onKeyDown(int, KeyEvent)}
   *
   * @return If the button pressed was the back button, close the in-app message
   * and return true to indicate that the event was handled.
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && BrazeInAppMessageManager.getInstance().getDoesBackButtonDismissInAppMessageView()) {
      InAppMessageViewUtils.closeInAppMessageOnKeycodeBack();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  /**
   * Returns the {@link View#getId()} used in the
   * default {@link InAppMessageHtmlBaseView#getMessageWebView()}
   * implementation.
   *
   * @return The {@link View#getId()} for the {@link WebView} backing this message.
   */
  public abstract int getWebViewViewId();

  /**
   * HTML messages can alternatively be closed by the back button.
   *
   * @return If the button pressed was the back button, close the in-app message
   * and return true to indicate that the event was handled.
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (!isInTouchMode() && event.getKeyCode() == KeyEvent.KEYCODE_BACK && BrazeInAppMessageManager.getInstance().getDoesBackButtonDismissInAppMessageView()) {
      InAppMessageViewUtils.closeInAppMessageOnKeycodeBack();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }
}
