package com.evops.plant.support;

import com.evops.plant.domain.LightStatus;
import com.evops.plant.entity.LightDevice;
import com.evops.plant.entity.LightDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 模拟灯控网关：非 ACTIVE 设备下发失败（用于演示重试队列），
 * 正常设备记录日志即视为成功。接入真实灯控协议时替换本实现。
 */
@Component
public class SimulatedLightDeviceGateway implements LightDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedLightDeviceGateway.class);

    @Override
    public String send(LightDevice device, LightDispatch dispatch) {
        if (!LightStatus.DEVICE_ACTIVE.equals(device.getStatus())) {
            return "设备状态不可用: " + device.getStatus();
        }
        log.info("[模拟灯控] 设备 {} 应用配方: R{}/B{}/FR{} {}PPFD {}-{} (下发单#{})",
                device.getDeviceCode(),
                dispatch.getSpectrumRed(), dispatch.getSpectrumBlue(), dispatch.getSpectrumFarRed(),
                dispatch.getBrightnessPpfd(), dispatch.getLightOn(), dispatch.getLightOff(),
                dispatch.getId());
        return null;
    }
}
