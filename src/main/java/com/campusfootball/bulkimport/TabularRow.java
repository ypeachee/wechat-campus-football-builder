package com.campusfootball.bulkimport;

import java.util.Map;

public record TabularRow(int rowNumber, Map<String, String> values) {
    public String get(String header) {
        return values.getOrDefault(header, "");
    }
}