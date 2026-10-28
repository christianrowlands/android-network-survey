package com.craxiom.networksurvey.logging;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats the decimal values (location, altitude, speed, accuracy) in the app's CSV log files.
 * <p>
 * The decimal separator follows the user's locale, so a German or French device writes "38,889484"
 * (the CSV printer quotes such a field, and NS Analytics reads a comma decimal). Everything else is
 * fixed, because the files are read by machines: ASCII digits, an ASCII hyphen-minus, no grouping
 * separators and no bidi marks. A locale whose decimal separator is neither a dot nor a comma (for
 * example the Arabic decimal separator U+066B) gets a dot. Formatting with the locale directly would
 * write Arabic-Indic, Persian, Devanagari or Bengali digits on those devices and a U+2212 minus sign
 * on Swedish, Finnish and Norwegian ones, none of which a CSV consumer can parse as a number.
 */
final class CsvNumberFormat
{
    private static final char DOT = '.';
    private static final char COMMA = ',';
    private static final String SIX_DECIMALS_PATTERN = "0.000000";
    private static final String UP_TO_TWO_DECIMALS_PATTERN = "#.##";
    private static final String INFINITY = "Infinity";

    private final DecimalFormat sixDecimals;
    private final DecimalFormat upToTwoDecimals;

    /**
     * @param locale The locale whose decimal separator to use (only a comma is kept; anything
     *               other than a comma becomes a dot).
     */
    CsvNumberFormat(Locale locale)
    {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setDecimalSeparator(decimalSeparatorFor(locale));
        symbols.setInfinity(INFINITY);

        sixDecimals = new DecimalFormat(SIX_DECIMALS_PATTERN, symbols);
        sixDecimals.setRoundingMode(RoundingMode.HALF_UP);
        sixDecimals.setGroupingUsed(false);

        upToTwoDecimals = new DecimalFormat(UP_TO_TWO_DECIMALS_PATTERN, symbols);
        upToTwoDecimals.setRoundingMode(RoundingMode.CEILING);
        upToTwoDecimals.setGroupingUsed(false);
    }

    /**
     * @return A comma if the locale uses a comma as its decimal separator, otherwise a dot.
     */
    static char decimalSeparatorFor(Locale locale)
    {
        return DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == COMMA ? COMMA : DOT;
    }

    /**
     * Formats a value (a latitude or longitude) with exactly six decimal places, keeping trailing
     * zeros. Synchronized because {@link DecimalFormat} is not thread safe and some loggers format
     * outside their own lock.
     */
    synchronized String toSixDecimalPlaces(double value)
    {
        return sixDecimals.format(value);
    }

    /**
     * Formats a value with at most two decimal places, rounding up and dropping trailing zeros.
     */
    synchronized String toAtMostTwoDecimalPlaces(double value)
    {
        return upToTwoDecimals.format(value);
    }
}
