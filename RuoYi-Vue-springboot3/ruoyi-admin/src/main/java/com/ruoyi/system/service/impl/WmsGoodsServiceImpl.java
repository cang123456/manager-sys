package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.WmsGoodsMapper;
import com.ruoyi.system.domain.WmsGoods;
import com.ruoyi.system.service.IWmsGoodsService;

/**
 * 仓储物品Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-09-10
 */
@Service
public class WmsGoodsServiceImpl implements IWmsGoodsService 
{
    @Autowired
    private WmsGoodsMapper wmsGoodsMapper;

    /**
     * 查询仓储物品
     * 
     * @param goodsId 仓储物品主键
     * @return 仓储物品
     */
    @Override
    public WmsGoods selectWmsGoodsByGoodsId(Long goodsId)
    {
        return wmsGoodsMapper.selectWmsGoodsByGoodsId(goodsId);
    }

    /**
     * 查询仓储物品列表
     * 
     * @param wmsGoods 仓储物品
     * @return 仓储物品
     */
    @Override
    public List<WmsGoods> selectWmsGoodsList(WmsGoods wmsGoods)
    {
        return wmsGoodsMapper.selectWmsGoodsList(wmsGoods);
    }

    /**
     * 新增仓储物品
     * 
     * @param wmsGoods 仓储物品
     * @return 结果
     */
    @Override
    public int insertWmsGoods(WmsGoods wmsGoods)
    {
        wmsGoods.setCreateTime(DateUtils.getNowDate());
        return wmsGoodsMapper.insertWmsGoods(wmsGoods);
    }

    /**
     * 修改仓储物品
     * 
     * @param wmsGoods 仓储物品
     * @return 结果
     */
    @Override
    public int updateWmsGoods(WmsGoods wmsGoods)
    {
        wmsGoods.setUpdateTime(DateUtils.getNowDate());
        return wmsGoodsMapper.updateWmsGoods(wmsGoods);
    }

    /**
     * 批量删除仓储物品
     * 
     * @param goodsIds 需要删除的仓储物品主键
     * @return 结果
     */
    @Override
    public int deleteWmsGoodsByGoodsIds(Long[] goodsIds)
    {
        return wmsGoodsMapper.deleteWmsGoodsByGoodsIds(goodsIds);
    }

    /**
     * 删除仓储物品信息
     * 
     * @param goodsId 仓储物品主键
     * @return 结果
     */
    @Override
    public int deleteWmsGoodsByGoodsId(Long goodsId)
    {
        return wmsGoodsMapper.deleteWmsGoodsByGoodsId(goodsId);
    }
}
