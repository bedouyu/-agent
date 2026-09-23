package com.paperagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端应用入口。
 *
 * <p>Spring Boot 会从当前包开始向下扫描 Controller、Service、Repository 等组件。</p>
 */
@SpringBootApplication
public class PaperAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAgentApplication.class, args);
    }
}

