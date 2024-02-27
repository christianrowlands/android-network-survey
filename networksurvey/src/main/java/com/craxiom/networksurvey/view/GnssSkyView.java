package com.craxiom.networksurvey.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.listeners.IGnssListener;
import com.craxiom.networksurvey.model.GnssType;
import com.craxiom.networksurvey.util.GpsTestUtil;
import com.craxiom.networksurvey.util.UIUtils;

/**
 * View that shows satellite positions on a circle representing the sky
 */
public class GnssSkyView extends View implements IGnssListener
{
    public static final float MIN_VALUE_CN0 = 10.0f;
    public static final float MAX_VALUE_CN0 = 45.0f;
    public static final float MIN_VALUE_SNR = 0.0f;
    public static final float MAX_VALUE_SNR = 30.0f;

    private static final float PRN_TEXT_SCALE = 0.7f;

    private int satRadius;

    private float[] snrThresholds;

    private int[] snrColors;

    private float[] cn0Thresholds;

    private int[] cn0Colors;

    Context context;

    WindowManager windowManager;

    private Paint horizonActiveFillPaint;
    private Paint horizonInactiveFillPaint;
    private Paint horizonStrokePaint;
    private Paint gridStrokePaint;
    private Paint satelliteFillPaint;
    private Paint satelliteStrokePaint;
    private Paint satelliteUsedStrokePaint;
    private Paint northPaint;
    private Paint northFillPaint;
    private Paint prnIdPaint;
    private Paint notInViewPaint;

    private double orientation = 0.0;

    private boolean started;

    private float[] snrCn0s;
    private float[] elevs;
    private float[] azims;  // Holds either SNR or C/N0 - see #65

    private float snrCn0UsedAvg = 0.0f;

    private float snrCn0InViewAvg = 0.0f;

    private boolean[] usedInFix;

    private int[] prns;
    private int[] constellationType;

    private int svCount;

    private boolean useLegacyGnssApi = false;

    private boolean isSnrBad = false;

    public GnssSkyView(Context context)
    {
        super(context);
        init(context);
    }

