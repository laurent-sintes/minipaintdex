package com.minipaintdex.application.port;

import java.util.List;
import java.util.Map;

public interface ProjectCatalogWriter {
    void replaceProject(String projectId, Map<String, Object> project, List<Map<String, Object>> paintingGuides);
}
