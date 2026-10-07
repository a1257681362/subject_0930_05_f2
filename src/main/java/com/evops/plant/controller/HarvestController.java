package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.HarvestRequest;
import com.evops.plant.dto.QualityCheckRequest;
import com.evops.plant.entity.HarvestOrder;
import com.evops.plant.entity.QualityCheck;
import com.evops.plant.service.HarvestService;
import com.evops.plant.service.QualityCheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 采收单（等级/重量/去向）与质检。销售结算后续再接，本阶段不提供结算接口。 */
@RestController
@RequestMapping("/api/pf/harvests")
public class HarvestController {

    private final HarvestService harvestService;
    private final QualityCheckService qualityCheckService;

    public HarvestController(HarvestService harvestService,
                             QualityCheckService qualityCheckService) {
        this.harvestService = harvestService;
        this.qualityCheckService = qualityCheckService;
    }

    @PostMapping("/batches/{batchId}")
    public ApiResponse<HarvestOrder> create(@PathVariable Long batchId,
                                            @Valid @RequestBody HarvestRequest request) {
        return ApiResponse.ok(harvestService.createHarvest(batchId, request));
    }

    @GetMapping
    public ApiResponse<List<HarvestOrder>> list(@RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(harvestService.listByBatch(batchId));
    }

    @GetMapping("/{harvestId}")
    public ApiResponse<HarvestOrder> detail(@PathVariable Long harvestId) {
        return ApiResponse.ok(harvestService.getHarvest(harvestId));
    }

    /** 质检：合格/不合格，回写采收单质检状态。 */
    @PostMapping("/{harvestId}/quality-check")
    public ApiResponse<QualityCheck> check(@PathVariable Long harvestId,
                                           @Valid @RequestBody QualityCheckRequest request) {
        return ApiResponse.ok(qualityCheckService.check(harvestId, request));
    }

    @GetMapping("/{harvestId}/quality-check")
    public ApiResponse<QualityCheck> getCheck(@PathVariable Long harvestId) {
        return ApiResponse.ok(qualityCheckService.getByHarvest(harvestId));
    }
}
