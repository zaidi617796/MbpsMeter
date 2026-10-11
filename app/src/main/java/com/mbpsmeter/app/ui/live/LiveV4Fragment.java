                tvStrength.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF331418));
            }
        }
        if (tvOperator != null) tvOperator.setText(curOp.isEmpty() ? "Unknown operator" : curOp);
        if (tvNetType != null) tvNetType.setText(curNet);
        if (tvGps != null) {
            tvGps.setText(String.format(Locale.getDefault(), "GPS: %.5f, %.5f", curLat, curLng));
        }
        if (tvTime != null) {
            tvTime.setText(new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date()));
        }
    }

    // ---------- location ----------

    private void updateLocation() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last != null) {
                    curLat = last.getLatitude();
                    curLng = last.getLongitude();
                }
            }
        } catch (Exception ignored) {}
    }

    private Location currentLocation() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (Exception ignored) { return null; }
    }

    // ---------- save ----------

    private void save(boolean silent, String type) {
        try {
            updateLocation();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("dbm", currentDbm);
            obj.put("time", System.currentTimeMillis());
            obj.put("lat", curLat);
            obj.put("lng", curLng);
            obj.put("type", type);
            obj.put("net", curNet);
            obj.put("op", curOp);
            arr.put(obj);

            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("signals", arr.toString());
            int oldBest = prefs.getInt("best_dbm", -120);
            if (currentDbm > oldBest) {
                ed.putFloat("best_lat", (float) curLat);
                ed.putFloat("best_lng", (float) curLng);
                ed.putInt("best_dbm", currentDbm);
            }
            ed.apply();
            if (!silent) Toast.makeText(getContext(), currentDbm + " dBm saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}