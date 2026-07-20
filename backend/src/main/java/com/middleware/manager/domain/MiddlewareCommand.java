package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareCommand {

    private Long id;
    private Long softwareTypeId;
    private String name;
    private String command;
    private String commandFormat;
    private String briefDescription;
    private String detailedDescription;
    private String categories;
    private int sortOrder = 0;
}
