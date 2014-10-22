/*
 * SBHS-Timetable-Android: Countdown and timetable all at once (Android app).
 * Copyright (C) 2014 Simon Shields, James Ye
 *
 * This file is part of SBHS-Timetable-Android.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sbhstimetable.sbhs_timetable_android.backend.service;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Layout;
import android.util.Log;
import android.view.Gravity;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.TextView;

import com.google.gson.JsonParser;
import com.sbhstimetable.sbhs_timetable_android.LoginActivity;
import com.sbhstimetable.sbhs_timetable_android.R;
import com.sbhstimetable.sbhs_timetable_android.backend.ApiAccessor;
import com.sbhstimetable.sbhs_timetable_android.backend.DateTimeHelper;
import com.sbhstimetable.sbhs_timetable_android.backend.internal.JsonUtil;
import com.sbhstimetable.sbhs_timetable_android.backend.json.TodayJson;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class TodayWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodayRemoteViewsFactory(this, TodayJson.getInstance());
    }

    class TodayRemoteViewsFactory implements RemoteViewsFactory {
        private TodayJson today;
        private Context con;
        TodayRemoteViewsFactory(Context c, TodayJson t) {
            this.con = c;
            this.today = t;
        }

        @Override
        public void onCreate() {
            Log.i("TodayWidgetService", "today is invalid");
            LocalBroadcastManager.getInstance(con).registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    today = new TodayJson(JsonUtil.safelyParseJson(intent.getStringExtra(ApiAccessor.EXTRA_JSON_DATA)));
                    Intent i = new Intent();
                    Log.i("todayWidgetService", "stuff happening");
                    int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), TodayAppWidget.class));
                    i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                    i.setClass(con, TodayAppWidget.class);
                    i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                    sendBroadcast(i);
                }
            }, new IntentFilter(ApiAccessor.ACTION_TODAY_JSON));
            ApiAccessor.load(con);
            ApiAccessor.getToday(con);
            ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
            Calendar c = new GregorianCalendar();
            c.set(Calendar.HOUR_OF_DAY, 15);
            c.set(Calendar.MINUTE, 16);
            long wait = c.getTimeInMillis() - DateTimeHelper.getTimeMillis();
            if (wait < 0) return; // bail out.
            ses.schedule(new Runnable() {
                @Override
                public void run() {
                    ApiAccessor.getToday(con);
                    ApiAccessor.getBelltimes(con); // update all the things yea
                }
            }, wait, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onDataSetChanged() {
        }

        @Override
        public void onDestroy() {

        }

        @Override
        public int getCount() {
            return this.today != null && this.today.valid() ? 6 : 1;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            Log.i("todayWidgetService", "getViewAt " + i + ". today ok == " + (this.today != null && this.today.valid()));
            if (this.today == null || !this.today.valid()) {
                RemoteViews r = new RemoteViews(con.getPackageName(), R.layout.layout_textview);
                r.setTextViewText(R.id.label, "You need to log in");
                Intent t = new Intent(con, LoginActivity.class);
                t.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                r.setOnClickPendingIntent(R.id.label, PendingIntent.getActivity(this.con, 0, t, 0));
                return r;
            }
            if (i == 0) {
                RemoteViews r = new RemoteViews(con.getPackageName(), R.layout.layout_textview);
                String res = today.getDayName();
                r.setTextViewText(R.id.label, res);
                //r.setInt(R.id.label, "setGravity", Gravity.LEFT);
                return r;
            }
            TodayJson.Period p = this.today.getPeriod(i);
            RemoteViews r = new RemoteViews(con.getPackageName(), R.layout.layout_timetable_classinfo_widget);
            r.setTextViewText(R.id.timetable_class_header, p.name());
            r.setTextViewText(R.id.timetable_class_room, p.room());
            int standout = getResources().getColor(R.color.standout);
            if (p.roomChanged()) {
                r.setTextColor(R.id.timetable_class_room, standout);
            }
            r.setTextViewText(R.id.timetable_class_teacher, p.fullTeacher());
            if (p.teacherChanged()) {
                r.setTextColor(R.id.timetable_class_teacher, standout);
            }

            if (!p.changed() || !today.finalised()) {
                r.setImageViewBitmap(R.id.timetable_class_changed, null);
            }
            return r;
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews r = new RemoteViews(con.getPackageName(), R.layout.layout_textview);
            r.setTextViewText(R.id.label, "Loading…");
            return r;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
