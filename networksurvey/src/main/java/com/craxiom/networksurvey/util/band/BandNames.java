package com.craxiom.networksurvey.util.band;

/**
 * Short colloquial names for LTE and NR operating bands, such as "3550 CBRS" or "TD 3700".
 * <p>
 * These are display labels rather than specification values: 3GPP assigns no colloquial name to a
 * band. Every name here has been checked against the band's own frequency range so that a label
 * never contradicts the numbers shown beside it.
 */
public final class BandNames
{
    /**
     * Gets the band name for a given LTE band number.
     * The band names are based on common frequency designations and regional usage.
     *
     * @param bandNumber The LTE band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getLteBandName(int bandNumber)
    {
        return switch (bandNumber)
        {
            case 1 -> "2100 MHz";
            case 2 -> "1900 PCS";
            case 3 -> "1800+";
            case 4 -> "AWS-1";
            case 5 -> "850";
            case 6 -> "850 Japan";
            case 7 -> "2600";
            case 8 -> "900 GSM";
            case 9 -> "1800";
            case 10 -> "AWS-3";
            case 11 -> "1500 Lower";
            case 12 -> "700 a";
            case 13 -> "700 c";
            case 14 -> "700 PS";
            case 17 -> "700 b";
            case 18 -> "800 Lower";
            case 19 -> "800 Upper";
            case 20 -> "800 DD";
            case 21 -> "1500 Upper";
            case 22 -> "3500";
            case 23 -> "2000 S-band";
            case 24 -> "1600 L-band";
            case 25 -> "1900+";
            case 26 -> "850+";
            case 27 -> "800 SMR";
            case 28 -> "700 APT";
            case 29 -> "700 d SDL";
            case 30 -> "2300 WCS";
            case 31 -> "450";
            case 32 -> "1500 L-band";
            case 33 -> "1900 TDD";
            case 34 -> "2000 TDD";
            case 35 -> "1850 TDD";
            case 36 -> "1930 TDD";
            case 37 -> "1910 TDD";
            case 38 -> "2600 TDD";
            case 39 -> "1880 TDD";
            case 40 -> "2300 TDD";
            case 41 -> "2500 TDD";
            case 42 -> "3400 TDD";
            case 43 -> "3600 TDD";
            case 44 -> "700 TDD";
            case 45 -> "1400 TDD";
            case 46 -> "5200 TDD";
            case 47 -> "5900 TDD";
            case 48 -> "3550 CBRS";
            case 49 -> "3550 TDD";
            case 50 -> "1500 TDD";
            case 51 -> "1500 TDD";
            case 52 -> "3300 TDD";
            case 53 -> "2500 S-band";
            case 54 -> "1670 TDD";
            case 65 -> "2100+";
            case 66 -> "AWS";
            case 67 -> "700 EU";
            case 68 -> "700 ME";
            case 69 -> "2600 SDL";
            case 70 -> "AWS-4";
            case 71 -> "600";
            case 72 -> "450 PMR/PAMR";
            case 73 -> "450 APAC";
            case 74 -> "L-band";
            case 75 -> "1500 SDL+";
            case 76 -> "1500 SDL-";
            case 85 -> "700 a+";
            case 87 -> "410";
            case 88 -> "410+";
            case 103 -> "NB-IoT";
            case 106 -> "900";
            case 111 -> "HD-1800";
            default -> null;
        };
    }

    /**
     * Gets the band name for a given 5G NR band number.
     * The band names are based on the frequency designations from RF wireless specifications.
     *
     * @param bandNumber The NR band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getNrBandName(int bandNumber)
    {
        return switch (bandNumber)
        {
            case 1 -> "2100";
            case 2 -> "1900 PCS";
            case 3 -> "1800";
            case 5 -> "850";
            case 7 -> "2600";
            case 8 -> "900 GSM";
            case 12 -> "700 a";
            case 13 -> "700 c";
            case 14 -> "700 PS";
            case 18 -> "800 Lower";
            case 20 -> "800";
            case 24 -> "1600 L";
            case 25 -> "1900+";
            case 26 -> "850+";
            case 28 -> "700 APT";
            case 29 -> "700 d";
            case 30 -> "2300 WCS";
            case 31 -> "450";
            case 34 -> "TD 2000";
            case 38 -> "TD 2600";
            case 39 -> "TD 1900+";
            case 40 -> "TD 2300";
            case 41 -> "TD 2600+";
            case 46 -> "TD Unlicensed";
            case 47 -> "TD V2X";
            case 48 -> "TD 3600";
            case 50 -> "TD 1500+";
            case 51 -> "TD 1500-";
            case 53 -> "TD 2500";
            case 54 -> "TD 1670";
            case 65 -> "2100+";
            case 66 -> "AWS";
            case 67 -> "700 EU";
            case 68 -> "700 ME";
            case 70 -> "AWS-4";
            case 71 -> "600";
            case 72 -> "450 PMR/PAMR";
            case 74 -> "L-band";
            case 75 -> "DL 1500+";
            case 76 -> "DL 1500-";
            case 77 -> "TD 3700";
            case 78 -> "TD 3500";
            case 79 -> "TD 4700";
            case 80 -> "SUL 1800";
            case 81 -> "SUL 900";
            case 82 -> "SUL 800";
            case 83 -> "SUL 700";
            case 84 -> "SUL 2100";
            case 85 -> "700 a+";
            case 86 -> "SUL 1700";
            case 87 -> "410";
            case 88 -> "410+";
            case 89 -> "SUL 850";
            case 90 -> "TD 2500";
            case 91 -> "L-band 1500";
            case 92 -> "L-band 1500";
            case 93 -> "L-band 1500";
            case 94 -> "L-band 1500";
            case 95 -> "SUL 2000";
            case 96 -> "TD 6 GHz Unlicensed";
            case 97 -> "S-band 2300";
            case 98 -> "S-band 1900";
            case 99 -> "L-band 1600";
            case 100 -> "900 Rail";
            case 101 -> "1900";
            case 102 -> "TD 5900+";
            case 104 -> "TD 6400+";
            case 105 -> "600+";
            case 106 -> "900 Narrowband";
            case 109 -> "1500";
            case 110 -> "1500 Narrow";
            case 256 -> "NTN 2100";
            case 257 -> "28 GHz";
            case 258 -> "26 GHz";
            case 259 -> "41 GHz";
            case 260 -> "39 GHz";
            case 261 -> "28 GHz";
            case 262 -> "47 GHz";
            case 263 -> "60 GHz";
            default -> null;
        };
    }

    private BandNames()
    {
    }
}
