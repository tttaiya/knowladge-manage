package com.km.gateway.filter;

import com.km.gateway.dto.AuthUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 用户上下文网关过滤器（R9 + 必改 1 + 必改 2 防伪）。
 *
 * <p>职责：
 * <ol>
 *   <li>读取 Authorization Bearer Token</li>
 *   <li>调 super-biz-agent /api/auth/me 原样转发 Token</li>
 *   <li>删除浏览器可能伪造的 X-User-Id / X-User-Name（必改 1：防伪）</li>
 *   <li>用响应里 <b>id</b> 字段（不是 userId）注入 X-User-Id</li>
 *   <li>用响应里 username 注入 X-User-Name</li>
 * </ol>
 *
 * <p>约束：
 * <ul>
 *   <li>Gateway 不持密钥、不本地验签</li>
 *   <li>super-biz-agent 不可达时返回 401（不静默放行）</li>
 *   <li>调 /api/auth/me 超时 3 秒</li>
 *   <li>白名单：/api/auth/**、/api/chat/**、/api/conversations/**、/api/admin/** 由 Gateway 直接代理，<b>不走本过滤器</b></li>
 * </ul>
 *
 * <p>另：v4 文档 v4 补充 1 要求"Gateway 未携带 Token 访问 /api/v1/** 必须直接返回 401，不能 chain.filter(exchange) 放行"。
 */
@Component
public class UserContextGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(UserContextGatewayFilter.class);

    /**
     * 不需要用户上下文的路径前缀（白名单）。
     * 这些路由由 Gateway 直接代理到 super-biz-agent，由 super-biz-agent 自己处理 401。
     */
    private static final String[] WHITE_LIST_PREFIXES = {
            "/api/auth/",
            "/api/chat",
            "/api/chat/",
            "/api/chat_stream",
            "/api/messages/",
            "/api/conversations/",
            "/api/admin/"
    };

    private final WebClient webClient;
    private final String superBizAgentBase;

    public UserContextGatewayFilter(
            @Value("${super-biz-agent.base-url:http://super-biz-agent-service:9900}") String superBizAgentBase) {
        this.superBizAgentBase = superBizAgentBase;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1) 白名单路径：直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        // 2) 非 /api/v1/** 路径：直接放行（不强制要 Token）
        if (!path.startsWith("/api/v1/")) {
            return chain.filter(exchange);
        }

        // 3) /api/v1/** 路径：必须有 Bearer Token，否则 401
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少 Authorization Bearer Token");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange, "Authorization Bearer Token 为空");
        }

        // 4) 调 super-biz-agent /api/auth/me 拿真实身份
        return webClient.get()
                .uri(superBizAgentBase + "/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(AuthUserResponse.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(authUser -> {
                    if (authUser == null || authUser.getId() == null || authUser.getId().isEmpty()) {
                        return unauthorized(exchange, "身份校验失败：未返回用户 ID");
                    }
                    // 5) 防伪：删除浏览器伪造的 Header，再注入真实身份（必改 1）
                    ServerHttpRequest mutated = request.mutate()
                            .headers(headers -> {
                                headers.remove("X-User-Id");
                                headers.remove("X-User-Name");
                                headers.set("X-User-Id", authUser.getId());
                                if (authUser.getUsername() != null) {
                                    headers.set("X-User-Name", authUser.getUsername());
                                }
                            })
                            .build();
                    LOG.debug("用户上下文注入：userId={}, username={}", authUser.getId(), authUser.getUsername());
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(e -> {
                    LOG.warn("调用 super-biz-agent /api/auth/me 失败：{}", e.getMessage());
                    return unauthorized(exchange, "身份校验失败：" + e.getClass().getSimpleName());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhiteList(String path) {
        if (path == null) return false;
        for (String prefix : WHITE_LIST_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + reason + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
