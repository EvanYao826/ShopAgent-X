package com.demo.aiknowledge.controller.admin;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.*;
import com.demo.aiknowledge.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识巡检Controller - 分析未命中问题和知识库质量
 */
@RestController
@RequestMapping("/api/admin/knowledge-inspection")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeInspectionController {

    private final QaUnansweredMapper qaUnansweredMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 未命中问题分析：从 qa_unanswered 表读取数据，做简单的聚类分析
     */
    @GetMapping("/unanswered/analyze")
    public Result<Map<String, Object>> analyzeUnanswered(
            @RequestParam(defaultValue = "1") int minCount,
            @RequestParam(defaultValue = "3") int clusterThreshold,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<QaUnanswered> all = qaUnansweredMapper.selectAll();
        if (all == null || all.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("totalUnansweredCount", 0);
            empty.put("totalUniqueQuestions", 0);
            empty.put("clusterCount", 0);
            empty.put("clusters", Collections.emptyList());
            empty.put("suggestions", Collections.emptyList());
            empty.put("exportData", Collections.emptyList());
            return Result.success(empty);
        }

        // 过滤最小出现次数
        List<QaUnanswered> filtered = all.stream()
                .filter(q -> q.getCount() != null && q.getCount() >= minCount)
                .collect(Collectors.toList());

        // 简单聚类：按问题关键词分组
        List<Map<String, Object>> clusters = simpleCluster(filtered, clusterThreshold);

        // 生成补库建议
        List<Map<String, Object>> suggestions = generateSuggestions(clusters);

        // 导出数据
        List<Map<String, String>> exportData = new ArrayList<>();
        for (QaUnanswered q : all) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", "未命中问题");
            row.put("name", q.getQuestion());
            row.put("issue", "出现 " + q.getCount() + " 次");
            row.put("detail", q.getCreateTime() != null ? q.getCreateTime().toString() : "-");
            exportData.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalUnansweredCount", all.size());
        result.put("totalUniqueQuestions", filtered.size());
        result.put("clusterCount", clusters.size());
        result.put("clusters", clusters);
        result.put("suggestions", suggestions);
        result.put("exportData", exportData);
        return Result.success(result);
    }

    /**
     * 知识库巡检：检查重复文档、低质量Chunk、过期文档、无人访问文档
     */
    @GetMapping("/library/analyze")
    public Result<Map<String, Object>> analyzeLibrary(
            @RequestParam(defaultValue = "10") int minChunkLength,
            @RequestParam(defaultValue = "180") int outdatedDays,
            @RequestParam(defaultValue = "90") int unaccessedDays,
            @RequestParam(defaultValue = "0.8") double similarityThreshold) {

        List<KnowledgeDoc> allDocs = knowledgeDocMapper.selectList(null);
        List<KnowledgeChunk> allChunks = knowledgeChunkMapper.selectList(null);
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        // 统计数据
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocs", allDocs.size());
        stats.put("totalChunks", allChunks.size());

        // 重复文档检测（按名称相似度简单判断）
        List<Map<String, Object>> duplicateDocs = detectDuplicates(allDocs, categoryMap, similarityThreshold);
        stats.put("duplicateDocGroups", duplicateDocs.size());

        // 低质量Chunk检测
        List<Map<String, Object>> lowQualityChunks = detectLowQualityChunks(allChunks, allDocs, minChunkLength);
        stats.put("lowQualityChunkCount", lowQualityChunks.size());

        // 过期文档检测
        LocalDateTime threshold = LocalDateTime.now().minus(outdatedDays, ChronoUnit.DAYS);
        List<Map<String, Object>> outdatedDocs = allDocs.stream()
                .filter(d -> d.getCreateTime() != null && d.getCreateTime().isBefore(threshold))
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("docName", d.getDocName());
                    m.put("categoryName", categoryMap.getOrDefault(d.getCategoryId(), "-"));
                    m.put("createTime", d.getCreateTime());
                    m.put("daySinceUpdate", ChronoUnit.DAYS.between(d.getCreateTime(), LocalDateTime.now()));
                    return m;
                })
                .sorted((a, b) -> Long.compare(
                        (long) b.get("daySinceUpdate"), (long) a.get("daySinceUpdate")))
                .collect(Collectors.toList());
        stats.put("outdatedDocCount", outdatedDocs.size());

        // 无人访问文档（暂时用创建时间久远 + 状态为 COMPLETED 作为近似）
        List<Map<String, Object>> unaccessedDocs = allDocs.stream()
                .filter(d -> d.getCreateTime() != null &&
                        d.getCreateTime().isBefore(LocalDateTime.now().minus(unaccessedDays, ChronoUnit.DAYS)))
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("docName", d.getDocName());
                    m.put("categoryName", categoryMap.getOrDefault(d.getCategoryId(), "-"));
                    m.put("accessCount", 0);
                    m.put("daySinceAccess", ChronoUnit.DAYS.between(d.getCreateTime(), LocalDateTime.now()));
                    return m;
                })
                .sorted((a, b) -> Long.compare(
                        (long) b.get("daySinceAccess"), (long) a.get("daySinceAccess")))
                .collect(Collectors.toList());
        stats.put("unaccessedDocCount", unaccessedDocs.size());

        // 导出数据
        List<Map<String, String>> exportData = new ArrayList<>();
        for (Map<String, Object> d : duplicateDocs) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", "重复文档");
            row.put("name", String.valueOf(d.getOrDefault("groupName", "-")));
            row.put("issue", d.get("documents") instanceof List ? ((List<?>) d.get("documents")).size() + "个" : "-");
            row.put("detail", "相似度: " + d.getOrDefault("similarity", "-"));
            exportData.add(row);
        }
        for (Map<String, Object> c : lowQualityChunks) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", "低质量Chunk");
            row.put("name", String.valueOf(c.getOrDefault("docName", "-")));
            row.put("issue", String.valueOf(c.getOrDefault("issueType", "-")));
            row.put("detail", String.valueOf(c.getOrDefault("issueDescription", "-")));
            exportData.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("stats", stats);
        result.put("duplicateDocs", duplicateDocs);
        result.put("lowQualityChunks", lowQualityChunks);
        result.put("outdatedDocs", outdatedDocs);
        result.put("unaccessedDocs", unaccessedDocs);
        result.put("exportData", exportData);
        return Result.success(result);
    }

    // ---------- 私有方法 ----------

    private List<Map<String, Object>> simpleCluster(List<QaUnanswered> questions, int threshold) {
        List<Map<String, Object>> clusters = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        for (int i = 0; i < questions.size(); i++) {
            if (used.contains(i)) continue;
            QaUnanswered center = questions.get(i);
            List<QaUnanswered> group = new ArrayList<>();
            group.add(center);
            used.add(i);

            for (int j = i + 1; j < questions.size(); j++) {
                if (used.contains(j)) continue;
                QaUnanswered other = questions.get(j);
                if (textSimilarity(center.getQuestion(), other.getQuestion()) > 0.4) {
                    group.add(other);
                    used.add(j);
                }
            }

            if (group.size() >= threshold || group.stream().mapToInt(q -> q.getCount() != null ? q.getCount() : 0).sum() >= threshold) {
                Map<String, Object> cluster = new HashMap<>();
                cluster.put("topic", extractTopic(group));
                cluster.put("totalCount", group.stream().mapToInt(q -> q.getCount() != null ? q.getCount() : 0).sum());
                cluster.put("questions", group.stream().map(QaUnanswered::getQuestion).collect(Collectors.toList()));
                cluster.put("topicSummary", "该主题涉及 " + group.size() + " 个相关问题，总计出现 "
                        + cluster.get("totalCount") + " 次，建议补充相关知识库内容。");
                cluster.put("suggestedKeywords", extractKeywords(group));
                clusters.add(cluster);
            }
        }
        clusters.sort((a, b) -> Integer.compare((int) b.get("totalCount"), (int) a.get("totalCount")));
        return clusters;
    }

    private List<Map<String, Object>> generateSuggestions(List<Map<String, Object>> clusters) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> cluster : clusters) {
            int count = (int) cluster.get("totalCount");
            String priority = count >= 10 ? "高" : count >= 5 ? "中" : "低";
            String suggestionType = count >= 10 ? "紧急补库" : count >= 5 ? "建议补库" : "观察";

            Map<String, Object> s = new HashMap<>();
            s.put("priority", priority);
            s.put("topic", cluster.get("topic"));
            s.put("suggestionType", suggestionType);
            s.put("questionCount", count);
            s.put("relatedCategory", "知识库");
            s.put("suggestion", "建议针对「" + cluster.get("topic") + "」主题补充相关文档，覆盖 "
                    + cluster.get("questions") + " 等高频问题。");
            suggestions.add(s);
        }
        return suggestions;
    }

    private String extractTopic(List<QaUnanswered> group) {
        if (group.isEmpty()) return "未知主题";
        String first = group.get(0).getQuestion();
        int len = Math.min(first.length(), 15);
        return first.substring(0, len) + (first.length() > len ? "..." : "");
    }

    private List<String> extractKeywords(List<QaUnanswered> group) {
        Set<String> keywords = new LinkedHashSet<>();
        for (QaUnanswered q : group) {
            String question = q.getQuestion();
            for (String kw : question.split("[，。！？\\s,]+")) {
                if (kw.length() >= 2 && kw.length() <= 6) {
                    keywords.add(kw);
                }
            }
            if (keywords.size() >= 8) break;
        }
        return new ArrayList<>(keywords).subList(0, Math.min(keywords.size(), 6));
    }

    private double textSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        Set<Character> setA = new HashSet<>();
        Set<Character> setB = new HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);
        for (char c : b.toCharArray()) setB.add(c);
        Set<Character> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<Character> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private List<Map<String, Object>> detectDuplicates(List<KnowledgeDoc> docs,
                                                        Map<Long, String> categoryMap, double threshold) {
        List<Map<String, Object>> groups = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        for (int i = 0; i < docs.size(); i++) {
            if (used.contains(i)) continue;
            KnowledgeDoc a = docs.get(i);
            List<KnowledgeDoc> group = new ArrayList<>();
            group.add(a);
            used.add(i);

            for (int j = i + 1; j < docs.size(); j++) {
                if (used.contains(j)) continue;
                KnowledgeDoc b = docs.get(j);
                String nameA = a.getDocName() != null ? a.getDocName() : "";
                String nameB = b.getDocName() != null ? b.getDocName() : "";
                double sim = textSimilarity(nameA, nameB);
                if (sim >= threshold) {
                    group.add(b);
                    used.add(j);
                }
            }

            if (group.size() >= 2) {
                List<Map<String, Object>> docMaps = group.stream().map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("docName", d.getDocName());
                    m.put("categoryName", categoryMap.getOrDefault(d.getCategoryId(), "-"));
                    m.put("createTime", d.getCreateTime());
                    return m;
                }).collect(Collectors.toList());

                Map<String, Object> g = new HashMap<>();
                g.put("groupName", group.get(0).getDocName());
                g.put("documents", docMaps);
                g.put("similarity", threshold);
                groups.add(g);
            }
        }
        return groups;
    }

    private List<Map<String, Object>> detectLowQualityChunks(List<KnowledgeChunk> chunks,
                                                              List<KnowledgeDoc> docs, int minLength) {
        Map<Long, String> docNameMap = docs.stream()
                .collect(Collectors.toMap(KnowledgeDoc::getId, KnowledgeDoc::getDocName, (a, b) -> a));

        List<Map<String, Object>> issues = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            String text = chunk.getChunkText();
            if (text == null || text.trim().isEmpty()) continue;

            String content = text.trim();

            // 检测过短的Chunk
            if (content.length() < minLength) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("docName", docNameMap.getOrDefault(chunk.getDocId(), "未知文档"));
                issue.put("chunkIndex", chunk.getChunkIndex());
                issue.put("issueType", "内容过短");
                issue.put("issueDescription", "Chunk内容仅" + content.length() + "字符，可能缺乏足够信息量");
                issues.add(issue);
                continue;
            }

            // 检测纯数字/符号的Chunk
            String alphaOnly = content.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z]", "");
            if ((double) alphaOnly.length() / content.length() < 0.3) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("docName", docNameMap.getOrDefault(chunk.getDocId(), "未知文档"));
                issue.put("chunkIndex", chunk.getChunkIndex());
                issue.put("issueType", "有效内容不足");
                issue.put("issueDescription", "文本中有效字符占比仅"
                        + String.format("%.0f", (double) alphaOnly.length() / content.length() * 100)
                        + "%，可能是表格或纯数字");
                issues.add(issue);
            }
        }
        return issues;
    }
}
