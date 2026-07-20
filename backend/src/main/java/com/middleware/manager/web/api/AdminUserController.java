package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final String DEFAULT_IMPORT_PASSWORD = "admin123";

    private final AdminAccountService adminAccountService;
    private final RoleService roleService;
    private final PermissionService permissionService;

    public AdminUserController(AdminAccountService adminAccountService,
                               RoleService roleService,
                               PermissionService permissionService) {
        this.adminAccountService = adminAccountService;
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return adminAccountService.listUsers().stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request) {
        AdminAccount account = adminAccountService.createUser(
                request.username, request.displayName, request.password, request.role);
        return toUserMap(account);
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        AdminAccount account = adminAccountService.updateUserRole(id, request.role);
        return toUserMap(account);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        adminAccountService.resetPassword(id, request.newPassword);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminAccountService.deleteUser(id);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户列表");
            Row header = sheet.createRow(0);
            String[] columns = {"账号", "用户名", "角色"};

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

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("zhangsan");
            example.createCell(1).setCellValue("张三");
            example.createCell(2).setCellValue("开发经理");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            respHeaders.setContentDispositionFormData("attachment", "用户导入模板.xlsx");

            return ResponseEntity.ok().headers(respHeaders).body(out.toByteArray());
        }
    }

    @PostMapping("/import")
    public Map<String, Object> importUsers(@RequestParam("file") MultipartFile file) {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String username = getCellString(row, 0);
                String displayName = getCellString(row, 1);
                String role = getCellString(row, 2);

                if (username.isEmpty()) {
                    skipped++;
                    continue;
                }
                if (role.isEmpty()) {
                    errors.add("第 " + (i + 1) + " 行：角色不能为空");
                    skipped++;
                    continue;
                }

                try {
                    adminAccountService.createUser(username, displayName, DEFAULT_IMPORT_PASSWORD, role);
                    imported++;
                } catch (Exception e) {
                    errors.add("第 " + (i + 1) + " 行：" + e.getMessage());
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, ErrorMessages.FILE_READ_FAILED);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    private String getCellString(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) return "";
        return cell.toString().trim();
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles() {
        return roleService.getAllRoles().stream().map(this::toRoleMap).collect(Collectors.toList());
    }

    // ── 角色管理（仅系统管理员）──

    @PostMapping("/roles")
    public Map<String, Object> createRole(@Valid @RequestBody CreateRoleRequest request,
                                          Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可创建角色");
        }
        RoleEntity role = roleService.createRole(request.displayName, request.authority,
                request.managedCategory, request.categoryAdmin);
        return toRoleMap(role);
    }

    @PutMapping("/roles/{id}")
    public Map<String, Object> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody CreateRoleRequest request,
                                          Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可修改角色");
        }
        RoleEntity role = roleService.updateRole(id, request.displayName, request.authority,
                request.managedCategory, request.categoryAdmin);
        return toRoleMap(role);
    }

    @DeleteMapping("/roles/{id}")
    public void deleteRole(@PathVariable Long id, Authentication auth) {
        if (!permissionService.isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可删除角色");
        }
        roleService.deleteRole(id);
    }

    private Map<String, Object> toUserMap(AdminAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("username", account.getUsername());
        map.put("displayName", account.getDisplayName());
        map.put("role", account.getRole());
        map.put("createdAt", account.getCreatedAt());
        return map;
    }

    private Map<String, Object> toRoleMap(RoleEntity role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getDisplayName());
        map.put("authority", role.getAuthority());
        map.put("category", role.getManagedCategory() != null ? role.getManagedCategory() : "");
        map.put("categoryAdmin", role.isCategoryAdmin());
        map.put("systemRole", role.isSystemRole());
        return map;
    }

    static class CreateUserRequest {
        @NotBlank @Size(min = 2, max = 60)
        public String username;
        public String displayName;
        @NotBlank @Size(min = 6, max = 64)
        public String password;
        @NotBlank
        public String role;
    }

    static class UpdateRoleRequest {
        @NotBlank
        public String role;
    }

    static class ResetPasswordRequest {
        @NotBlank @Size(min = 6, max = 64)
        public String newPassword;
    }

    static class CreateRoleRequest {
        @NotBlank
        public String displayName;
        @NotBlank
        public String authority;
        public String managedCategory;
        public boolean categoryAdmin;
    }
}
