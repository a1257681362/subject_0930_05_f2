package com.evops.plant.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 灯控网关的本地实现：记录指令日志，不接真实设备。
 * 通过 {@code markFailing} 模拟指定棚区设备离线/超时，下发将抛出 {@link LightDeviceException}，
 * 供"失败设备进入重试队列"链路联调与测试使用；生产替换为真实协议实现即可。
 */
@Component
public class LoggingLightDeviceGateway implements LightDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingLightDeviceGateway.class);

    private final CopyOnWriteArraySet<String> failingKeys = new CopyOnWriteArraySet<>();
    private final List<LightCommand> sentCommands = new CopyOnWriteArrayList<>();

    @Override
    public void send(LightCommand command) {
        String key = command.failKey();
        if (failingKeys.contains(key)) {
            throw new LightDeviceException("棚区设备无响应（模拟失败）: " + key);
        }
        sentCommands.add(command);
        log.info("[灯控下发] 温室={} 分区={} 批次={} 亮度={}% 光谱R/G/B/W={}/{}/{}/{} 来源={}",
                command.getGreenhouseId(), command.getZoneId(), command.getBatchId(),
                command.getBrightnessPct(),
                command.getRatioRedPct(), command.getRatioGreenPct(),
                command.getRatioBluePct(), command.getRatioWhitePct(),
                command.getSource());
    }

    /** 测试/联调：标记某棚区设备为故障（键格式 温室id:分区id 或 温室id:*）。 */
    public void markFailing(String failKey) {
        failingKeys.add(failKey);
    }

    public void clearFailures() {
        failingKeys.clear();
    }

    public List<LightCommand> getSentCommands() {
        return sentCommands;
    }

    public void clearSentCommands() {
        sentCommands.clear();
    }
}
