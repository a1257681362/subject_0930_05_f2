package com.evops.plant.support;

import com.evops.plant.entity.LightDevice;
import com.evops.plant.entity.LightDispatch;

/** 灯控设备下发网关：成功返回 null，失败返回错误原因（进入重试队列）。 */
public interface LightDeviceGateway {

    /**
     * 向设备下发光配方参数。
     *
     * @return 成功返回 null；失败返回错误原因
     */
    String send(LightDevice device, LightDispatch dispatch);
}
