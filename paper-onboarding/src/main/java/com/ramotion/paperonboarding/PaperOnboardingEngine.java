package com.ramotion.paperonboarding;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ramotion.paperonboarding.listeners.AnimatorEndListener;
import com.ramotion.paperonboarding.listeners.OnSwipeListener;
import com.ramotion.paperonboarding.listeners.PaperOnboardingOnChangeListener;
import com.ramotion.paperonboarding.listeners.PaperOnboardingOnLeftOutListener;
import com.ramotion.paperonboarding.listeners.PaperOnboardingOnRightOutListener;
import com.ramotion.paperonboarding.utils.PaperOnboardingEngineDefaults;

import java.util.ArrayList;


public class PaperOnboardingEngine implements PaperOnboardingEngineDefaults {


    private final float dpToPixelsScaleFactor;


    private final RelativeLayout mRootLayout;
    private final FrameLayout mContentTextContainer;
    private final FrameLayout mContentIconContainer;
    private final FrameLayout mBackgroundContainer;
    private final LinearLayout mPagerIconsContainer;

    private final RelativeLayout mContentRootLayout;
    private final LinearLayout mContentCenteredContainer;


    private final Context mAppContext;


    private ArrayList<PaperOnboardingPage> mElements = new ArrayList<>();
    private int mActiveElementIndex = 0;


    private int mPagerElementActiveSize;
    private int mPagerElementNormalSize;
    private int mPagerElementLeftMargin;
    private int mPagerElementRightMargin;


    private PaperOnboardingOnChangeListener mOnChangeListener;
    private PaperOnboardingOnRightOutListener mOnRightOutListener;
    private PaperOnboardingOnLeftOutListener mOnLeftOutListener;


    public PaperOnboardingEngine(View rootLayout, ArrayList<PaperOnboardingPage> contentElements, Context appContext) {
        if (contentElements == null || contentElements.isEmpty())
            throw new IllegalArgumentException("No content elements provided");

        this.mElements.addAll(contentElements);
        this.mAppContext = appContext.getApplicationContext();

        mRootLayout = (RelativeLayout) rootLayout;
        mContentTextContainer = (FrameLayout) rootLayout.findViewById(R.id.onboardingContentTextContainer);
        mContentIconContainer = (FrameLayout) rootLayout.findViewById(R.id.onboardingContentIconContainer);
        mBackgroundContainer = (FrameLayout) rootLayout.findViewById(R.id.onboardingBackgroundContainer);
        mPagerIconsContainer = (LinearLayout) rootLayout.findViewById(R.id.onboardingPagerIconsContainer);

        mContentRootLayout = (RelativeLayout) mRootLayout.getChildAt(1);
        mContentCenteredContainer = (LinearLayout) mContentRootLayout.getChildAt(0);

        this.dpToPixelsScaleFactor = this.mAppContext.getResources().getDisplayMetrics().density;

        initializeStartingState();

        mRootLayout.setOnTouchListener(new OnSwipeListener(mAppContext) {
            @Override
            public void onSwipeLeft() {
                toggleContent(false);
            }

            @Override
            public void onSwipeRight() {
                toggleContent(true);
            }

        });

        mRootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mRootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                mPagerElementActiveSize = mPagerIconsContainer.getHeight();
                mPagerElementNormalSize = Math.min(mPagerIconsContainer.getChildAt(0).getHeight(),
                        mPagerIconsContainer.getChildAt(mPagerIconsContainer.getChildCount() - 1).getHeight());

                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) mPagerIconsContainer.getChildAt(0).getLayoutParams();
                mPagerElementLeftMargin = layoutParams.leftMargin;
                mPagerElementRightMargin = layoutParams.rightMargin;

