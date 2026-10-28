package com.campusfootball.bulkimport;

import java.util.List;
import java.util.Map;

public record ImportPreview(int totalRows, int validRows, int createCount, int updateCount,
                            List<ImportError> errors, List<Map<String, String>> sample) {
}