package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.GreenhouseRequest;
import com.evops.plant.dto.TrayRequest;
import com.evops.plant.dto.VarietyRequest;
import com.evops.plant.dto.ZoneRequest;
import com.evops.plant.entity.Greenhouse;
import com.evops.plant.entity.Tray;
import com.evops.plant.entity.Variety;
import com.evops.plant.entity.Zone;
import com.evops.plant.service.MasterDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 建档：温室（光照/营养液循环）、设备分区、品种、播种盘。 */
@RestController
@RequestMapping("/api/pf")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @PostMapping("/greenhouses")
    public ApiResponse<Greenhouse> createGreenhouse(@Valid @RequestBody GreenhouseRequest request) {
        return ApiResponse.ok(masterDataService.createGreenhouse(request));
    }

    @GetMapping("/greenhouses")
    public ApiResponse<List<Greenhouse>> listGreenhouses() {
        return ApiResponse.ok(masterDataService.listGreenhouses());
    }

    @PostMapping("/greenhouses/{greenhouseId}/zones")
    public ApiResponse<Zone> createZone(@PathVariable Long greenhouseId,
                                        @Valid @RequestBody ZoneRequest request) {
        return ApiResponse.ok(masterDataService.createZone(greenhouseId, request));
    }

    @GetMapping("/greenhouses/{greenhouseId}/zones")
    public ApiResponse<List<Zone>> listZones(@PathVariable Long greenhouseId) {
        return ApiResponse.ok(masterDataService.listZones(greenhouseId));
    }

    @PostMapping("/varieties")
    public ApiResponse<Variety> createVariety(@Valid @RequestBody VarietyRequest request) {
        return ApiResponse.ok(masterDataService.createVariety(request));
    }

    @GetMapping("/varieties")
    public ApiResponse<List<Variety>> listVarieties() {
        return ApiResponse.ok(masterDataService.listVarieties());
    }

    @PostMapping("/trays")
    public ApiResponse<Tray> createTray(@Valid @RequestBody TrayRequest request) {
        return ApiResponse.ok(masterDataService.createTray(request));
    }

    @GetMapping("/trays")
    public ApiResponse<List<Tray>> listTrays() {
        return ApiResponse.ok(masterDataService.listTrays());
    }
}
