package com.evops.plant.gateway;

/**
 * 棚区灯具网关：把光配方指令送达指定棚区设备。
 * 生产环境对接真实灯控协议（MQTT/Modbus 等），实现各自替换本接口即可。
 */
public interface LightDeviceGateway {

    /**
     * 下发指令。
     *
     * @throws LightDeviceException 设备离线/超时/拒绝等失败——调用方据此把设备放入重试队列
     */
    void send(LightCommand command);
}
