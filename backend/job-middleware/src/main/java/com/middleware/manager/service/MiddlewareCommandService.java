package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.repository.SoftwareTypeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MiddlewareCommandService {

    private final SoftwareTypeMapper softwareTypeMapper;
    private final MiddlewareCommandMapper commandMapper;

    public MiddlewareCommandService(SoftwareTypeMapper softwareTypeMapper,
                                    MiddlewareCommandMapper commandMapper) {
        this.softwareTypeMapper = softwareTypeMapper;
        this.commandMapper = commandMapper;
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
        cmd.setName(briefDescription);
        cmd.setCommand(commandFormat);
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
        cmd.setName(briefDescription);
        cmd.setCommand(commandFormat);
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
