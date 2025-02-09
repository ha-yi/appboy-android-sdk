package com.braze.ui.inappmessage.views;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import com.appboy.ui.R;
import com.braze.enums.inappmessage.ClickAction;
import com.braze.models.inappmessage.IInAppMessage;
import com.braze.support.BrazeLogger;
import com.braze.ui.inappmessage.utils.InAppMessageViewUtils;
import com.braze.ui.support.ViewUtils;

public class InAppMessageSlideupView extends InAppMessageBaseView {
  private static final String TAG = BrazeLogger.getBrazeLogTag(InAppMessageSlideupView.class);
  private InAppMessageImageView mInAppMessageImageView;

  public InAppMessageSlideupView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void applyInAppMessageParameters(IInAppMessage inAppMessage) {
    mInAppMessageImageView = findViewById(R.id.com_braze_inappmessage_slideup_imageview);
    mInAppMessageImageView.setInAppMessageImageCropType(inAppMessage.getCropType());
  }

  public void setMessageChevron(int color, ClickAction clickAction) {
    if (clickAction == ClickAction.NONE) {
      getMessageChevronView().setVisibility(View.GONE);
    } else {
      InAppMessageViewUtils.setViewBackgroundColorFilter(getMessageChevronView(), color);
    }
  }

  @Override
  public void setMessageBackgroundColor(int color) {
    if (getMessageBackgroundObject().getBackground() instanceof GradientDrawable) {
      InAppMessageViewUtils.setViewBackgroundColorFilter(getMessageBackgroundObject(), color);
    } else {
      super.setMessageBackgroundColor(color);
    }
  }

  @Override
  public void resetMessageMargins(boolean imageRetrievalSuccessful) {
    super.resetMessageMargins(imageRetrievalSuccessful);

    boolean isIconBlank = getMessageIconView() == null || getMessageIconView().getText() == null || getMessageIconView().getText().length() == 0;
    if (!imageRetrievalSuccessful && isIconBlank) {
      // There's neither an icon nor an image
      // Remove the image container layout and reset our text's left margin
      RelativeLayout imageContainerLayout = findViewById(R.id.com_braze_inappmessage_slideup_image_layout);
      if (imageContainerLayout != null) {
        ViewUtils.removeViewFromParent(imageContainerLayout);
      }

      // Reset the margin for the message
      TextView slideupMessage = findViewById(R.id.com_braze_inappmessage_slideup_message);
      RelativeLayout.LayoutParams layoutParams = (LayoutParams) slideupMessage.getLayoutParams();
      layoutParams.leftMargin = getContext().getResources().getDimensionPixelSize(R.dimen.com_braze_inappmessage_slideup_left_message_margin_no_image);
      slideupMessage.setLayoutParams(layoutParams);
    }
  }

  @Override
  public TextView getMessageTextView() {
    return findViewById(R.id.com_braze_inappmessage_slideup_message);
  }

  @Override
  public View getMessageBackgroundObject() {
    return findViewById(R.id.com_braze_inappmessage_slideup_container);
  }

  @Override
  public ImageView getMessageImageView() {
    return mInAppMessageImageView;
  }

  @Override
  public TextView getMessageIconView() {
    return findViewById(R.id.com_braze_inappmessage_slideup_icon);
  }

  /**
   * Applies the {@link WindowInsetsCompat} by ensuring any part of the slideup does not render in the cutout area.
   *
   * @param insets The {@link WindowInsetsCompat} object directly from
   * {@link androidx.core.view.ViewCompat#setOnApplyWindowInsetsListener(View, OnApplyWindowInsetsListener)}.
   */
  @Override
  public void applyWindowInsets(@NonNull WindowInsetsCompat windowInsets) {
    super.applyWindowInsets(windowInsets);
    if (getLayoutParams() == null || !(getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
      BrazeLogger.d(TAG, "Close button view is null or not of the expected class. Not applying window insets.");
      return;
    }

    // Offset the existing margin with whatever the inset margins safe area values are
    final ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();
    layoutParams.setMargins(
        ViewUtils.getMaxSafeLeftInset(windowInsets) + layoutParams.leftMargin,
        ViewUtils.getMaxSafeTopInset(windowInsets) + layoutParams.topMargin,
        ViewUtils.getMaxSafeRightInset(windowInsets) + layoutParams.rightMargin,
        ViewUtils.getMaxSafeBottomInset(windowInsets) + layoutParams.bottomMargin);
  }

  public View getMessageChevronView() {
    return findViewById(R.id.com_braze_inappmessage_slideup_chevron);
  }
}