    public GnssSkyView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    private void init(Context context)
    {
        this.context = context;
        windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        satRadius = UIUtils.dpToPixels(context, 5);

        horizonActiveFillPaint = new Paint();
        horizonActiveFillPaint.setColor(Color.WHITE);
        horizonActiveFillPaint.setStyle(Paint.Style.FILL);
        horizonActiveFillPaint.setAntiAlias(true);

        horizonInactiveFillPaint = new Paint();
        horizonInactiveFillPaint.setColor(Color.LTGRAY);
        horizonInactiveFillPaint.setStyle(Paint.Style.FILL);
        horizonInactiveFillPaint.setAntiAlias(true);

        horizonStrokePaint = new Paint();
        horizonStrokePaint.setColor(Color.BLACK);
        horizonStrokePaint.setStyle(Paint.Style.STROKE);
        horizonStrokePaint.setStrokeWidth(2.0f);
        horizonStrokePaint.setAntiAlias(true);

        gridStrokePaint = new Paint();
        gridStrokePaint.setColor(ContextCompat.getColor(this.context, R.color.gray));
        gridStrokePaint.setStyle(Paint.Style.STROKE);
        gridStrokePaint.setAntiAlias(true);

        satelliteFillPaint = new Paint();
        satelliteFillPaint.setColor(ContextCompat.getColor(this.context, R.color.yellow));
        satelliteFillPaint.setStyle(Paint.Style.FILL);
        satelliteFillPaint.setAntiAlias(true);

        satelliteStrokePaint = new Paint();
        satelliteStrokePaint.setColor(Color.BLACK);
        satelliteStrokePaint.setStyle(Paint.Style.STROKE);
        satelliteStrokePaint.setStrokeWidth(2.0f);
        satelliteStrokePaint.setAntiAlias(true);

        satelliteUsedStrokePaint = new Paint();
        satelliteUsedStrokePaint.setColor(Color.BLACK);
        satelliteUsedStrokePaint.setStyle(Paint.Style.STROKE);
        satelliteUsedStrokePaint.setStrokeWidth(8.0f);
        satelliteUsedStrokePaint.setAntiAlias(true);

        snrThresholds = new float[]{MIN_VALUE_SNR, 10.0f, 20.0f, MAX_VALUE_SNR};
        snrColors = new int[]{ContextCompat.getColor(this.context, R.color.gray),
                ContextCompat.getColor(this.context, R.color.red),
                ContextCompat.getColor(this.context, R.color.yellow),
                ContextCompat.getColor(this.context, R.color.green)};

        cn0Thresholds = new float[]{MIN_VALUE_CN0, 21.67f, 33.3f, MAX_VALUE_CN0};
        cn0Colors = new int[]{ContextCompat.getColor(this.context, R.color.gray),
                ContextCompat.getColor(this.context, R.color.red),
                ContextCompat.getColor(this.context, R.color.yellow),
                ContextCompat.getColor(this.context, R.color.green)};

        northPaint = new Paint();
        northPaint.setColor(Color.BLACK);
        northPaint.setStyle(Paint.Style.STROKE);
        northPaint.setStrokeWidth(4.0f);
        northPaint.setAntiAlias(true);

        northFillPaint = new Paint();
        northFillPaint.setColor(Color.GRAY);
        northFillPaint.setStyle(Paint.Style.FILL);
        northFillPaint.setStrokeWidth(4.0f);
        northFillPaint.setAntiAlias(true);

        prnIdPaint = new Paint();
        prnIdPaint.setColor(Color.BLACK);
        prnIdPaint.setStyle(Paint.Style.STROKE);
        prnIdPaint
                .setTextSize(UIUtils.dpToPixels(getContext(), satRadius * PRN_TEXT_SCALE));
        prnIdPaint.setAntiAlias(true);

        notInViewPaint = new Paint();
        notInViewPaint.setColor(ContextCompat.getColor(context, R.color.not_in_view_sat));
        notInViewPaint.setStyle(Paint.Style.FILL);
        notInViewPaint.setStrokeWidth(4.0f);
        notInViewPaint.setAntiAlias(true);

        setFocusable(true);
    }

    public void onDestroy()
    {
        context = null;
    }

    public void setStarted()
    {
        started = true;
        invalidate();
    }

    public void setStopped()
    {
        started = false;
        svCount = 0;
        invalidate();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public synchronized void setGnssStatus(GnssStatus status)
    {
        useLegacyGnssApi = false;
        isSnrBad = false;
        if (prns == null)
        {
            /*
             * We need to allocate arrays big enough so we don't overflow them.  Per
             * https://developer.android.com/reference/android/location/GnssStatus.html#getSvid(int)
             * 255 should be enough to contain all known satellites world-wide.
             */
            final int maxLength = 255;
            prns = new int[maxLength];
            snrCn0s = new float[maxLength];
            elevs = new float[maxLength];
            azims = new float[maxLength];
            constellationType = new int[maxLength];
            usedInFix = new boolean[maxLength];
        }

        int length = status.getSatelliteCount();
        svCount = 0;
        int svInViewCount = 0;
        int svUsedCount = 0;
        float cn0InViewSum = 0.0f;
        float cn0UsedSum = 0.0f;
        snrCn0InViewAvg = 0.0f;
        snrCn0UsedAvg = 0.0f;
        while (svCount < length)
        {
            snrCn0s[svCount] = status.getCn0DbHz(svCount);  // Store C/N0 values (see #65)
            elevs[svCount] = status.getElevationDegrees(svCount);
            azims[svCount] = status.getAzimuthDegrees(svCount);
            prns[svCount] = status.getSvid(svCount);
            constellationType[svCount] = status.getConstellationType(svCount);
            usedInFix[svCount] = status.usedInFix(svCount);
            // If satellite is in view, add signal to calculate avg
            if (status.getCn0DbHz(svCount) != 0.0f)
            {
                svInViewCount++;
                cn0InViewSum = cn0InViewSum + status.getCn0DbHz(svCount);
            }
            if (status.usedInFix(svCount))
            {
                svUsedCount++;
                cn0UsedSum = cn0UsedSum + status.getCn0DbHz(svCount);
            }
            svCount++;
        }

        if (svInViewCount > 0)
        {
            snrCn0InViewAvg = cn0InViewSum / svInViewCount;
        }
        if (svUsedCount > 0)
        {
            snrCn0UsedAvg = cn0UsedSum / svUsedCount;
        }

        started = true;
        invalidate();
    }

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2)
    {
        // rotate the line based on orientation
        double angle = Math.toRadians(-orientation);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;
        x1 -= centerX;
        y1 = centerY - y1;
        x2 -= centerX;
        y2 = centerY - y2;

        float X1 = cos * x1 + sin * y1 + centerX;
        float Y1 = -(-sin * x1 + cos * y1) + centerY;
        float X2 = cos * x2 + sin * y2 + centerX;
        float Y2 = -(-sin * x2 + cos * y2) + centerY;

        c.drawLine(X1, Y1, X2, Y2, gridStrokePaint);
    }

