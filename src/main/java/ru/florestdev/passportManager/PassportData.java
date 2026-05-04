package ru.florestdev.passportManager;
import java.util.concurrent.ThreadLocalRandom;

public class PassportData {
    private final int serial;
    private final String country;
    private final String realName;
    private final String nickname;
    private final String birthDate;
    private final String address;
    private final String maritalStatus;

    public PassportData(String country, String realName, String nickname, String birthDate, String address, String maritalStatus) {
        this.serial = ThreadLocalRandom.current().nextInt(100000, 999999);
        this.country = country;
        this.realName = realName;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.address = address;
        this.maritalStatus = maritalStatus;
    }

    public int getSerial() { return serial; }
    public String getCountry() { return country; }
    public String getRealName() { return realName; }
    public String getNickname() { return nickname; }
    public String getBirthDate() { return birthDate; }
    public String getAddress() { return address; }
    public String getMaritalStatus() { return maritalStatus; }
}