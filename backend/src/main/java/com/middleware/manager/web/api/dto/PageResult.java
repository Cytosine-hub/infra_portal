package com.middleware.manager.web.api.dto;

import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PageResult<T> of(PageInfo<T> pageInfo) {
        PageResult<T> r = new PageResult<>();
        r.content = pageInfo.getList();
        r.page = pageInfo.getPageNum() - 1; // PageHelper 从 1 开始，前端从 0 开始
        r.size = pageInfo.getPageSize();
        r.totalElements = pageInfo.getTotal();
        r.totalPages = pageInfo.getPages();
        r.first = pageInfo.isIsFirstPage();
        r.last = pageInfo.isIsLastPage();
        return r;
    }
}