    private void drawHorizon(Canvas c, int s)
    {
        float radius = s / 2f;

        c.drawCircle(radius, radius, radius,
                started ? horizonActiveFillPaint : horizonInactiveFillPaint);
        drawLine(c, 0, radius, 2 * radius, radius);
        drawLine(c, radius, 0, radius, 2 * radius);
        c.drawCircle(radius, radius, elevationToRadius(s, 60.0f), gridStrokePaint);
        c.drawCircle(radius, radius, elevationToRadius(s, 30.0f), gridStrokePaint);
        c.drawCircle(radius, radius, elevationToRadius(s, 0.0f), gridStrokePaint);
        c.drawCircle(radius, radius, radius, horizonStrokePaint);
    }

    private void drawNorthIndicator(Canvas c, int s)
    {
        float radius = s / 2f;
        final float arrowHeightScale = 0.05f;
        final float arrowWidthScale = 0.1f;

        // Tip of arrow
        float x1 = radius;
        float y1 = elevationToRadius(s, 90.0f);

        float x2 = x1 + radius * arrowHeightScale;
        float y2 = y1 + radius * arrowWidthScale;

        float x3 = x1 - radius * arrowHeightScale;
        float y3 = y1 + radius * arrowWidthScale;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        // Rotate arrow around center point
        Matrix matrix = new Matrix();
        matrix.postRotate((float) -orientation, radius, radius);
        path.transform(matrix);

        c.drawPath(path, northPaint);
        c.drawPath(path, northFillPaint);
    }

    private void drawSatellite(Canvas c, int s, float elev, float azim, float snrCn0, int prn,
                               int constellationType, boolean usedInFix)
    {
        // Place PRN text slightly below drawn satellite
        final double prnXScale = 1.4;
        final double prnYScale = 3.8;

        Paint fillPaint;
        if (snrCn0 == 0.0f)
        {
            // Satellite can't be seen
            fillPaint = notInViewPaint;
        } else
        {
            // Calculate fill color based on signal strength
            fillPaint = getSatellitePaint(satelliteFillPaint, snrCn0);
        }

        Paint strokePaint;
        if (usedInFix)
        {
            strokePaint = satelliteUsedStrokePaint;
        } else
        {
            strokePaint = satelliteStrokePaint;
        }

        final double radius = elevationToRadius(s, elev);
        azim -= orientation;
        final double angle = (float) Math.toRadians(azim);

        final float x = (float) ((s / 2) + (radius * Math.sin(angle)));
        final float y = (float) ((s / 2) - (radius * Math.cos(angle)));

        // Change shape based on satellite operator
        final GnssType operator = GpsTestUtil.getGnssConstellationType(constellationType);

        switch (operator)
        {
            case NAVSTAR:
                c.drawCircle(x, y, satRadius, fillPaint);
                c.drawCircle(x, y, satRadius, strokePaint);
                break;
            case GLONASS:
                c.drawRect(x - satRadius, y - satRadius, x + satRadius, y + satRadius,
                        fillPaint);
                c.drawRect(x - satRadius, y - satRadius, x + satRadius, y + satRadius,
                        strokePaint);
                break;
            case QZSS:
                drawHexagon(c, x, y, fillPaint, strokePaint);
                break;
            case BEIDOU:
                drawPentagon(c, x, y, fillPaint, strokePaint);
                break;
            case GALILEO:
                drawTriangle(c, x, y, fillPaint, strokePaint);
                break;
            case IRNSS:
                drawOval(c, x, y, fillPaint, strokePaint);
                break;
//            case GAGAN:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case ANIK:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case GALAXY_15:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case INMARSAT_3F2:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case INMARSAT_3F5:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case INMARSAT_4F3:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case SES_5:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
//            case ASTRA_5B:
//                // SBAS
//                drawDiamond(c, x, y, fillPaint, strokePaint);
//                break;
        }

        c.drawText(String.valueOf(prn), x - (int) (satRadius * prnXScale),
                y + (int) (satRadius * prnYScale), prnIdPaint);
    }

