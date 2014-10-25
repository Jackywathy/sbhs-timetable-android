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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.gson.JsonObject;
import com.sbhstimetable.sbhs_timetable_android.R;
import com.sbhstimetable.sbhs_timetable_android.backend.ApiAccessor;
import com.sbhstimetable.sbhs_timetable_android.backend.DateTimeHelper;
import com.sbhstimetable.sbhs_timetable_android.backend.StorageCache;
import com.sbhstimetable.sbhs_timetable_android.backend.internal.JsonUtil;
import com.sbhstimetable.sbhs_timetable_android.backend.json.BelltimesJson;
import com.sbhstimetable.sbhs_timetable_android.backend.json.TodayJson;

public class DashclockService extends DashClockExtension {
    private TodayJson mine;
    private BelltimesJson bells;
    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        setUpdateWhenScreenOn(true);
        mine = TodayJson.getInstance();
        bells = BelltimesJson.getInstance();
        if (mine == null) {
            Log.i("dashclock", "today");
            JsonObject temp = StorageCache.getTodayJson(this);
            if (temp != null) {
                mine = new TodayJson(temp);
            }
            ApiAccessor.getToday(this);
        }
        if (bells == null) {
            Log.i("dashclock", "bells");
            JsonObject temp = StorageCache.getBelltimes(this);
            if (temp != null) {
                bells = new BelltimesJson(temp);
            }
            ApiAccessor.getBelltimes(this);
        }
        IntentFilter wanted = new IntentFilter();
        wanted.addAction(ApiAccessor.ACTION_TODAY_JSON);
        final DashclockService that = this;
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ApiAccessor.ACTION_TODAY_JSON)) {
                    mine = new TodayJson(JsonUtil.safelyParseJson(intent.getStringExtra(ApiAccessor.EXTRA_JSON_DATA)));
                } else if (intent.getAction().equals(ApiAccessor.ACTION_BELLTIMES_JSON)) {
                    bells = new BelltimesJson(JsonUtil.safelyParseJson(intent.getStringExtra(ApiAccessor.EXTRA_JSON_DATA)));
                }
                that.onUpdateData(DashClockExtension.UPDATE_REASON_MANUAL);

            }
        }, wanted);
    }

    @Override
    protected void onUpdateData(int reason) {
        int num;
        boolean summary = false;
        if (bells != null && bells.valid()) {
            BelltimesJson.Bell b = bells.getNextPeriod();
            if (b == null) {
                // what
                num = 5;
            }
            else {
                num = b.getPeriodNumber();
            }

            if (DateTimeHelper.getDateOffset() > 0 || DateTimeHelper.getHour() < 9 || DateTimeHelper.needsMidnightCountdown()) {
                summary = true;
            }
        }
        else {
            publishUpdate(new ExtensionData().visible(false));
            return;
        }
        if (mine != null && !summary) {
            publishUpdate(new ExtensionData()
                            .icon(R.drawable.ic_launcher)
                            .status(mine.getPeriod(num).getShortName() + " - " + mine.getPeriod(num).room())
                            .expandedTitle(mine.getPeriod(num).name())
                            .expandedBody("in " + mine.getPeriod(num).room() + " with " + mine.getPeriod(num).teacher())
                            .visible(true)
            );
        }
        else if (summary && mine != null) {
            String subjects = "";
            for (int i : new int[] { 1, 2, 3, 4, 5}) {
                TodayJson.Period p = mine.getPeriod(i);
                if (i == 5) {
                    if (p != null) {
                        subjects += "and " + p.name().replace(" Period", "");
                    }
                    else {
                        subjects += "and free!";
                    }
                    continue;
                }
                if (p != null) {
                    subjects += p.name().replace(" Period", "") + ", ";
                }
                else {
                    subjects += "Free, ";
                }
            }
            if (!mine.valid()) {
                subjects = "I need to reload!";
                ApiAccessor.getToday(this);
            }
            String shortTitle;
            if (mine.getDayName().length() > 3) {
                shortTitle = mine.getDayName().substring(0, 3);
                shortTitle += " " + mine.getDayName().split(" ")[1];
            }
            else {
                shortTitle = "TMR";
            }
            publishUpdate(new ExtensionData()
                .icon(R.drawable.ic_launcher)
                .status(shortTitle)
                .expandedTitle(mine.getDayName())
                .expandedBody(subjects)
                .visible(true)
            );
        }
    }
}
