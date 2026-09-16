package com.wyy.mes.cal.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.wyy.common.annotation.Excel;
import com.wyy.common.core.domain.BaseEntity;

/**
 * 班组对象 cal_team
 * 
 * @author yinjinlu
 * @date 2022-06-05
 */
public class CalTeam extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 班组ID */
    private Long teamId;

    /** 班组编号 */
    @Excel(name = "班组编号")
    private String teamCode;

    /** 班组名称 */
    @Excel(name = "班组名称")
    private String teamName;

    private String calendarType;

    /** 班组长用户ID。生产任务派给班组时，仅班组长在 APP 接收任务。 */
    private Long leaderUserId;

    /** 班组长登录名 */
    private String leaderUserName;

    /** 班组长姓名 */
    private String leaderNick;

    /** 预留字段1 */
    private String attr1;

    /** 预留字段2 */
    private String attr2;

    /** 预留字段3 */
    private Long attr3;

    /** 预留字段4 */
    private Long attr4;

    public void setTeamId(Long teamId) 
    {
        this.teamId = teamId;
    }

    public Long getTeamId() 
    {
        return teamId;
    }
    public void setTeamCode(String teamCode) 
    {
        this.teamCode = teamCode;
    }

    public String getTeamCode() 
    {
        return teamCode;
    }
    public void setTeamName(String teamName) 
    {
        this.teamName = teamName;
    }

    public String getTeamName() 
    {
        return teamName;
    }
    public void setAttr1(String attr1) 
    {
        this.attr1 = attr1;
    }

    public String getCalendarType() {
        return calendarType;
    }

    public void setCalendarType(String calendarType) {
        this.calendarType = calendarType;
    }

    public Long getLeaderUserId()
    {
        return leaderUserId;
    }

    public void setLeaderUserId(Long leaderUserId)
    {
        this.leaderUserId = leaderUserId;
    }

    public String getLeaderUserName()
    {
        return leaderUserName;
    }

    public void setLeaderUserName(String leaderUserName)
    {
        this.leaderUserName = leaderUserName;
    }

    public String getLeaderNick()
    {
        return leaderNick;
    }

    public void setLeaderNick(String leaderNick)
    {
        this.leaderNick = leaderNick;
    }

    public String getAttr1()
    {
        return attr1;
    }
    public void setAttr2(String attr2) 
    {
        this.attr2 = attr2;
    }

    public String getAttr2() 
    {
        return attr2;
    }
    public void setAttr3(Long attr3) 
    {
        this.attr3 = attr3;
    }

    public Long getAttr3() 
    {
        return attr3;
    }
    public void setAttr4(Long attr4) 
    {
        this.attr4 = attr4;
    }

    public Long getAttr4() 
    {
        return attr4;
    }

    @Override
    public String toString() {
        return "CalTeam{" +
                "teamId=" + teamId +
                ", teamCode='" + teamCode + '\'' +
                ", teamName='" + teamName + '\'' +
                ", calendarType='" + calendarType + '\'' +
                ", leaderUserId=" + leaderUserId +
                ", leaderUserName='" + leaderUserName + '\'' +
                ", leaderNick='" + leaderNick + '\'' +
                ", attr1='" + attr1 + '\'' +
                ", attr2='" + attr2 + '\'' +
                ", attr3=" + attr3 +
                ", attr4=" + attr4 +
                '}';
    }
}
