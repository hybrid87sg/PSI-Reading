package sg.psi.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A 270° ring gauge that fills up to the PSI value (scale 0–300) in the band colour. */
public class PsiGauge extends View {

    private static final float START_ANGLE = 135f;
    private static final float FULL_SWEEP = 270f;
    private static final float SCALE_MAX = 300f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private Integer value;
    private float progress = 1f;
    private ValueAnimator animator;

    public PsiGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1B535C);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        number.setColor(0xFFFFFFFF);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        unit.setColor(0xFF8DB5B9);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        unit.setLetterSpacing(0.15f);
    }

    /** Shows {@code psi} (or an empty ring when null), counting up from zero when {@code animate}. */
    public void setValue(Integer psi, boolean animate) {
        value = psi;
        if (animator != null) animator.cancel();
        setContentDescription(psi == null ? "No reading" : "PSI " + psi);
        if (animate && psi != null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(900);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            progress = 1f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = size * 0.1f;
        float r = size / 2f - stroke / 2f - 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        canvas.drawArc(oval, START_ANGLE, FULL_SWEEP, false, track);

        String text = "–";
        if (value != null) {
            float fraction = Math.min(value, SCALE_MAX) / SCALE_MAX;
            arc.setStrokeWidth(stroke);
            arc.setColor(PsiClient.bandColor(value));
            canvas.drawArc(oval, START_ANGLE, Math.max(FULL_SWEEP * fraction * progress, 0.5f), false, arc);
            text = String.valueOf(Math.round(value * progress));
        }

        number.setTextSize(size * 0.3f);
        canvas.drawText(text, cx, cy + size * 0.08f, number);
        unit.setTextSize(size * 0.09f);
        canvas.drawText("PSI", cx, cy + size * 0.27f, unit);
    }
}
