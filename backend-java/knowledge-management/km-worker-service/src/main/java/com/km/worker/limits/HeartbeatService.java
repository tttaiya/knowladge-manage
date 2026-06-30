package com.km.worker.limits;

import com.km.worker.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * F4 整合（commit #24）：心跳服务（抽出独立文件）。
 */
@Service
public class HeartbeatService {
    private final AdminClient adminClient;
    private final PermitManager permitManager;
    private final TaskScheduler scheduler;

    @Autowired
    public HeartbeatService(AdminClient adminClient, PermitManager permitManager) {
        this.adminClient = adminClient;
        this.permitManager = permitManager;
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("km-heartbeat-");
        s.initialize();
        this.scheduler = s;
    }

    public HeartbeatHandle start(Long taskId, String claimToken, boolean withPermit) {
        HeartbeatHandle h = new HeartbeatHandle(taskId, claimToken);
        h.future = scheduler.scheduleAtFixedRate(() -> {
            boolean claimOk = adminClient.heartbeat(taskId, claimToken);
            boolean permitOk = !withPermit || permitManager.refresh(taskId, claimToken);
            if (!claimOk || !permitOk) {
                h.leaseLost.set(true);
            }
        }, Duration.ofSeconds(30));
        return h;
    }
}