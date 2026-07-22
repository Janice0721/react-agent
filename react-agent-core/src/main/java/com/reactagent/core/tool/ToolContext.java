package com.reactagent.core.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行上下文,每次工具调用时传入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {
    /** 请求/回复 ID */
    private String replyId;
    /** 会话 ID */
    private String sessionId;
    /** 工作目录 */
    private String workDir;
    /** 超时时间(秒) */
    private int timeoutSeconds;
    /** 环境变量 */
    private Map<String, String> env;

    public static ToolContext of(String sessionId, String workDir) {
        return ToolContext.builder()
                .sessionId(sessionId)
                .workDir(workDir)
                .timeoutSeconds(30)
                .build();
    }
}
