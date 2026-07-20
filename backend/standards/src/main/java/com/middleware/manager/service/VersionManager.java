package com.middleware.manager.service;

import com.middleware.manager.domain.DocumentStatus;

/**
 * 版本号管理器。
 * 草稿: 0.YZ (如 0.11, 0.23)
 * 已发布: X.Y (如 1.0, 1.2, 2.0)
 * 修改中: X.YZ (如 1.13, 2.05)
 */
public final class VersionManager {

    private VersionManager() {}

    /** 创建草稿时的初始版本号 */
    public static String firstDraftVersion() {
        return "0.11";
    }

    /** 草稿编辑保存时递增：0.11 → 0.12 → ... → 0.99 */
    public static String nextDraftVersion(String current) {
        if (current == null || !current.startsWith("0.")) {
            return firstDraftVersion();
        }
        String yzStr = current.substring(2);
        int yz = parseIntSafe(yzStr, 11);
        if (yz < 99) {
            yz++;
        }
        return String.format("0.%02d", yz);
    }

    /** 首次发布：1.0 */
    public static String firstPublishVersion() {
        return "1.0";
    }

    /**
     * 从修改态转为已发布版本号：Z归零，Y递增。
     * 1.13 → 1.2, 1.09 → 1.1, 1.99 → 2.0
     */
    public static String toPublishedVersion(String version) {
        if (version == null) {
            return firstPublishVersion();
        }
        int dotIdx = version.indexOf('.');
        if (dotIdx < 0) {
            return firstPublishVersion();
        }
        int x = parseIntSafe(version.substring(0, dotIdx), 1);
        String yzPart = version.substring(dotIdx + 1);
        if (yzPart.length() == 1) {
            // 已经是 X.Y 格式
            int y = parseIntSafe(yzPart, 0);
            return x + "." + y;
        }
        // X.YZ 格式，取 Y 部分（除最后一位是 Z 外，其余是 Y）
        // 但更简单的做法：把 YZ 当整体，Y = YZ / 10，然后 Y+1
        // 不对，1.13 中 Y=1, Z=3，所以 1.13 → 1.2
        // 1.09 中 Y=0, Z=9，所以 1.09 → 1.1
        // 所以 YZ 两位数，第一位是 Y
        int y = parseIntSafe(yzPart.substring(0, yzPart.length() - 1), 0);
        y++;
        if (y > 9) {
            x++;
            y = 0;
        }
        if (x > 9) {
            x = 9;
        }
        return x + "." + y;
    }

    /**
     * 从已发布转为修改态版本号：追加 Z=1。
     * 1.0 → 1.01, 1.2 → 1.21, 2.0 → 2.01
     */
    public static String toModifyingVersion(String publishedVersion) {
        if (publishedVersion == null || !publishedVersion.contains(".")) {
            return firstPublishVersion() + "1";
        }
        return publishedVersion + "1";
    }

    /**
     * 修改态编辑保存时递增 Z：1.21 → 1.22, 1.29 → 1.210
     */
    public static String nextModifyingVersion(String current) {
        if (current == null || !current.contains(".")) {
            return toModifyingVersion(firstPublishVersion());
        }
        int dotIdx = current.indexOf('.');
        String xStr = current.substring(0, dotIdx);
        String yzStr = current.substring(dotIdx + 1);
        if (yzStr.length() <= 1) {
            return current + "1";
        }
        String yStr = yzStr.substring(0, yzStr.length() - 1);
        String zStr = yzStr.substring(yzStr.length() - 1);
        int z = parseIntSafe(zStr, 1);
        if (z < 9) {
            z++;
        }
        return xStr + "." + yStr + z;
    }

    /** 获取状态对应的操作提示 */
    public static String statusLabel(DocumentStatus status) {
        switch (status) {
            case DRAFT: return "草稿";
            case PENDING_REVIEW: return "审核中";
            case PUBLISHED: return "已发布";
            case MODIFYING: return "修改中";
            default: return status.name();
        }
    }

    private static int parseIntSafe(String s, int defaultVal) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
