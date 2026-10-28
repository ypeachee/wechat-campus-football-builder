package com.campusfootball.bulkimport;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class TabularFileReaderTest {
    private final TabularFileReader reader = new TabularFileReader();

    @Test
    void readsUtf8Csv() {
        MockMultipartFile file = new MockMultipartFile("file", "players.csv", "text/csv",
                "球队名称,姓名,号码,位置\n蓝队,张三,9,前锋\n".getBytes(Charset.forName("UTF-8")));

        TabularData data = reader.read(file);

        assertThat(data.headers()).containsExactly("球队名称", "姓名", "号码", "位置");
        assertThat(data.rows()).hasSize(1);
        assertThat(data.rows().get(0).get("姓名")).isEqualTo("张三");
    }

    @Test
    void readsGbkCsvFallback() {
        MockMultipartFile file = new MockMultipartFile("file", "teams.csv", "text/csv",
                "球队名称,简称,教练,主场,状态\n蓝队,蓝,李教练,东场,启用\n".getBytes(Charset.forName("GBK")));

        TabularData data = reader.read(file);

        assertThat(data.rows()).hasSize(1);
        assertThat(data.rows().get(0).get("球队名称")).isEqualTo("蓝队");
    }

    @Test
    void readsXlsxFirstSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("球队名称");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("号码");
            header.createCell(3).setCellValue("位置");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("红队");
            row.createCell(1).setCellValue("李四");
            row.createCell(2).setCellValue(10);
            row.createCell(3).setCellValue("中场");
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "players.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            TabularData data = reader.read(file);

            assertThat(data.rows()).hasSize(1);
            assertThat(data.rows().get(0).get("号码")).isEqualTo("10");
        }
    }
}