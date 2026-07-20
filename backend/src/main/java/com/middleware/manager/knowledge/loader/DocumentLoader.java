package com.middleware.manager.knowledge.loader;

import java.io.InputStream;

public interface DocumentLoader {

    String load(InputStream inputStream, String fileName) throws Exception;

    boolean supports(String fileName);
}
