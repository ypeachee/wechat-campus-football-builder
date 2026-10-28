package com.campusfootball.team;

public interface TeamQrCodeClient {
    byte[] fetch(long teamId);
}