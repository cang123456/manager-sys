package com.ruoyi.system.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 仓储物品对象 wms_goods
 * 
 * @author ruoyi
 * @date 2026-09-10
 */
public class WmsGoods extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 物品主键ID */
    private Long goodsId;

    /** 物品名称 */
    @Excel(name = "物品名称")
    private String goodsName;

    /** 物品编码/物料编号 */
    @Excel(name = "物品编码/物料编号")
    private String goodsNo;

    /** 物品分类ID */
    @Excel(name = "物品分类ID")
    private Long categoryId;

    /** 规格型号 */
    @Excel(name = "规格型号")
    private String spec;

    /** 单位：件/箱/kg */
    @Excel(name = "单位：件/箱/kg")
    private String unit;

    /** 当前库存数量 */
    @Excel(name = "当前库存数量")
    private Long stockNum;

    /** 库存预警值 */
    @Excel(name = "库存预警值")
    private Long warnStock;

    /** 采购成本价 */
    @Excel(name = "采购成本价")
    private BigDecimal purchasePrice;

    /** 参考售价 */
    @Excel(name = "参考售价")
    private BigDecimal salePrice;

    /** 缩略图地址 MinIO */
    @Excel(name = "缩略图地址 MinIO")
    private String thumbUrl;

    /** 原图地址 MinIO */
    @Excel(name = "原图地址 MinIO")
    private String originUrl;

    /** 状态 0正常 1停用 */
    @Excel(name = "状态 0正常 1停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public void setGoodsId(Long goodsId) 
    {
        this.goodsId = goodsId;
    }

    public Long getGoodsId() 
    {
        return goodsId;
    }

    public void setGoodsName(String goodsName) 
    {
        this.goodsName = goodsName;
    }

    public String getGoodsName() 
    {
        return goodsName;
    }

    public void setGoodsNo(String goodsNo) 
    {
        this.goodsNo = goodsNo;
    }

    public String getGoodsNo() 
    {
        return goodsNo;
    }

    public void setCategoryId(Long categoryId) 
    {
        this.categoryId = categoryId;
    }

    public Long getCategoryId() 
    {
        return categoryId;
    }

    public void setSpec(String spec) 
    {
        this.spec = spec;
    }

    public String getSpec() 
    {
        return spec;
    }

    public void setUnit(String unit) 
    {
        this.unit = unit;
    }

    public String getUnit() 
    {
        return unit;
    }

    public void setStockNum(Long stockNum) 
    {
        this.stockNum = stockNum;
    }

    public Long getStockNum() 
    {
        return stockNum;
    }

    public void setWarnStock(Long warnStock) 
    {
        this.warnStock = warnStock;
    }

    public Long getWarnStock() 
    {
        return warnStock;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) 
    {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getPurchasePrice() 
    {
        return purchasePrice;
    }

    public void setSalePrice(BigDecimal salePrice) 
    {
        this.salePrice = salePrice;
    }

    public BigDecimal getSalePrice() 
    {
        return salePrice;
    }

    public void setThumbUrl(String thumbUrl) 
    {
        this.thumbUrl = thumbUrl;
    }

    public String getThumbUrl() 
    {
        return thumbUrl;
    }

    public void setOriginUrl(String originUrl) 
    {
        this.originUrl = originUrl;
    }

    public String getOriginUrl() 
    {
        return originUrl;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setDelFlag(String delFlag) 
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag() 
    {
        return delFlag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("goodsId", getGoodsId())
            .append("goodsName", getGoodsName())
            .append("goodsNo", getGoodsNo())
            .append("categoryId", getCategoryId())
            .append("spec", getSpec())
            .append("unit", getUnit())
            .append("stockNum", getStockNum())
            .append("warnStock", getWarnStock())
            .append("purchasePrice", getPurchasePrice())
            .append("salePrice", getSalePrice())
            .append("thumbUrl", getThumbUrl())
            .append("originUrl", getOriginUrl())
            .append("remark", getRemark())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