                mPagerIconsContainer.setX(calculateNewPagerPosition(0));
                mContentCenteredContainer.setY((mContentRootLayout.getHeight() - mContentCenteredContainer.getHeight()) / 2);

            }
        });
    }

    protected int calculateNewPagerPosition(int newActiveElement) {
        newActiveElement++;
        if (newActiveElement <= 0)
            newActiveElement = 1;
        int pagerActiveElemCenterPosX = mPagerElementActiveSize / 2
                + newActiveElement * mPagerElementLeftMargin
                + (newActiveElement - 1) * (mPagerElementNormalSize + mPagerElementRightMargin);
        return mRootLayout.getWidth() / 2 - pagerActiveElemCenterPosX;
    }


    protected int[] calculateCurrentCenterCoordinatesOfPagerElement(int activeElementIndex) {
        int y = (int) (mPagerIconsContainer.getY() + mPagerIconsContainer.getHeight() / 2);

        if (activeElementIndex >= mPagerIconsContainer.getChildCount())
            return new int[]{mRootLayout.getWidth() / 2, y};

        View pagerElem = mPagerIconsContainer.getChildAt(activeElementIndex);
        int x = (int) (mPagerIconsContainer.getX() + pagerElem.getX() + pagerElem.getWidth() / 2);
        return new int[]{x, y};
    }


    protected void initializeStartingState() {

        for (int i = 0; i < mElements.size(); i++) {
            PaperOnboardingPage PaperOnboardingPage = mElements.get(i);
            ViewGroup bottomBarIconElement = createPagerIconElement(PaperOnboardingPage.getBottomBarIconRes(), i == 0);
            mPagerIconsContainer.addView(bottomBarIconElement);
        }

        PaperOnboardingPage activeElement = getActiveElement();

        ViewGroup initialContentText = createContentTextView(activeElement);
        mContentTextContainer.addView(initialContentText);

        ImageView initContentIcon = createContentIconView(activeElement);
        mContentIconContainer.addView(initContentIcon);

        mRootLayout.setBackgroundColor(activeElement.getBgColor());
    }


    protected void toggleContent(boolean prev) {
        int oldElementIndex = mActiveElementIndex;
        PaperOnboardingPage newElement = prev ? toggleToPreviousElement() : toggleToNextElement();

        if (newElement == null) {
            if (prev && mOnLeftOutListener != null)
                mOnLeftOutListener.onLeftOut();
            if (!prev && mOnRightOutListener != null)
                mOnRightOutListener.onRightOut();
            return;
        }

        int newPagerPosX = calculateNewPagerPosition(mActiveElementIndex);


        AnimatorSet bgAnimation = createBGAnimatorSet(newElement.getBgColor());


        Animator pagerMoveAnimation = ObjectAnimator.ofFloat(mPagerIconsContainer, "x", mPagerIconsContainer.getX(), newPagerPosX);
        pagerMoveAnimation.setDuration(ANIM_PAGER_BAR_MOVE_TIME);


        AnimatorSet pagerIconAnimation = createPagerIconAnimation(oldElementIndex, mActiveElementIndex);


        ViewGroup newContentText = createContentTextView(newElement);
        mContentTextContainer.addView(newContentText);
        AnimatorSet contentTextShowAnimation = createContentTextShowAnimation(
                mContentTextContainer.getChildAt(mContentTextContainer.getChildCount() - 2), newContentText);


        ImageView newContentIcon = createContentIconView(newElement);
        mContentIconContainer.addView(newContentIcon);
        AnimatorSet contentIconShowAnimation = createContentIconShowAnimation(
                mContentIconContainer.getChildAt(mContentIconContainer.getChildCount() - 2), newContentIcon);


        Animator centerContentAnimation = createContentCenteringVerticalAnimation(newContentText, newContentIcon);

        centerContentAnimation.start();
        bgAnimation.start();
        pagerMoveAnimation.start();
        pagerIconAnimation.start();
        contentIconShowAnimation.start();
        contentTextShowAnimation.start();

        if (mOnChangeListener != null)
            mOnChangeListener.onPageChanged(oldElementIndex, mActiveElementIndex);
    }

    public void setOnChangeListener(PaperOnboardingOnChangeListener onChangeListener) {
        this.mOnChangeListener = onChangeListener;
    }

    public void setOnRightOutListener(PaperOnboardingOnRightOutListener onRightOutListener) {
        this.mOnRightOutListener = onRightOutListener;
    }

    public void setOnLeftOutListener(PaperOnboardingOnLeftOutListener onLeftOutListener) {
        this.mOnLeftOutListener = onLeftOutListener;
    }


    protected AnimatorSet createBGAnimatorSet(final int color) {
        final View bgColorView = new ImageView(mAppContext);
        bgColorView.setLayoutParams(new RelativeLayout.LayoutParams(mRootLayout.getWidth(), mRootLayout.getHeight()));
        bgColorView.setBackgroundColor(color);
        mBackgroundContainer.addView(bgColorView);

        int[] pos = calculateCurrentCenterCoordinatesOfPagerElement(mActiveElementIndex);

        float finalRadius = mRootLayout.getWidth() > mRootLayout.getHeight() ? mRootLayout.getWidth() : mRootLayout.getHeight();

        AnimatorSet bgAnimSet = new AnimatorSet();
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(bgColorView, pos[0], pos[1], 0, finalRadius);
        Animator fadeIn = ObjectAnimator.ofFloat(bgColorView, "alpha", 0, 1);
        circularReveal.setInterpolator(new AccelerateInterpolator());
        bgAnimSet.playTogether(circularReveal, fadeIn);
        bgAnimSet.setDuration(ANIM_BACKGROUND_TIME);
        bgAnimSet.addListener(new AnimatorEndListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mRootLayout.setBackgroundColor(color);
                bgColorView.setVisibility(View.GONE);
                mBackgroundContainer.removeView(bgColorView);
            }
        });
        return bgAnimSet;
    }


    private AnimatorSet createContentTextShowAnimation(final View currentContentText, final View newContentText) {
        int positionDeltaPx = dpToPixels(CONTENT_TEXT_POS_DELTA_Y_DP);
        AnimatorSet animations = new AnimatorSet();
        Animator currentContentMoveUp = ObjectAnimator.ofFloat(currentContentText, "y", 0, -positionDeltaPx);
        currentContentMoveUp.setDuration(ANIM_CONTENT_TEXT_HIDE_TIME);
        currentContentMoveUp.addListener(new AnimatorEndListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContentTextContainer.removeView(currentContentText);
            }
        });
        Animator currentContentFadeOut = ObjectAnimator.ofFloat(currentContentText, "alpha", 1, 0);
        currentContentFadeOut.setDuration(ANIM_CONTENT_TEXT_HIDE_TIME);

        animations.playTogether(currentContentMoveUp, currentContentFadeOut);

        Animator newContentMoveUp = ObjectAnimator.ofFloat(newContentText, "y", positionDeltaPx, 0);
        newContentMoveUp.setDuration(ANIM_CONTENT_TEXT_SHOW_TIME);

        Animator newContentFadeIn = ObjectAnimator.ofFloat(newContentText, "alpha", 0, 1);
        newContentFadeIn.setDuration(ANIM_CONTENT_TEXT_SHOW_TIME);

        animations.playTogether(newContentMoveUp, newContentFadeIn);

        animations.setInterpolator(new DecelerateInterpolator());

        return animations;
    }


    protected AnimatorSet createContentIconShowAnimation(final View currentContentIcon, final View newContentIcon) {
        int positionDeltaPx = dpToPixels(CONTENT_ICON_POS_DELTA_Y_DP);
        AnimatorSet animations = new AnimatorSet();
        Animator currentContentMoveUp = ObjectAnimator.ofFloat(currentContentIcon, "y", 0, -positionDeltaPx);
        currentContentMoveUp.setDuration(ANIM_CONTENT_ICON_HIDE_TIME);

        currentContentMoveUp.addListener(new AnimatorEndListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContentIconContainer.removeView(currentContentIcon);
            }
        });
        Animator currentContentFadeOut = ObjectAnimator.ofFloat(currentContentIcon, "alpha", 1, 0);
        currentContentFadeOut.setDuration(ANIM_CONTENT_ICON_HIDE_TIME);

        animations.playTogether(currentContentMoveUp, currentContentFadeOut);

        Animator newContentMoveUp = ObjectAnimator.ofFloat(newContentIcon, "y", positionDeltaPx, 0);
        newContentMoveUp.setDuration(ANIM_CONTENT_ICON_SHOW_TIME);

        Animator newContentFadeIn = ObjectAnimator.ofFloat(newContentIcon, "alpha", 0, 1);
        newContentFadeIn.setDuration(ANIM_CONTENT_ICON_SHOW_TIME);

        animations.playTogether(newContentMoveUp, newContentFadeIn);

        animations.setInterpolator(new DecelerateInterpolator());

        return animations;
    }

    protected Animator createContentCenteringVerticalAnimation(View newContentText, View newContentIcon) {
        newContentText.measure(View.MeasureSpec.makeMeasureSpec(mContentCenteredContainer.getWidth(), View.MeasureSpec.AT_MOST), -2);
        int measuredContentTextHeight = newContentText.getMeasuredHeight();
        newContentIcon.measure(-2, -2);
        int measuredContentIconHeight = newContentIcon.getMeasuredHeight();

        int newHeightOfContent = measuredContentIconHeight + measuredContentTextHeight + ((ViewGroup.MarginLayoutParams) mContentTextContainer.getLayoutParams()).topMargin;
        Animator centerContentAnimation = ObjectAnimator.ofFloat(mContentCenteredContainer, "y", mContentCenteredContainer.getY(),
                (mContentRootLayout.getHeight() - newHeightOfContent) / 2);
        centerContentAnimation.setDuration(ANIM_CONTENT_CENTERING_TIME);
        centerContentAnimation.setInterpolator(new DecelerateInterpolator());
        return centerContentAnimation;
    }

    protected AnimatorSet createPagerIconAnimation(int oldIndex, int newIndex) {
        AnimatorSet animations = new AnimatorSet();
        animations.setDuration(ANIM_PAGER_ICON_TIME);


        final ViewGroup oldActiveItem = (ViewGroup) mPagerIconsContainer.getChildAt(oldIndex);
        final LinearLayout.LayoutParams oldActiveItemParams = (LinearLayout.LayoutParams) oldActiveItem.getLayoutParams();
        ValueAnimator oldItemScaleDown = ValueAnimator.ofInt(mPagerElementActiveSize, mPagerElementNormalSize);
        oldItemScaleDown.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                oldActiveItemParams.height = (Integer) valueAnimator.getAnimatedValue();
                oldActiveItemParams.width = (Integer) valueAnimator.getAnimatedValue();
                oldActiveItem.requestLayout();
            }
        });

        final View oldActiveIcon = oldActiveItem.getChildAt(1);
        Animator oldActiveIconFadeOut = ObjectAnimator.ofFloat(oldActiveIcon, "alpha", 1, 0);


        final ImageView oldActiveShape = (ImageView) oldActiveItem.getChildAt(0);
        oldActiveShape.setImageResource(oldIndex - newIndex > 0 ? R.drawable.onboarding_pager_circle_icon : R.drawable.onboarding_pager_round_icon);
        Animator oldActiveShapeFadeIn = ObjectAnimator.ofFloat(oldActiveShape, "alpha", 0, PAGER_ICON_SHAPE_ALPHA);

        animations.playTogether(oldItemScaleDown, oldActiveIconFadeOut, oldActiveShapeFadeIn);


        final ViewGroup newActiveItem = (ViewGroup) mPagerIconsContainer.getChildAt(newIndex);
        final LinearLayout.LayoutParams newActiveItemParams = (LinearLayout.LayoutParams) newActiveItem.getLayoutParams();
        ValueAnimator newItemScaleUp = ValueAnimator.ofInt(mPagerElementNormalSize, mPagerElementActiveSize);
        newItemScaleUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                newActiveItemParams.height = (Integer) valueAnimator.getAnimatedValue();
                newActiveItemParams.width = (Integer) valueAnimator.getAnimatedValue();
                newActiveItem.requestLayout();
            }
        });


        final View newActiveIcon = newActiveItem.getChildAt(1);
        Animator newActiveIconFadeIn = ObjectAnimator.ofFloat(newActiveIcon, "alpha", 0, 1);


        final ImageView newActiveShape = (ImageView) newActiveItem.getChildAt(0);
        Animator newActiveShapeFadeOut = ObjectAnimator.ofFloat(newActiveShape, "alpha", PAGER_ICON_SHAPE_ALPHA, 0);


        animations.playTogether(newItemScaleUp, newActiveShapeFadeOut, newActiveIconFadeIn);

        animations.setInterpolator(new DecelerateInterpolator());
        return animations;
    }


    @SuppressWarnings("SuspiciousNameCombination")
    protected ViewGroup createPagerIconElement(int iconDrawableRes, boolean isActive) {
        LayoutInflater vi = LayoutInflater.from(mAppContext);
        FrameLayout bottomBarElement = (FrameLayout) vi.inflate(R.layout.onboarding_pager_layout, mPagerIconsContainer, false);
        ImageView elementShape = (ImageView) bottomBarElement.getChildAt(0);
        ImageView elementIcon = (ImageView) bottomBarElement.getChildAt(1);
        elementIcon.setImageResource(iconDrawableRes);
        if (isActive) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) bottomBarElement.getLayoutParams();
            layoutParams.width = mPagerIconsContainer.getLayoutParams().height;
            layoutParams.height = mPagerIconsContainer.getLayoutParams().height;
            elementShape.setAlpha(0f);
            elementIcon.setAlpha(1f);
        } else {
            elementShape.setAlpha(PAGER_ICON_SHAPE_ALPHA);
            elementIcon.setAlpha(0f);
        }
        return bottomBarElement;
    }

    protected ViewGroup createContentTextView(PaperOnboardingPage PaperOnboardingPage) {
        LayoutInflater vi = LayoutInflater.from(mAppContext);
        ViewGroup contentTextView = (ViewGroup) vi.inflate(R.layout.onboarding_text_content_layout, mContentTextContainer, false);
        TextView contentTitle = (TextView) contentTextView.getChildAt(0);
        contentTitle.setText(PaperOnboardingPage.getTitleText());
        TextView contentText = (TextView) contentTextView.getChildAt(1);
        contentText.setText(PaperOnboardingPage.getDescriptionText());
        return contentTextView;
    }


    protected ImageView createContentIconView(PaperOnboardingPage PaperOnboardingPage) {
        ImageView contentIcon = new ImageView(mAppContext);
        contentIcon.setImageResource(PaperOnboardingPage.getContentIconRes());
        FrameLayout.LayoutParams iconLP = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLP.gravity = Gravity.CENTER;
        contentIcon.setLayoutParams(iconLP);
        return contentIcon;
    }

    public int getActiveElementIndex() {
        return mActiveElementIndex;
    }


    protected PaperOnboardingPage getActiveElement() {
        return mElements.size() > mActiveElementIndex ? mElements.get(mActiveElementIndex) : null;
    }


    protected PaperOnboardingPage toggleToPreviousElement() {
        if (mActiveElementIndex - 1 >= 0) {
            mActiveElementIndex--;
            return mElements.size() > mActiveElementIndex ? mElements.get(mActiveElementIndex) : null;
        } else
            return null;
    }


    protected PaperOnboardingPage toggleToNextElement() {
        if (mActiveElementIndex + 1 < mElements.size()) {
            mActiveElementIndex++;
            return mElements.size() > mActiveElementIndex ? mElements.get(mActiveElementIndex) : null;
        } else
            return null;
    }


    protected int dpToPixels(int dpValue) {
        return (int) (dpValue * dpToPixelsScaleFactor + 0.5f);
    }


}
