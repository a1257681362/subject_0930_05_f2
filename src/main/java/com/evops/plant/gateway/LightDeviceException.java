package com.evops.plant.gateway;

/** 灯具设备下发失败（离线、超时、拒绝执行）。 */
public class LightDeviceException extends RuntimeException {
    public LightDeviceException(String message) {
        super(message);
    }

    public LightDeviceException(String message, Throwable cause) {
        super(message, cause);
    }
}
