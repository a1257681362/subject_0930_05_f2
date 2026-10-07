package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.GreenhouseRequest;
import com.evops.plant.dto.TrayRequest;
import com.evops.plant.dto.VarietyRequest;
import com.evops.plant.dto.ZoneRequest;
import com.evops.plant.entity.Greenhouse;
import com.evops.plant.entity.Tray;
import com.evops.plant.entity.Variety;
import com.evops.plant.entity.Zone;
import com.evops.plant.mapper.GreenhouseMapper;
import com.evops.plant.mapper.TrayMapper;
import com.evops.plant.mapper.VarietyMapper;
import com.evops.plant.mapper.ZoneMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 建档：温室（光照/营养液循环）、设备分区、品种、播种盘。 */
@Service
public class MasterDataService {

    private final GreenhouseMapper greenhouseMapper;
    private final ZoneMapper zoneMapper;
    private final VarietyMapper varietyMapper;
    private final TrayMapper trayMapper;

    public MasterDataService(GreenhouseMapper greenhouseMapper, ZoneMapper zoneMapper,
                             VarietyMapper varietyMapper, TrayMapper trayMapper) {
        this.greenhouseMapper = greenhouseMapper;
        this.zoneMapper = zoneMapper;
        this.varietyMapper = varietyMapper;
        this.trayMapper = trayMapper;
    }

    @Transactional
    public Greenhouse createGreenhouse(GreenhouseRequest request) {
        if (greenhouseMapper.selectCount(new QueryWrapper<Greenhouse>()
                .eq("gh_code", request.getGhCode())) > 0) {
            throw new IllegalArgumentException("温室编号已存在: " + request.getGhCode());
        }
        Greenhouse gh = new Greenhouse();
        gh.setGhCode(request.getGhCode());
        gh.setGhName(request.getGhName());
        gh.setLightSource(request.getLightSource());
        gh.setPhotoperiodHours(request.getPhotoperiodHours());
        gh.setPpfd(request.getPpfd());
        gh.setNutrientCycleMode(request.getNutrientCycleMode());
        gh.setNutrientEc(request.getNutrientEc());
        gh.setNutrientPh(request.getNutrientPh());
        gh.setCycleIntervalMin(request.getCycleIntervalMin());
        gh.setStatus(PfStatus.ACTIVE);
        gh.setRemark(request.getRemark());
        try {
            greenhouseMapper.insert(gh);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("温室编号已存在: " + request.getGhCode());
        }
        return gh;
    }

    @Transactional
    public Zone createZone(Long greenhouseId, ZoneRequest request) {
        Greenhouse gh = greenhouseMapper.selectById(greenhouseId);
        if (gh == null) {
            throw new IllegalArgumentException("温室不存在: " + greenhouseId);
        }
        if (zoneMapper.selectCount(new QueryWrapper<Zone>()
                .eq("greenhouse_id", greenhouseId)
                .eq("zone_code", request.getZoneCode())) > 0) {
            throw new IllegalArgumentException("分区编号在该温室内已存在: " + request.getZoneCode());
        }
        Zone zone = new Zone();
        zone.setGreenhouseId(greenhouseId);
        zone.setZoneCode(request.getZoneCode());
        zone.setZoneName(request.getZoneName());
        zone.setFunctionType(request.getFunctionType());
        zone.setEquipmentDesc(request.getEquipmentDesc());
        zone.setStatus(PfStatus.ACTIVE);
        try {
            zoneMapper.insert(zone);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("分区编号在该温室内已存在: " + request.getZoneCode());
        }
        return zone;
    }

    @Transactional
    public Variety createVariety(VarietyRequest request) {
        if (varietyMapper.selectCount(new QueryWrapper<Variety>()
                .eq("variety_code", request.getVarietyCode())) > 0) {
            throw new IllegalArgumentException("品种编号已存在: " + request.getVarietyCode());
        }
        Variety variety = new Variety();
        variety.setVarietyCode(request.getVarietyCode());
        variety.setVarietyName(request.getVarietyName());
        variety.setCategory(request.getCategory());
        variety.setGrowDays(request.getGrowDays() == null ? 30 : request.getGrowDays());
        variety.setStatus(PfStatus.ACTIVE);
        try {
            varietyMapper.insert(variety);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("品种编号已存在: " + request.getVarietyCode());
        }
        return variety;
    }

    @Transactional
    public Tray createTray(TrayRequest request) {
        if (trayMapper.selectCount(new QueryWrapper<Tray>()
                .eq("tray_code", request.getTrayCode())) > 0) {
            throw new IllegalArgumentException("播种盘编号已存在: " + request.getTrayCode());
        }
        Tray tray = new Tray();
        tray.setTrayCode(request.getTrayCode());
        tray.setSpec(request.getSpec());
        tray.setHoleCount(request.getHoleCount());
        tray.setStatus(PfStatus.TRAY_IDLE);
        try {
            trayMapper.insert(tray);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("播种盘编号已存在: " + request.getTrayCode());
        }
        return tray;
    }

    public List<Greenhouse> listGreenhouses() {
        return greenhouseMapper.selectList(new QueryWrapper<Greenhouse>().orderByAsc("id"));
    }

    public List<Zone> listZones(Long greenhouseId) {
        return zoneMapper.selectList(new QueryWrapper<Zone>()
                .eq(greenhouseId != null, "greenhouse_id", greenhouseId)
                .orderByAsc("id"));
    }

    public List<Variety> listVarieties() {
        return varietyMapper.selectList(new QueryWrapper<Variety>().orderByAsc("id"));
    }

    public List<Tray> listTrays() {
        return trayMapper.selectList(new QueryWrapper<Tray>().orderByAsc("id"));
    }
}
