package com.reactagent;

import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.core.tool.Tool;
import com.reactagent.model.ModelAdapter;
import com.reactagent.model.ModelResponse;
import com.reactagent.skills.SkillRegistry;
import com.reactagent.tools.ToolKit;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * ReAct Agent 引擎启动类。
 * <p>
 * 不需要手动加 @ComponentScan,@SpringBootApplication 已包含,
 * 默认扫描 com.reactagent 及其子包(包括 com.reactagent.model)。
 */
@SpringBootApplication
public class ReactAgentApplication {

    @Autowired
    private ModelAdapter modelAdapter;

    @Autowired
    private ToolKit toolkit;

    @Autowired
    private SkillRegistry skillRegistry;

    public static void main(String[] args) {
        SpringApplication.run(ReactAgentApplication.class, args);
    }

    @PostConstruct
    public void init() {

        ContentBlock contentBlock = new TextBlock("你好,帮我执行一下code review的skill");
        Msg msg = Msg.user("1", "user", List.of(contentBlock));
        Mono<ModelResponse> call = modelAdapter.call(List.of(msg), toolkit.toFunctionSpecs(),skillRegistry.listMeta());
        call.subscribe(modelResponse -> {
            System.out.println("=== 模型响应 ===");
            System.out.println(modelResponse);
        }, error -> {
            System.err.println("模型调用失败: " + error.getMessage());
        });
//        modelAdapter.stream(List.of(msg), null)
//                .subscribe(modelResponse -> {
//                    System.out.println("=== 模型响应 ===");
//                    System.out.println(modelResponse);
//                }, error -> {
//                    System.err.println("模型调用失败: " + error.getMessage());
//                });
    }
}
