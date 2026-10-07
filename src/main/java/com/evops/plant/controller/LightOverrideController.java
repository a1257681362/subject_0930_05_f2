package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.LightOverrideRequest;
import com.evops.plant.entity.LightOverride;
import com.evops.plant.service.LightOverrideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 人工临时调整：必须带结束时间与审批人；窗口内优先于配方，到点自动失效。 */
@RestController
@RequestMapping("/api/pf/light-overrides")
public class LightOverrideController {

    private final LightOverrideService overrideService;

    public LightOverrideController(LightOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    /** 新建临时调整（结束时间、审批人必填；同棚区同时只允许一条生效）。 */
    @PostMapping
    public ApiResponse<LightOverride> create(@Valid @RequestBody LightOverrideRequest request) {
        return ApiResponse.ok(overrideService.create(request));
    }

    @GetMapping
    public ApiResponse<List<LightOverride>> list(@RequestParam(required = false) Long zoneId,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(overrideService.list(zoneId, status));
    }

    /** 撤销生效中的临时调整。 */
    @PostMapping("/{overrideId}/revoke")
    public ApiResponse<LightOverride> revoke(@PathVariable Long overrideId) {
        return ApiResponse.ok(overrideService.revoke(overrideId));
    }
}
