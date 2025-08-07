package com.example.util;

import cn.hutool.core.io.FileUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * openapi 自动生成代码注释
 *
 * @author king
 */
public class EntityFieldCommenter2 {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+.*;");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+.*;");

    public static void processDirectory(String directoryPath) throws IOException {
        List<String> excludedFiles = FileUtil.readLines("file-exclude.txt", "UTF-8");
        Files.walk(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !excludedFiles.contains(path.toString()))
                .forEach(EntityFieldCommenter2::processFile);
    }

    private static void processFile(Path filePath) {
        System.out.println("Processing file: " + filePath);
        try {
            List<String> lines = Files.readAllLines(filePath);

            // 过滤掉大于等于500行的文件
            if (lines.size() >= 500) {
                System.out.println("Skipping file with less than or equal to 500 lines: " + filePath);
                return;
            }

            List<String> importLines = new ArrayList<>();
            List<String> packageLines = new ArrayList<>();
            List<String> codeLines = new ArrayList<>();

            // 将 package、import 语句和其他代码分开
            for (String line : lines) {
                if (PACKAGE_PATTERN.matcher(line).matches()) {
                    // 保存package语句
                    packageLines.add(line);
                } else if (IMPORT_PATTERN.matcher(line).matches()) {
                    // 保存import语句
                    importLines.add(line);
                } else {
                    // 保存其他代码
                    codeLines.add(line);
                }
            }

            // 将其他代码生成注释
            String codeContent = String.join("\n", codeLines);
            String prompt = "请为以下 Java 代码生成详细的注释,要求如下:\n" +
                    "1.注释符合注释格式，不要增加任何其他格式逻辑，不要删除和修改原来任意代码逻辑.\n" +
                    "2.方法头注释要使用标准的多行注释格式 (/** ... */),包括方法的功能描述、参数说明以及返回值说明,使用 @param 和 @return 标志来明确参数和返回值的用途\n" +
                    "3.在方法内部使用 // 对关键代码行进行单行注释,在该行代码的上新增一行增加注释,简要说明代码的功能或逻辑\n" +
                    "4.禁止使用行尾注释\n" +
                    "5.对类变量的定义使用多行注释 (/** ... */)\n" +
                    "6.对变量的初始化或使用进行注释，解释变量的用途\n" +
                    "7.仅在缺少文件头注释的时候,添加文件头注释\n" +
                    "8.如果要添加注释的位置已存在注释,就无需再添加\n" +
                    "9.仅返回增加注释后的代码,不要有其他内容\n\n" +
                    "10.禁止增加新的代码\n\n" +
                    "要添加注释的代码如下:\n" + codeContent;
            String generatedComments = fetchMethodCommentFromOpenAPI(prompt);
            if (!generatedComments.isEmpty()) {
                System.out.println(generatedComments);
                // 清理生成的注释，去掉可能的代码块标记
                generatedComments = generatedComments.replaceAll("```java", "").replaceAll("```", "").trim();

                // 合并生成的注释和package、import语句
                List<String> updatedLines = new ArrayList<>();
                updatedLines.addAll(packageLines);
                updatedLines.addAll(importLines);
                updatedLines.add(generatedComments);

                // 替换文件内容
                Files.write(filePath, updatedLines);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String fetchMethodCommentFromOpenAPI(String prompt) {
        String resultCode = "";
        while ("".equals(resultCode)) {
            try {
                Generation gen = new Generation();
                Message userMsg = Message.builder()
                        .role(Role.USER.getValue())
                        .content(prompt)
                        .build();
                GenerationParam param = GenerationParam.builder()
                        // 替换成实际API密钥
                        .apiKey("apikey")
                        .model("qwen-max-2025-01-25")
                        .messages(Collections.singletonList(userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .build();
                GenerationResult result = gen.call(param);
                resultCode = result.getOutput().getChoices().get(0).getMessage().getContent();
            } catch (ApiException | NoApiKeyException | InputRequiredException e) {
                e.printStackTrace();
                resultCode = "";
            }
        }
        return resultCode;
    }

    public static void main(String[] args) throws IOException {
        // 替换成实际路径
        String directoryPath = "E:\\workspace\\";
        processDirectory(directoryPath);

    }
}
