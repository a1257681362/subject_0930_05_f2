package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.CareLogRequest;
import com.evops.plant.dto.RelocateRequest;
import com.evops.plant.dto.SowRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.CareLog;
import com.evops.plant.entity.Relocation;
import com.evops.plant.service.BatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 种植批次：播种、养护、转棚留痕。 */
@RestController
@RequestMapping("/api/pf/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    /** 播种：关联品种、播种盘、计划采收日，生成唯一批次号。 */
    @PostMapping
    public ApiResponse<Batch> sow(@Valid @RequestBody SowRequest request) {
        return ApiResponse.ok(batchService.sow(request));
    }

    @GetMapping
    public ApiResponse<List<Batch>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(batchService.listBatches(status));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<Batch> detail(@PathVariable Long batchId) {
        return ApiResponse.ok(batchService.getBatch(batchId));
    }

    /** 养护：光照/营养液循环/设备巡检。 */
    @PostMapping("/{batchId}/care-logs")
    public ApiResponse<CareLog> addCareLog(@PathVariable Long batchId,
                                           @Valid @RequestBody CareLogRequest request) {
        return ApiResponse.ok(batchService.addCareLog(batchId, request));
    }

    @GetMapping("/{batchId}/care-logs")
    public ApiResponse<List<CareLog>> listCareLogs(@PathVariable Long batchId) {
        return ApiResponse.ok(batchService.listCareLogs(batchId));
    }

    /** 转棚：必须留痕。 */
    @PostMapping("/{batchId}/relocations")
    public ApiResponse<Relocation> relocate(@PathVariable Long batchId,
                                            @Valid @RequestBody RelocateRequest request) {
        return ApiResponse.ok(batchService.relocate(batchId, request));
    }

    @GetMapping("/{batchId}/relocations")
    public ApiResponse<List<Relocation>> listRelocations(@PathVariable Long batchId) {
        return ApiResponse.ok(batchService.listRelocations(batchId));
    }
}
