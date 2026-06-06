package com.middleware.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.repository.SoftwareTypeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MiddlewareCommandService implements ApplicationRunner {

    private final SoftwareTypeMapper softwareTypeMapper;
    private final MiddlewareCommandMapper commandMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MiddlewareCommandService(SoftwareTypeMapper softwareTypeMapper,
                                    MiddlewareCommandMapper commandMapper) {
        this.softwareTypeMapper = softwareTypeMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            seedCommands();
        } catch (Exception e) {
            log.warn("初始化命令失败: {}", e.getMessage());
        }
    }

    private void seedCommands() throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:commands/commands.json");
        if (!resource.exists()) {
            log.info("未找到 commands.json，跳过初始化");
            return;
        }
        List<Map<String, Object>> commands;
        try (InputStream is = resource.getInputStream()) {
            commands = objectMapper.readValue(is, new TypeReference<>() {});
        }

        for (Map<String, Object> c : commands) {
            String categoryName = (String) c.get("softwareTypeCategory");
            String typeName = (String) c.get("softwareTypeName");
            SoftwareType type = softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase(categoryName, typeName);
            if (type == null) {
                log.warn("未知软件类型: {}/{}，跳过命令", categoryName, typeName);
                continue;
            }
            String format = (String) c.get("commandFormat");
            List<MiddlewareCommand> existing = commandMapper.findBySoftwareTypeIdOrderBySortOrderAsc(type.getId());
            boolean exists = existing.stream().anyMatch(ec -> ec.getCommandFormat().equals(format));
            if (exists) continue;

            MiddlewareCommand entity = new MiddlewareCommand();
            entity.setSoftwareTypeId(type.getId());
            entity.setCommandFormat(format);
            entity.setBriefDescription((String) c.get("briefDescription"));
            entity.setDetailedDescription((String) c.get("detailedDescription"));
            entity.setSortOrder(((Number) c.get("sortOrder")).intValue());
            Object categories = c.get("categories");
            if (categories instanceof List<?> list) {
                entity.setCategories(objectMapper.writeValueAsString(list));
            }
            commandMapper.insert(entity);
        }
        log.info("命令初始化完成，共 {} 条", commandMapper.count());
    }

    public List<SoftwareType> listTypes() {
        Set<Long> typeIds = commandMapper.findDistinctSoftwareTypeIds().stream().collect(Collectors.toSet());
        return softwareTypeMapper.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(t -> typeIds.contains(t.getId()))
                .collect(Collectors.toList());
    }

    public List<MiddlewareCommand> listCommands(Long softwareTypeId) {
        if (softwareTypeId != null) {
            return commandMapper.findBySoftwareTypeIdOrderBySortOrderAsc(softwareTypeId);
        }
        return commandMapper.findAllByOrderBySoftwareTypeIdAscSortOrderAsc();
    }

    public List<MiddlewareCommand> listCommandsByCategory(String category) {
        return commandMapper.findByCategory(category);
    }

    @Transactional
    public MiddlewareCommand create(Long softwareTypeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        SoftwareType type = softwareTypeMapper.findById(softwareTypeId);
        if (type == null) {
            throw new NotFoundException(ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
        }
        MiddlewareCommand cmd = new MiddlewareCommand();
        cmd.setSoftwareTypeId(type.getId());
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.insert(cmd);
        log.info("命令已创建 id={}", cmd.getId());
        return cmd;
    }

    @Transactional
    public MiddlewareCommand update(Long id, Long softwareTypeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        MiddlewareCommand cmd = commandMapper.findById(id);
        if (cmd == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, ErrorMessages.COMMAND_NOT_FOUND);
        }
        if (softwareTypeId != null) {
            SoftwareType type = softwareTypeMapper.findById(softwareTypeId);
            if (type == null) {
                throw new NotFoundException(ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
            }
            cmd.setSoftwareTypeId(type.getId());
        }
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.update(cmd);
        log.info("命令已更新 id={}", id);
        return cmd;
    }

    public MiddlewareCommand findById(Long id) {
        return commandMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        commandMapper.deleteById(id);
        log.info("命令已删除 id={}", id);
    }
}
