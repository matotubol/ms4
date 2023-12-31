package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

public class ClubOffer implements ISerialize {

    @Getter
    private final int id;
    @Getter
    private final String name;
    @Getter
    private final int days;
    @Getter
    private final int credits;
    @Getter
    private final int points;
    @Getter
    private final int pointsType;
    @Getter
    private final boolean vip;
    @Getter
    private final boolean deal;

    public ClubOffer(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.days = set.getInt("days");
        this.credits = set.getInt("credits");
        this.points = set.getInt("points");
        this.pointsType = set.getInt("points_type");
        this.vip = set.getString("type").equalsIgnoreCase("vip");
        this.deal = set.getString("deal").equals("1");
    }

    @Override
    public void serialize(ServerMessage message) {
        serialize(message, Emulator.getIntUnixTimestamp());
    }

    public void serialize(ServerMessage message, int hcExpireTimestamp) {
        hcExpireTimestamp = Math.max(Emulator.getIntUnixTimestamp(), hcExpireTimestamp);
        message.appendInt(this.id);
        message.appendString(this.name);
        message.appendBoolean(false); //unused
        message.appendInt(this.credits);
        message.appendInt(this.points);
        message.appendInt(this.pointsType);
        message.appendBoolean(this.vip);

        long seconds = this.days * 86400L;

        long secondsTotal = seconds;

        int totalYears = (int) Math.floor((int) seconds / (86400.0 * 31 * 12));
        seconds -= totalYears * (86400 * 31 * 12);

        int totalMonths = (int) Math.floor((int) seconds / (86400.0 * 31));
        seconds -= totalMonths * (86400 * 31);

        int totalDays = (int) Math.floor((int) seconds / 86400.0);
        seconds -= totalDays * 86400L;

        message.appendInt((int) secondsTotal / 86400 / 31);
        message.appendInt((int) seconds);
        message.appendBoolean(false); //giftable
        message.appendInt((int) seconds);

        hcExpireTimestamp += secondsTotal;

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(hcExpireTimestamp * 1000L);
        message.appendInt(cal.get(Calendar.YEAR));
        message.appendInt(cal.get(Calendar.MONTH) + 1);
        message.appendInt(cal.get(Calendar.DAY_OF_MONTH));
    }
}