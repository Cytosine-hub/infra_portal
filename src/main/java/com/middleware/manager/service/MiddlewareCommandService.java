package com.middleware.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.MiddlewareType;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.repository.MiddlewareTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MiddlewareCommandService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareCommandService.class);

    private final MiddlewareTypeMapper typeMapper;
    private final MiddlewareCommandMapper commandMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MiddlewareCommandService(MiddlewareTypeMapper typeMapper,
                                    MiddlewareCommandMapper commandMapper) {
        this.typeMapper = typeMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            seedTypes();
            seedCommands();
        } catch (Exception e) {
            log.warn("[MiddlewareCommand] Seed failed: {}", e.getMessage());
        }
    }

    private void seedTypes() throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:commands/middleware-types.json");
        if (!resource.exists()) {
            log.info("[MiddlewareCommand] No middleware-types.json found, skipping seed");
            return;
        }
        List<Map<String, Object>> types;
        try (InputStream is = resource.getInputStream()) {
            types = objectMapper.readValue(is, new TypeReference<>() {});
        }
        for (Map<String, Object> t : types) {
            String name = (String) t.get("name");
            if (typeMapper.findByName(name) != null) continue;
            MiddlewareType entity = new MiddlewareType();
            entity.setName(name);
            entity.setSortOrder(((Number) t.get("sortOrder")).intValue());
            typeMapper.insert(entity);
            log.info("[MiddlewareCommand] Seeded type: {}", name);
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
        // Build type name -> id map
        Map<String, MiddlewareType> typeMap = new HashMap<>();
        typeMapper.findAllByOrderBySortOrderAsc().forEach(t -> typeMap.put(t.getName(), t));

        for (Map<String, Object> c : commands) {
            String typeName = (String) c.get("middlewareTypeName");
            MiddlewareType type = typeMap.get(typeName);
            if (type == null) {
                log.warn("[MiddlewareCommand] Unknown type: {}, skipping command", typeName);
                continue;
            }
            // Check if command already exists by format + type
            String format = (String) c.get("commandFormat");
            List<MiddlewareCommand> existing = commandMapper.findByMiddlewareTypeIdOrderBySortOrderAsc(type.getId());
            boolean exists = existing.stream().anyMatch(ec -> ec.getCommandFormat().equals(format));
            if (exists) continue;

            MiddlewareCommand entity = new MiddlewareCommand();
            entity.setMiddlewareTypeId(type.getId());
            entity.setCommandFormat(format);
            entity.setBriefDescription((String) c.get("briefDescription"));
            entity.setDetailedDescription((String) c.get("detailedDescription"));
            entity.setSortOrder(((Number) c.get("sortOrder")).intValue());
            // categories is a JSON array in the source data
            Object categories = c.get("categories");
            if (categories instanceof List<?> list) {
                entity.setCategories(objectMapper.writeValueAsString(list));
            }
            commandMapper.insert(entity);
        }
        log.info("[MiddlewareCommand] Seed complete. {} types, {} commands",
                typeMapper.count(), commandMapper.count());
    }

    public List<MiddlewareType> listTypes() {
        return typeMapper.findAllByOrderBySortOrderAsc();
    }

    public List<MiddlewareCommand> listCommands(Long typeId) {
        if (typeId != null) {
            return commandMapper.findByMiddlewareTypeIdOrderBySortOrderAsc(typeId);
        }
        return commandMapper.findAllByOrderByMiddlewareTypeIdAscSortOrderAsc();
    }

    @Transactional
    public MiddlewareCommand create(Long typeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        MiddlewareType type = typeMapper.findById(typeId);
        if (type == null) {
            throw new IllegalArgumentException("类型不存在: " + typeId);
        }
        MiddlewareCommand cmd = new MiddlewareCommand();
        cmd.setMiddlewareTypeId(type.getId());
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.insert(cmd);
        return cmd;
    }

    @Transactional
    public MiddlewareCommand update(Long id, Long typeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        MiddlewareCommand cmd = commandMapper.findById(id);
        if (cmd == null) {
            throw new IllegalArgumentException("命令不存在: " + id);
        }
        if (typeId != null) {
            MiddlewareType type = typeMapper.findById(typeId);
            if (type == null) {
                throw new IllegalArgumentException("类型不存在: " + typeId);
            }
            cmd.setMiddlewareTypeId(type.getId());
        }
        cmd.setCommandFormat(commandFormat);
        cmd.setBriefDescription(briefDescription);
        cmd.setDetailedDescription(detailedDescription);
        cmd.setCategories(categories);
        cmd.setSortOrder(sortOrder);
        commandMapper.update(cmd);
        return cmd;
    }

    @Transactional
    public void delete(Long id) {
        commandMapper.deleteById(id);
    }
}
