package cn.gov.enterprise.common.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 平台统一Mapper基类，后续可集中扩展批量操作和数据库兼容能力。
 *
 * @param <T> 实体类型
 */
public interface BaseMapperX<T> extends BaseMapper<T> {
}
