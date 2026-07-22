package com.reactagent.tools;

import com.reactagent.tools.impl.BashTool;
import com.reactagent.tools.impl.EditTool;
import com.reactagent.tools.impl.GlobTool;
import com.reactagent.tools.impl.GrepTool;
import com.reactagent.tools.impl.ReadTool;
import com.reactagent.tools.impl.ScriptTool;
import com.reactagent.tools.impl.WriteTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具自动注册配置。
 * Spring 启动时自动创建 ToolKit Bean 并注册所有内置工具。
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolKit toolKit() {
        ToolKit kit = new ToolKit();
        // 注册内置工具
        kit.register(new BashTool());
        kit.register(new ReadTool());
        kit.register(new WriteTool());
        kit.register(new EditTool());
        kit.register(new GrepTool());
        kit.register(new GlobTool());
        kit.register(new ScriptTool());
        return kit;
    }
}
