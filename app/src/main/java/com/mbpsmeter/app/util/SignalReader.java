package com.mbpsmeter.app.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

import java.util.List;

/**
 * ASAL signal + tower info (Cell ID, band) TelephonyManager se.
 * Agar permission/data na mile to real=false (caller demo fallback dikhaye).
 */
public class SignalReader {

    public static class Result {
        public int dbm = -120;
        public String networkType = "Unknown";
        public String operator = "";
        public String cellId = "";
        public String band = "";
        public boolean real = false;
    }

    public static Result read(Context ctx) {
        Result r = new Result();
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return r;
            try { r.operator = tm.getNetworkOperatorName(); } catch (Exception ignored) {}
            if (r.operator == null) r.operator = "";
            r.networkType = netTypeName(tm);

            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return r;
            }
            List<CellInfo> infos = tm.getAllCellInfo();
            if (infos == null) return r;
            for (CellInfo ci : infos) {
                if (!ci.isRegistered()) continue;
                int dbm = Integer.MIN_VALUE;
                if (ci instanceof CellInfoLte) {
                    CellInfoLte lte = (CellInfoLte) ci;
                    dbm = lte.getCellSignalStrength().getDbm();
                    try {
                        CellIdentityLte id = lte.getCellIdentity();
                        if (id.getCi() != Integer.MAX_VALUE) r.cellId = String.valueOf(id.getCi());
                        r.band = lteBand(id.getEarfcn());
                    } catch (Exception ignored) {}
                } else if (ci instanceof CellInfoWcdma) {
                    CellInfoWcdma w = (CellInfoWcdma) ci;
                    dbm = w.getCellSignalStrength().getDbm();
                    try {
                        CellIdentityWcdma id = w.getCellIdentity();
                        if (id.getCid() != Integer.MAX_VALUE) r.cellId = String.valueOf(id.getCid());
                        r.band = "3G";
                    } catch (Exception ignored) {}
                } else if (ci instanceof CellInfoGsm) {
                    CellInfoGsm g = (CellInfoGsm) ci;
                    dbm = g.getCellSignalStrength().getDbm();
                    try {
                        CellIdentityGsm id = g.getCellIdentity();
                        if (id.getCid() != Integer.MAX_VALUE) r.cellId = String.valueOf(id.getCid());
                        r.band = "2G";
                    } catch (Exception ignored) {}
                } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr) {
                    CellInfoNr nr = (CellInfoNr) ci;
                    dbm = nr.getCellSignalStrength().getDbm();
                    try {
                        CellIdentityNr id = (CellIdentityNr) nr.getCellIdentity();
                        long nci = id.getNci();
                        if (nci != Long.MAX_VALUE) r.cellId = String.valueOf(nci);
                        r.band = nrBand(id.getNrarfcn());
                    } catch (Exception ignored) {}
                }
                if (dbm != Integer.MIN_VALUE && dbm < 0 && dbm > -150) {
                    r.dbm = dbm;
                    r.real = true;
                    break;
                }
            }
        } catch (Exception ignored) {}
        return r;
    }

    /** LTE EARFCN -> band (Pakistan me common) */
    private static String lteBand(int earfcn) {
        if (earfcn < 0 || earfcn == Integer.MAX_VALUE) return "";
        if (earfcn <= 599) return "B1";
        if (earfcn <= 1199) return "B2";
        if (earfcn <= 1949) return "B3";
        if (earfcn <= 2399) return "B4";
        if (earfcn <= 2649) return "B5";
        if (earfcn <= 2749) return "B6";
        if (earfcn <= 3449) return "B7";
        if (earfcn <= 3799) return "B8";
        if (earfcn <= 4149) return "B9";
        if (earfcn <= 4749) return "B10";
        if (earfcn <= 4949) return "B11";
        if (earfcn <= 5179) return "B12";
        if (earfcn <= 5379) return "B13";
        if (earfcn <= 5529) return "B14";
        if (earfcn <= 5729) return "B17";
        if (earfcn <= 5849) return "B18";
        if (earfcn <= 5999) return "B19";
        if (earfcn <= 6449) return "B20";
        if (earfcn <= 6599) return "B21";
        if (earfcn <= 7399) return "B22";
        if (earfcn <= 7699) return "B23";
        if (earfcn <= 8039) return "B24";
        if (earfcn <= 8689) return "B25";
        if (earfcn <= 9049) return "B26";
        if (earfcn <= 9659) return "B28";
        if (earfcn <= 9769) return "B29";
        if (earfcn <= 9869) return "B30";
        if (earfcn <= 9919) return "B31";
        if (earfcn <= 10359) return "B32";
        if (earfcn >= 36000 && earfcn <= 36199) return "B33";
        if (earfcn >= 36200 && earfcn <= 36349) return "B34";
        if (earfcn >= 36350 && earfcn <= 36949) return "B35";
        if (earfcn >= 36950 && earfcn <= 37549) return "B36";
        if (earfcn >= 37550 && earfcn <= 37749) return "B37";
        if (earfcn >= 37750 && earfcn <= 38249) return "B38";
        if (earfcn >= 38250 && earfcn <= 38649) return "B39";
        if (earfcn >= 38650 && earfcn <= 39649) return "B40";
        if (earfcn >= 39650 && earfcn <= 41589) return "B41";
        if (earfcn >= 41590 && earfcn <= 43589) return "B42";
        if (earfcn >= 43590 && earfcn <= 45589) return "B43";
        return "";
    }

    /** 5G NR-ARFCN -> band */
    private static String nrBand(int arfcn) {
        if (arfcn <= 0 || arfcn == Integer.MAX_VALUE) return "";
        if (arfcn >= 151600 && arfcn <= 160600) return "n28";
        if (arfcn >= 163800 && arfcn <= 167300) return "n5";
        if (arfcn >= 185000 && arfcn <= 188000) return "n8";
        if (arfcn >= 370000 && arfcn <= 382000) return "n3";
        if (arfcn >= 422000 && arfcn <= 434000) return "n1";
        if (arfcn >= 499200 && arfcn <= 537999) return "n41";
        if (arfcn >= 524000 && arfcn <= 538000) return "n7";
        if (arfcn >= 620000 && arfcn <= 653333) return "n78";
        return "";
    }

    private static String netTypeName(TelephonyManager tm) {
        try {
            int t;
            if (Build.VERSION.SDK_INT >= 24) {
                t = tm.getDataNetworkType();
                if (t == TelephonyManager.NETWORK_TYPE_UNKNOWN) t = tm.getNetworkType();
            } else {
                t = tm.getNetworkType();
            }
            switch (t) {
                case TelephonyManager.NETWORK_TYPE_NR: return "5G";
                case TelephonyManager.NETWORK_TYPE_LTE: return "4G LTE";
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B: return "3G";
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN: return "2G";
                default: return "Unknown";
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    /** dBm ko label me badlein */
    public static String label(int dbm) {
        if (dbm >= -80) return "Strong";
        if (dbm >= -95) return "Fair";
        return "Weak";
    }
}
