package cn.gov.enterprise.common.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一带主键实体基类。
 *
 * <p>包含主键、创建时间、更新时间和逻辑删除标识，并继承完整审计字段。</p>
 */
@Getter
@Setter
public abstract class BaseIdEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 逻辑删除后的唯一键占位值，正常数据固定为0。 */
    private Long deleteToken;
}
