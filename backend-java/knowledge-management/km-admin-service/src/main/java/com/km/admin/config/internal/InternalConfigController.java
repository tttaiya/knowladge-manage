package com.km.admin.config.internal;

import com.km.admin.config.ConfigService;
import com.km.admin.config.dto.ParserConfigDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 内部配置接口（Worker 启动初始化拉 parser 配置）。
 *
 * R33：路径 /internal/km/configs/parser + X-Service-Token: ${INTERNAL_TOKEN}，与项目其它 internal 接口共用环境变量。
 * v6 修正：返回原始 ParserConfigDTO（不包装 ApiResponse），与 Worker RestTemplate 反序列化对齐。
 * v6 修正：Token 错误抛 ResponseStatusException(UNAUTHORIZED)，项目无 UnauthorizedException。
 */
@RestController
@RequestMapping("/internal/km/configs")
public class InternalConfigController {

    @Autowired
    private ConfigService configService;

    @Value("${km.internal.token:demo-internal-token}")
    private String internalToken;

    @GetMapping("/parser")
    public ParserConfigDTO getParserConfig(
            @RequestHeader(value = "X-Service-Token", required = false) String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
        return configService.getParserConfig();
    }
}