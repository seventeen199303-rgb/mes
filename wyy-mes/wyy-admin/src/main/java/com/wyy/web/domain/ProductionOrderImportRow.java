package com.wyy.web.domain;

import com.wyy.common.annotation.Excel;

import java.math.BigDecimal;
import java.util.Date;

/** 生产订单 Excel 导入行。首行字段名称必须与模板一致。 */
public class ProductionOrderImportRow
{
    @Excel(name = "订单名称")
    private String productionOrderName;

    @Excel(name = "客户编码")
    private String clientCode;

    @Excel(name = "产品编码")
    private String productCode;

    @Excel(name = "生产数量")
    private BigDecimal quantity;

    @Excel(name = "需求交期", dateFormat = "yyyy-MM-dd")
    private Date requestDate;

    @Excel(name = "备注")
    private String remark;

    public String getProductionOrderName()
    {
        return productionOrderName;
    }

    public void setProductionOrderName(String productionOrderName)
    {
        this.productionOrderName = productionOrderName;
    }

    public String getClientCode()
    {
        return clientCode;
    }

    public void setClientCode(String clientCode)
    {
        this.clientCode = clientCode;
    }

    public String getProductCode()
    {
        return productCode;
    }

    public void setProductCode(String productCode)
    {
        this.productCode = productCode;
    }

    public BigDecimal getQuantity()
    {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity)
    {
        this.quantity = quantity;
    }

    public Date getRequestDate()
    {
        return requestDate;
    }

    public void setRequestDate(Date requestDate)
    {
        this.requestDate = requestDate;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
