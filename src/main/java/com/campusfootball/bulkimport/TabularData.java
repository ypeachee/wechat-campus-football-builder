package com.campusfootball.bulkimport;

import java.util.List;

public record TabularData(List<String> headers, List<TabularRow> rows) {
}