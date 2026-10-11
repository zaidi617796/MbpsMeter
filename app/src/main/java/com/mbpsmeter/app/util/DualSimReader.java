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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual SIM signal compare (DSDS).
 * Har active subscription ka alag TelephonyManager bana ke registered cell ka dBm parhta hai.
 * Idle SIM par kuch phones approximate/khaali data dete hain - hasData=false me handle karein.
 */
public class DualSimReader {

    public static class SimInfo {
        public int slot = 0;
        public String carrier = "";
        public int dbm = -120;
        public boolean hasData = false;
    }

    public static List<SimInfo> read(Context ctx) {
        List<SimInfo> out = new ArrayList<>();
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (tm == null || sm == null) return out;

            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) return out;
            boolean locOk = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;

            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs == null) return out;
            for (SubscriptionInfo sub : subs) {
                SimInfo si = new SimInfo();
                si.slot = sub.getSimSlotIndex() + 1;
                try {
                    CharSequence cs = sub.getCarrierName();
                    si.carrier = cs != null ? cs.toString() : ("SIM " + si.slot);
                } catch (Exception ignored) { si.carrier = "SIM " + si.slot; }

                if (locOk && Build.VERSION.SDK_INT >= 24) {
                    try {
                        TelephonyManager subTm = tm.createForSubscriptionId(sub.getSubscriptionId());
                        List<CellInfo> infos = subTm.getAllCellInfo();
                        if (infos != null) {
                            for (CellInfo ci : infos) {
                                if (!ci.isRegistered()) continue;
                                int dbm = Integer.MIN_VALUE;
                                if (ci instanceof CellInfoLte) dbm = ((CellInfoLte) ci).getCellSignalStrength().getDbm();
                                else if (ci instanceof CellInfoWcdma) dbm = ((CellInfoWcdma) ci).getCellSignalStrength().getDbm();
                                else if (ci instanceof CellInfoGsm) dbm = ((CellInfoGsm) ci).getCellSignalStrength().getDbm();
                                else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr)
                                    dbm = ((CellInfoNr) ci).getCellSignalStrength().getDbm();
                                if (dbm != Integer.MIN_VALUE && dbm < 0 && dbm > -150) {
                                    si.dbm = dbm;
                                    si.hasData = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                out.add(si);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
