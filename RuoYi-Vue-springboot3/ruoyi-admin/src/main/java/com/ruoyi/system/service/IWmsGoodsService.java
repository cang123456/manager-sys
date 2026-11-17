package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.WmsGoods;

/**
 * 仓储物品Service接口
 * 
 * @author ruoyi
 * @date 2026-09-10
 */
public interface IWmsGoodsService 
{
    /**
     * 查询仓储物品
     * 
     * @param goodsId 仓储物品主键
     * @return 仓储物品
     */
    public WmsGoods selectWmsGoodsByGoodsId(Long goodsId);

    /**
     * 查询仓储物品列表
     * 
     * @param wmsGoods 仓储物品
     * @return 仓储物品集合
     */
    public List<WmsGoods> selectWmsGoodsList(WmsGoods wmsGoods);

    /**
     * 新增仓储物品
     * 
     * @param wmsGoods 仓储物品
     * @return 结果
     */
    public int insertWmsGoods(WmsGoods wmsGoods);

    /**
     * 修改仓储物品
     * 
     * @param wmsGoods 仓储物品
     * @return 结果
     */
    public int updateWmsGoods(WmsGoods wmsGoods);

    /**
     * 批量删除仓储物品
     * 
     * @param goodsIds 需要删除的仓储物品主键集合
     * @return 结果
     */
    public int deleteWmsGoodsByGoodsIds(Long[] goodsIds);

    /**
     * 删除仓储物品信息
     * 
     * @param goodsId 仓储物品主键
     * @return 结果
     */
    public int deleteWmsGoodsByGoodsId(Long goodsId);
}
