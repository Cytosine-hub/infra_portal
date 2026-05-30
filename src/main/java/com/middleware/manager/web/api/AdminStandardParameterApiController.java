package com.middleware.manager.web.api;

import com.middleware.manager.service.StandardParameterService;
import com.middleware.manager.web.api.dto.StandardParameterRequest;
import com.middleware.manager.web.api.dto.StandardParameterResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/standard-parameters")
public class AdminStandardParameterApiController {
    private final StandardParameterService service;

    public AdminStandardParameterApiController(StandardParameterService service) {
        this.service = service;
    }

    @GetMapping
    public List<StandardParameterResponse> list(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(defaultValue = "") String category,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Long standardDocumentId,
                                                @RequestParam(required = false) Long parameterStandardId) {
        return service.list(keyword, category, active, standardDocumentId, parameterStandardId).stream()
                .map(StandardParameterResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public StandardParameterResponse create(@Valid @RequestBody StandardParameterRequest request) {
        return StandardParameterResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    public StandardParameterResponse update(@PathVariable Long id,
                                            @Valid @RequestBody StandardParameterRequest request) {
        return StandardParameterResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("参数列表");

            // 表头
            Row header = sheet.createRow(0);
            String[] columns = {"参数编码", "参数名称", "参数值", "分类", "说明", "是否启用", "是否部署标准"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }
            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(2, 5000);

            // 示例数据
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("JDK_VERSION");
            example.createCell(1).setCellValue("JDK版本");
            example.createCell(2).setCellValue("1.8_8u392");
            example.createCell(3).setCellValue("运行环境");
            example.createCell(4).setCellValue("推荐使用的JDK版本");
            example.createCell(5).setCellValue("是");
            example.createCell(6).setCellValue("否");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parameter-template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    @PostMapping("/import")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file,
                                           @RequestParam("parameterStandardId") Long parameterStandardId) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件为空");
            }

            // 跳过表头，从第2行开始
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String code = getCellString(row.getCell(0)).trim();
                String name = getCellString(row.getCell(1)).trim();
                String value = getCellString(row.getCell(2)).trim();
                String category = getCellString(row.getCell(3)).trim();
                String description = getCellString(row.getCell(4)).trim();
                String activeStr = getCellString(row.getCell(5)).trim();
                String deployStr = getCellString(row.getCell(6)).trim();

                if (code.isEmpty() && name.isEmpty() && value.isEmpty()) {
                    continue; // 跳过空行
                }

                if (code.isEmpty()) {
                    errors.add("第" + (i + 1) + "行: 参数编码不能为空");
                    skipped++;
                    continue;
                }
                if (name.isEmpty()) {
                    errors.add("第" + (i + 1) + "行: 参数名称不能为空");
                    skipped++;
                    continue;
                }
                if (value.isEmpty()) {
                    errors.add("第" + (i + 1) + "行: 参数值不能为空");
                    skipped++;
                    continue;
                }

                StandardParameterRequest request = new StandardParameterRequest();
                request.setParameterStandardId(parameterStandardId);
                request.setCode(code);
                request.setName(name);
                request.setValue(value);
                request.setCategory(category.isEmpty() ? null : category);
                request.setDescription(description.isEmpty() ? null : description);
                request.setActive("是".equals(activeStr) || "true".equalsIgnoreCase(activeStr) || "1".equals(activeStr));
                request.setDeploymentStandard("是".equals(deployStr) || "true".equalsIgnoreCase(deployStr) || "1".equals(deployStr));

                try {
                    service.create(request);
                    imported++;
                } catch (Exception ex) {
                    errors.add("第" + (i + 1) + "行: " + ex.getMessage());
                    skipped++;
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Excel 文件失败: " + ex.getMessage(), ex);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue();
    }
}