    private float elevationToRadius(int s, float elev)
    {
        return ((s / 2f) - satRadius) * (1.0f - (elev / 90.0f));
    }

    private void drawTriangle(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint)
    {
        // Top
        float x1 = x;
        float y1 = y - satRadius;

        // Lower left
        float x2 = x - satRadius;
        float y2 = y + satRadius;

        // Lower right
        float x3 = x + satRadius;
        float y3 = y + satRadius;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawDiamond(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint)
    {
        Path path = new Path();
        path.moveTo(x, y - satRadius);
        path.lineTo(x - satRadius * 1.5f, y);
        path.lineTo(x, y + satRadius);
        path.lineTo(x + satRadius * 1.5f, y);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawPentagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint)
    {
        Path path = new Path();
        path.moveTo(x, y - satRadius);
        path.lineTo(x - satRadius, y - (satRadius / 3f));
        path.lineTo(x - 2 * (satRadius / 3f), y + satRadius);
        path.lineTo(x + 2 * (satRadius / 3f), y + satRadius);
        path.lineTo(x + satRadius, y - (satRadius / 3f));
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawHexagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint)
    {
        final float multiplier = 0.6f;
        final float sideMultiplier = 1.4f;
        Path path = new Path();
        // Top-left
        path.moveTo(x - satRadius * multiplier, y - satRadius);
        // Left
        path.lineTo(x - satRadius * sideMultiplier, y);
        // Bottom
        path.lineTo(x - satRadius * multiplier, y + satRadius);
        path.lineTo(x + satRadius * multiplier, y + satRadius);
        // Right
        path.lineTo(x + satRadius * sideMultiplier, y);
        // Top-right
        path.lineTo(x + satRadius * multiplier, y - satRadius);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawOval(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint)
    {
        RectF rect = new RectF(x - satRadius * 1.5f, y - satRadius, x + satRadius * 1.5f, y + satRadius);

        c.drawOval(rect, fillPaint);
        c.drawOval(rect, strokePaint);
    }

    private Paint getSatellitePaint(Paint base, float snrCn0)
    {
        final Paint newPaint = new Paint(base);
        newPaint.setColor(getSatelliteColor(snrCn0));
        return newPaint;
    }

    /**
     * Gets the paint color for a satellite based on provided SNR or C/N0 and the thresholds defined in this class
     *
     * @param snrCn0 the SNR to use (if using legacy GpsStatus) or the C/N0 to use (if using is
     *               GnssStatus) to generate the satellite color based on signal quality
     * @return the paint color for a satellite based on provided SNR or C/N0
     */
    public synchronized int getSatelliteColor(float snrCn0)
    {
        int numSteps;
        final float[] thresholds;
        final int[] colors;

        if (!useLegacyGnssApi || isSnrBad)
        {
            // Use C/N0 ranges/colors for both C/N0 and SNR on Android 7.0 and higher (see #76)
            numSteps = cn0Thresholds.length;
            thresholds = cn0Thresholds;
            colors = cn0Colors;
        } else
        {
            // Use legacy SNR ranges/colors for Android versions less than Android 7.0 or if user selects legacy API (see #76)
            numSteps = snrThresholds.length;
            thresholds = snrThresholds;
            colors = snrColors;
        }

        if (snrCn0 <= thresholds[0])
        {
            return colors[0];
        }

        if (snrCn0 >= thresholds[numSteps - 1])
        {
            return colors[numSteps - 1];
        }

        for (int i = 0; i < numSteps - 1; i++)
        {
            float threshold = thresholds[i];
            float nextThreshold = thresholds[i + 1];
            if (snrCn0 >= threshold && snrCn0 <= nextThreshold)
            {
                int c1 = colors[i];
                int r1 = Color.red(c1);
                int g1 = Color.green(c1);
                int b1 = Color.blue(c1);

                int c2 = colors[i + 1];
                int r2 = Color.red(c2);
                int g2 = Color.green(c2);
                int b2 = Color.blue(c2);

                float f = (snrCn0 - threshold) / (nextThreshold - threshold);

                int r3 = (int) (r2 * f + r1 * (1.0f - f));
                int g3 = (int) (g2 * f + g1 * (1.0f - f));
                int b3 = (int) (b2 * f + b1 * (1.0f - f));

                return Color.rgb(r3, g3, b3);
            }
        }
        return Color.MAGENTA;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        int minScreenDimen = Math.min(getWidth(), getHeight());

        drawHorizon(canvas, minScreenDimen);

        drawNorthIndicator(canvas, minScreenDimen);

        if (elevs != null)
        {
            int numSats = svCount;

            for (int i = 0; i < numSats; i++)
            {
                if (elevs[i] != 0.0f || azims[i] != 0.0f)
                {
                    drawSatellite(canvas, minScreenDimen, elevs[i], azims[i], snrCn0s[i],
                            prns[i], constellationType[i], usedInFix[i]);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // Use the width of the screen as the measured dimension for width and height of view
        // This allows other views in the same layout to be visible on the screen (#124)
        int specSize = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(specSize, specSize);
    }

    @Override
    public void onOrientationChanged(double orientation, double tilt)
    {
        this.orientation = orientation;
        invalidate();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status)
    {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
    {

    }

    @Override
    public void onLocationChanged(Location location)
    {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    @Override
    public void onProviderEnabled(String provider)
    {
    }

    @Override
    public void onProviderDisabled(String provider)
    {
    }

    /**
     * Returns the average signal strength (C/N0 if isUsingLegacyGpsApi is false, SNR if isUsingLegacyGpsApi is true) for satellites that are in view of the device (i.e., value is not 0), or 0 if the average can't be calculated
     *
     * @return the average signal strength (C/N0 if isUsingLegacyGpsApi is false, SNR if isUsingLegacyGpsApi is true) for satellites that are in view of the device (i.e., value is not 0), or 0 if the average can't be calculated
     */
    public synchronized float getSnrCn0InViewAvg()
    {
        return snrCn0InViewAvg;
    }

    /**
     * Returns the average signal strength (C/N0 if isUsingLegacyGpsApi is false, SNR if isUsingLegacyGpsApi is true) for satellites that are being used to calculate a location fix, or 0 if the average can't be calculated
     *
     * @return the average signal strength (C/N0 if isUsingLegacyGpsApi is false, SNR if isUsingLegacyGpsApi is true) for satellites that are being used to calculate a location fix, or 0 if the average can't be calculated
     */
    public synchronized float getSnrCn0UsedAvg()
    {
        return snrCn0UsedAvg;
    }

    /**
     * Returns true if the app is monitoring the legacy GpsStatus.Listener, or false if the app is monitoring the GnssStatus.Callback
     *
     * @return true if the app is monitoring the legacy GpsStatus.Listener, or false if the app is monitoring the GnssStatus.Callback
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public synchronized boolean isUsingLegacyGpsApi()
    {
        return useLegacyGnssApi;
    }

    /**
     * Returns true if bad SNR data has been detected (avgs exceeded max SNR threshold), or false if no SNR is observed (i.e., C/N0 data is observed) or SNR data seems ok
     *
     * @return true if bad SNR data has been detected (avgs exceeded max SNR threshold), or false if no SNR is observed (i.e., C/N0 data is observed) or SNR data seems ok
     */
    public synchronized boolean isSnrBad()
    {
        return isSnrBad;
    }
}