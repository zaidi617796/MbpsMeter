package com.mbpsmeter.app.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

import java.util.List;

/**
 * ASAL signal TelephonyManager se parhta hai (random nahi).
 * Agar permission/data na mile to real=false, caller demo fallback dikhaye.
 */
public class SignalReader {

    public static class Result {
        public int dbm = -120;
        public String networkType = "Unknown";
        public String operator = "";
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
                return r; // permission nahi -> real=false
            }
            List<CellInfo> infos = tm.getAllCellInfo();
            if (infos == null) return r;
            for (CellInfo ci : infos) {
                if (!ci.isRegistered()) continue;
                int dbm = Integer.MIN_VALUE;
                if (ci instanceof CellInfoLte) {
                    dbm = ((CellInfoLte) ci).getCellSignalStrength().getDbm();
                } else if (ci instanceof CellInfoWcdma) {
                    dbm = ((CellInfoWcdma) ci).getCellSignalStrength().getDbm();
                } else if (ci instanceof CellInfoGsm) {
                    dbm = ((CellInfoGsm) ci).getCellSignalStrength().getDbm();
                } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr) {
                    dbm = ((CellInfoNr) ci).getCellSignalStrength().getDbm();
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
