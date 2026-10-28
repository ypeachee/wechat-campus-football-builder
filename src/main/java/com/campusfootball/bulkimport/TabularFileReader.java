package com.campusfootball.bulkimport;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TabularFileReader {
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 1000;

    public TabularData read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要导入的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("导入文件不能超过 2 MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String normalized = filename.toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith(".csv")) {
                return readCsv(file.getBytes());
            }
            if (normalized.endsWith(".xlsx")) {
                return readXlsx(file.getBytes());
            }
            throw badRequest("仅支持 CSV 或 XLSX 文件");
        } catch (IOException ex) {
            throw badRequest("文件读取失败：" + ex.getMessage());
        }
    }

    private TabularData readCsv(byte[] bytes) throws IOException {
        Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), decode(bytes));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (CSVParser parser = format.parse(reader)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<TabularRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_DATA_ROWS) {
                    throw badRequest("单次最多导入 1000 行数据");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : headers) {
                    values.put(header, record.isMapped(header) ? record.get(header).trim() : "");
                }
                if (values.values().stream().anyMatch(value -> !value.isBlank())) {
                    rows.add(new TabularRow((int) record.getRecordNumber() + 1, values));
                }
            }
            return new TabularData(headers, rows);
        }
    }

    private TabularData readXlsx(byte[] bytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw badRequest("表格缺少表头");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                String header = formatter.formatCellValue(headerRow.getCell(index)).trim();
                if (!header.isBlank()) {
                    headers.add(header);
                }
            }
            List<TabularRow> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int index = 0; index < headers.size(); index++) {
                    String value = formatter.formatCellValue(row.getCell(index)).trim();
                    values.put(headers.get(index), value);
                    hasValue |= !value.isBlank();
                }
                if (hasValue) {
                    if (rows.size() >= MAX_DATA_ROWS) {
                        throw badRequest("单次最多导入 1000 行数据");
                    }
                    rows.add(new TabularRow(rowIndex + 1, values));
                }
            }
            return new TabularData(headers, rows);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("XLSX 文件解析失败：" + ex.getMessage());
        }
    }

    private Charset decode(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException ex) {
            return Charset.forName("GB18030");
        }
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}