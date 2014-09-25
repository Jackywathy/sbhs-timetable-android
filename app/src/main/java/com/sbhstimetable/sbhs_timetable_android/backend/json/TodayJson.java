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

package com.sbhstimetable.sbhs_timetable_android.backend.json;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TodayJson {
    private final JsonObject today;
    private Period periods[] = new Period[5];
    private static TodayJson INSTANCE;

    public static TodayJson getInstance() {
        return INSTANCE;
    }

    public TodayJson(JsonObject obj) {
         this.today = obj;
         for (int i = 0; i < 5; i++) {
             try {
                 JsonElement j = today.get("timetable").getAsJsonObject().get(String.valueOf(i + 1));
                 if (j != null) {
                     periods[i] = new Period(j.getAsJsonObject(), this.finalised());
                 } else {
                     JsonObject b = new JsonObject();
                     b.addProperty("fullName", "Free Period");
                     b.addProperty("room", "N/A");
                     b.addProperty("fullTeacher", "Nobody");
                     b.addProperty("changed", false);
                     b.addProperty("year", "");
                     b.addProperty("title", "");
                     periods[i] = new Period(b, false);
                 }
             }
             catch (NullPointerException e) {
                JsonObject b = new JsonObject();
                 b.addProperty("fullName", "…");
                 b.addProperty("room", "…");
                 b.addProperty("fullTeacher", "…");
                 b.addProperty("changed", false);
                 b.addProperty("year", "");
                 b.addProperty("title", "");
                 periods[i] = new Period(b, false);
             }
         }
        INSTANCE = this;
    }

    public boolean valid() {
        return this.today.has("timetable");
    }

    public String getDate() {
        return valid() ? this.today.get("date").getAsString() : "";
    }

    public Period getPeriod(int num) {
        return periods[num-1];
    }

    public boolean finalised() {
        return this.today.get("variationsFinalised").getAsBoolean();
    }

    @Override
    public String toString() {
        return this.today.toString();
    }

    public static boolean isValid(JsonObject res) {
        return res.has("timetable");
    }

    public static class Period {
        private final JsonObject period;
        private boolean finalised = true;
        public Period(JsonObject obj, boolean finalised) {
            this.finalised = finalised;
            this.period = obj;
        }

        public boolean changed() {
            return period.get("changed").getAsBoolean();
        }

        public String fullTeacher() {
            if (this.changed() && period.get("hasCasual") != null && period.get("hasCasual").getAsBoolean() && this.finalised) {
                return period.get("casualDisplay").getAsString().trim();
            }
            return period.get("fullTeacher").getAsString();
        }

        public String getShortName() {
            return period.get("year").getAsString() + period.get("title").getAsString();
        }

        public boolean teacherChanged() {
            return !this.fullTeacher().equals(period.get("fullTeacher").getAsString());
        }

        public boolean roomChanged() {
            return !this.room().equals(period.get("room").getAsString());
        }

        public String room() {
            if (period.has("roomFrom") && this.finalised) {
                return period.get("roomTo").getAsString();
            }
            return period.get("room").getAsString();
        }

        public String name() {
            return period.get("fullName").getAsString();
        }

        @Override
        public String toString() {
            return period.toString();
        }

    }
}
