package com.campusfootball.stats;

public record StandingRow(long teamId, String teamName, int played, int won, int drawn, int lost,
                          int goalsFor, int goalsAgainst, int goalDifference,
                          int yellowCards, int redCards, int fairPlayPoints, int points) {
}