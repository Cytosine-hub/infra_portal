package com.middleware.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.repository.SoftwareTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MiddlewareCommandService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareCommandService.class);

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
            log.warn("[MiddlewareCommand] Seed failed: {}", e.getMessage());
        }
    }

    private void seedCommands() throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:commands/commands.json");
        if (!resource.exists()) {
            log.info("[MiddlewareCommand] No commands.json found, skipping seed");
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
                log.warn("[MiddlewareCommand] Unknown software type: {}/{}, skipping command", categoryName, typeName);
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
        log.info("[MiddlewareCommand] Seed complete. {} commands", commandMapper.count());
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
            throw new IllegalArgumentException("类型不存在: " + softwareTypeId);
        }
        MiddlewareCommand cmd = new MiddlewareCommand();
        cmd.setSoftwareTypeId(type.getId());
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.insert(cmd);
        return cmd;
    }

    @Transactional
    public MiddlewareCommand update(Long id, Long softwareTypeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        MiddlewareCommand cmd = commandMapper.findById(id);
        if (cmd == null) {
            throw new IllegalArgumentException("命令不存在: " + id);
        }
        if (softwareTypeId != null) {
            SoftwareType type = softwareTypeMapper.findById(softwareTypeId);
            if (type == null) {
                throw new IllegalArgumentException("类型不存在: " + softwareTypeId);
            }
            cmd.setSoftwareTypeId(type.getId());
        }
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.update(cmd);
        return cmd;
    }

    public MiddlewareCommand findById(Long id) {
        return commandMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        commandMapper.deleteById(id);
    }
}
