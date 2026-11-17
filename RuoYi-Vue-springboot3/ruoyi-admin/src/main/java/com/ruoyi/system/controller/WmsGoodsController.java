package com.ruoyi.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.WmsGoods;
import com.ruoyi.system.service.IWmsGoodsService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 仓储物品Controller
 * 
 * @author ruoyi
 * @date 2026-09-10
 */
@RestController
@RequestMapping("/system/goods")
public class WmsGoodsController extends BaseController
{
    @Autowired
    private IWmsGoodsService wmsGoodsService;

    /**
     * 查询仓储物品列表
     */
    @PreAuthorize("@ss.hasPermi('system:goods:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmsGoods wmsGoods)
    {
        startPage();
        List<WmsGoods> list = wmsGoodsService.selectWmsGoodsList(wmsGoods);
        return getDataTable(list);
    }

    /**
     * 导出仓储物品列表
     */
    @PreAuthorize("@ss.hasPermi('system:goods:export')")
    @Log(title = "仓储物品", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmsGoods wmsGoods)
    {
        List<WmsGoods> list = wmsGoodsService.selectWmsGoodsList(wmsGoods);
        ExcelUtil<WmsGoods> util = new ExcelUtil<WmsGoods>(WmsGoods.class);
        util.exportExcel(response, list, "仓储物品数据");
    }

    /**
     * 获取仓储物品详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:goods:query')")
    @GetMapping(value = "/{goodsId}")
    public AjaxResult getInfo(@PathVariable("goodsId") Long goodsId)
    {
        return success(wmsGoodsService.selectWmsGoodsByGoodsId(goodsId));
    }

    /**
     * 新增仓储物品
     */
    @PreAuthorize("@ss.hasPermi('system:goods:add')")
    @Log(title = "仓储物品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmsGoods wmsGoods)
    {
        return toAjax(wmsGoodsService.insertWmsGoods(wmsGoods));
    }

    /**
     * 修改仓储物品
     */
    @PreAuthorize("@ss.hasPermi('system:goods:edit')")
    @Log(title = "仓储物品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmsGoods wmsGoods)
    {
        return toAjax(wmsGoodsService.updateWmsGoods(wmsGoods));
    }

    /**
     * 删除仓储物品
     */
    @PreAuthorize("@ss.hasPermi('system:goods:remove')")
    @Log(title = "仓储物品", businessType = BusinessType.DELETE)
	@DeleteMapping("/{goodsIds}")
    public AjaxResult remove(@PathVariable Long[] goodsIds)
    {
        return toAjax(wmsGoodsService.deleteWmsGoodsByGoodsIds(goodsIds));
    }
}
