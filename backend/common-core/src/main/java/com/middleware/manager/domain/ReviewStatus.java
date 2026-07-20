package com.middleware.manager.domain;

public enum ReviewStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static String labelOf(String status) {
        if (status == null) return status;
        try {
            return valueOf(status).getLabel();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }
}
