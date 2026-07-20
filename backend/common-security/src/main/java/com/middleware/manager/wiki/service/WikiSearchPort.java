package com.middleware.manager.wiki.service;

import org.springframework.security.core.Authentication;

import java.util.List;

public interface WikiSearchPort {
    List<WikiSearchResult> search(String query, int topK);

    List<WikiSearchResult> search(String query, int topK, Authentication authentication);
}
