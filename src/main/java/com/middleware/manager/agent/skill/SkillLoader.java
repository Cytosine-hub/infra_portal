package com.middleware.manager.agent.skill;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final Set<String> externalSkillNames = ConcurrentHashMap.newKeySet();

    @Value("${skills.external-dir:./data/skills}")
    private String externalDir;

    @PostConstruct
    public void load() {
        // 1. 加载 classpath 内置 Skill
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:skills/*.yaml");
            for (Resource resource : resources) {
                try {
                    Skill skill = yamlMapper.readValue(resource.getInputStream(), Skill.class);
                    skills.put(skill.getName(), skill);
                    log.info("[Skill] Loaded (builtin): {}", skill.getName());
                } catch (Exception e) {
                    log.warn("[Skill] Failed to load {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.info("[Skill] No builtin skills found in classpath:skills/*.yaml");
        }

        // 2. 加载外部目录 Skill（可覆盖同名内置）
        Path extPath = Path.of(externalDir);
        if (Files.isDirectory(extPath)) {
            try {
                Files.list(extPath)
                        .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .forEach(p -> {
                            try {
                                Skill skill = yamlMapper.readValue(p.toFile(), Skill.class);
                                skills.put(skill.getName(), skill);
                                externalSkillNames.add(skill.getName());
                                log.info("[Skill] Loaded (external): {}", skill.getName());
                            } catch (Exception e) {
                                log.warn("[Skill] Failed to load external {}: {}", p.getFileName(), e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.warn("[Skill] Failed to list external dir: {}", e.getMessage());
            }
        } else {
            try {
                Files.createDirectories(extPath);
                log.info("[Skill] Created external dir: {}", extPath);
            } catch (IOException e) {
                log.warn("[Skill] Failed to create external dir: {}", e.getMessage());
            }
        }

        log.info("[Skill] Loaded {} skills total", skills.size());
    }

    public Skill match(String input) {
        if (input == null || input.isEmpty()) return null;
        String upper = input.toUpperCase();
        return skills.values().stream()
                .filter(s -> s.getTrigger() != null && s.getTrigger().getKeywords() != null)
                .filter(s -> s.getTrigger().getKeywords().stream()
                        .anyMatch(kw -> upper.contains(kw.toUpperCase())))
                .findFirst().orElse(null);
    }

    public List<Skill> getAll() {
        return new ArrayList<>(skills.values());
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public void save(Skill skill) {
        skills.put(skill.getName(), skill);
        externalSkillNames.add(skill.getName());
        writeYaml(skill);
        log.info("[Skill] Saved: {}", skill.getName());
    }

    public void delete(String name) {
        if (!externalSkillNames.contains(name)) {
            log.warn("[Skill] Cannot delete builtin skill: {}", name);
            return;
        }
        skills.remove(name);
        externalSkillNames.remove(name);
        Path yamlPath = Path.of(externalDir, name + ".yaml");
        try {
            Files.deleteIfExists(yamlPath);
        } catch (IOException e) {
            log.warn("[Skill] Failed to delete YAML {}: {}", yamlPath, e.getMessage());
        }
        log.info("[Skill] Deleted: {}", name);
    }

    public boolean isExternal(String name) {
        return externalSkillNames.contains(name);
    }

    private void writeYaml(Skill skill) {
        try {
            Path dir = Path.of(externalDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(skill.getName() + ".yaml");
            String yaml = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(skill);
            Files.writeString(file, yaml);
        } catch (Exception e) {
            log.error("[Skill] Failed to write YAML for {}: {}", skill.getName(), e.getMessage());
        }
    }
}
