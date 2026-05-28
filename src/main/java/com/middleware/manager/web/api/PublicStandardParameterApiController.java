package com.middleware.manager.web.api;

import com.middleware.manager.service.StandardParameterService;
import com.middleware.manager.web.api.dto.StandardParameterResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/standard-parameters")
public class PublicStandardParameterApiController {
    private final StandardParameterService service;

    public PublicStandardParameterApiController(StandardParameterService service) {
        this.service = service;
    }

    @GetMapping
    public List<StandardParameterResponse> list(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(defaultValue = "") String category,
                                                @RequestParam(required = false) Long parameterStandardId,
                                                @RequestParam(required = false) Long standardDocumentId) {
        if (parameterStandardId == null && standardDocumentId == null) {
            return service.listActive().stream()
                    .filter(p -> matchKeyword(p, keyword))
                    .filter(p -> matchCategory(p, category))
                    .map(StandardParameterResponse::from)
                    .collect(Collectors.toList());
        }
        return service.list(keyword, category, true, standardDocumentId, parameterStandardId).stream()
                .map(StandardParameterResponse::from)
                .collect(Collectors.toList());
    }

    private boolean matchKeyword(com.middleware.manager.domain.StandardParameter p, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String q = keyword.toLowerCase();
        return (p.getCode() != null && p.getCode().toLowerCase().contains(q))
                || (p.getName() != null && p.getName().toLowerCase().contains(q))
                || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q));
    }

    private boolean matchCategory(com.middleware.manager.domain.StandardParameter p, String category) {
        if (category == null || category.isBlank()) return true;
        return category.equalsIgnoreCase(p.getCategory());
    }
}
