package com.codejedix.deadlineatlas;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class NotificationScheduler {
    static final String CHANNEL_ID = "deadline_alerts";
    private static final String PREFS = "deadline_atlas_notifications";
    private static final String PAYLOAD_KEY = "schedule_payload";
    private static final String IDS_KEY = "scheduled_request_ids";

    private NotificationScheduler() {}

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Deadline reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Competition deadline alerts from Deadline Atlas");
        channel.enableVibration(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    static void showTest(Context context) {
        ensureChannel(context);
        Intent intent = new Intent(context, NotificationReceiver.class)
                .putExtra("notification_id", 910001)
                .putExtra("title", "Deadline Atlas test")
                .putExtra("body", "Notifications are working. Your deadline reminders are ready.");
        context.sendBroadcast(intent);
    }

    static void scheduleFromPayload(Context context, String payload) {
        try {
            JSONObject parsed = new JSONObject(payload);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(PAYLOAD_KEY, parsed.toString()).apply();
            schedule(context, parsed);
        } catch (Exception ignored) {
            // Ignore malformed data from the page and preserve the previous valid schedule.
        }
    }

    static void scheduleStored(Context context) {
        String payload = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PAYLOAD_KEY, "");
        if (payload.isEmpty()) return;
        try {
            schedule(context, new JSONObject(payload));
        } catch (Exception ignored) {
            // A future app launch will replace malformed legacy schedule data.
        }
    }

    private static void schedule(Context context, JSONObject payload) throws Exception {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        cancelExisting(context, alarmManager);

        JSONArray offsets = payload.optJSONArray("offsets");
        JSONArray competitions = payload.optJSONArray("competitions");
        if (offsets == null || competitions == null) return;

        long now = System.currentTimeMillis();
        List<Integer> scheduledIds = new ArrayList<>();
        for (int i = 0; i < competitions.length(); i++) {
            JSONObject competition = competitions.optJSONObject(i);
            if (competition == null) continue;
            String id = competition.optString("id", "competition-" + i);
            String name = competition.optString("name", "Competition");
            long deadlineAt = competition.optLong("deadlineAt", 0L);
            if (deadlineAt <= now) continue;

            for (int j = 0; j < offsets.length(); j++) {
                JSONObject offset = offsets.optJSONObject(j);
                if (offset == null) continue;
                int minutes = offset.optInt("minutes", 0);
                String label = offset.optString("label", minutes + " minutes");
                long triggerAt = deadlineAt - minutes * 60_000L;
                if (minutes <= 0 || triggerAt <= now) continue;

                int requestId = positiveHash(id + "|" + minutes);
                Intent notificationIntent = new Intent(context, NotificationReceiver.class)
                        .putExtra("notification_id", requestId)
                        .putExtra("title", "Deadline approaching")
                        .putExtra("body", name + " is due in " + label + ".");
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestId,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                scheduleAlarm(alarmManager, triggerAt, pendingIntent);
                scheduledIds.add(requestId);
            }
        }

        JSONArray ids = new JSONArray();
        for (Integer id : scheduledIds) ids.put(id);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(IDS_KEY, ids.toString()).apply();
    }

    private static void scheduleAlarm(AlarmManager manager, long triggerAt, PendingIntent intent) {
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
            return;
        }
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        } catch (SecurityException ignored) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        }
    }

    private static void cancelExisting(Context context, AlarmManager manager) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(IDS_KEY, "[]");
        try {
            JSONArray ids = new JSONArray(stored);
            for (int i = 0; i < ids.length(); i++) {
                int requestId = ids.optInt(i, -1);
                if (requestId < 0) continue;
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestId,
                        new Intent(context, NotificationReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pendingIntent != null) {
                    manager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        } catch (Exception ignored) {
            // Nothing to cancel.
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(IDS_KEY).apply();
    }

    private static int positiveHash(String value) {
        return value.hashCode() & 0x7fffffff;
    }
}

